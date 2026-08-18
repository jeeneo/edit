package org.codeberg.editorie.data

// SPDX-License-Identifier: MIT

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import org.codeberg.editorie.options.save.DeletionMode

class UserPreferences(private val context: Context) {
    val defaultUndoLevels = 20
    val defaultExportQuality = 95

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    fun loadExifDateOnly(): Boolean = prefs().getBoolean(KEY_EXIF_DATE_ONLY, true)
    fun saveExifDateOnly(value: Boolean) = prefs().edit { putBoolean(KEY_EXIF_DATE_ONLY, value) }
    fun loadExportQuality(): Int = prefs().getInt(KEY_EXPORT_QUALITY, defaultExportQuality)
    fun saveExportQuality(quality: Int) = prefs().edit { putInt(KEY_EXPORT_QUALITY, quality) }
    fun loadPenWidth(): Float = prefs().getFloat(KEY_PEN_WIDTH, 8f)
    fun savePenWidth(width: Float) = prefs().edit { putFloat(KEY_PEN_WIDTH, width) }
    fun loadPenColor(): Color {
        val savedArgb = prefs().getInt(KEY_PEN, -1)
        if (savedArgb != -1) return Color(savedArgb)
        return loadPalette().firstOrNull() ?: DEFAULT_PALETTE.first()
    }

    fun savePenColor(color: Color) = prefs().edit { putInt(KEY_PEN, color.toArgb()) }
    fun loadPalette(): List<Color> {
        val p = prefs()
        val hasPalette = p.contains(KEY_PALETTE)
        if (!hasPalette) return DEFAULT_PALETTE
        val raw = p.getString(KEY_PALETTE, "") ?: ""
        if (raw.isBlank()) return DEFAULT_PALETTE
        return raw.split(",").mapNotNull { it.trim().toLongOrNull()?.let { v -> Color(v.toInt()) } }
            .ifEmpty { DEFAULT_PALETTE }
    }

    fun savePalette(colors: List<Color>) = prefs().edit {
        putString(KEY_PALETTE, colors.joinToString(",") { it.toArgb().toLong().toString() })
    }

    fun loadUndoLevels(): Int = prefs().getInt(KEY_MAX_UNDO, defaultUndoLevels)

    fun saveUndoLevels(levels: Int) = prefs().edit { putInt(KEY_MAX_UNDO, levels.coerceIn(1, 100)) }

    fun addColor(color: Color): List<Color> {
        val current = loadPalette().toMutableList()
        if (current.none { it.toArgb() == color.toArgb() }) {
            current.add(0, color)
            if (current.size > 24) current.removeAt(current.lastIndex)
            savePalette(current)
        }
        return current
    }

    fun removeColor(color: Color): List<Color> {
        val updated = loadPalette().filter { it.toArgb() != color.toArgb() }
        savePalette(updated)
        return updated
    }

    fun setColorAt(index: Int, newColor: Color): List<Color> {
        val current = loadPalette().toMutableList()
        if (index in current.indices) {
            current[index] = newColor
            savePalette(current)
        }
        return current
    }

    fun loadDeletionMode(): DeletionMode {
        return when (prefs().getString(KEY_DELETE_MODE, "trash")) {
            "permanent" -> DeletionMode.Permanent
            "trash" -> DeletionMode.Trash
            else -> DeletionMode.Trash
        }
    }

    fun saveDeletionMode(mode: DeletionMode) {
        val value = when (mode) {
            DeletionMode.Trash -> "trash"
            DeletionMode.Permanent -> "permanent"
        }
        prefs().edit { putString(KEY_DELETE_MODE, value) }
    }

    fun loadCloseOnSave(): Boolean = prefs().getBoolean(KEY_CLOSE_ON_SAVE, true)
    fun saveCloseOnSave(value: Boolean) = prefs().edit {
        putBoolean(KEY_CLOSE_ON_SAVE, value)
    }

    fun loadAppTheme(): AppTheme {
        return when (prefs().getString(KEY_THEME, "dynamic")) {
            "dynamic" -> AppTheme.Dynamic
            "light" -> AppTheme.Light
            "dark" -> AppTheme.Dark
            else -> AppTheme.OLED
        }
    }

    fun saveAppTheme(theme: AppTheme) {
        val value = when (theme) {
            AppTheme.OLED -> "OLED"
            AppTheme.Dynamic -> "dynamic"
            AppTheme.Light -> "light"
            AppTheme.Dark -> "dark"
        }
        prefs().edit { putString(KEY_THEME, value) }
    }

    fun loadDistraction(): Boolean = prefs().getBoolean(KEY_DISTRACTION, true)
    fun saveDistraction(value: Boolean) = prefs().edit { putBoolean(KEY_DISTRACTION, value) }

    fun loadHaptic(): Boolean = prefs().getBoolean(HAPTICS, true)
    fun saveHaptic(value: Boolean) = prefs().edit { putBoolean(HAPTICS, value) }

    fun loadNewImageColor(): Color {
        val savedArgb = prefs().getInt(KEY_NEW_IMAGE_COLOR, -1)
        return if (savedArgb != -1) Color(savedArgb) else Color.White
    }

    fun saveNewImageColor(color: Color) =
        prefs().edit { putInt(KEY_NEW_IMAGE_COLOR, color.toArgb()) }

    fun loadTextColor(): Color {
        val savedArgb = prefs().getInt(KEY_TEXT_COLOR, -1)
        return if (savedArgb != -1) Color(savedArgb) else loadPenColor()
    }

    fun saveTextColor(color: Color) = prefs().edit { putInt(KEY_TEXT_COLOR, color.toArgb()) }

    fun loadStickerFillColor(): Color? {
        val savedArgb = prefs().getInt(KEY_STICKER_FILL_COLOR, -1)
        return if (savedArgb != -1) Color(savedArgb) else null
    }

    fun saveStickerFillColor(color: Color?) {
        if (color != null && color != Color.Unspecified) {
            prefs().edit { putInt(KEY_STICKER_FILL_COLOR, color.toArgb()) }
        } else {
            prefs().edit { remove(KEY_STICKER_FILL_COLOR) }
        }
    }

    fun loadStickerOutlineColor(): Color? {
        val savedArgb = prefs().getInt(KEY_STICKER_OUTLINE_COLOR, -1)
        return if (savedArgb != -1) Color(savedArgb) else null
    }

    fun saveStickerOutlineColor(color: Color?) {
        if (color != null) prefs().edit { putInt(KEY_STICKER_OUTLINE_COLOR, color.toArgb()) }
        else prefs().edit { remove(KEY_STICKER_OUTLINE_COLOR) }
    }

    fun loadStickerOutlineThickness(): Float = prefs().getFloat(KEY_STICKER_OUTLINE_THICKNESS, 6f)

    fun saveStickerOutlineThickness(value: Float) = prefs().edit {
        putFloat(KEY_STICKER_OUTLINE_THICKNESS, value)
    }

    fun loadDefaultTool(): Pair<EditorPanel, EditorSubpanel?> {
        val value = prefs().getString(KEY_DEFAULT_TOOL, null) ?: return EditorPanel.None to null
        val parts = value.split(":", limit = 2)
        val modeName = parts[0]
        val panelName = parts.getOrNull(1)
        if (modeName == "Transform") {
            if (panelName != null) {
                val panel = try {
                    EditorSubpanel.Transform.valueOf(panelName)
                } catch (_: Exception) {
                    null
                }
                if (panel != null) return EditorPanel.Transform to panel
            }
            return EditorPanel.Transform to null
        }
        return when (modeName) {
            "Draw" -> EditorPanel.Draw to null
            "Text" -> EditorPanel.Text to null
            "Stickers" -> EditorPanel.Stickers to null
            "Adjust" -> EditorPanel.Adjust to null
            else -> EditorPanel.None to null
        }
    }

    fun saveDefaultMode(mode: EditorPanel, panel: EditorSubpanel? = null) {
        val value = when (mode) {
            is EditorPanel.Transform -> (panel as? EditorSubpanel.Transform)?.let { "Transform:${it.name}" }
                ?: "Transform"

            is EditorPanel.Draw -> "Draw"
            is EditorPanel.Text -> "Text"
            is EditorPanel.Stickers -> "Stickers"
            is EditorPanel.Adjust -> "Adjust"
            else -> "None"
        }
        prefs().edit { putString(KEY_DEFAULT_TOOL, value) }
    }

    companion object {
        const val PREFS_NAME = "editor_preferences"
        const val KEY_PEN = "pen_color"
        const val KEY_PALETTE = "custom_palette"
        const val KEY_PEN_WIDTH = "pen_width"
        const val KEY_DELETE_MODE = "delete_mode"
        const val KEY_CLOSE_ON_SAVE = "close_on_save"
        const val KEY_MAX_UNDO = "max_undo_levels"
        const val KEY_EXPORT_QUALITY = "export_quality"
        const val KEY_EXIF_DATE_ONLY = "exif_date_only"
        const val KEY_THEME = "app_theme"
        const val KEY_DISTRACTION = "distraction"
        const val HAPTICS = "haptics"
        const val KEY_NEW_IMAGE_COLOR = "new_image_color"
        const val KEY_TEXT_COLOR = "text_color"
        const val KEY_STICKER_FILL_COLOR = "sticker_fill_color"
        const val KEY_STICKER_OUTLINE_COLOR = "sticker_outline_color"
        const val KEY_STICKER_OUTLINE_THICKNESS = "sticker_outline_thickness"
        const val KEY_DEFAULT_TOOL = "default_tool"
        val DEFAULT_PALETTE = listOf(
            Color(200, 60, 60),
            Color(200, 130, 50),
            Color(200, 200, 50),
            Color(80, 180, 80),
            Color(50, 190, 190),
            Color(60, 90, 200),
            Color(130, 60, 200),
            Color(200, 60, 180),
            Color(200, 200, 200),
            Color(60, 60, 60)
        )
    }
}
