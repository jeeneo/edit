package org.codeberg.editorie.options.adjust

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.codeberg.editorie.options.adjust.AdjustOps.toMatrixValues
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdjustOpsTest {

    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    @Test
    fun defaultStateProducesIdentityMatrix() {
        assertArrayEquals(identity, AdjustState().toMatrixValues(), 1e-4f)
    }

    @Test
    fun invertNegatesAndOffsetsChannels() {
        val m = AdjustState(invert = true).toMatrixValues()
        for (row in 0..2) {
            assertEquals(-1f, m[row * 5 + row], 1e-4f)
            assertEquals(255f, m[row * 5 + 4], 1e-4f)
        }
        assertEquals(1f, m[18], 1e-4f)
        assertEquals(0f, m[19], 1e-4f)
    }

    @Test
    fun zeroSaturationUsesLuminanceWeights() {
        val m = AdjustState(saturation = 0f).toMatrixValues()
        for (row in 0..2) {
            assertEquals(0.213f, m[row * 5], 1e-3f)
            assertEquals(0.715f, m[row * 5 + 1], 1e-3f)
            assertEquals(0.072f, m[row * 5 + 2], 1e-3f)
        }
    }

    @Test
    fun brightnessTranslatesChannels() {
        val m = AdjustState(brightness = 40f).toMatrixValues()
        for (row in 0..2) {
            assertEquals(1f, m[row * 5 + row], 1e-4f)
            assertEquals(40f, m[row * 5 + 4], 1e-4f)
        }
    }

    @Test
    fun exposureScalesExponentially() {
        val m = AdjustState(exposure = 1f).toMatrixValues()
        for (row in 0..2) {
            assertEquals(2f, m[row * 5 + row], 1e-4f)
        }
        val half = AdjustState(exposure = -1f).toMatrixValues()
        assertEquals(0.5f, half[0], 1e-4f)
    }

    @Test
    fun contrastPivotsAroundMidGray() {
        val m = AdjustState(contrast = 2f).toMatrixValues()
        for (row in 0..2) {
            assertEquals(2f, m[row * 5 + row], 1e-4f)
            assertEquals(-127.5f, m[row * 5 + 4], 1e-3f)
        }
        assertEquals(128.5f, 128f * m[0] + m[4], 1e-3f)
    }

    @Test
    fun luminosityScalesWithoutOffset() {
        val m = AdjustState(luminosity = 1.5f).toMatrixValues()
        for (row in 0..2) {
            assertEquals(1.5f, m[row * 5 + row], 1e-4f)
            assertEquals(0f, m[row * 5 + 4], 1e-4f)
        }
    }

    @Test
    fun warmTemperatureBoostsRedCutsBlue() {
        val m = AdjustOps.temperatureColorMatrix(50f).array
        assertEquals(1.2f, m[0], 1e-4f)
        assertEquals(1.05f, m[6], 1e-4f)
        assertEquals(0.7f, m[12], 1e-4f)
    }

    @Test
    fun coolTemperatureCutsRedBoostsBlue() {
        val m = AdjustOps.temperatureColorMatrix(-50f).array
        assertEquals(0.7f, m[0], 1e-4f)
        assertEquals(0.95f, m[6], 1e-4f)
        assertEquals(1.2f, m[12], 1e-4f)
    }

    @Test
    fun zeroTemperatureIsIdentity() {
        assertArrayEquals(identity, AdjustOps.temperatureColorMatrix(0f).array, 1e-5f)
    }

    @Test
    fun positiveTintShiftsTowardMagenta() {
        val m = AdjustOps.tintColorMatrix(100f).array
        assertEquals(1.2f, m[0], 1e-4f)
        assertEquals(0.6f, m[6], 1e-4f)
        assertEquals(1.2f, m[12], 1e-4f)
    }

    @Test
    fun zeroTintIsIdentity() {
        assertArrayEquals(identity, AdjustOps.tintColorMatrix(0f).array, 1e-5f)
    }

    @Test
    fun zeroHueIsIdentity() {
        assertArrayEquals(identity, AdjustOps.hueColorMatrix(0f).array, 1e-5f)
    }

    @Test
    fun hueRotationPreservesGray() {
        val m = AdjustOps.hueColorMatrix(90f).array
        for (row in 0..2) {
            val sum = m[row * 5] + m[row * 5 + 1] + m[row * 5 + 2]
            assertEquals("row $row", 1f, sum, 1e-2f)
        }
    }

    @Test
    fun fadeLiftsBlacksAndCompresses() {
        val m = AdjustState(fade = 1f).toMatrixValues()
        for (row in 0..2) {
            assertEquals(0.65f, m[row * 5 + row], 1e-4f)
            assertEquals(40f, m[row * 5 + 4], 1e-4f)
        }
    }

    @Test
    fun whitesGainPivotsAtBlack() {
        val m = AdjustState(whites = 100f).toMatrixValues()
        assertEquals(1.5f, m[0], 1e-4f)
        assertEquals(0f, m[4], 1e-4f)
    }

    @Test
    fun blacksGainPivotsAtWhite() {
        val m = AdjustState(blacks = 100f).toMatrixValues()
        assertEquals(0.5f, m[0], 1e-4f)
        assertEquals(127.5f, m[4], 1e-3f)
        assertEquals(255f, 255f * m[0] + m[4], 1e-3f)
    }
}
