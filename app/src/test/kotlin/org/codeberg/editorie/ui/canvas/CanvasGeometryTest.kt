package org.codeberg.editorie.ui.canvas

// SPDX-License-Identifier: MIT

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasGeometryTest {

    @Test
    fun computeDisplayFitsSourceInsideSizeCentered() {
        val result = computeDisplay(IntSize(1000, 500), Rect(0f, 0f, 200f, 100f))
        assertEquals(0f, result.left)
        assertEquals(0f, result.top)
        assertEquals(1000f, result.width)
        assertEquals(500f, result.height)
    }

    @Test
    fun computeDisplayLetterboxesWideSource() {
        val result = computeDisplay(IntSize(1000, 1000), Rect(0f, 0f, 200f, 100f))
        assertEquals(0f, result.left)
        assertEquals(250f, result.top)
        assertEquals(1000f, result.width)
        assertEquals(500f, result.height)
    }

    @Test
    fun applyAspectNullIsIdentity() {
        val rect = Rect(0f, 0f, 100f, 100f)
        assertEquals(rect, applyAspect(rect, null))
    }

    @Test
    fun applyAspectCropsWideRectDownToRatio() {
        val rect = Rect(0f, 0f, 100f, 100f)
        val result = applyAspect(rect, Pair(1, 2))
        assertEquals(50f, result.width, 0.0001f)
        assertEquals(100f, result.height, 0.0001f)
    }

    @Test
    fun clampPixelKeepsWithinFullRect() {
        val result = clampPixel(Rect(-5f, -5f, 500f, 500f), Rect(0f, 0f, 100f, 100f))
        assertEquals(Rect(0f, 0f, 100f, 100f), result)
    }

    @Test
    fun clampPixelMoveCannotExceedSourceRect() {
        val result = clampPixelMove(
            Rect(90f, 90f, 110f, 110f), Rect(0f, 0f, 100f, 100f)
        )
        assertEquals(Rect(80f, 80f, 100f, 100f), result)
    }

    @Test
    fun clampPixelRatioAnchorsLeftTop() {
        val result = clampPixelRatio(
            Rect(0f, 0f, 500f, 500f),
            target = 2f,
            anchorLeft = true,
            anchorTop = true,
            sourceRect = Rect(0f, 0f, 100f, 100f),
        )
        assertEquals(100f, result.width, 0.0001f)
        assertEquals(50f, result.height, 0.0001f)
        assertEquals(Rect(0f, 0f, 100f, 50f), result)
    }

    @Test
    fun rotateOffset90DegreesMapsXToNegativeY() {
        val result = rotateOffset(Offset(1f, 0f), 90f)
        assertEquals(0f, result.x, 0.0001f)
        assertEquals(1f, result.y, 0.0001f)
    }
}
