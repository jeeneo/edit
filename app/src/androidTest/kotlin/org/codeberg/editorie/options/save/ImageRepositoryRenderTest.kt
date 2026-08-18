package org.codeberg.editorie.options.save

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.options.adjust.AdjustState
import org.codeberg.editorie.options.drawing.DrawingOps
import org.codeberg.editorie.options.drawing.DrawnStroke
import org.codeberg.editorie.options.text.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import android.graphics.Color as AColor

@RunWith(AndroidJUnit4::class)
class ImageRepositoryRenderTest {

    private lateinit var repository: ImageRepository

    @Before
    fun setUp() {
        repository = ImageRepository(ApplicationProvider.getApplicationContext())
    }

    private fun solid(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(color)
        }

    private fun fullRect(bmp: Bitmap) = Rect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())

    private fun bg(id: Long = 0L) = EditorLayer.Background(id = id, groupId = 0L)

    private fun assertChannelClose(expected: Int, actual: Int, tolerance: Int = 4) {
        assertTrue(
            "expected #${Integer.toHexString(expected)} got #${Integer.toHexString(actual)}",
            abs(AColor.red(expected) - AColor.red(actual)) <= tolerance &&
                    abs(AColor.green(expected) - AColor.green(actual)) <= tolerance &&
                    abs(AColor.blue(expected) - AColor.blue(actual)) <= tolerance
        )
    }

    @Test
    fun cropRegionProducesExpectedSize() {
        val src = solid(100, 100, AColor.RED)
        val out = repository.renderEditedBitmap(
            source = src,
            cropRect = Rect(10f, 20f, 60f, 50f),
            layers = listOf(bg()),
            adjust = AdjustState(),
        )
        assertEquals(50, out.width)
        assertEquals(30, out.height)
        assertEquals(AColor.RED, out.getPixel(25, 15))
    }

    @Test
    fun flipHorizontalIsApplied() {
        val src = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        src.setPixel(0, 0, AColor.RED)
        src.setPixel(1, 0, AColor.BLUE)
        val out = repository.renderEditedBitmap(
            source = src, cropRect = fullRect(src),
            layers = listOf(bg()), adjust = AdjustState(),
            flipHorizontal = true,
        )
        assertChannelClose(AColor.BLUE, out.getPixel(0, 0))
        assertChannelClose(AColor.RED, out.getPixel(1, 0))
    }

    @Test
    fun invertAdjustmentInvertsColors() {
        val src = solid(10, 10, AColor.RED)
        val out = repository.renderEditedBitmap(
            source = src, cropRect = fullRect(src),
            layers = listOf(bg()), adjust = AdjustState(invert = true),
        )
        assertChannelClose(AColor.CYAN, out.getPixel(5, 5))
    }

    @Test
    fun brushStrokeIsRendered() {
        val src = solid(100, 100, AColor.WHITE)
        val stroke = EditorLayer.Stroke(
            DrawnStroke(
                id = 1,
                points = listOf(Offset(10f, 50f), Offset(90f, 50f)),
                color = Color.Black,
                strokeWidthPx = 10f,
            ),
            groupId = 1,
        )
        val out = repository.renderEditedBitmap(
            source = src, cropRect = fullRect(src),
            layers = listOf(bg(), stroke), adjust = AdjustState(),
        )
        assertChannelClose(AColor.BLACK, out.getPixel(50, 50))
        assertChannelClose(AColor.WHITE, out.getPixel(50, 10))
    }

    @Test
    fun eraserOnlyErasesLayerContentNotSource() {
        val src = solid(100, 100, AColor.WHITE)
        val brush = EditorLayer.Stroke(
            DrawnStroke(
                id = 1,
                points = listOf(Offset(10f, 50f), Offset(90f, 50f)),
                color = Color.Black,
                strokeWidthPx = 10f,
            ),
            groupId = 1,
        )
        val erased = DrawingOps.eraseFromLayersExact(
            layers = listOf(brush),
            eraserPoints = listOf(Offset(40f, 30f), Offset(40f, 70f)),
            eraserWidthPx = 20f,
        )
        val out = repository.renderEditedBitmap(
            source = src, cropRect = fullRect(src),
            layers = listOf(bg()) + (erased ?: listOf(brush)), adjust = AdjustState(),
        )
        assertChannelClose(AColor.WHITE, out.getPixel(40, 50))
        assertChannelClose(AColor.BLACK, out.getPixel(70, 50))
    }

    @Test
    fun singlePointStrokeIsIgnored() {
        val src = solid(20, 20, AColor.WHITE)
        val stroke = EditorLayer.Stroke(
            DrawnStroke(
                id = 1, points = listOf(Offset(10f, 10f)),
                color = Color.Black, strokeWidthPx = 10f,
            ),
            groupId = 1,
        )
        val out = repository.renderEditedBitmap(
            source = src, cropRect = fullRect(src),
            layers = listOf(bg(), stroke), adjust = AdjustState(),
        )
        assertChannelClose(AColor.WHITE, out.getPixel(10, 10))
    }

    @Test
    fun textOverlayIsRendered() {
        val src = solid(200, 100, AColor.WHITE)
        val text = EditorLayer.Text(
            TextOverlay(
                id = 1, text = "XXXX", color = Color.Black, fontSizeSp = 40f,
                positionPx = Offset(10f, 10f), boxWidthPx = 180f,
            ),
            groupId = 1,
        )
        val out = repository.renderEditedBitmap(
            source = src, cropRect = fullRect(src),
            layers = listOf(bg(), text), adjust = AdjustState(),
            scaledDensity = 1f,
        )
        var foundDark = false
        outer@ for (y in 10 until 90) {
            for (x in 10 until 190) {
                val p = out.getPixel(x, y)
                if (AColor.red(p) < 100 && AColor.green(p) < 100 && AColor.blue(p) < 100) {
                    foundDark = true
                    break@outer
                }
            }
        }
        assertTrue("expected rendered text pixels", foundDark)
    }

    @Test
    fun strokeOutsideCropDoesNotAppear() {
        val src = solid(100, 100, AColor.WHITE)
        val stroke = EditorLayer.Stroke(
            DrawnStroke(
                id = 1,
                points = listOf(Offset(0f, 5f), Offset(10f, 5f)),
                color = Color.Black,
                strokeWidthPx = 4f,
            ),
            groupId = 1,
        )
        val out = repository.renderEditedBitmap(
            source = src, cropRect = Rect(50f, 50f, 100f, 100f),
            layers = listOf(bg(), stroke), adjust = AdjustState(),
        )
        for (y in 0 until out.height) {
            for (x in 0 until out.width) {
                assertChannelClose(AColor.WHITE, out.getPixel(x, y))
            }
        }
    }

    @Test
    fun renderLayerGroupPreviewIsBounded() {
        val src = solid(1024, 512, AColor.RED)
        val out = repository.renderLayerGroupPreview(
            source = src,
            cropRect = Rect(0f, 0f, 1024f, 512f),
            groupLayers = emptyList(),
            maxDimensionPx = 256,
        )
        assertEquals(256, out.width)
        assertEquals(128, out.height)
    }

    @Test
    fun buildStrokePathAppliesOffsets() {
        val path = repository.buildStrokePath(
            listOf(Offset(10f, 10f), Offset(30f, 30f)), offsetX = 5, offsetY = 5
        )
        val bounds = android.graphics.RectF()
        @Suppress("DEPRECATION") path.computeBounds(bounds, true)
        assertEquals(5f, bounds.left, 0.01f)
        assertEquals(5f, bounds.top, 0.01f)
        assertEquals(25f, bounds.right, 0.01f)
        assertEquals(25f, bounds.bottom, 0.01f)
    }
}
