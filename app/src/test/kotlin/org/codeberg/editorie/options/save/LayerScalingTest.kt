package org.codeberg.editorie.options.save

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.options.drawing.DrawnStroke
import org.codeberg.editorie.options.stickers.StickerOverlay
import org.codeberg.editorie.options.stickers.StickerSource
import org.codeberg.editorie.options.text.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerScalingTest {

    private val eps = 1e-4f

    private fun stroke(points: List<Offset>, width: Float = 10f) = EditorLayer.Stroke(
        DrawnStroke(id = 1, points = points, color = Color.Black, strokeWidthPx = width),
        groupId = 1,
    )

    private fun text(
        pos: Offset, boxW: Float = 100f, boxH: Float? = 50f, fontSp: Float = 20f
    ) = EditorLayer.Text(
        TextOverlay(
            id = 2, text = "t", color = Color.Black, fontSizeSp = fontSp,
            positionPx = pos, boxWidthPx = boxW, boxHeightPx = boxH,
        ),
        groupId = 1,
    )

    private fun sticker(pos: Offset, w: Float = 40f, h: Float = 20f) = EditorLayer.Sticker(
        StickerOverlay(
            id = 3, source = StickerSource.Asset("stickers/shapes/x.svg"),
            positionPx = pos, widthPx = w, heightPx = h,
        ),
        groupId = 1,
    )

    @Test
    fun scaleStrokePointsAndWidth() {
        val layers = listOf(stroke(listOf(Offset(10f, 10f), Offset(20f, 40f)), width = 10f))
        val scaled = layers.scaleForResize(2f, 3f)

        val s = (scaled.single() as EditorLayer.Stroke).stroke
        assertEquals(Offset(20f, 30f), s.points[0])
        assertEquals(Offset(40f, 120f), s.points[1])
        assertEquals(25f, s.strokeWidthPx, eps)
    }

    @Test
    fun scaleTextBoxAndFont() {
        val layers = listOf(text(Offset(10f, 20f), boxW = 100f, boxH = 50f, fontSp = 20f))
        val scaled = layers.scaleForResize(2f, 4f)

        val t = (scaled.single() as EditorLayer.Text).overlay
        assertEquals(Offset(20f, 80f), t.positionPx)
        assertEquals(200f, t.boxWidthPx, eps)
        assertEquals(200f, t.boxHeightPx!!, eps)
        assertEquals(60f, t.fontSizeSp, eps) // avg scale = 3
    }

    @Test
    fun scaleTextWithNullBoxHeightStaysNull() {
        val layers = listOf(text(Offset.Zero, boxH = null))
        val scaled = layers.scaleForResize(2f, 2f)
        assertNull((scaled.single() as EditorLayer.Text).overlay.boxHeightPx)
    }

    @Test
    fun scaleStickerDimensions() {
        val layers = listOf(sticker(Offset(10f, 10f), w = 40f, h = 20f))
        val scaled = layers.scaleForResize(0.5f, 0.25f)

        val st = (scaled.single() as EditorLayer.Sticker).overlay
        assertEquals(Offset(5f, 2.5f), st.positionPx)
        assertEquals(20f, st.widthPx, eps)
        assertEquals(5f, st.heightPx, eps)
    }

    @Test
    fun scaleStickerOutlineThicknessWithAvg() {
        val layers = listOf(sticker(Offset.Zero, w = 40f, h = 20f))
        val scaled = layers.scaleForResize(0.5f, 0.25f)

        val st = (scaled.single() as EditorLayer.Sticker).overlay
        assertEquals(6f * 0.375f, st.renderMode.outlineThicknessPx, eps)
    }

    @Test
    fun identityScaleKeepsOutlineThickness() {
        val layers = listOf(sticker(Offset.Zero, w = 40f, h = 20f))
        val scaled = layers.scaleForResize(1f, 1f)

        val st = (scaled.single() as EditorLayer.Sticker).overlay
        assertEquals(6f, st.renderMode.outlineThicknessPx, eps)
    }

    @Test
    fun identityScaleKeepsValues() {
        val layers = listOf(
            stroke(listOf(Offset(1f, 2f), Offset(3f, 4f))),
            text(Offset(5f, 6f)),
            sticker(Offset(7f, 8f)),
        )
        val scaled = layers.scaleForResize(1f, 1f)
        assertEquals(
            (layers[0] as EditorLayer.Stroke).stroke.points,
            (scaled[0] as EditorLayer.Stroke).stroke.points
        )
        assertEquals(
            (layers[1] as EditorLayer.Text).overlay.positionPx,
            (scaled[1] as EditorLayer.Text).overlay.positionPx
        )
        assertEquals(
            (layers[2] as EditorLayer.Sticker).overlay.positionPx,
            (scaled[2] as EditorLayer.Sticker).overlay.positionPx
        )
    }

    @Test
    fun translateShiftsAllLayerTypes() {
        val layers = listOf(
            stroke(listOf(Offset(10f, 10f), Offset(20f, 20f))),
            text(Offset(30f, 30f)),
            sticker(Offset(40f, 40f)),
        )
        val moved = layers.translateForCrop(5f, 7f)

        val s = (moved[0] as EditorLayer.Stroke).stroke
        assertEquals(Offset(5f, 3f), s.points[0])
        assertEquals(Offset(15f, 13f), s.points[1])

        assertEquals(Offset(25f, 23f), (moved[1] as EditorLayer.Text).overlay.positionPx)
        assertEquals(Offset(35f, 33f), (moved[2] as EditorLayer.Sticker).overlay.positionPx)
    }

    @Test
    fun negativeTranslationMovesOppositeDirection() {
        val moved = listOf(text(Offset(10f, 10f))).translateForCrop(-5f, -5f)
        assertEquals(Offset(15f, 15f), (moved.single() as EditorLayer.Text).overlay.positionPx)
    }

    @Test
    fun translatePreservesLayerCountAndOrder() {
        val layers = listOf(sticker(Offset.Zero), text(Offset.Zero))
        val moved = layers.translateForCrop(1f, 1f)
        assertEquals(layers.size, moved.size)
        assertEquals(layers.map { it.id }, moved.map { it.id })
    }
}
