package org.codeberg.editorie.options.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.codeberg.editorie.data.EditorLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOpsTest {

    private fun textLayer(id: Long, text: String = "old") = EditorLayer.Text(
        TextOverlay(
            id = id, text = text, color = Color.Black, fontSizeSp = 20f,
            positionPx = Offset.Zero,
        ),
        groupId = 1,
    )

    @Test
    fun applyDraftUpdatesOnlySelectedLayer() {
        val layers = listOf(textLayer(1), textLayer(2))
        val newState = TextEditorState(
            draft = "updated",
            fontSizeSp = 32f,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            italic = true,
            underline = true,
            strikethrough = true,
            align = TextAlign.Center,
            wordWrap = false,
            selectedId = 1,
            fontFamily = EditorFontFamily.Monospace,
            letterSpacing = 0.1f,
        )
        val result = TextOps.applyDraftToLayers(layers, newState)

        val updated = (result[0] as EditorLayer.Text).overlay
        assertEquals("updated", updated.text)
        assertEquals(32f, updated.fontSizeSp)
        assertEquals(Color.Red, updated.color)
        assertEquals(FontWeight.Bold, updated.fontWeight)
        assertTrue(updated.italic)
        assertTrue(updated.underline)
        assertTrue(updated.strikethrough)
        assertEquals(TextAlign.Center, updated.align)
        assertEquals(false, updated.wordWrap)
        assertEquals(EditorFontFamily.Monospace, updated.fontFamily)
        assertEquals(0.1f, updated.letterSpacing)

        val untouched = (result[1] as EditorLayer.Text).overlay
        assertEquals("old", untouched.text)
        assertEquals(20f, untouched.fontSizeSp)
    }

    @Test
    fun applyDraftWithNoSelectionChangesNothing() {
        val layers = listOf(textLayer(1), textLayer(2))
        val result = TextOps.applyDraftToLayers(layers, TextEditorState(draft = "x"))
        assertEquals(layers, result)
    }

    @Test
    fun applyDraftIgnoresNonTextLayers() {
        val stroke = EditorLayer.Stroke(
            org.codeberg.editorie.options.drawing.DrawnStroke(
                id = 1,
                points = listOf(Offset.Zero, Offset(1f, 1f)),
                color = Color.Black,
                strokeWidthPx = 1f,
            ),
            groupId = 1,
        )
        // stroke id collides with selectedId, must still be untouched
        val result = TextOps.applyDraftToLayers(
            listOf(stroke), TextEditorState(draft = "x", selectedId = 1)
        )
        assertEquals(listOf<EditorLayer>(stroke), result)
    }

    @Test
    fun fontFamilyLabels() {
        assertEquals("Sans", EditorFontFamily.SansSerif.label)
        assertEquals("Serif", EditorFontFamily.Serif.label)
        assertEquals("Mono", EditorFontFamily.Monospace.label)
    }
}
