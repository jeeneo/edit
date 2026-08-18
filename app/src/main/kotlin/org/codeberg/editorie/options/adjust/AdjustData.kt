package org.codeberg.editorie.options.adjust

// SPDX-License-Identifier: MIT

data class AdjustState(
    val invert: Boolean = false,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val contrast: Float = 1f,
    val brightness: Float = 0f,
    val luminosity: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val exposure: Float = 0f,
    val blacks: Float = 0f,
    val whites: Float = 0f,
    val fade: Float = 0f,
)
