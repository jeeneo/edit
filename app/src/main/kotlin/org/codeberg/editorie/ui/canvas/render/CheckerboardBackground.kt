package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.createBitmap
import org.codeberg.editorie.ui.canvas.CanvasViewport

private fun buildCheckerPaint(isDark: Boolean): Paint {
    val cellPx = 16
    val tile = createBitmap(cellPx * 2, cellPx * 2)
    val c = android.graphics.Canvas(tile)
    val (colorA, colorB) = if (isDark) {
        android.graphics.Color.rgb(38, 38, 38) to android.graphics.Color.rgb(52, 52, 52)
    } else {
        android.graphics.Color.rgb(204, 204, 204) to android.graphics.Color.rgb(255, 255, 255)
    }
    val paintA = Paint().apply { color = colorA }
    val paintB = Paint().apply { color = colorB }
    c.drawRect(0f, 0f, cellPx.toFloat(), cellPx.toFloat(), paintA)
    c.drawRect(cellPx.toFloat(), cellPx.toFloat(), cellPx * 2f, cellPx * 2f, paintA)
    c.drawRect(cellPx.toFloat(), 0f, cellPx * 2f, cellPx.toFloat(), paintB)
    c.drawRect(0f, cellPx.toFloat(), cellPx.toFloat(), cellPx * 2f, paintB)
    return Paint().apply {
        isAntiAlias = false
        shader = BitmapShader(
            tile,
            Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT,
        )
    }
}

@Composable
fun rememberCheckerPaint(isDark: Boolean): Paint = remember(isDark) { buildCheckerPaint(isDark) }

fun DrawScope.drawTransparencyMap(viewport: CanvasViewport, checkerPaint: Paint) {
    val dstLeft = viewport.displayRect.left * viewport.zoom + viewport.pan.x
    val dstTop = viewport.displayRect.top * viewport.zoom + viewport.pan.y
    val dstRight = dstLeft + viewport.displayRect.width * viewport.zoom
    val dstBottom = dstTop + viewport.displayRect.height * viewport.zoom
    drawContext.canvas.nativeCanvas.drawRect(
        dstLeft, dstTop, dstRight, dstBottom, checkerPaint
    )
}
