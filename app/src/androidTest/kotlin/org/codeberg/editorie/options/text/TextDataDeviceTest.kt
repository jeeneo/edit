package org.codeberg.editorie.options.text

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextDataDeviceTest {

    @Test
    fun buildTextPaintBasics() {
        val tp = buildTextPaint(
            textSize = 42f,
            fontWeight = FontWeight.Normal,
            italic = false,
            underline = false,
            strikethrough = false,
        )
        assertEquals(42f, tp.textSize)
        assertTrue(tp.isAntiAlias)
        assertFalse(tp.isFakeBoldText)
        assertEquals(0f, tp.textSkewX)
        assertEquals(0, tp.flags and Paint.UNDERLINE_TEXT_FLAG)
        assertEquals(0, tp.flags and Paint.STRIKE_THRU_TEXT_FLAG)
    }

    @Test
    fun buildTextPaintStyleFlags() {
        val tp = buildTextPaint(
            textSize = 20f,
            fontWeight = FontWeight.Bold,
            italic = true,
            underline = true,
            strikethrough = true,
            color = 0xFF112233.toInt(),
            letterSpacingEm = 0.2f,
        )
        assertTrue(tp.isFakeBoldText)
        assertEquals(-0.25f, tp.textSkewX)
        assertTrue(tp.flags and Paint.UNDERLINE_TEXT_FLAG != 0)
        assertTrue(tp.flags and Paint.STRIKE_THRU_TEXT_FLAG != 0)
        assertEquals(0xFF112233.toInt(), tp.color)
        assertEquals(0.2f, tp.letterSpacing)
    }

    @Test
    fun fontFamiliesMapToPlatformTypefaces() {
        assertEquals(Typeface.SANS_SERIF, EditorFontFamily.SansSerif.toTypeface())
        assertEquals(Typeface.SERIF, EditorFontFamily.Serif.toTypeface())
        assertEquals(Typeface.MONOSPACE, EditorFontFamily.Monospace.toTypeface())
    }

    @Test
    fun toTextPaintUsesOverlayStyling() {
        val overlay = TextOverlay(
            id = 1, text = "t", color = Color.Black,
            fontSizeSp = 20f, positionPx = Offset.Zero,
            underline = true, fontFamily = EditorFontFamily.Monospace,
        )
        val tp = overlay.toTextPaint(textSize = 33f, color = 0xFF00FF00.toInt())
        assertEquals(33f, tp.textSize)
        assertEquals(0xFF00FF00.toInt(), tp.color)
        assertTrue(tp.flags and Paint.UNDERLINE_TEXT_FLAG != 0)
        assertEquals(Typeface.MONOSPACE, tp.typeface)
    }

    @Test
    fun wordWrapProducesMultipleLines() {
        val tp = buildTextPaint(
            30f, FontWeight.Normal,
            italic = false,
            underline = false,
            strikethrough = false
        )
        val layout = makeStaticLayout(
            "many words that will definitely not fit on one narrow line",
            tp, width = 120, wordWrap = true,
        )
        assertTrue("expected wrapping, got ${layout.lineCount} line(s)", layout.lineCount > 1)
    }

    @Test
    fun noWordWrapKeepsSingleLine() {
        val tp = buildTextPaint(
            30f, FontWeight.Normal, italic = false,
            underline = false,
            strikethrough = false
        )
        val layout = makeStaticLayout(
            "many words that will definitely not fit on one narrow line",
            tp, width = 120, wordWrap = false,
        )
        assertEquals(1, layout.lineCount)
    }

    @Test
    fun alignmentMapping() {
        val tp = buildTextPaint(
            20f, FontWeight.Normal, italic = false,
            underline = false,
            strikethrough = false
        )

        fun alignOf(align: TextAlign?) =
            makeStaticLayout("text", tp, 200, align = align).alignment

        assertEquals(Layout.Alignment.ALIGN_NORMAL, alignOf(null))
        assertEquals(Layout.Alignment.ALIGN_NORMAL, alignOf(TextAlign.Left))
        assertEquals(Layout.Alignment.ALIGN_NORMAL, alignOf(TextAlign.Start))
        assertEquals(Layout.Alignment.ALIGN_CENTER, alignOf(TextAlign.Center))
        assertEquals(Layout.Alignment.ALIGN_OPPOSITE, alignOf(TextAlign.Right))
        assertEquals(Layout.Alignment.ALIGN_OPPOSITE, alignOf(TextAlign.End))
        assertEquals(Layout.Alignment.ALIGN_NORMAL, alignOf(TextAlign.Justify))
    }

    companion object {
        private const val EPS = 1e-4f
    }

    @Test
    fun buildOverlaySetsBasicFields() {
        val state = TextEditorState(
            draft = "hello",
            fontSizeSp = 32f,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            italic = true,
            underline = true,
            strikethrough = true,
            align = null,
            wordWrap = true,
            fontFamily = EditorFontFamily.Monospace,
            letterSpacing = 0.1f,
        )
        val overlay = TextOps.buildOverlay(
            "hello", state,
            viewCrop = Rect(0f, 0f, 200f, 400f),
            density = Density(1f, 1f),
        )
        assertEquals("hello", overlay.text)
        assertEquals(Color.Red, overlay.color)
        assertEquals(32f, overlay.fontSizeSp, EPS)
        assertEquals(FontWeight.Bold, overlay.fontWeight)
        assertTrue(overlay.italic)
        assertTrue(overlay.underline)
        assertTrue(overlay.strikethrough)
        assertEquals(true, overlay.wordWrap)
        assertEquals(EditorFontFamily.Monospace, overlay.fontFamily)
        assertEquals(0.1f, overlay.letterSpacing, EPS)
    }

    @Test
    fun buildOverlayPositionsRelativeToViewCrop() {
        val overlay = TextOps.buildOverlay(
            "x", TextEditorState(),
            viewCrop = Rect(50f, 100f, 250f, 500f),
            density = Density(1f, 1f),
        )
        assertEquals(120f, overlay.boxWidthPx, EPS)
        assertEquals(90f, overlay.positionPx.x, EPS)
        assertEquals(220f, overlay.positionPx.y, EPS)
    }

    @Test
    fun buildOverlayBoxWidthIsSixtyPercentOfCropWidth() {
        val overlay = TextOps.buildOverlay(
            "x", TextEditorState(),
            viewCrop = Rect(0f, 0f, 500f, 100f),
            density = Density(1f, 1f),
        )
        assertEquals(300f, overlay.boxWidthPx, EPS)
    }

    @Test
    fun buildOverlayBoxHeightMatchesLayoutHeight() {
        val overlay = TextOps.buildOverlay(
            "hello world", TextEditorState(),
            viewCrop = Rect(0f, 0f, 200f, 400f),
            density = Density(1f, 1f),
        )
        assertTrue("box height should be positive", overlay.boxHeightPx!! > 0f)
    }

    @Test
    fun buildOverlayWithEmptyDraft() {
        val overlay = TextOps.buildOverlay(
            "", TextEditorState(),
            viewCrop = Rect(0f, 0f, 200f, 400f),
            density = Density(1f, 1f),
        )
        assertEquals("", overlay.text)
        assertTrue(overlay.boxHeightPx!! >= 0f)
    }

    @Test
    fun buildOverlayScalesFontSizeWithDensity() {
        val state = TextEditorState(fontSizeSp = 24f)
        val normal = TextOps.buildOverlay("x", state, Rect(0f, 0f, 100f, 100f), Density(1f, 1f))
        val scaled = TextOps.buildOverlay("x", state, Rect(0f, 0f, 100f, 100f), Density(2f, 1.5f))
        assertEquals(24f, normal.fontSizeSp, EPS)
        assertEquals(24f, scaled.fontSizeSp, EPS)
        assertTrue(
            "scaled box height should be larger",
            scaled.boxHeightPx!! > normal.boxHeightPx!!
        )
    }

    @Test
    fun buildOverlayWordWrapAffectsLayoutHeight() {
        val long = "a ".repeat(50).trimEnd()
        val wrapped = TextOps.buildOverlay(
            long, TextEditorState(wordWrap = true),
            Rect(0f, 0f, 200f, 400f), Density(1f, 1f),
        )
        val unwrapped = TextOps.buildOverlay(
            long, TextEditorState(wordWrap = false),
            Rect(0f, 0f, 200f, 400f), Density(1f, 1f),
        )
        assertTrue(
            "wrapped should be taller than unwrapped",
            wrapped.boxHeightPx!! > unwrapped.boxHeightPx!!
        )
    }

    @Test
    fun buildOverlayPassesColorThrough() {
        val c = Color(red = 170, green = 187, blue = 204)
        val overlay = TextOps.buildOverlay(
            "x", TextEditorState(color = c),
            Rect(0f, 0f, 100f, 100f), Density(1f, 1f),
        )
        assertEquals(c, overlay.color)
    }

    @Test
    fun buildOverlayDefaults() {
        val overlay = TextOps.buildOverlay(
            "defaults", TextEditorState(),
            Rect(10f, 20f, 110f, 220f), Density(1f, 1f),
        )
        assertEquals("defaults", overlay.text)
        assertEquals(Color(0, 0, 0), overlay.color)
        assertEquals(48f, overlay.fontSizeSp, EPS)
        assertEquals(FontWeight.Normal, overlay.fontWeight)
        assertFalse(overlay.italic)
        assertFalse(overlay.underline)
        assertFalse(overlay.strikethrough)
        assertEquals(true, overlay.wordWrap)
        assertEquals(EditorFontFamily.SansSerif, overlay.fontFamily)
        assertEquals(0f, overlay.letterSpacing, EPS)
    }
}
