@file:Suppress("SpellCheckingInspection")

package org.codeberg.editorie.util

// SPDX-License-Identifier: MIT

import kotlin.math.floor
import kotlin.math.pow

const val MATH_SYMBOLS = "+-*/^!−×÷ "

fun evaluateMath(expression: String): Int? {
    return try {
        if (expression.isBlank()) return null
        val sanitized =
            expression.replace("\\s+".toRegex(), "").replace('−', '-')
                .replace('×', '*').replace('÷', '/')
        if (sanitized.matches(Regex("^\\d+$"))) return sanitized.toInt()
        val tokens = mutableListOf<String>()
        val currentNumber = StringBuilder()
        for (char in sanitized) {
            if (char.isDigit()) {
                currentNumber.append(char)
            } else {
                if (currentNumber.isNotEmpty()) {
                    tokens.add(currentNumber.toString())
                    currentNumber.clear()
                }
                tokens.add(char.toString())
            }
        }
        if (currentNumber.isNotEmpty()) tokens.add(currentNumber.toString())
        if (tokens.isEmpty()) return null

        // factorial "!" (is nonsense for dims)
        val pass0 = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val numVal = tokens[i].toDoubleOrNull()
            if (numVal == null) {
                pass0.add(tokens[i])
                i++
                continue
            }
            var value: Double = numVal
            i++
            while (i < tokens.size && tokens[i] == "!") {
                if (value < 0 || value != floor(value) || value > 20) { // im nonsense
                    return null
                }
                var f = 1.0
                var n = value.toInt()
                while (n > 1) {
                    f *= n; n--
                }
                value = f
                i++
            }
            pass0.add(value.toString())
        }
        if (pass0.isEmpty()) return null

        // exponent "^"
        val pass1 = mutableListOf<String>()
        i = 0
        while (i < pass0.size) {
            val token = pass0[i]
            if (token == "^") {
                if (pass1.isEmpty() || i + 1 >= pass0.size) return null
                val left = pass1.removeAt(pass1.size - 1).toDoubleOrNull() ?: return null
                val right = pass0[++i].toDoubleOrNull() ?: return null
                pass1.add(left.pow(right).toString())
            } else {
                pass1.add(token)
            }
            i++
        }
        if (pass1.isEmpty()) return null

        // multiplication / division
        val pass2 = mutableListOf<String>()
        i = 0
        while (i < pass1.size) {
            val token = pass1[i]
            if (token == "*" || token == "/") {
                if (pass2.isEmpty() || i + 1 >= pass1.size) return null
                val left = pass2.removeAt(pass2.size - 1).toDoubleOrNull() ?: return null
                val right = pass1[++i].toDoubleOrNull() ?: return null
                val res = if (token == "*") left * right else left / right
                pass2.add(res.toString())
            } else {
                pass2.add(token)
            }
            i++
        }
        if (pass2.isEmpty()) return null

        // addition / subtraction
        var result = pass2[0].toDoubleOrNull() ?: return null
        i = 1
        while (i < pass2.size) {
            val op = pass2[i]
            if (op != "+" && op != "-") return null
            if (i + 1 >= pass2.size) return null
            val right = pass2[++i].toDoubleOrNull() ?: return null
            result = if (op == "+") result + right else result - right
            i++
        }

        if (result.isInfinite() || result.isNaN()) {
            AppToasts.show("Ummm what")
            return 0
        }

        result.toInt()
    } catch (e: Exception) {
        AppToasts.show("Expression error: $e")
        null
    }
}
