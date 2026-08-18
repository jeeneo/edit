package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.ImageOverlayState
import org.codeberg.editorie.options.drawing.DrawTool
import org.codeberg.editorie.ui.canvas.CanvasViewport

fun buildPath(points: List<Offset>, fromPixel: (Offset) -> Offset): Path? {
    if (points.size < 2) return null
    return Path().apply {
        val first = fromPixel(points.first())
        moveTo(first.x, first.y)
        if (points.size == 2) {
            val second = fromPixel(points[1])
            lineTo(second.x, second.y)
            return@apply
        }
        for (i in 0 until points.size - 1) {
            val current = fromPixel(points[i])
            val next = fromPixel(points[i + 1])
            val midX = (current.x + next.x) / 2f
            val midY = (current.y + next.y) / 2f
            quadraticTo(current.x, current.y, midX, midY)
        }
        val last = fromPixel(points.last())
        lineTo(last.x, last.y)
    }
}

fun isLiveErasing(
    overlay: ImageOverlayState,
    panel: EditorPanel,
    pendingPoints: List<Offset>
): Boolean =
    overlay.drawTool is DrawTool.Eraser && pendingPoints.size >= 2 && panel == EditorPanel.Draw

fun DrawScope.drawAllLayers(
    ctx: CanvasRenderContext,
    overlay: ImageOverlayState,
    panel: EditorPanel,
    pendingPoints: List<Offset>,
) {
    if (!isLiveErasing(overlay, panel, pendingPoints)) {
        drawLayers(overlay.layers, ctx)
    } else {
        drawLiveErasePreview(ctx, overlay, pendingPoints)
    }

    if (pendingPoints.isNotEmpty() && overlay.drawTool is DrawTool.Brush) {
        buildPath(pendingPoints, ctx.viewport::fromPixel)?.let { path ->
            val strokeWidthPx = ctx.viewport.screenToPixelWidth(overlay.penWidth)
            val screenWidth =
                (strokeWidthPx / ctx.viewport.sourceRect.width) * ctx.viewport.displayRect.width
            drawPath(
                path, overlay.penColor, style = Stroke(
                    screenWidth, cap = StrokeCap.Round, join = StrokeJoin.Round
                )
            )
        }
    }
}

fun DrawScope.drawLiveErasePreview(
    ctx: CanvasRenderContext,
    overlay: ImageOverlayState,
    pendingPoints: List<Offset>,
) {
    val viewport = ctx.viewport
    overlay.layers.forEach { layer ->
        if (layer is EditorLayer.Background) drawLayer(layer, ctx)
    }

    val bounds = Rect(
        viewport.toContent(Offset.Zero), viewport.toContent(Offset(size.width, size.height))
    )
    drawContext.canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint())
    overlay.layers.forEach { layer ->
        if (layer is EditorLayer.Stroke || layer is EditorLayer.EraseStroke) {
            drawLayer(layer, ctx)
        }
    }

    buildPath(pendingPoints, viewport::fromPixel)?.let { eraserPath ->
        val pxWidth = viewport.screenToPixelWidth(overlay.penWidth)
        val screenWidth = (pxWidth / viewport.sourceRect.width) * viewport.displayRect.width
        drawPath(
            eraserPath,
            color = Color.Transparent,
            style = Stroke(
                screenWidth, cap = StrokeCap.Round, join = StrokeJoin.Round
            ),
            blendMode = BlendMode.Clear,
        )
    }
    drawContext.canvas.restore()

    overlay.layers.forEach { layer ->
        if (layer is EditorLayer.Text || layer is EditorLayer.Sticker) {
            drawLayer(layer, ctx)
        }
    }
}

fun DrawScope.drawPenSizePreview(
    viewport: CanvasViewport,
    panel: EditorPanel,
    drawTool: DrawTool,
    penWidth: Float,
    penColor: Color,
    pendingPoints: List<Offset>,
    canvasSize: IntSize,
    showPenSizePreview: Boolean,
) {
    if (panel != EditorPanel.Draw) return
    if (drawTool is DrawTool.Eyedropper) return
    val center = when {
        pendingPoints.isNotEmpty() -> viewport.fromPixel(pendingPoints.last())
        showPenSizePreview -> {
            if (canvasSize == IntSize.Zero) return
            viewport.toContent(Offset(canvasSize.width / 2f, canvasSize.height / 2f))
        }

        else -> null
    } ?: return
    val radius = penWidth / 2f
    val isEraser = drawTool is DrawTool.Eraser
    val baseColor = if (isEraser) Color.White else penColor
    val factor = 0.8f
    val tint = Color(
        red = baseColor.red * factor,
        green = baseColor.green * factor,
        blue = baseColor.blue * factor,
        alpha = baseColor.alpha,
    )
    drawCircle(color = tint.copy(alpha = 0.18f), radius = radius, center = center)
    drawCircle(
        color = tint,
        radius = radius,
        center = center,
        style = Stroke(width = 1.5f / viewport.zoom)
    )
}
