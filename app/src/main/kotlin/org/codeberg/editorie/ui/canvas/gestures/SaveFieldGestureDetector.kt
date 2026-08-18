package org.codeberg.editorie.ui.canvas.gestures

// SPDX-License-Identifier: MIT

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import org.codeberg.editorie.ui.canvas.CanvasViewport

fun Modifier.saveFieldGestures(
    viewport: CanvasViewport,
    onDismissKeyboard: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(); down.consume()
        val drag = awaitTouchSlopOrCancellation(down.id) { change, _ ->
            change.consume()
        }
        if (drag != null) {
            var lastPos = drag.position
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.position - lastPos
                lastPos = change.position
                change.consume()
                if (viewport.zoom > 1f) viewport.pan += delta
            }
        } else {
            onDismissKeyboard()
        }
    }
}
