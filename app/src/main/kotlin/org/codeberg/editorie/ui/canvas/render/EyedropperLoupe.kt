package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.codeberg.editorie.data.DropperPreviewData
import org.codeberg.editorie.ui.canvas.CanvasViewport
import kotlin.math.ceil
import kotlin.math.floor

fun DrawScope.drawDropperOverlay(
    viewport: CanvasViewport,
    preview: DropperPreviewData,
    progress: Float,
    dropperPainter: Painter,
    background: Color,
    drawContent: DrawScope.() -> Unit,
) {
    val displayRect = viewport.displayRect
    val fullRect = viewport.fullRect
    val zoom = viewport.zoom
    val pan = viewport.pan
    val dstLeft = displayRect.left
    val dstTop = displayRect.top
    val dstOffsetX = floor(dstLeft).toInt()
    val dstOffsetY = floor(dstTop).toInt()
    val dstW = (ceil(dstLeft + displayRect.width).toInt() - dstOffsetX).coerceAtLeast(1)
    val dstH = (ceil(dstTop + displayRect.height).toInt() - dstOffsetY).coerceAtLeast(1)
    val finger = Offset(
        (dstOffsetX + (preview.pixelX + 0.5f - fullRect.left) / fullRect.width * dstW) * zoom + pan.x,
        (dstOffsetY + (preview.pixelY + 0.5f - fullRect.top) / fullRect.height * dstH) * zoom + pan.y
    )
    val circleRadius = 52.dp.toPx()
    val upOffset = 54.dp.toPx()
    val circleCenter = Offset(finger.x, finger.y - upOffset - circleRadius)
    val pxPerImagePx = maxOf(dstW / fullRect.width, dstH / fullRect.height) * zoom
    val loupePixelsAcross = 11f
    val mag = (circleRadius * 2f) / (loupePixelsAcross * pxPerImagePx)
    val circlePath = Path().apply { addOval(Rect(circleCenter, circleRadius)) }
    val drawableSize = Size(circleRadius * 3.2f, circleRadius * 3.2f)
    val drawableTopLeft = Offset(
        circleCenter.x - drawableSize.width / 2f,
        circleCenter.y - drawableSize.height / 2f
    )
    val growPivot = finger
    withTransform({
        translate(growPivot.x, growPivot.y)
        scale(progress, progress, Offset.Zero)
        translate(-growPivot.x, -growPivot.y)
    }) {
        translate(drawableTopLeft.x, drawableTopLeft.y) {
            with(dropperPainter) { draw(drawableSize) }
        }
        clipPath(circlePath) {
            drawRect(color = background)
            withTransform({
                translate(circleCenter.x - mag * finger.x, circleCenter.y - mag * finger.y)
                scale(mag, mag, Offset.Zero)
            }) {
                drawContent()
            }
        }
        val marker = circleRadius * 2f / loupePixelsAcross
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(circleCenter.x - marker / 2f, circleCenter.y - marker / 2f),
            size = Size(marker, marker),
            style = Stroke(width = 1.5.dp.toPx())
        )
        val boxW = 160.dp.toPx()
        val boxH = 56.dp.toPx()
        val boxGap = 10.dp.toPx()
        val boxCorner = 10.dp.toPx()
        var boxTop = circleCenter.y - circleRadius - boxGap - boxH
        var boxLeft = circleCenter.x - boxW / 2f
        if (boxTop < 0f) {
            boxTop = circleCenter.y - boxH / 2f
            boxLeft = circleCenter.x + circleRadius + boxGap
            if (boxLeft + boxW > size.width) {
                boxLeft = circleCenter.x - circleRadius - boxGap - boxW
            }
        }
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.8f),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(boxCorner)
        )
        val swatch = 22.dp.toPx()
        val swatchPad = 10.dp.toPx()
        drawRoundRect(
            color = preview.color,
            topLeft = Offset(boxLeft + swatchPad, boxTop + (boxH - swatch) / 2f),
            size = Size(swatch, swatch),
            cornerRadius = CornerRadius(6.dp.toPx())
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 13.sp.toPx()
            typeface = Typeface.MONOSPACE
        }
        val textX = boxLeft + swatchPad * 2 + swatch
        drawContext.canvas.nativeCanvas.drawText(
            "(${preview.pixelX}, ${preview.pixelY})", textX, boxTop + boxH * 0.4f, textPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            preview.hex, textX, boxTop + boxH * 0.75f, textPaint
        )
    }
}
