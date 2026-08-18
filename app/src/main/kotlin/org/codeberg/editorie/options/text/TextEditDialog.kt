package org.codeberg.editorie.options.text

// SPDX-License-Identifier: MIT

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.ui.ColorPickerBar

@Composable
fun TextEditDialog(
    state: TextEditorState,
    onChange: (TextEditorState) -> Unit,
    onCommit: () -> Unit,
    onDeleteText: () -> Unit,
    onDismiss: () -> Unit,
    palette: List<Color>,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
) {
    var spacingText by remember { mutableStateOf(state.letterSpacing.toString()) }
    val focusRequester = remember { FocusRequester() }

    fun closeAndCommit() {
        onCommit()
        onDismiss()
    }

    Dialog(
        onDismissRequest = { closeAndCommit() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 640.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.draft.isEmpty()) {
                            "Edit text"
                        } else {
                            "Edit ${state.draft}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        HapticPatterns.tap()
                        onDeleteText()
                        onDismiss()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete text",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = { onChange(state.copy(draft = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .focusRequester(focusRequester),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    placeholder = { Text("Text") },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = spacingText,
                        onValueChange = { newValue ->
                            spacingText = newValue
                            newValue.toFloatOrNull()?.let { spacing ->
                                onChange(
                                    state.copy(letterSpacing = spacing.coerceIn(-5f, 1f))
                                )
                            }
                        },
                        label = { Text("Spacing") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    listOf(
                        EditorFontFamily.SansSerif,
                        EditorFontFamily.Serif,
                        EditorFontFamily.Monospace,
                    ).forEach { family ->
                        ToggleButton(
                            checked = state.fontFamily == family, onCheckedChange = {
                                HapticPatterns.tap()
                                onChange(state.copy(fontFamily = family))
                            }) { Text(family.label) }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.fontSizeSp.toInt()}px",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.widthIn(min = 48.dp)
                    )
                    //noinspection MissingHapticFeedback
                    Slider(
                        value = state.fontSizeSp,
                        onValueChange = { onChange(state.copy(fontSizeSp = it)) },
                        valueRange = 12f..120f,
                        modifier = Modifier.weight(1f),
                    )
                }
                StyleTogglesRow(state, onChange)
                AlignmentRow(state, onChange)
                ColorPickerBar(
                    activeColor = state.color,
                    palette = palette,
                    onColorSelected = { onChange(state.copy(color = it)) },
                    onAddColor = onAddColor,
                    onRemoveColor = onRemoveColor,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = { HapticPatterns.tap(); closeAndCommit() }, shapes = ButtonShapes(
                            shape = RoundedCornerShape(21.dp),
                            pressedShape = RoundedCornerShape(8.dp)
                        ), contentPadding = ButtonDefaults.SmallContentPadding
                    ) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun StyleTogglesRow(
    state: TextEditorState,
    onChange: (TextEditorState) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        ToggleButton(
            checked = state.fontWeight == FontWeight.Bold,
            onCheckedChange = {
                HapticPatterns.tap()
                onChange(
                    state.copy(
                        fontWeight = if (state.fontWeight == FontWeight.Bold) FontWeight.Normal else FontWeight.Bold
                    )
                )
            },
        ) { Icon(Icons.Default.FormatBold, "Bold") }
        ToggleButton(
            checked = state.italic,
            onCheckedChange = { HapticPatterns.tap(); onChange(state.copy(italic = !state.italic)) },
        ) { Icon(Icons.Default.FormatItalic, "Italic") }
        ToggleButton(
            checked = state.underline,
            onCheckedChange = { HapticPatterns.tap(); onChange(state.copy(underline = !state.underline)) },
        ) { Icon(Icons.Default.FormatUnderlined, "Underline") }
        ToggleButton(
            checked = state.strikethrough,
            onCheckedChange = { HapticPatterns.tap(); onChange(state.copy(strikethrough = !state.strikethrough)) },
        ) { Icon(Icons.Default.FormatStrikethrough, "Strikethrough") }
        ToggleButton(
            checked = state.wordWrap,
            onCheckedChange = { HapticPatterns.tap(); onChange(state.copy(wordWrap = !state.wordWrap)) },
        ) { Icon(Icons.AutoMirrored.Filled.WrapText, "Word wrap") }
    }
}

@Composable
private fun AlignmentRow(
    state: TextEditorState,
    onChange: (TextEditorState) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        ToggleButton(
            checked = state.align == TextAlign.Left,
            onCheckedChange = {
                HapticPatterns.tap()
                onChange(state.copy(align = if (state.align == TextAlign.Left) null else TextAlign.Left))
            },
        ) { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, "Align left") }
        ToggleButton(
            checked = state.align == TextAlign.Center,
            onCheckedChange = {
                HapticPatterns.tap()
                onChange(state.copy(align = if (state.align == TextAlign.Center) null else TextAlign.Center))
            },
        ) { Icon(Icons.Default.FormatAlignCenter, "Align center") }
        ToggleButton(
            checked = state.align == TextAlign.Right,
            onCheckedChange = {
                HapticPatterns.tap()
                onChange(state.copy(align = if (state.align == TextAlign.Right) null else TextAlign.Right))
            },
        ) { Icon(Icons.AutoMirrored.Filled.FormatAlignRight, "Align right") }
        ToggleButton(
            checked = state.align == TextAlign.Justify,
            onCheckedChange = {
                HapticPatterns.tap()
                onChange(state.copy(align = if (state.align == TextAlign.Justify) null else TextAlign.Justify))
            },
        ) { Icon(Icons.Default.FormatAlignJustify, "Justify") }
    }
}
