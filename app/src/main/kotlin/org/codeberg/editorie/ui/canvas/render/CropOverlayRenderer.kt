package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withSave
import org.codeberg.editorie.options.transform.McuInfo
import org.codeberg.editorie.ui.canvas.CanvasViewport
import org.codeberg.editorie.ui.canvas.TransformBoxColors
import org.codeberg.editorie.ui.canvas.drawTransformHandles

fun DrawScope.drawMCUOverlay(
    cropMode: Boolean,
    showMcuGrid: Boolean,
    mcuInfo: McuInfo?,
    viewport: CanvasViewport,
) {
    if (!(showMcuGrid && cropMode)) return
    val info = mcuInfo ?: return
    val displayRect = viewport.displayRect
    val sourceRect = viewport.sourceRect
    val mcuW = info.mcuWidth.toFloat()
    val mcuH = info.mcuHeight.toFloat()
    if (mcuW > 0f && mcuH > 0f) {
        val stepX = mcuW * displayRect.width / sourceRect.width
        val stepY = mcuH * displayRect.height / sourceRect.height
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(170, 255, 64, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1f / viewport.zoom
        }
        val nc = drawContext.canvas.nativeCanvas
        val sx =
            displayRect.left - ((sourceRect.left % mcuW) * displayRect.width / sourceRect.width)
        val sy =
            displayRect.top - ((sourceRect.top % mcuH) * displayRect.height / sourceRect.height)
        var x = sx; while (x <= displayRect.right) {
            nc.drawLine(x, displayRect.top, x, displayRect.bottom, gp); x += stepX
        }
        var y = sy; while (y <= displayRect.bottom) {
            nc.drawLine(displayRect.left, y, displayRect.right, y, gp); y += stepY
        }
    }
}

fun DrawScope.drawCropOverlay(
    cropMode: Boolean,
    viewport: CanvasViewport,
    cropRectDisplay: Rect?,
    transformColors: TransformBoxColors,
    handlePx: Float,
) {
    if (!cropMode) return
    val selection = cropRectDisplay ?: return
    val displayRect = viewport.displayRect
    val zoom = viewport.zoom
    drawContext.canvas.nativeCanvas.apply {
        withSave {
            val dimPaint = Paint().apply {
                color = transformColors.scrim
                style = Paint.Style.FILL
            }
            val overdraw = 1f / zoom
            val outerL = displayRect.left - overdraw
            val outerT = displayRect.top - overdraw
            val outerR = displayRect.right + overdraw
            val outerB = displayRect.bottom + overdraw
            drawRect(outerL, outerT, outerR, selection.top, dimPaint)
            drawRect(outerL, selection.bottom, outerR, outerB, dimPaint)
            drawRect(
                outerL,
                selection.top,
                selection.left,
                selection.bottom,
                dimPaint
            )
            drawRect(
                selection.right,
                selection.top,
                outerR,
                selection.bottom,
                dimPaint
            )
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = transformColors.gridLines
                style = Paint.Style.STROKE
                strokeWidth = 2f / zoom
            }
            drawRoundRect(
                selection.left,
                selection.top,
                selection.right,
                selection.bottom,
                4f / zoom,
                4f / zoom,
                borderPaint
            )
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = transformColors.gridLines
                style = Paint.Style.STROKE
                strokeWidth = 1f / zoom
            }
            val thirdWidth = selection.width / 3f
            val thirdHeight = selection.height / 3f
            for (index in 1..2) {
                val x = selection.left + thirdWidth * index
                drawLine(x, selection.top, x, selection.bottom, gridPaint)
                val y = selection.top + thirdHeight * index
                drawLine(selection.left, y, selection.right, y, gridPaint)
            }
            translate(selection.left, selection.top)
            drawTransformHandles(
                nativeCanvas = this,
                widthPx = selection.width,
                heightPx = selection.height,
                zoom = zoom,
                handlePx = handlePx,
                rotationHandleOffsetPx = 0f,
                colors = transformColors,
                showRotationHandle = false,
                mode = org.codeberg.editorie.data.EditorPanel.Transform
            )
        }
    }
}
