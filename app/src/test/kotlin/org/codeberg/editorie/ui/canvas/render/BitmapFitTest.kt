package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapFitTest {

    @Test
    fun smallImageDoesNotDownscale() {
        assertEquals(1f, downscaleFactor(100, 100, maxBytes = 1_000_000_000L), 0.0001f)
    }

    @Test
    fun oversizedImageScalesCubicCapacity() {
        // 1000x1000x4 = 4_000_000 bytes vs 1MB budget -> scale factor sqrt(1/4) = 0.5
        assertEquals(0.5f, downscaleFactor(1000, 1000, maxBytes = 1_000_000L), 0.0001f)
    }

    @Test
    fun budgetExactFitStaysAtOne() {
        assertEquals(1f, downscaleFactor(100, 100, maxBytes = 40_000L), 0.0001f)
    }
}
