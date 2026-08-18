package org.codeberg.editorie.options.text

// SPDX-License-Identifier: MIT

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import org.codeberg.editorie.data.EditorLayer

internal object TextOps {
    fun buildOverlay(
        draft: String,
        editorState: TextEditorState,
        viewCrop: Rect,
        density: Density,
    ): TextOverlay {
        val boxWidth = viewCrop.width * 0.6f
        val scaledDensity = density.density * density.fontScale
        val tp = buildTextPaint(
            editorState.fontSizeSp * scaledDensity,
            editorState.fontWeight,
            editorState.italic,
            editorState.underline,
            editorState.strikethrough,
        )
        val layout = makeStaticLayout(
            draft, tp, boxWidth.toInt().coerceAtLeast(1), null, editorState.wordWrap
        )
        return TextOverlay(
            text = draft,
            color = editorState.color,
            fontSizeSp = editorState.fontSizeSp,
            fontWeight = editorState.fontWeight,
            italic = editorState.italic,
            underline = editorState.underline,
            strikethrough = editorState.strikethrough,
            positionPx = Offset(
                viewCrop.left + viewCrop.width * 0.2f,
                viewCrop.top + viewCrop.height * 0.3f,
            ),
            boxWidthPx = boxWidth,
            boxHeightPx = layout.height.toFloat(),
            align = null,
            wordWrap = editorState.wordWrap,
            fontFamily = editorState.fontFamily,
            letterSpacing = editorState.letterSpacing,
        )
    }

    fun applyDraftToLayers(
        layers: List<EditorLayer>,
        newState: TextEditorState,
    ): List<EditorLayer> = layers.map { layer ->
        if (layer is EditorLayer.Text && layer.id == newState.selectedId) {
            layer.copy(
                overlay = layer.overlay.copy(
                    text = newState.draft,
                    fontSizeSp = newState.fontSizeSp,
                    color = newState.color,
                    fontWeight = newState.fontWeight,
                    italic = newState.italic,
                    underline = newState.underline,
                    strikethrough = newState.strikethrough,
                    align = newState.align,
                    wordWrap = newState.wordWrap,
                    fontFamily = newState.fontFamily,
                    letterSpacing = newState.letterSpacing,
                )
            )
        } else layer
    }
}
