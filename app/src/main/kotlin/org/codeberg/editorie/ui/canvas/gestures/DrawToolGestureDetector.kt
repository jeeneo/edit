package org.codeberg.editorie.ui.canvas.gestures

// SPDX-License-Identifier: MIT

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import org.codeberg.editorie.options.drawing.DrawTool
import org.codeberg.editorie.ui.canvas.CanvasViewport

@Stable
class PendingPointsState {
    var points: List<Offset> by mutableStateOf(emptyList())
}

@Composable
fun rememberPendingPointsState(): PendingPointsState = remember { PendingPointsState() }

fun Modifier.drawToolGestures(
    viewport: CanvasViewport,
    pendingPoints: PendingPointsState,
    drawTool: DrawTool,
    penWidth: Float,
    onStrokeEnd: (List<Offset>, Float) -> Unit,
    onEyedropper: (Offset) -> Unit,
    onEyedropperPreview: (Offset) -> Unit,
): Modifier = this.pointerInput(
    drawTool,
    penWidth,
    viewport.displayRect,
    viewport.zoom,
    viewport.sourceRect
) {
    when (drawTool) {
        DrawTool.Brush, DrawTool.Eraser -> detectDragGestures(onDragStart = {
            pendingPoints.points = listOf(viewport.toPixel(it))
        }, onDrag = { change, _ ->
            change.consume()
            pendingPoints.points += viewport.toPixel(change.position)
        }, onDragEnd = {
            onStrokeEnd(pendingPoints.points, viewport.screenToPixelWidth(penWidth))
            pendingPoints.points = emptyList()
        })

        is DrawTool.Eyedropper -> awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            pendingPoints.points = listOf(viewport.toPixel(down.position))
            onEyedropperPreview(pendingPoints.points.last())
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                val pixel = viewport.toPixel(change.position)
                if (pixel != pendingPoints.points.last()) {
                    pendingPoints.points = listOf(pixel)
                    onEyedropperPreview(pixel)
                }
            }
            pendingPoints.points.lastOrNull()?.let { onEyedropper(Offset(it.x, it.y)) }
            pendingPoints.points = emptyList()
        }
    }
}
