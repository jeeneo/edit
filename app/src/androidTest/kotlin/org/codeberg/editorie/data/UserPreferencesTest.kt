package org.codeberg.editorie.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.codeberg.editorie.options.save.DeletionMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: UserPreferences

    private fun clear() {
        context.getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Before
    fun setUp() {
        clear()
        prefs = UserPreferences(context)
    }

    @After
    fun tearDown() = clear()

    @Test
    fun defaults() {
        assertEquals(true, prefs.loadExifDateOnly())
        assertEquals(95, prefs.loadExportQuality())
        assertEquals(8f, prefs.loadPenWidth())
        assertEquals(20, prefs.loadUndoLevels())
        assertEquals(UserPreferences.DEFAULT_PALETTE, prefs.loadPalette())
        assertEquals(UserPreferences.DEFAULT_PALETTE.first(), prefs.loadPenColor())
        assertEquals(DeletionMode.Trash, prefs.loadDeletionMode())
        assertEquals(AppTheme.Dynamic, prefs.loadAppTheme())
        assertTrue(prefs.loadCloseOnSave())
        assertTrue(prefs.loadHaptic())
    }

    @Test
    fun scalarRoundTrips() {
        prefs.saveExifDateOnly(false)
        assertFalse(prefs.loadExifDateOnly())

        prefs.saveExportQuality(60)
        assertEquals(60, prefs.loadExportQuality())

        prefs.savePenWidth(14.5f)
        assertEquals(14.5f, prefs.loadPenWidth())

        prefs.saveCloseOnSave(false)
        assertFalse(prefs.loadCloseOnSave())

        prefs.saveHaptic(false)
        assertFalse(prefs.loadHaptic())
    }

    @Test
    fun penColorRoundTrip() {
        prefs.savePenColor(Color(0.5f, 0.25f, 0.75f, 1f))
        assertEquals(
            Color(0.5f, 0.25f, 0.75f, 1f).toArgb(), prefs.loadPenColor().toArgb()
        )
    }

    @Test
    fun undoLevelsAreCoerced() {
        prefs.saveUndoLevels(0)
        assertEquals(1, prefs.loadUndoLevels())
        prefs.saveUndoLevels(9999)
        assertEquals(100, prefs.loadUndoLevels())
        prefs.saveUndoLevels(42)
        assertEquals(42, prefs.loadUndoLevels())
    }

    @Test
    fun paletteRoundTrip() {
        val colors = listOf(Color.Red, Color.Green, Color.Blue)
        prefs.savePalette(colors)
        assertEquals(colors.map { it.toArgb() }, prefs.loadPalette().map { it.toArgb() })
    }

    @Test
    fun addColorPrepends() {
        val updated = prefs.addColor(Color.Magenta)
        assertEquals(Color.Magenta.toArgb(), updated.first().toArgb())
        assertEquals(UserPreferences.DEFAULT_PALETTE.size + 1, updated.size)
        assertEquals(Color.Magenta.toArgb(), prefs.loadPalette().first().toArgb())
    }

    @Test
    fun addColorIsIdempotentForDuplicates() {
        prefs.addColor(Color.Magenta)
        val again = prefs.addColor(Color.Magenta)
        assertEquals(1, again.count { it.toArgb() == Color.Magenta.toArgb() })
    }

    @Test
    fun paletteIsCappedAtTwentyFour() {
        repeat(30) { i ->
            prefs.addColor(Color(i, i, 100))
        }
        assertEquals(24, prefs.loadPalette().size)
    }

    @Test
    fun removeColor() {
        prefs.savePalette(listOf(Color.Red, Color.Green))
        val updated = prefs.removeColor(Color.Red)
        assertEquals(1, updated.size)
        assertEquals(Color.Green.toArgb(), updated.single().toArgb())
        assertEquals(1, prefs.loadPalette().size)
    }

    @Test
    fun deletionModeRoundTrip() {
        prefs.saveDeletionMode(DeletionMode.Permanent)
        assertEquals(DeletionMode.Permanent, prefs.loadDeletionMode())
        prefs.saveDeletionMode(DeletionMode.Trash)
        assertEquals(DeletionMode.Trash, prefs.loadDeletionMode())
    }

    @Test
    fun themeRoundTrip() {
        for (theme in listOf(AppTheme.OLED, AppTheme.Light, AppTheme.Dark, AppTheme.Dynamic)) {
            prefs.saveAppTheme(theme)
            assertEquals(theme, prefs.loadAppTheme())
        }
    }
}
