package org.codeberg.editorie.options.drawing

// SPDX-License-Identifier: MIT

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.codeberg.editorie.data.DropperPreviewData
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.options.adjust.AdjustState
import org.codeberg.editorie.options.save.ImageRepository
import kotlin.math.roundToInt

fun Path.translated(dx: Float, dy: Float): Path =
    Path(this).apply { transform(Matrix().apply { setTranslate(dx, dy) }) }

fun Path.scaled(sx: Float, sy: Float): Path =
    Path(this).apply { transform(Matrix().apply { setScale(sx, sy) }) }

fun Path.mapped(src: RectF, dst: RectF): Path =
    Path(this).apply {
        transform(Matrix().apply {
            setRectToRect(
                src,
                dst,
                Matrix.ScaleToFit.FILL
            )
        })
    }

internal object DrawingOps {
    fun buildStrokeLayer(
        points: List<Offset>,
        strokeWidthPx: Float,
        penColor: Color,
        tool: DrawTool,
        groupId: Long,
    ): EditorLayer.Stroke? {
        if (points.size < 2) return null
        return EditorLayer.Stroke(
            DrawnStroke(
                points = points, color = penColor, strokeWidthPx = strokeWidthPx, tool = tool
            ),
            groupId = groupId,
        )
    }

    fun eraseFromLayersExact(
        layers: List<EditorLayer>,
        eraserPoints: List<Offset>,
        eraserWidthPx: Float,
    ): List<EditorLayer>? {
        if (eraserPoints.size < 2) return null
        val eraserOutline = strokeToFillPath(eraserPoints, eraserWidthPx)
        var changed = false

        val result = layers.flatMap { layer ->
            when (layer) {
                is EditorLayer.EraseStroke -> {
                    if (!layer.path.intersects(eraserOutline)) {
                        listOf(layer)
                    } else {
                        changed = true
                        val cut = Path(layer.path).apply {
                            op(eraserOutline, Path.Op.DIFFERENCE)
                        }
                        if (cut.isEmptyRegion()) emptyList() else listOf(
                            layer.copy(path = cut, id = System.nanoTime())
                        )
                    }
                }

                is EditorLayer.Stroke -> {
                    val strokeOutline =
                        strokeToFillPath(layer.stroke.points, layer.stroke.strokeWidthPx)
                    if (!strokeOutline.intersects(eraserOutline)) {
                        listOf(layer)
                    } else {
                        changed = true
                        val cut = Path(strokeOutline).apply {
                            op(eraserOutline, Path.Op.DIFFERENCE)
                        }
                        if (cut.isEmptyRegion()) emptyList() else listOf(
                            EditorLayer.EraseStroke(
                                path = cut,
                                color = layer.stroke.color,
                                groupId = layer.groupId,
                            )
                        )
                    }
                }

                else -> listOf(layer)
            }
        }
        return if (changed) result else null
    }

    private fun Path.intersects(other: Path): Boolean {
        val intersection = Path(this)
        return intersection.op(other, Path.Op.INTERSECT) && !intersection.isEmptyRegion()
    }

    private fun strokeToFillPath(points: List<Offset>, widthPx: Float): Path {
        val centerline = Path().apply {
            moveTo(points.first().x, points.first().y)
            if (points.size == 2) {
                lineTo(points[1].x, points[1].y)
            } else {
                for (i in 0 until points.size - 1) {
                    val cur = points[i]
                    val nxt = points[i + 1]
                    quadTo(cur.x, cur.y, (cur.x + nxt.x) / 2f, (cur.y + nxt.y) / 2f)
                }
                lineTo(points.last().x, points.last().y)
            }
        }
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = widthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val outline = Path()
        strokePaint.getFillPath(centerline, outline)
        return outline
    }

    private fun Path.isEmptyRegion(): Boolean {
        val bounds = RectF()
        computeBounds(bounds, true)
        return isEmpty || bounds.width() <= 0f || bounds.height() <= 0f
    }

    suspend fun sampleColor(
        source: Bitmap?,
        layers: List<EditorLayer>,
        adjust: AdjustState,
        scaledDensity: Float,
        sampleX: Int,
        sampleY: Int,
        context: Context,
    ): Color = withContext(Dispatchers.Default) {
        if (source == null) return@withContext Color.Transparent
        val clampedX = sampleX.coerceIn(0, source.width - 1)
        val clampedY = sampleY.coerceIn(0, source.height - 1)
        val pixelRect = Rect(
            clampedX.toFloat(), clampedY.toFloat(),
            (clampedX + 1).coerceAtMost(source.width).toFloat(),
            (clampedY + 1).coerceAtMost(source.height).toFloat(),
        )
        val repository = ImageRepository(context)
        val rendered = repository.renderEditedBitmap(
            source = source,
            cropRect = pixelRect,
            layers = layers,
            adjust = adjust,
            scaledDensity = scaledDensity,
        )
        Color(rendered[0, 0])
    }

    /**
     * Renders a small region of composited pixels around a sample point and
     * returns them plus the exact sampled color, for the live eyedropper
     * overlay. The region is centered on the sample point and clamped to the
     * image bounds.
     */
    suspend fun samplePreviewRegion(
        source: Bitmap?,
        layers: List<EditorLayer>,
        adjust: AdjustState,
        scaledDensity: Float,
        sampleX: Int,
        sampleY: Int,
        context: Context,
        radius: Int = 4,
    ): DropperPreviewData? = withContext(Dispatchers.Default) {
        if (source == null) return@withContext null
        val clampedX = sampleX.coerceIn(0, source.width - 1)
        val clampedY = sampleY.coerceIn(0, source.height - 1)
        val left = (clampedX - radius).coerceAtLeast(0)
        val top = (clampedY - radius).coerceAtLeast(0)
        val right = (clampedX + radius + 1).coerceAtMost(source.width)
        val bottom = (clampedY + radius + 1).coerceAtMost(source.height)
        val pixelRect = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        val repository = ImageRepository(context)
        val rendered = repository.renderEditedBitmap(
            source = source,
            cropRect = pixelRect,
            layers = layers,
            adjust = adjust,
            scaledDensity = scaledDensity,
        )
        val pixels = IntArray(rendered.width * rendered.height)
        rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
        val centerIndex = (clampedY - top) * rendered.width + (clampedX - left)
        val center = Color(pixels[centerIndex])
        val hex = "#%02X%02X%02X".format(
            (center.red * 255).roundToInt(),
            (center.green * 255).roundToInt(),
            (center.blue * 255).roundToInt(),
        )
        DropperPreviewData(
            pixels = pixels.toList(),
            regionLeft = left,
            regionTop = top,
            regionWidth = rendered.width,
            regionHeight = rendered.height,
            radius = radius,
            color = center,
            pixelX = clampedX,
            pixelY = clampedY,
            hex = hex,
        )
    }
}
