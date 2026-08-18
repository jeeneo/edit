package org.codeberg.editorie.util

// SPDX-License-Identifier: MIT

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MathUtilsTest {

    @Test
    fun plainNumber() {
        assertEquals(42, evaluateMath("42"))
        assertEquals(0, evaluateMath("0"))
    }

    @Test
    fun additionAndSubtraction() {
        assertEquals(3, evaluateMath("1+2"))
        assertEquals(5, evaluateMath("10-2-3"))
        assertEquals(0, evaluateMath("5-5"))
    }

    @Test
    fun multiplicationPrecedence() {
        assertEquals(7, evaluateMath("1+2*3"))
        assertEquals(9, evaluateMath("1*3+2*3"))
    }

    @Test
    fun divisionTruncatesTowardZero() {
        assertEquals(2, evaluateMath("10/4"))
        assertEquals(3, evaluateMath("6/2"))
    }

    @Test
    fun exponentIsLeftAssociative() {
        assertEquals(1024, evaluateMath("2^10"))
        assertEquals(64, evaluateMath("2^3^2"))
    }

    @Test
    fun factorial() {
        assertEquals(6, evaluateMath("3!"))
        assertEquals(120, evaluateMath("5!"))
        assertEquals(720, evaluateMath("3!!"))
        assertEquals(1, evaluateMath("0!"))
    }

    @Test
    fun factorialAboveTwentyIsRejected() {
        assertNull(evaluateMath("21!"))
    }

    @Test
    fun unicodeOperators() {
        assertEquals(6, evaluateMath("2×3"))
        assertEquals(3, evaluateMath("6÷2"))
        assertEquals(5, evaluateMath("7−2"))
        assertEquals(60, evaluateMath("100 − 8 × 5"))
    }

    @Test
    fun whitespaceIsIgnored() {
        assertEquals(3, evaluateMath(" 1 + 2 "))
        assertEquals(42, evaluateMath("  42  "))
    }

    @Test
    fun blankInputReturnsNull() {
        assertNull(evaluateMath(""))
        assertNull(evaluateMath("   "))
    }

    @Test
    fun nonNumericInputReturnsNull() {
        assertNull(evaluateMath("abc"))
        assertNull(evaluateMath("!"))
    }

    @Test
    fun parenthesesAreUnsupported() {
        assertNull(evaluateMath("(1+2)"))
    }

    @Test
    fun decimalsAreUnsupported() {
        assertNull(evaluateMath("2.5"))
    }

    @Test
    fun trailingOperatorReturnsNull() {
        assertNull(evaluateMath("5+"))
        assertNull(evaluateMath("5*"))
    }

    @Test
    fun leadingNegativeIsUnsupported() {
        assertNull(evaluateMath("-5"))
    }

    @Test
    fun mathSymbolsConstantCoversAllOperators() {
        for (op in listOf('+', '-', '*', '/', '^', '!', '−', '×', '÷', ' ')) {
            assertEquals("missing '$op'", true, MATH_SYMBOLS.contains(op))
        }
    }
}
