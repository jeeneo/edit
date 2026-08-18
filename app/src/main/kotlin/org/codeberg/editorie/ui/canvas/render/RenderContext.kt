package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import org.codeberg.editorie.ui.canvas.CanvasViewport
import org.codeberg.editorie.ui.canvas.TransformBoxColors

data class CanvasRenderContext(
    val viewport: CanvasViewport,
    val drawImageBitmap: ImageBitmap,
    val drawScale: Float,
    val colorFilter: ColorFilter,
    val handlePx: Float,
    val transformColors: TransformBoxColors,
    val rotationDeg: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val selectedTextId: Long?,
    val selectedStickerId: Long?,
)
