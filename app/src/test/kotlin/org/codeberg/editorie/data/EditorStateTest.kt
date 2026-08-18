package org.codeberg.editorie.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.codeberg.editorie.options.adjust.AdjustState
import org.codeberg.editorie.options.drawing.DrawTool
import org.codeberg.editorie.options.drawing.DrawnStroke
import org.codeberg.editorie.options.save.ExportFormat
import org.codeberg.editorie.options.stickers.StickerOverlay
import org.codeberg.editorie.options.stickers.StickerPanelState
import org.codeberg.editorie.options.stickers.StickerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for EditorState's derived properties. The default value of
 * [EditorState.stickerPanelState] scans app assets, so every state here
 * passes an explicit empty StickerPanelState.
 */
class EditorStateTest {

    private fun state(
        losslessChain: LosslessChain? = null,
        layers: List<EditorLayer> = emptyList(),
        adjust: AdjustState = AdjustState(),
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        exportFormat: ExportFormat = ExportFormat.JPEG,
        losslessEnabled: Boolean = false,
    ) = EditorState(
        stickerPanelState = StickerPanelState(),
        losslessChain = losslessChain,
        layers = layers,
        adjust = adjust,
        flipHorizontal = flipHorizontal,
        flipVertical = flipVertical,
        exportFormat = exportFormat,
        losslessEnabled = losslessEnabled,
    )

    private fun stickerLayer(id: Long = 1) = EditorLayer.Sticker(
        StickerOverlay(
            id = id, source = StickerSource.Asset("stickers/shapes/x.svg"),
            positionPx = Offset.Zero, widthPx = 10f, heightPx = 10f,
        ),
        groupId = 1,
    )

    private fun strokeLayer() = EditorLayer.Stroke(
        DrawnStroke(
            id = 2, points = listOf(Offset.Zero, Offset(1f, 1f)),
            color = Color.Black, strokeWidthPx = 1f,
        ),
        groupId = 1,
    )

    // losslessPossible / canLossless

    @Test
    fun losslessImpossibleWithoutChain() {
        assertFalse(state(losslessChain = null).losslessPossible)
    }

    @Test
    fun losslessPossibleWithPristineJpegChain() {
        assertTrue(state(losslessChain = LosslessChain()).losslessPossible)
    }

    @Test
    fun layersDefeatLossless() {
        assertFalse(
            state(losslessChain = LosslessChain(), layers = listOf(strokeLayer())).losslessPossible
        )
    }

    @Test
    fun adjustmentsDefeatLossless() {
        assertFalse(
            state(
                losslessChain = LosslessChain(),
                adjust = AdjustState(saturation = 1.5f)
            ).losslessPossible
        )
    }

    @Test
    fun flipsDefeatLossless() {
        assertFalse(
            state(losslessChain = LosslessChain(), flipHorizontal = true).losslessPossible
        )
        assertFalse(
            state(losslessChain = LosslessChain(), flipVertical = true).losslessPossible
        )
    }

    @Test
    fun nonJpegExportDefeatsLossless() {
        assertFalse(
            state(losslessChain = LosslessChain(), exportFormat = ExportFormat.PNG).losslessPossible
        )
        assertFalse(
            state(
                losslessChain = LosslessChain(), exportFormat = ExportFormat.WEBP
            ).losslessPossible
        )
    }

    @Test
    fun canLosslessRequiresTheToggle() {
        val possible = state(losslessChain = LosslessChain())
        assertTrue(possible.losslessPossible)
        assertFalse(possible.canLossless)
        assertTrue(possible.copy(losslessEnabled = true).canLossless)
    }

    @Test
    fun losslessChainWithOpsStillPossible() {
        val chain = LosslessChain(
            exifRotation = 1,
            ops = listOf(LosslessOp.Rotate(1), LosslessOp.Crop(0, 0, 100, 100)),
        )
        assertTrue(state(losslessChain = chain).losslessPossible)
    }

    @Test
    fun transformStateMirrorsFields() {
        val s = state().copy(
            rotation = 90f,
            aspectRatio = 4 to 3,
            flipHorizontal = true,
            widthInput = "800",
            heightInput = "600",
            lockAspect = false,
            canvasAnchorX = 0.25f,
            canvasFillColor = Color.Red,
        )
        val t = s.transformState
        assertEquals(90f, t.rotation)
        assertEquals(4 to 3, t.aspectRatio)
        assertTrue(t.flipHorizontal)
        assertFalse(t.flipVertical)
        assertEquals("800", t.widthInput)
        assertEquals("600", t.heightInput)
        assertFalse(t.lockAspect)
        assertEquals(0.25f, t.canvasAnchorX)
        assertEquals(Color.Red, t.canvasFillColor)
    }

    @Test
    fun drawStateMirrorsFields() {
        val s = state().copy(
            penColor = Color.Blue,
            penWidth = 12f,
            drawTool = DrawTool.Eraser,
            palette = listOf(Color.Red, Color.Green),
        )
        val d = s.drawState
        assertEquals(Color.Blue, d.penColor)
        assertEquals(12f, d.penWidth)
        assertEquals(DrawTool.Eraser, d.tool)
        assertEquals(listOf(Color.Red, Color.Green), d.palette)
    }

    @Test
    fun stickerOverlaysFiltersStickerLayers() {
        val s = state(layers = listOf(strokeLayer(), stickerLayer(5), stickerLayer(6)))
        assertEquals(listOf(5L, 6L), s.stickerOverlays.map { it.id })
    }

    @Test
    fun layerGroupsDerivedFromLayers() {
        val s = state(layers = listOf(strokeLayer(), stickerLayer()))
            .copy(layerGroupMeta = mapOf(1L to LayerGroupMeta(1, "combo")))
        val groups = s.layerGroups
        assertEquals(1, groups.size)
        assertEquals("combo", groups.single().displayName)
        assertEquals(2, groups.single().layers.size)
    }

    @Test
    fun imageOverlayStateFilters() {
        val overlay = ImageOverlayState(
            layers = listOf(strokeLayer(), stickerLayer(9)),
            penColor = Color.Black,
            penWidth = 1f,
            drawTool = DrawTool.Brush,
            selectedTextId = null,
            selectedStickerId = null,
        )
        assertTrue(overlay.textOverlays.isEmpty())
        assertEquals(listOf(9L), overlay.stickerOverlays.map { it.id })
    }
}
