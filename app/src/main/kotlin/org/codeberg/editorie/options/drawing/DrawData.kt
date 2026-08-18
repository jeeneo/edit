package org.codeberg.editorie.options.drawing

// SPDX-License-Identifier: MIT

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

sealed class DrawTool {
    data object Brush : DrawTool()
    data object Eraser : DrawTool()
    data object Eyedropper : DrawTool()
}

data class DrawnStroke(
    val id: Long = System.nanoTime(),
    val points: List<Offset>,
    val color: Color,
    val strokeWidthPx: Float,
    val tool: DrawTool = DrawTool.Brush
)

data class DrawState(
    val penColor: Color,
    val penWidth: Float,
    val tool: DrawTool,
    val palette: List<Color>,
)
