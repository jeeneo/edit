package org.codeberg.editorie.ui.canvas

// SPDX-License-Identifier: MIT

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.codeberg.editorie.ui.canvas.gestures.CropResizeResult
import org.codeberg.editorie.ui.canvas.gestures.resizeCropSelection
import org.codeberg.editorie.ui.canvas.gestures.resolveCropDragHandle
import org.codeberg.editorie.ui.canvas.gestures.resolvePendingCropEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropGestureMathTest {

    private val source = Rect(0f, 0f, 1000f, 1000f)
    private val display = Rect(0f, 0f, 1000f, 1000f)
    private val selection = Rect(100f, 100f, 400f, 400f)

    @Test
    fun resolveHandleDetectsCorner() {
        val handle = resolveCropDragHandle(
            Offset(100f, 100f), selection, source, display, zoom = 1f, handlePx = 20f
        )
        assertEquals(CropDrag.Edge(BoxHandle.TL), handle)
    }

    @Test
    fun resolveHandleDetectsMoveInsideSelection() {
        val handle = resolveCropDragHandle(
            Offset(250f, 250f), selection, source, display, zoom = 1f, handlePx = 20f
        )
        assertEquals(CropDrag.Move, handle)
    }

    @Test
    fun resolveHandleReturnsNullFarAway() {
        val handle = resolveCropDragHandle(
            Offset(900f, 900f), selection, source, display, zoom = 1f, handlePx = 20f
        )
        assertNull(handle)
    }

    @Test
    fun pendingEdgePrefersPrimaryWhenDraggingPerpendicular() {
        // Primary L is a vertical edge: horizontal drag keeps L.
        val result = resolvePendingCropEdge(
            CropDrag.PendingEdge(BoxHandle.L, BoxHandle.T),
            aspectRatio = null,
            accum = Offset(5f, 0f)
        )
        assertEquals(CropDrag.Edge(BoxHandle.L), result)
    }

    @Test
    fun pendingEdgeSelectsAltWhenDraggingAlongEdge() {
        val result = resolvePendingCropEdge(
            CropDrag.PendingEdge(BoxHandle.L, BoxHandle.T),
            aspectRatio = null,
            accum = Offset(0f, 5f)
        )
        assertEquals(CropDrag.Edge(BoxHandle.T), result)
    }

    @Test
    fun pendingEdgeForcesPrimaryWhenAspectLocked() {
        val result = resolvePendingCropEdge(
            CropDrag.PendingEdge(BoxHandle.L, BoxHandle.T),
            aspectRatio = Pair(1, 1),
            accum = Offset(0f, 5f)
        )
        assertEquals(CropDrag.Edge(BoxHandle.L), result)
    }

    @Test
    fun moveWithAspectKeepsSizeAndStaysInSource() {
        val selection = Rect(300f, 300f, 400f, 400f)
        val result = resizeCropSelection(
            CropDrag.Move, selection, Offset(-1000f, 0f), Pair(1, 1), source, source
        )
        assertEquals(Rect(0f, 300f, 100f, 400f), (result as CropResizeResult).next)
    }
}
