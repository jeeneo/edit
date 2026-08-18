package org.codeberg.editorie.options.transform

// SPDX-License-Identifier: MIT

import androidx.compose.ui.graphics.Color

enum class ResizeMode { Scale, Canvas }

data class TransformState(
    val rotation: Float = 0f,
    val aspectRatio: Pair<Int, Int>? = null,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val widthInput: String = "",
    val heightInput: String = "",
    val lockAspect: Boolean = true,
    val resizeMode: ResizeMode = ResizeMode.Scale,
    val canvasAnchorX: Float = 0.5f,
    val canvasAnchorY: Float = 0.5f,
    val canvasFillColor: Color = Color.Transparent,
)
