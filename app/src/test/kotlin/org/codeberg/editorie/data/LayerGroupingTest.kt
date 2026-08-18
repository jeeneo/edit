package org.codeberg.editorie.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.codeberg.editorie.options.drawing.DrawnStroke
import org.codeberg.editorie.options.stickers.StickerOverlay
import org.codeberg.editorie.options.stickers.StickerSource
import org.codeberg.editorie.options.text.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerGroupingTest {

    private fun strokeLayer(id: Long, groupId: Long) = EditorLayer.Stroke(
        DrawnStroke(
            id = id,
            points = listOf(Offset(0f, 0f), Offset(1f, 1f)),
            color = Color.Black,
            strokeWidthPx = 4f,
        ),
        groupId = groupId,
    )

    private fun textLayer(id: Long, groupId: Long) = EditorLayer.Text(
        TextOverlay(
            id = id,
            text = "hello",
            color = Color.Black,
            fontSizeSp = 24f,
            positionPx = Offset(10f, 10f),
        ),
        groupId = groupId,
    )

    private fun stickerLayer(id: Long, groupId: Long) = EditorLayer.Sticker(
        StickerOverlay(
            id = id,
            source = StickerSource.Asset("stickers/shapes/circle.svg"),
            positionPx = Offset(0f, 0f),
            widthPx = 32f,
            heightPx = 32f,
        ),
        groupId = groupId,
    )

    @Test
    fun layerIdDelegatesToContent() {
        assertEquals(7L, strokeLayer(7, 1).id)
        assertEquals(8L, textLayer(8, 1).id)
        assertEquals(9L, stickerLayer(9, 1).id)
        assertEquals(42L, EditorLayer.Background(42, 1).id)
    }

    @Test
    fun toolModeMatchesLayerType() {
        assertEquals(EditorPanel.Draw, strokeLayer(1, 1).toolMode)
        assertEquals(EditorPanel.Text, textLayer(2, 1).toolMode)
        assertEquals(EditorPanel.Stickers, stickerLayer(3, 1).toolMode)
        assertEquals(EditorPanel.None, EditorLayer.Background(4, 1).toolMode)
    }

    @Test
    fun toLayerGroupsGroupsByGroupId() {
        val layers = listOf(
            strokeLayer(1, 10),
            strokeLayer(2, 10),
            textLayer(3, 20),
        )
        val groups = layers.toLayerGroups(emptyMap())
        assertEquals(2, groups.size)

        val g10 = groups.first { it.groupId == 10L }
        assertEquals(2, g10.layers.size)
        assertEquals(EditorPanel.Draw, g10.toolMode)

        val g20 = groups.first { it.groupId == 20L }
        assertEquals(1, g20.layers.size)
        assertEquals(EditorPanel.Text, g20.toolMode)
    }

    @Test
    fun metaSuppliesOrdinalAndName() {
        val layers = listOf(strokeLayer(1, 10), textLayer(2, 20))
        val meta = mapOf(
            10L to LayerGroupMeta(ordinal = 3, customName = "My drawing"),
            20L to LayerGroupMeta(ordinal = 4),
        )
        val groups = layers.toLayerGroups(meta)

        val g10 = groups.first { it.groupId == 10L }
        assertEquals(3, g10.ordinal)
        assertEquals("My drawing", g10.customName)
        assertEquals("My drawing", g10.displayName)

        val g20 = groups.first { it.groupId == 20L }
        assertEquals(4, g20.ordinal)
        assertNull(g20.customName)
        assertEquals("New layer 4 Text", g20.displayName)
    }

    @Test
    fun missingMetaDefaultsToOrdinalZero() {
        val groups = listOf(strokeLayer(1, 10)).toLayerGroups(emptyMap())
        assertEquals(0, groups.single().ordinal)
        assertNull(groups.single().customName)
        assertEquals("New layer 0 Draw", groups.single().displayName)
    }

    @Test
    fun toolLabels() {
        assertEquals(
            "Draw",
            listOf(strokeLayer(1, 1)).toLayerGroups(emptyMap()).single().toolLabel
        )
        assertEquals(
            "Text",
            listOf(textLayer(1, 1)).toLayerGroups(emptyMap()).single().toolLabel
        )
        assertEquals(
            "Sticker",
            listOf(stickerLayer(1, 1)).toLayerGroups(emptyMap()).single().toolLabel
        )
    }

    @Test
    fun groupOrderFollowsLayerOrder() {
        val layers = listOf(
            textLayer(1, 20),
            strokeLayer(2, 10),
        )
        val groups = layers.toLayerGroups(emptyMap())
        assertEquals(listOf(20L, 10L), groups.map { it.groupId })
    }
}
