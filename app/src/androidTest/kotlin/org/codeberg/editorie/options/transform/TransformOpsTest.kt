package org.codeberg.editorie.options.transform

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.options.drawing.DrawnStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Color as AColor

@RunWith(AndroidJUnit4::class)
class TransformOpsTest {

    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(color)
        }

    private fun strokeLayer(points: List<Offset>) = EditorLayer.Stroke(
        DrawnStroke(
            id = 1, points = points, color = Color.Black, strokeWidthPx = 4f
        ),
        groupId = 1,
    )

    @Test
    fun rotateZeroReturnsSameBitmap() {
        val src = solid(40, 20, AColor.RED)
        val result = TransformOps.rotate(src, 0f)
        assertSame(src, result.bitmap)
        assertEquals(0, result.trimOffsetX)
        assertEquals(0, result.trimOffsetY)
    }

    @Test
    fun rotateFullTurnReturnsSameBitmap() {
        val src = solid(40, 20, AColor.RED)
        assertSame(src, TransformOps.rotate(src, 360f).bitmap)
        assertSame(src, TransformOps.rotate(src, 720f).bitmap)
    }

    @Test
    fun rotateNinetySwapsDimensions() {
        val src = solid(40, 20, AColor.RED)
        val result = TransformOps.rotate(src, 90f)
        assertEquals(20, result.bitmap.width)
        assertEquals(40, result.bitmap.height)
    }

    @Test
    fun rotateNinetyMovesPixelsCorrectly() {
        val src = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        src.setPixel(0, 0, AColor.RED)
        src.setPixel(1, 0, AColor.BLUE)
        val rotated = TransformOps.rotate(src, 90f).bitmap
        assertEquals(1, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(AColor.RED, rotated.getPixel(0, 0))
        assertEquals(AColor.BLUE, rotated.getPixel(0, 1))
    }

    @Test
    fun rotateOneEightyKeepsDimensions() {
        val src = solid(40, 20, AColor.GREEN)
        val result = TransformOps.rotate(src, 180f)
        assertEquals(40, result.bitmap.width)
        assertEquals(20, result.bitmap.height)
    }

    @Test
    fun noFlipReturnsSameBitmap() {
        val src = solid(10, 10, AColor.RED)
        assertSame(src, TransformOps.flip(src, flipH = false, flipV = false))
    }

    @Test
    fun flipHorizontalMirrorsPixels() {
        val src = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        src.setPixel(0, 0, AColor.RED)
        src.setPixel(1, 0, AColor.BLUE)
        val flipped = TransformOps.flip(src, flipH = true, flipV = false)
        assertEquals(AColor.BLUE, flipped.getPixel(0, 0))
        assertEquals(AColor.RED, flipped.getPixel(1, 0))
    }

    @Test
    fun flipVerticalMirrorsPixels() {
        val src = Bitmap.createBitmap(1, 2, Bitmap.Config.ARGB_8888)
        src.setPixel(0, 0, AColor.RED)
        src.setPixel(0, 1, AColor.BLUE)
        val flipped = TransformOps.flip(src, flipH = false, flipV = true)
        assertEquals(AColor.BLUE, flipped.getPixel(0, 0))
        assertEquals(AColor.RED, flipped.getPixel(0, 1))
    }

    @Test
    fun nullCropReturnsSource() {
        val src = solid(10, 10, AColor.RED)
        assertSame(src, TransformOps.crop(src, null).bitmap)
    }

    @Test
    fun cropProducesRequestedRegion() {
        val src = solid(100, 100, AColor.RED)
        val result = TransformOps.crop(src, Rect(10f, 5f, 30f, 15f))
        assertEquals(20, result.bitmap.width)
        assertEquals(10, result.bitmap.height)
    }

    @Test
    fun cropIsClampedToBounds() {
        val src = solid(50, 50, AColor.RED)
        val result = TransformOps.crop(src, Rect(-10f, -10f, 100f, 100f))
        assertEquals(50, result.bitmap.width)
        assertEquals(50, result.bitmap.height)
    }

    @Test
    fun scaleBitmapDownscalesToExactSize() {
        val src = solid(100, 100, AColor.RED)
        val scaled = TransformOps.scaleBitmap(src, 25, 25)
        assertEquals(25, scaled.width)
        assertEquals(25, scaled.height)
        assertEquals(AColor.RED, scaled.getPixel(12, 12))
    }

    @Test
    fun scaleBitmapHandlesLargeReduction() {
        val src = solid(512, 256, AColor.BLUE)
        val scaled = TransformOps.scaleBitmap(src, 30, 15)
        assertEquals(30, scaled.width)
        assertEquals(15, scaled.height)
        assertEquals(AColor.BLUE, scaled.getPixel(15, 7))
    }

    @Test
    fun trimFindsOpaqueRegion() {
        val src = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        val paint = android.graphics.Paint().apply { color = AColor.RED }
        canvas.drawRect(10f, 20f, 20f, 30f, paint) // 10x10 at (10, 20)

        val result = TransformOps.trimTransparentEdges(src)
        assertEquals(10, result.bitmap.width)
        assertEquals(10, result.bitmap.height)
        assertEquals(10, result.offsetX)
        assertEquals(20, result.offsetY)
    }

    @Test
    fun trimFullyTransparentReturnsSource() {
        val src = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val result = TransformOps.trimTransparentEdges(src)
        assertSame(src, result.bitmap)
        assertEquals(0, result.offsetX)
        assertEquals(0, result.offsetY)
    }

    @Test
    fun trimFullyOpaqueReturnsSource() {
        val src = solid(10, 10, AColor.RED)
        val result = TransformOps.trimTransparentEdges(src)
        assertSame(src, result.bitmap)
    }

    @Test
    fun canvasResizeCentersSourceAndMovesLayers() {
        val src = solid(20, 20, AColor.RED)
        val layers = listOf(strokeLayer(listOf(Offset(0f, 0f), Offset(5f, 5f))))
        val result = TransformOps.canvasResize(
            source = src, newWidth = 40, newHeight = 40,
            layers = layers, anchorX = 0.5f, anchorY = 0.5f,
            fillColor = Color.Transparent,
        )
        assertEquals(40, result.bitmap.width)
        assertEquals(40, result.bitmap.height)
        assertEquals(AColor.RED, result.bitmap.getPixel(20, 20))
        assertEquals(0, result.bitmap.getPixel(0, 0))
        val moved = (result.layers.single() as EditorLayer.Stroke).stroke
        assertEquals(Offset(10f, 10f), moved.points[0])
        assertEquals(Offset(15f, 15f), moved.points[1])
    }

    @Test
    fun canvasResizeFillsBackground() {
        val src = solid(10, 10, AColor.RED)
        val result = TransformOps.canvasResize(
            source = src, newWidth = 30, newHeight = 30,
            layers = emptyList(), anchorX = 0.5f, anchorY = 0.5f,
            fillColor = Color.Blue,
        )
        assertEquals(AColor.BLUE, result.bitmap.getPixel(0, 0))
        assertEquals(AColor.RED, result.bitmap.getPixel(15, 15))
    }

    @Test
    fun canvasResizeAnchorZeroKeepsLayersInPlace() {
        val src = solid(10, 10, AColor.RED)
        val layers = listOf(strokeLayer(listOf(Offset(1f, 1f), Offset(2f, 2f))))
        val result = TransformOps.canvasResize(
            source = src, newWidth = 20, newHeight = 20,
            layers = layers, anchorX = 0f, anchorY = 0f,
            fillColor = Color.Transparent,
        )
        assertSame(layers, result.layers)
        assertEquals(AColor.RED, result.bitmap.getPixel(5, 5))
    }

    @Test
    fun bitmapResizeScalesBitmapAndLayers() {
        val src = solid(100, 100, AColor.GREEN)
        val layers = listOf(strokeLayer(listOf(Offset(10f, 10f), Offset(20f, 20f))))
        val result = TransformOps.bitmapResize(
            source = src, newWidth = 50, newHeight = 50,
            layers = layers, originalWidth = 100, originalHeight = 100,
        )
        assertEquals(50, result.bitmap.width)
        assertEquals(50, result.bitmap.height)
        val scaled = (result.layers.single() as EditorLayer.Stroke).stroke
        assertEquals(Offset(5f, 5f), scaled.points[0])
        assertEquals(Offset(10f, 10f), scaled.points[1])
        assertEquals(2f, scaled.strokeWidthPx, 1e-4f)
    }
}
