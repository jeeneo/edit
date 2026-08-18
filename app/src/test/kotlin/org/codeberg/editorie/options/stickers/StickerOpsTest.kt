package org.codeberg.editorie.options.stickers

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StickerOpsTest {

    private val eps = 1e-3f

    @Test
    fun placeNewStickerSizesFromSmallerCropDimension() {
        val overlay = StickerOps.placeNewSticker(
            StickerSource.Asset("stickers/shapes/circle.svg"),
            viewCrop = Rect(0f, 0f, 100f, 200f),
        )
        assertEquals(30f, overlay.widthPx, eps)
        assertEquals(30f, overlay.heightPx, eps)
        assertEquals(35f, overlay.positionPx.x, eps)
        assertEquals(85f, overlay.positionPx.y, eps)
    }

    @Test
    fun placeNewStickerHonorsCropOffset() {
        val overlay = StickerOps.placeNewSticker(
            StickerSource.Asset("stickers/shapes/circle.svg"),
            viewCrop = Rect(50f, 100f, 150f, 200f),
            sizeFraction = 0.5f,
        )
        assertEquals(50f, overlay.widthPx, eps)
        assertEquals(50f + (100f - 50f) / 2f, overlay.positionPx.x, eps)
        assertEquals(100f + (100f - 50f) / 2f, overlay.positionPx.y, eps)
    }

    @Test
    fun placeOverlayImageWideKeepsAspect() {
        val overlay = StickerOps.placeOverlayImage(
            bitmapWidth = 200, bitmapHeight = 100,
            source = StickerSource.Asset("x"),
            viewCrop = Rect(0f, 0f, 100f, 200f),
        )
        assertEquals(60f, overlay.widthPx, eps)
        assertEquals(30f, overlay.heightPx, eps)
        assertEquals((100f - 60f) / 2f, overlay.positionPx.x, eps)
        assertEquals((200f - 30f) / 2f, overlay.positionPx.y, eps)
    }

    @Test
    fun placeOverlayImageTallKeepsAspect() {
        val overlay = StickerOps.placeOverlayImage(
            bitmapWidth = 100, bitmapHeight = 400,
            source = StickerSource.Asset("x"),
            viewCrop = Rect(0f, 0f, 100f, 100f),
        )
        assertEquals(15f, overlay.widthPx, eps)
        assertEquals(60f, overlay.heightPx, eps)
    }

    @Test
    fun placeOverlayImageSquare() {
        val overlay = StickerOps.placeOverlayImage(
            bitmapWidth = 128, bitmapHeight = 128,
            source = StickerSource.Asset("x"),
            viewCrop = Rect(0f, 0f, 100f, 100f),
        )
        assertEquals(60f, overlay.widthPx, eps)
        assertEquals(60f, overlay.heightPx, eps)
    }

    @Test
    fun placeOverlayImageZeroWidthProducesFiniteValues() {
        val overlay = StickerOps.placeOverlayImage(
            bitmapWidth = 0, bitmapHeight = 100,
            source = StickerSource.Asset("x"),
            viewCrop = Rect(0f, 0f, 100f, 100f),
        )
        assertFalse(overlay.widthPx.isNaN() || overlay.widthPx.isInfinite())
        assertFalse(overlay.heightPx.isNaN() || overlay.heightPx.isInfinite())
        assertFalse(overlay.positionPx.x.isNaN() || overlay.positionPx.x.isInfinite())
        assertFalse(overlay.positionPx.y.isNaN() || overlay.positionPx.y.isInfinite())
    }

    @Test
    fun placeOverlayImageZeroHeightProducesFiniteValues() {
        val overlay = StickerOps.placeOverlayImage(
            bitmapWidth = 100, bitmapHeight = 0,
            source = StickerSource.Asset("x"),
            viewCrop = Rect(0f, 0f, 100f, 100f),
        )
        assertFalse(overlay.widthPx.isNaN() || overlay.widthPx.isInfinite())
        assertFalse(overlay.heightPx.isNaN() || overlay.heightPx.isInfinite())
        assertFalse(overlay.positionPx.x.isNaN() || overlay.positionPx.x.isInfinite())
        assertFalse(overlay.positionPx.y.isNaN() || overlay.positionPx.y.isInfinite())
    }

}
