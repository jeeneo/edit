package org.codeberg.editorie.options.save

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExifCopierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "exif-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun writeJpeg(name: String): File {
        val file = File(dir, name)
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    private fun sourceWithExif(orientation: Int? = null): File {
        val file = writeJpeg("source.jpg")
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "TestMake")
        exif.setAttribute(ExifInterface.TAG_MODEL, "TestModel")
        exif.setAttribute(ExifInterface.TAG_DATETIME, "2020:01:02 03:04:05")
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2019:12:31 23:59:58")
        orientation?.let { exif.setAttribute(ExifInterface.TAG_ORIENTATION, it.toString()) }
        exif.saveAttributes()
        return file
    }

    @Test
    fun exifTagListIsDiscoveredViaReflection() {
        assertTrue("expected many tags, got ${EXIF_TAGS.size}", EXIF_TAGS.size > 50)
        assertTrue(EXIF_TAGS.contains(ExifInterface.TAG_DATETIME))
        assertTrue(EXIF_TAGS.contains(ExifInterface.TAG_MAKE))
    }

    @Test
    fun dateTagsAreSubsetOfAllTags() {
        for (tag in EXIF_DATE_TAGS) {
            assertTrue("date tag $tag missing from EXIF_TAGS", EXIF_TAGS.contains(tag))
        }
    }

    @Test
    fun copiesAllTagsToFileTarget() {
        val source = sourceWithExif()
        val dest = writeJpeg("dest.jpg")

        val result = copyExif(
            context, Uri.fromFile(source), ExifTarget.FileTarget(dest)
        )
        assertTrue(result.isSuccess)

        val exif = ExifInterface(dest.absolutePath)
        assertEquals("TestMake", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("TestModel", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("2020:01:02 03:04:05", exif.getAttribute(ExifInterface.TAG_DATETIME))
        assertEquals(
            "2019:12:31 23:59:58", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        )
    }

    @Test
    fun dateOnlyCopiesDatesAndClearsOtherTags() {
        val source = sourceWithExif()
        val dest = writeJpeg("dest.jpg")
        ExifInterface(dest.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "StaleMake")
            saveAttributes()
        }

        val result = copyExif(
            context, Uri.fromFile(source), ExifTarget.FileTarget(dest), dateOnly = true
        )
        assertTrue(result.isSuccess)

        val exif = ExifInterface(dest.absolutePath)
        assertEquals("2020:01:02 03:04:05", exif.getAttribute(ExifInterface.TAG_DATETIME))
        assertEquals(
            "2019:12:31 23:59:58", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        )
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
    }

    @Test
    fun losslessNormalizesOrientation() {
        val source = sourceWithExif(orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val dest = writeJpeg("dest.jpg")

        val result = copyExif(
            context, Uri.fromFile(source), ExifTarget.FileTarget(dest), useLossless = true
        )
        assertTrue(result.isSuccess)

        val exif = ExifInterface(dest.absolutePath)
        assertEquals(
            ExifInterface.ORIENTATION_NORMAL,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
        )
    }

    @Test
    fun withoutLosslessOrientationIsCopiedAsIs() {
        val source = sourceWithExif(orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val dest = writeJpeg("dest.jpg")

        copyExif(context, Uri.fromFile(source), ExifTarget.FileTarget(dest))

        val exif = ExifInterface(dest.absolutePath)
        assertEquals(
            ExifInterface.ORIENTATION_ROTATE_90,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
        )
    }

    @Test
    fun missingSourceReturnsFailureOrNoop() {
        val dest = writeJpeg("dest.jpg")
        val bogus = Uri.fromFile(File(dir, "nope.jpg"))
        val result = copyExif(context, bogus, ExifTarget.FileTarget(dest))
        assertTrue(result.isFailure)
    }

    @Test
    fun copyTimestampsSucceeds() {
        val source = writeJpeg("source.jpg")
        val dest = writeJpeg("dest.jpg")
        val result = copyTimestamps(context, Uri.fromFile(source), dest)
        assertTrue(result.isSuccess)
    }
}
