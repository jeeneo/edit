package org.codeberg.editorie.options.text

// SPDX-License-Identifier: MIT

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

data class TextOverlay(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val color: Color,
    val fontSizeSp: Float,
    val fontWeight: FontWeight = FontWeight.Normal,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val positionPx: Offset,
    val scale: Float = 1f,
    val boxWidthPx: Float = 400f,
    val boxHeightPx: Float? = null,
    val rotation: Float = 0f,
    val align: TextAlign? = null,
    val wordWrap: Boolean = true,
    val fontFamily: EditorFontFamily = EditorFontFamily.SansSerif,
    val letterSpacing: Float = 0f,
)

data class TextEditorState(
    val draft: String = "",
    val fontSizeSp: Float = 48f,
    val color: Color = Color(0, 0, 0),
    val fontWeight: FontWeight = FontWeight.Normal,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val align: TextAlign? = null,
    val wordWrap: Boolean = true,
    val selectedId: Long? = null,
    val fontFamily: EditorFontFamily = EditorFontFamily.SansSerif,
    val letterSpacing: Float = 0f,
)

fun TextOverlay.toTextPaint(textSize: Float, color: Int? = null): TextPaint = buildTextPaint(
    textSize,
    this.fontWeight,
    this.italic,
    this.underline,
    this.strikethrough,
    color,
    fontFamily = this.fontFamily,
    letterSpacingEm = this.letterSpacing,
)

enum class EditorFontFamily(val label: String) {
    SansSerif("Sans"), Serif("Serif"), Monospace("Mono");

    fun toTypeface(): Typeface = when (this) {
        SansSerif -> Typeface.SANS_SERIF
        Serif -> Typeface.SERIF
        Monospace -> Typeface.MONOSPACE
    }
}

fun buildTextPaint(
    textSize: Float,
    fontWeight: FontWeight,
    italic: Boolean,
    underline: Boolean,
    strikethrough: Boolean,
    color: Int? = null,
    fontFamily: EditorFontFamily = EditorFontFamily.SansSerif,
    letterSpacingEm: Float = 0f,
): TextPaint = TextPaint().apply {
    isAntiAlias = true
    this.textSize = textSize
    typeface = fontFamily.toTypeface()
    isFakeBoldText = fontWeight == FontWeight.Bold
    textSkewX = if (italic) -0.25f else 0f
    if (underline) flags = flags or Paint.UNDERLINE_TEXT_FLAG
    if (strikethrough) flags = flags or Paint.STRIKE_THRU_TEXT_FLAG
    this.letterSpacing = letterSpacingEm
    color?.let { this.color = it }
}

fun makeStaticLayout(
    text: String,
    textPaint: TextPaint,
    width: Int,
    align: TextAlign? = null,
    wordWrap: Boolean = true
): Layout {
    val alignment = when (align) {
        TextAlign.Left, null, TextAlign.Start -> Layout.Alignment.ALIGN_NORMAL
        TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
        TextAlign.Right, TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
        TextAlign.Justify -> Layout.Alignment.ALIGN_NORMAL
        else -> Layout.Alignment.ALIGN_NORMAL
    }
    val layoutWidth = if (wordWrap) width else Int.MAX_VALUE
    val builder = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth)
        .setAlignment(alignment).setIncludePad(false)
    if (align == TextAlign.Justify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
    }
    return builder.build()
}
