package org.codeberg.editorie.ui.canvas.gestures

// SPDX-License-Identifier: MIT

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import org.codeberg.editorie.ui.canvas.CanvasViewport
import org.codeberg.editorie.ui.canvas.pointerPan
import org.codeberg.editorie.ui.canvas.pointerZoom
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

fun Modifier.zoomPanGestures(
    viewport: CanvasViewport,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
): Modifier = pointerInput(viewport.canvasSize) {
    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        var sawMultiTouch = false
        var tapGestures = 0
        var maxPointers = 0
        val trackingId = awaitFirstDown(requireUnconsumed = false).id
        val downPositions = mutableMapOf<PointerId, Offset>()
        var maxTravel = 0f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            event.changes.forEach { change ->
                if (change.pressed) {
                    if (!downPositions.containsKey(change.id)) {
                        downPositions[change.id] = change.position
                    } else {
                        maxTravel = maxOf(
                            maxTravel,
                            (change.position - downPositions.getValue(change.id)).getDistance()
                        )
                    }
                }
            }
            maxPointers = maxOf(maxPointers, downPositions.size)

            event.changes.firstOrNull { it.id == trackingId }?.let {
                velocityTracker.addPosition(it.uptimeMillis, it.position)
            }

            if (pressed.size > 1) {
                sawMultiTouch = true
                val zoomChange = event.pointerZoom()
                val panChange = event.pointerPan()
                val nextZoom = (viewport.zoom * zoomChange).coerceIn(1f, viewport.maxZoom)
                val centroid =
                    pressed.fold(Offset.Zero) { acc, c -> acc + c.position } / pressed.size.toFloat()
                val rawPan =
                    (viewport.pan - centroid) * (nextZoom / viewport.zoom) + centroid + panChange
                viewport.pan = viewport.clampPan(rawPan, nextZoom)
                viewport.zoom = nextZoom
                event.changes.forEach { it.consume() }
            } else if (pressed.isEmpty()) {
                val travelSlop = viewConfiguration.touchSlop * 2f
                if (sawMultiTouch && maxPointers == 2 && maxTravel <= travelSlop) {
                    tapGestures = 1
                } else if (sawMultiTouch && maxPointers == 3 && maxTravel <= travelSlop) {
                    tapGestures = 2
                }
                when (tapGestures) {
                    1 -> onUndo()
                    2 -> onRedo()
                }
                if (sawMultiTouch && viewport.zoom > 1f && tapGestures == 0) {
                    val v = velocityTracker.calculateVelocity()
                    viewport.launch { viewport.flingPan(v) }
                }
                break
            }
        }
    }
}

fun Modifier.defaultZoomPanGestures(
    viewport: CanvasViewport,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val firstDown = awaitFirstDown()
        firstDown.consume()
        val firstUp = try {
            withTimeout(viewConfiguration.doubleTapTimeoutMillis) { waitForUpOrCancellation() }
        } catch (_: PointerEventTimeoutCancellationException) {
            null
        }
        if (firstUp == null) {
            var lastPos = firstDown.position
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size > 1) break
                val change = event.changes.firstOrNull { it.id == firstDown.id } ?: break
                if (!change.pressed) break
                val delta = change.position - lastPos
                lastPos = change.position
                change.consume()
                if (viewport.zoom > 1f) viewport.pan =
                    viewport.clampPan(viewport.pan + delta, viewport.zoom)
            }
            return@awaitEachGesture
        }
        firstUp.consume()

        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = true)
        }

        if (secondDown == null) {
            return@awaitEachGesture
        }
        secondDown.consume()
        val secondDownTime = TimeSource.Monotonic.markNow()

        val dragStart = awaitVerticalTouchSlopOrCancellation(secondDown.id) { change, _ ->
            change.consume()
        }

        if (dragStart != null) {
            val focal = secondDown.position
            verticalDrag(secondDown.id) { drag ->
                val delta = (1f + drag.positionChange().y * 0.004f).coerceIn(0.1f, 2f)
                val nextZoom = (viewport.zoom * delta).coerceIn(1f, viewport.maxZoom)
                viewport.pan = viewport.clampPan(
                    (viewport.pan - focal) * (nextZoom / viewport.zoom) + focal,
                    nextZoom
                )
                viewport.zoom = nextZoom
                drag.consume()
            }
            if (viewport.zoom == 1f) viewport.pan = Offset.Zero
        } else if (secondDownTime.elapsedNow() < viewConfiguration.doubleTapTimeoutMillis.milliseconds) {
            val focal = secondDown.position
            val target = if (viewport.zoom > 1f) 1f else viewport.maxZoom.coerceAtMost(3f)
            viewport.launch { viewport.animateZoomTo(target, focal) }
        }
    }
}
