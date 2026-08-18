package org.codeberg.editorie.options.transform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class JPEGLosslessTransformTest {

    private fun makeJpeg(w: Int = 160, h: Int = 112): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, w / 2f, h / 2f, paint)
        paint.color = Color.GREEN
        canvas.drawRect(w / 2f, 0f, w.toFloat(), h / 2f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(0f, h / 2f, w / 2f, h.toFloat(), paint)
        paint.color = Color.WHITE
        canvas.drawRect(w / 2f, h / 2f, w.toFloat(), h.toFloat(), paint)
        val out = ByteArrayOutputStream()
        check(bmp.compress(Bitmap.CompressFormat.JPEG, 95, out))
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): Bitmap =
        requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    @Test
    fun isValidJpegAcceptsJpegMagic() {
        assertTrue(JPEGLosslessTransform.isValidJpeg(makeJpeg()))
    }

    @Test
    fun isValidJpegRejectsGarbage() {
        assertFalse(JPEGLosslessTransform.isValidJpeg(ByteArray(0)))
        assertFalse(JPEGLosslessTransform.isValidJpeg(byteArrayOf(0xFF.toByte())))
        assertFalse(JPEGLosslessTransform.isValidJpeg(byteArrayOf(1, 2, 3, 4)))
        assertFalse(
            JPEGLosslessTransform.isValidJpeg(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
            )
        )
    }

    @Test
    fun mcuSizeReportsImageDimensions() {
        val info = JPEGLosslessTransform.mcuSize(makeJpeg())
        assertEquals(160, info.imageWidth)
        assertEquals(112, info.imageHeight)
        assertTrue("mcuWidth=${info.mcuWidth}", info.mcuWidth in listOf(8, 16, 32))
        assertTrue("mcuHeight=${info.mcuHeight}", info.mcuHeight in listOf(8, 16, 32))
    }

    @Test
    fun rotateOneQuarterTurnSwapsDimensions() {
        val result = JPEGLosslessTransform.rotate90Lossless(makeJpeg(), 1, trim = false)
        assertNotNull(result)
        val rotated = decode(result!!.bytes)
        assertEquals(112, rotated.width)
        assertEquals(160, rotated.height)
    }

    @Test
    fun rotateTwoQuarterTurnsKeepsDimensions() {
        val result = JPEGLosslessTransform.rotate90Lossless(makeJpeg(), 2, trim = false)
        assertNotNull(result)
        val rotated = decode(result!!.bytes)
        assertEquals(160, rotated.width)
        assertEquals(112, rotated.height)
    }

    @Test
    fun rotateMovesQuadrants() {
        val result = JPEGLosslessTransform.rotate90Lossless(makeJpeg(), 1, trim = false)
        assertNotNull(result)
        val rotated = decode(result!!.bytes)
        val pixel = rotated.getPixel(rotated.width - 10, 10)
        assertTrue("expected red-ish, got #${Integer.toHexString(pixel)}", Color.red(pixel) > 180)
        assertTrue(Color.green(pixel) < 90)
        assertTrue(Color.blue(pixel) < 90)
    }

    @Test
    fun rotatedOutputIsStillValidJpeg() {
        val result = JPEGLosslessTransform.rotate90Lossless(makeJpeg(), 3, trim = true)
        assertNotNull(result)
        assertTrue(JPEGLosslessTransform.isValidJpeg(result!!.bytes))
    }

    @Test
    fun cropAlignedRegion() {
        val result = JPEGLosslessTransform.cropLossless(makeJpeg(), 0, 0, 64, 48)
        assertNotNull(result)
        val cropped = decode(result!!.bytes)
        assertEquals(64, cropped.width)
        assertEquals(48, cropped.height)
        val pixel = cropped.getPixel(10, 10)
        assertTrue(Color.red(pixel) > 180)
    }

    @Test
    fun cropReportsResultGeometry() {
        val result = JPEGLosslessTransform.cropLossless(makeJpeg(), 0, 0, 64, 48)
        assertNotNull(result)
        assertEquals(64, result!!.w)
        assertEquals(48, result.h)
        assertEquals(0, result.x)
        assertEquals(0, result.y)
    }

    @Test
    fun unalignedCropSnapsToMcuBoundary() {
        val info = JPEGLosslessTransform.mcuSize(makeJpeg())
        val result = JPEGLosslessTransform.cropLossless(makeJpeg(), 5, 5, 64, 48)
        assertNotNull(result)
        assertEquals(0, result!!.x % info.mcuWidth)
        assertEquals(0, result.y % info.mcuHeight)
        assertTrue(result.x <= 5)
        assertTrue(result.y <= 5)
    }
}
