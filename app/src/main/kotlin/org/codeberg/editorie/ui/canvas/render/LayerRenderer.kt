package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.options.drawing.mapped
import org.codeberg.editorie.options.stickers.StickerBitmapCache
import org.codeberg.editorie.options.text.makeStaticLayout
import org.codeberg.editorie.options.text.toTextPaint
import org.codeberg.editorie.ui.canvas.TRANSFORM_ROTATION_HANDLE
import org.codeberg.editorie.ui.canvas.drawTransformHandles
import kotlin.math.ceil
import kotlin.math.floor

fun DrawScope.drawLayer(layer: EditorLayer, ctx: CanvasRenderContext) {
    val viewport = ctx.viewport
    val displayRect = viewport.displayRect
    val sourceRect = viewport.sourceRect
    when (layer) {
        is EditorLayer.Background -> {
            val dstLeft = displayRect.left
            val dstTop = displayRect.top
            val dstWidth = displayRect.width
            val dstHeight = displayRect.height
            val cx = dstLeft + dstWidth / 2f
            val cy = dstTop + dstHeight / 2f
            val dstOffset = IntOffset(
                floor(dstLeft).toInt(), floor(dstTop).toInt()
            )
            val dstSize = IntSize(
                (ceil(dstLeft + dstWidth).toInt() - dstOffset.x).coerceAtLeast(1),
                (ceil(dstTop + dstHeight).toInt() - dstOffset.y).coerceAtLeast(1)
            )
            val needsTransform =
                ctx.rotationDeg % 360f != 0f || ctx.flipHorizontal || ctx.flipVertical
            val drawBitmap: DrawScope.() -> Unit = {
                drawImage(
                    ctx.drawImageBitmap,
                    srcOffset = IntOffset(
                        (viewport.fullRect.left * ctx.drawScale).toInt(),
                        (viewport.fullRect.top * ctx.drawScale).toInt()
                    ),
                    srcSize = IntSize(
                        (viewport.fullRect.width * ctx.drawScale).toInt().coerceAtLeast(1),
                        (viewport.fullRect.height * ctx.drawScale).toInt().coerceAtLeast(1)
                    ),
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    colorFilter = ctx.colorFilter,
                    filterQuality = FilterQuality.None,
                )
            }
            if (needsTransform) {
                withTransform({
                    if (ctx.rotationDeg % 360f != 0f) rotate(
                        ctx.rotationDeg, Offset(cx, cy)
                    )
                    if (ctx.flipHorizontal || ctx.flipVertical) scale(
                        scaleX = if (ctx.flipHorizontal) -1f else 1f,
                        scaleY = if (ctx.flipVertical) -1f else 1f,
                        pivot = Offset(cx, cy),
                    )
                }) {
                    drawBitmap()
                }
            } else {
                drawBitmap()
            }
        }

        is EditorLayer.Stroke -> {
            val s = layer.stroke
            buildPath(s.points, viewport::fromPixel)?.let { path ->
                val strokeWidth =
                    (s.strokeWidthPx / sourceRect.width) * displayRect.width
                drawPath(
                    path, s.color, style = Stroke(
                        strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round
                    )
                )
            }
        }

        is EditorLayer.EraseStroke -> {
            val src = RectF(
                sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom
            )
            val dst = RectF(
                displayRect.left, displayRect.top, displayRect.right, displayRect.bottom
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = layer.color.toArgb()
            }
            drawContext.canvas.nativeCanvas.drawPath(
                layer.path.mapped(src, dst), paint
            )
        }

        is EditorLayer.Text -> {
            val textOverlay = layer.overlay
            val sp = viewport.fromPixel(textOverlay.positionPx)
            val isSelected = textOverlay.id == ctx.selectedTextId
            val fontPxInImage = textOverlay.fontSizeSp.sp.toPx()
            val screenFontPx = fontPxInImage * (displayRect.width / sourceRect.width)
            val tp = textOverlay.toTextPaint(screenFontPx, textOverlay.color.toArgb())
            val screenBoxWFloat =
                textOverlay.boxWidthPx * displayRect.width / sourceRect.width
            val screenBoxW = screenBoxWFloat.toInt().coerceAtLeast(1)
            val layout = makeStaticLayout(
                textOverlay.text,
                tp,
                screenBoxW,
                textOverlay.align,
                textOverlay.wordWrap
            )
            val autoH = layout.height.toFloat()
            val screenBoxH =
                textOverlay.boxHeightPx?.let { it * displayRect.height / sourceRect.height }
                    ?: autoH
            val centerX = sp.x + screenBoxWFloat / 2f
            val centerY = sp.y + screenBoxH / 2f
            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.translate(centerX, centerY)
            nativeCanvas.rotate(textOverlay.rotation)
            nativeCanvas.translate(-screenBoxWFloat / 2f, -screenBoxH / 2f)
            nativeCanvas.clipRect(0f, 0f, screenBoxWFloat, screenBoxH)
            layout.draw(nativeCanvas)
            if (isSelected) {
                nativeCanvas.restore()
                nativeCanvas.save()
                nativeCanvas.translate(centerX, centerY)
                nativeCanvas.rotate(textOverlay.rotation)
                nativeCanvas.translate(-screenBoxWFloat / 2f, -screenBoxH / 2f)
                drawTransformHandles(
                    nativeCanvas = nativeCanvas,
                    widthPx = screenBoxWFloat,
                    heightPx = screenBoxH,
                    zoom = viewport.zoom,
                    handlePx = ctx.handlePx,
                    rotationHandleOffsetPx = TRANSFORM_ROTATION_HANDLE.dp.toPx() / viewport.zoom,
                    colors = ctx.transformColors
                )
            }
            nativeCanvas.restore()
        }

        is EditorLayer.Sticker -> {
            val stickerOverlay = layer.overlay
            val sp = viewport.fromPixel(stickerOverlay.positionPx)
            val screenW = stickerOverlay.widthPx * displayRect.width / sourceRect.width
            val screenH =
                stickerOverlay.heightPx * displayRect.height / sourceRect.height
            val bmp = StickerBitmapCache.get(
                overlay = stickerOverlay,
                widthPx = screenW.toInt().coerceAtLeast(1),
                heightPx = screenH.toInt().coerceAtLeast(1)
            ) ?: return
            val centerX = sp.x + screenW / 2f
            val centerY = sp.y + screenH / 2f
            val native = drawContext.canvas.nativeCanvas
            native.withSave {
                translate(centerX, centerY)
                rotate(stickerOverlay.rotation)
                translate(-screenW / 2f, -screenH / 2f)
                drawBitmap(bmp, 0f, 0f, null)
                if (stickerOverlay.id == ctx.selectedStickerId) {
                    drawTransformHandles(
                        nativeCanvas = this,
                        widthPx = screenW,
                        heightPx = screenH,
                        zoom = viewport.zoom,
                        handlePx = ctx.handlePx,
                        rotationHandleOffsetPx = TRANSFORM_ROTATION_HANDLE.dp.toPx() / viewport.zoom,
                        colors = ctx.transformColors
                    )
                }
            }
        }
    }
}

fun DrawScope.drawLayers(layers: List<EditorLayer>, ctx: CanvasRenderContext) {
    layers.forEach { drawLayer(it, ctx) }
}
