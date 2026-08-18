package org.codeberg.editorie.options.save

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamingTest {

    @Test
    fun replacesExistingExtension() {
        assertEquals("photo.png", withExtension("photo.jpg", "png"))
        assertEquals("photo.webp", withExtension("photo.jpeg", "webp"))
    }

    @Test
    fun appendsWhenNoExtension() {
        assertEquals("photo.png", withExtension("photo", "png"))
    }

    @Test
    fun onlyLastExtensionIsReplaced() {
        assertEquals("archive.tar.jpeg", withExtension("archive.tar.gz", "jpeg"))
    }

    @Test
    fun dotfileIsTreatedAsBaseName() {
        assertEquals(".hidden.png", withExtension(".hidden", "png"))
    }

    @Test
    fun emptyExtensionReturnsNameUnchanged() {
        assertEquals("photo.jpg", withExtension("photo.jpg", ""))
        assertEquals("photo", withExtension("photo", ""))
    }

    @Test
    fun emptyFileName() {
        assertEquals(".png", withExtension("", "png"))
    }

    @Test
    fun nameEndingInDot() {
        assertEquals("photo.png", withExtension("photo.", "png"))
    }
}
