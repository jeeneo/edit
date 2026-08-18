package org.codeberg.editorie.options.stickers

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import org.codeberg.editorie.App
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

internal object StickerOps {
    fun placeNewSticker(
        source: StickerSource,
        viewCrop: Rect,
        sizeFraction: Float = 0.3f,
    ): StickerOverlay {
        val sizePx = minOf(viewCrop.width, viewCrop.height) * sizeFraction
        return StickerOverlay(
            source = source,
            positionPx = Offset(
                viewCrop.left + (viewCrop.width - sizePx) / 2f,
                viewCrop.top + (viewCrop.height - sizePx) / 2f,
            ),
            widthPx = sizePx,
            heightPx = sizePx,
        )
    }

    fun placeOverlayImage(
        bitmapWidth: Int,
        bitmapHeight: Int,
        source: StickerSource,
        viewCrop: Rect,
        sizeFraction: Float = 0.6f,
    ): StickerOverlay {
        val sizePx = minOf(viewCrop.width, viewCrop.height) * sizeFraction
        val safeW = bitmapWidth.coerceAtLeast(1)
        val safeH = bitmapHeight.coerceAtLeast(1)
        val aspect = safeW.toFloat() / safeH
        val w = if (aspect >= 1f) sizePx else sizePx * aspect
        val h = if (aspect >= 1f) sizePx / aspect else sizePx
        return StickerOverlay(
            source = source,
            positionPx = Offset(
                viewCrop.left + (viewCrop.width - w) / 2f,
                viewCrop.top + (viewCrop.height - h) / 2f,
            ),
            widthPx = w,
            heightPx = h,
        )
    }

}

object StickerBitmapCache {
    private sealed class Key {
        data class Asset(
            val assetPath: String,
            val widthPx: Int,
            val heightPx: Int,
            val renderMode: StickerRenderMode,
        ) : Key()

        class BitmapKey(
            val bitmap: Bitmap,
            val overlayId: Long,
            val widthPx: Int,
            val heightPx: Int,
            val renderMode: StickerRenderMode,
        ) : Key() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is BitmapKey) return false
                return bitmap === other.bitmap && overlayId == other.overlayId && widthPx == other.widthPx && heightPx == other.heightPx && renderMode == other.renderMode
            }

            override fun hashCode(): Int {
                var result = System.identityHashCode(bitmap)
                result = 31 * result + overlayId.hashCode()
                result = 31 * result + widthPx
                result = 31 * result + heightPx
                result = 31 * result + renderMode.hashCode()
                return result
            }
        }
    }

    private val cache = LinkedHashMap<Key, Bitmap>(256, 0.75f, true)
    fun get(overlay: StickerOverlay, widthPx: Int, heightPx: Int): Bitmap? {
        val key = when (overlay.source) {
            is StickerSource.Asset -> Key.Asset(
                overlay.source.assetPath, widthPx, heightPx, overlay.renderMode
            )

            is StickerSource.Bitmap -> Key.BitmapKey(
                bitmap = overlay.source.bitmap,
                overlayId = overlay.id,
                widthPx = widthPx,
                heightPx = heightPx,
                renderMode = overlay.renderMode,
            )
        }
        cache[key]?.let { return it }
        val bmp = StickerRenderer.render(overlay.source, widthPx, heightPx, overlay.renderMode)
            ?: return null
        if (cache.size >= 256) cache.remove(cache.keys.first())
        cache[key] = bmp
        return bmp
    }
}

private data class SvgPath(
    val path: Path,
    val fillColor: Int?,
    val fillRuleEvenOdd: Boolean,
)

object StickerRenderer {
    fun render(
        source: StickerSource,
        widthPx: Int,
        heightPx: Int,
        renderMode: StickerRenderMode = StickerRenderMode(),
    ): Bitmap? {
        return when (source) {
            is StickerSource.Bitmap -> {
                if (renderMode.preserveAspectRatio) {
                    val srcAspect = source.bitmap.width.toFloat() / source.bitmap.height
                    val dstAspect = widthPx.toFloat() / heightPx
                    val (scaledW, scaledH) = if (dstAspect > srcAspect) {
                        ((heightPx * srcAspect).toInt().coerceAtLeast(1)) to heightPx
                    } else {
                        widthPx to ((widthPx / srcAspect).toInt().coerceAtLeast(1))
                    }
                    scaleBitmap(source.bitmap, scaledW, scaledH)
                } else {
                    scaleBitmap(source.bitmap, widthPx, heightPx)
                }
            }

            is StickerSource.Asset -> renderAsset(source, widthPx, heightPx, renderMode)
        }
    }

    private fun renderAsset(
        source: StickerSource.Asset,
        widthPx: Int,
        heightPx: Int,
        renderMode: StickerRenderMode,
    ): Bitmap? {
        if (!source.assetPath.endsWith(".svg", ignoreCase = true)) {
            return rasterFromAsset(source, widthPx, heightPx)
        }
        val hasOutline = renderMode.outlineColor != null
        val pad = if (hasOutline) (renderMode.outlineThicknessPx / 2f).toInt() + 1 else 0
        val innerW = (widthPx - pad * 2).coerceAtLeast(1)
        val innerH = (heightPx - pad * 2).coerceAtLeast(1)
        val paths = extractSvgPaths(
            source, innerW, innerH, renderMode.preserveAspectRatio
        ) ?: return null
        val result = createBitmap(widthPx, heightPx)
        val canvas = Canvas(result)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = renderMode.outlineColor?.let { oc ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = renderMode.outlineThicknessPx
                color = oc.toArgb()
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
        }
        val svgDefaultFill = android.graphics.Color.WHITE
        canvas.withTranslation(pad.toFloat(), pad.toFloat()) {
            for (svgPath in paths) {
                fillPaint.color = when (renderMode.fillColor) {
                    null -> Color.Transparent.toArgb()
                    Color.Unspecified -> svgPath.fillColor ?: svgDefaultFill
                    else -> renderMode.fillColor.toArgb()
                }
                drawPath(svgPath.path, fillPaint)
            }
            strokePaint?.let { sp ->
                for (svgPath in paths) {
                    drawPath(svgPath.path, sp)
                }
            }
        }
        return result
    }

    fun renderThumbnail(source: StickerSource, sizePx: Int = 128): Bitmap? =
        render(source, sizePx, sizePx)

    private fun rasterFromAsset(source: StickerSource.Asset, widthPx: Int, heightPx: Int): Bitmap? =
        runCatching {
            App.ctx.assets.open(source.assetPath).use { stream ->
                val raw = BitmapFactory.decodeStream(stream) ?: return null
                if (raw.width == widthPx && raw.height == heightPx) raw
                else raw.scale(widthPx, heightPx)
            }
        }.getOrNull()

    private fun scaleBitmap(
        src: Bitmap, dstWidth: Int, dstHeight: Int
    ): Bitmap {
        val scaleW = dstWidth.toFloat() / src.width
        val scaleH = dstHeight.toFloat() / src.height
        val matrix = Matrix().apply { setScale(scaleW, scaleH) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun String.parseSvgColor(): Int? = when (this.lowercase().trim()) {
        "none", "transparent" -> android.graphics.Color.TRANSPARENT
        "white" -> android.graphics.Color.WHITE
        "black" -> android.graphics.Color.BLACK
        else -> runCatching { this.toColorInt() }.getOrNull()
    }

    private fun extractSvgPaths(
        source: StickerSource.Asset,
        widthPx: Int,
        heightPx: Int,
        preserveAspectRatio: Boolean,
    ): List<SvgPath>? = runCatching {
        App.ctx.assets.open(source.assetPath).use { stream ->
            val parser =
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, null) }
            val paths = mutableListOf<SvgPath>()
            val classColors = mutableMapOf<String, Int?>()
            var viewportWidth = 0f
            var viewportHeight = 0f
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "svg" -> {
                            val vb = parser.getAttributeValue(null, "viewBox")
                            if (vb != null) {
                                val parts = vb.trim().split(Regex("[,\\s]+"))
                                viewportWidth = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                                viewportHeight = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
                            }
                            if (viewportWidth == 0f) viewportWidth =
                                parser.getAttributeValue(null, "width")?.removeSuffix("px")
                                    ?.toFloatOrNull() ?: 0f
                            if (viewportHeight == 0f) viewportHeight =
                                parser.getAttributeValue(null, "height")?.removeSuffix("px")
                                    ?.toFloatOrNull() ?: 0f
                        }

                        "style" -> {
                            val css = parser.nextText()
                            Regex(
                                """\.(\w+)\s*\{[^}]*fill\s*:\s*(#[0-9a-fA-F]{3,8}|[a-zA-Z]+)"""
                            ).findAll(css).forEach { m ->
                                classColors[m.groupValues[1]] = m.groupValues[2].parseSvgColor()
                            }
                        }

                        "path" -> {
                            val d = parser.getAttributeValue(null, "d") ?: continue
                            val fillColor: Int? = run {
                                val style = parser.getAttributeValue(null, "style")
                                if (style != null) {
                                    val styleFill = Regex(
                                        """fill\s*:\s*(#[0-9a-fA-F]{3,8}|[a-zA-Z]+)"""
                                    ).find(style)?.groupValues?.get(1)?.parseSvgColor()
                                    if (styleFill != null) return@run styleFill
                                }
                                val cls = parser.getAttributeValue(null, "class")
                                if (cls != null) {
                                    val classFill = classColors[cls.trim()]
                                    if (classFill != null) return@run classFill
                                }
                                val fillAttr = parser.getAttributeValue(null, "fill")
                                if (fillAttr != null) {
                                    return@run fillAttr.parseSvgColor()
                                }
                                null
                            }
                            val fillRuleEvenOdd = run {
                                val frAttr = parser.getAttributeValue(null, "fill-rule")
                                if (frAttr != null) {
                                    frAttr.equals("evenodd", ignoreCase = true)
                                } else {
                                    val style = parser.getAttributeValue(null, "style")
                                    style?.let { s ->
                                        Regex("""fill-rule\s*:\s*(\w+)""").find(s)?.groupValues?.get(
                                            1
                                        )?.equals("evenodd", ignoreCase = true) == true
                                    } == true
                                }
                            }
                            val path = PathParser.createPathFromPathData(d)
                            if (fillRuleEvenOdd) {
                                path.fillType = Path.FillType.EVEN_ODD
                            }
                            if (viewportWidth > 0f && viewportHeight > 0f) {
                                val matrix = if (preserveAspectRatio) {
                                    val scale = minOf(
                                        widthPx / viewportWidth, heightPx / viewportHeight
                                    )
                                    val dx = (widthPx - viewportWidth * scale) / 2f
                                    val dy = (heightPx - viewportHeight * scale) / 2f
                                    Matrix().apply {
                                        setScale(scale, scale)
                                        postTranslate(dx, dy)
                                    }
                                } else {
                                    Matrix().apply {
                                        setScale(
                                            widthPx / viewportWidth, heightPx / viewportHeight
                                        )
                                    }
                                }
                                path.transform(matrix)
                            }
                            paths.add(SvgPath(path, fillColor, fillRuleEvenOdd))
                        }
                    }
                }
                eventType = parser.next()
            }
            paths.takeIf { it.isNotEmpty() }
        }
    }.getOrNull()
}
