package org.codeberg.editorie.options.save

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM-safe parts of [ExportFormat]. The compressFormat property touches
 * android.graphics.Bitmap and is covered by instrumented tests.
 */
class ExportFormatTest {

    @Test
    fun fromMimeTypeMapsKnownTypes() {
        assertEquals(ExportFormat.JPEG, ExportFormat.fromMimeType("image/jpeg"))
        assertEquals(ExportFormat.PNG, ExportFormat.fromMimeType("image/png"))
        assertEquals(ExportFormat.WEBP, ExportFormat.fromMimeType("image/webp"))
    }

    @Test
    fun fromMimeTypeFallsBackToJpeg() {
        assertEquals(ExportFormat.JPEG, ExportFormat.fromMimeType(null))
        assertEquals(ExportFormat.JPEG, ExportFormat.fromMimeType("image/gif"))
        assertEquals(ExportFormat.JPEG, ExportFormat.fromMimeType(""))
    }

    @Test
    fun extensionsMatchFormats() {
        assertEquals("jpeg", ExportFormat.JPEG.extension)
        assertEquals("png", ExportFormat.PNG.extension)
        assertEquals("webp", ExportFormat.WEBP.extension)
    }

    @Test
    fun mimeTypesRoundTrip() {
        for (format in ExportFormat.entries) {
            assertEquals(format, ExportFormat.fromMimeType(format.mimeType))
        }
    }

    @Test
    fun labels() {
        assertEquals("JPEG", ExportFormat.JPEG.label)
        assertEquals("PNG", ExportFormat.PNG.label)
        assertEquals("WebP", ExportFormat.WEBP.label)
    }
}
