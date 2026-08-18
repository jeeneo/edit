package org.codeberg.editorie.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MathUtilsDeviceTest {

    private fun onMain(expression: String): Int? {
        var result: Int? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = evaluateMath(expression)
        }
        return result
    }

    @Test
    fun divisionByZeroReturnsZero() {
        assertEquals(0, onMain("5/0"))
    }

    @Test
    fun nanResultReturnsZero() {
        assertEquals(0, onMain("0/0"))
    }

    @Test
    fun intOverflowReturnsNull() {
        assertNull(onMain("99999999999"))
    }
}
