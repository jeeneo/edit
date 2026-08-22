package org.codeberg.editorie.ui.bottombar

// SPDX-License-Identifier: MIT

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.codeberg.editorie.App
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.EditorState
import org.codeberg.editorie.data.EditorSubpanel
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.options.adjust.AdjustOptions
import org.codeberg.editorie.options.adjust.AdjustState
import org.codeberg.editorie.options.drawing.DrawOptions
import org.codeberg.editorie.options.drawing.DrawState
import org.codeberg.editorie.options.save.DeletionMode
import org.codeberg.editorie.options.save.ExportFormat
import org.codeberg.editorie.options.save.SaveOptions
import org.codeberg.editorie.options.stickers.StickerAsset
import org.codeberg.editorie.options.stickers.StickerOptions
import org.codeberg.editorie.options.stickers.StickerPanelState
import org.codeberg.editorie.options.text.TextEditorState
import org.codeberg.editorie.options.text.TextOptions
import org.codeberg.editorie.options.transform.TransformOptions
import org.codeberg.editorie.options.transform.TransformState
import org.codeberg.editorie.util.AppToasts
import java.util.Locale.getDefault

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BottomEditorBar(
    state: EditorState,
    onTransformStateChange: (TransformState) -> Unit,
    onApplyResize: (Int, Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onActivePanelChange: (EditorSubpanel.Transform?) -> Unit,
    onSetUndoLevels: (Int) -> Unit,
    onDrawStateChange: (DrawState) -> Unit,
    onClearStrokes: () -> Unit,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
    onTextStateChange: (TextEditorState) -> Unit,
    onCommitTextEdit: () -> Unit,
    onAddText: () -> Unit,
    onDeleteText: () -> Unit,
    onMoveLayer: (Long, Int) -> Unit,
    onAdjustChange: (AdjustState) -> Unit,
    onStickerStateChange: (StickerPanelState) -> Unit,
    onCommitStickerEdit: () -> Unit,
    onInsertSticker: (StickerAsset) -> Unit,
    onDeleteSticker: (Long) -> Unit,
    onExportFormatChange: (ExportFormat) -> Unit,
    onFileNameChange: (String) -> Unit,
    onOverwrite: (DeletionMode) -> Unit,
    onSaveAs: () -> Unit,
    onExportQualityChange: (Int) -> Unit,
    onExifDateOnlyChange: (Boolean) -> Unit,
    onLosslessToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onInsert: () -> Unit,
    onModeChange: (EditorPanel, Long?) -> Unit,
    closeOnSave: Boolean,
    onToggleCloseOnSave: (Boolean) -> Unit,
    onToggleEyedropperAutoSwitch: (Boolean) -> Unit,
    focusChanged: (Boolean) -> Unit = {},
    onDrawSizePreviewChange: (Boolean) -> Unit = {},
) {
    val cornerRadius by animateDpAsState(
        targetValue = 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bottom_bar_corners",
    )
    val pendingDefaultMode = remember { mutableStateOf<EditorPanel?>(null) }
    fun EditorPanel.displayLabel(): String = when (this) {
        is EditorPanel.Transform -> "transform"
        is EditorPanel.Draw -> "draw"
        is EditorPanel.Text -> "text"
        is EditorPanel.Stickers -> "stickers"
        is EditorPanel.Adjust -> "adjust"
        else -> "none"
    }

    fun saveExpandedDefault(mode: EditorPanel, setToNone: Boolean = false) {
        if (setToNone) {
            App.prefs.saveDefaultMode(EditorPanel.None)
            Log.d("saveExpandedDefault", "EditorPanel set to none")
            AppToasts.show("Panel hidden")
            return
        }
        val panel =
            if (mode is EditorPanel.Transform && state.mode is EditorPanel.Transform) state.editorSubpanel as? EditorSubpanel.Transform else null
        App.prefs.saveDefaultMode(mode, panel)
        val label = panel?.name?.lowercase(getDefault()) ?: mode.displayLabel()
        Log.d("saveExpandedDefault", "Panel set to $label")
        AppToasts.show("Panel set to $label")
    }

    pendingDefaultMode.value?.let { mode ->
        val label = mode.displayLabel()
        AlertDialog(
            onDismissRequest = { pendingDefaultMode.value = null },
            title = { Text("Default panel") },
            text = { Text("Do you want to hide the panel by default or set to $label?") },
            dismissButton = {
                TextButton(
                    onClick = {
                        HapticPatterns.tap()
                        saveExpandedDefault(mode, setToNone = false)
                        pendingDefaultMode.value = null
                    }) { Text("Set to $label") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        HapticPatterns.tap()
                        saveExpandedDefault(mode, setToNone = true)
                        pendingDefaultMode.value = null
                    }, shapes = ButtonShapes(
                        shape = RoundedCornerShape(21.dp), pressedShape = RoundedCornerShape(8.dp)
                    ), contentPadding = ButtonDefaults.SmallContentPadding
                ) { Text("Hide") }
            },
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
    ) {
        Column {
            AnimatedContent(
                targetState = state.mode,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 4 } + fadeOut())
                },
                label = "editor_panel",
            ) { currentMode ->
                when (currentMode) {
                    EditorPanel.Transform -> TransformOptions(
                        transformState = state.transformState,
                        onTransformStateChange = onTransformStateChange,
                        onApplyResize = onApplyResize,
                        onUndoResize = onUndo,
                        onRedoResize = onRedo,
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        imageBitmap = state.workingBitmap,
                        activeSubpanel = state.editorSubpanel as? EditorSubpanel.Transform,
                        onActivePanelChange = onActivePanelChange,
                        onSetUndoLevels = onSetUndoLevels,
                        currentUndoLevels = state.undoLevels,
                    )

                    EditorPanel.Draw -> DrawOptions(
                        drawState = state.drawState,
                        onDrawStateChange = onDrawStateChange,
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onClearStrokes = onClearStrokes,
                        onAddColor = onAddColor,
                        onRemoveColor = onRemoveColor,
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        onSetUndoLevels = onSetUndoLevels,
                        currentUndoLevels = state.undoLevels,
                        hasStrokes = state.layers.any {
                            it is EditorLayer.Stroke || it is EditorLayer.EraseStroke
                        },
                        eyedropperAutoSwitch = state.eyedropperAutoSwitch,
                        onToggleEyedropperAutoSwitch = onToggleEyedropperAutoSwitch,
                        onSizePreviewChange = onDrawSizePreviewChange,
                    )

                    EditorPanel.Text -> TextOptions(
                        state = state.textEditorState,
                        onChange = onTextStateChange,
                        onCommit = onCommitTextEdit,
                        onDeleteText = onDeleteText,
                        onAddText = onAddText,
                        onMoveLayer = { delta ->
                            state.textEditorState.selectedId?.let { id ->
                                onMoveLayer(id, delta)
                            }
                        },
                        palette = state.palette,
                        onAddColor = onAddColor,
                        onRemoveColor = onRemoveColor,
                    )

                    EditorPanel.Adjust -> AdjustOptions(
                        state = state.adjust,
                        onChange = onAdjustChange,
                    )

                    EditorPanel.Stickers -> StickerOptions(
                        state = state.stickerPanelState,
                        onChange = onStickerStateChange,
                        onCommit = onCommitStickerEdit,
                        onInsert = onInsertSticker,
                        onInsertCustom = onInsert,
                        onDelete = onDeleteSticker,
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onMoveLayer = { delta ->
                            state.stickerPanelState.selectedId?.let { id ->
                                onMoveLayer(id, delta)
                            }
                        },
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        palette = state.palette,
                        onAddColor = onAddColor,
                        onRemoveColor = onRemoveColor,
                        onSetUndoLevels = onSetUndoLevels,
                        currentUndoLevels = state.undoLevels,
                    )

                    EditorPanel.Save -> SaveOptions(
                        customFileName = state.customFileName,
                        exportFormat = state.exportFormat,
                        onExportFormatChange = onExportFormatChange,
                        onFileNameChange = onFileNameChange,
                        onOverwrite = onOverwrite,
                        onSaveAs = onSaveAs,
                        exportQuality = state.exportQuality,
                        onExportQualityChange = onExportQualityChange,
                        exifDateOnly = state.exifDateOnly,
                        onExifDateOnlyChange = onExifDateOnlyChange,
                        canLossless = state.canLossless,
                        onLosslessToggle = onLosslessToggle,
                        losslessEnabled = state.losslessEnabled,
                        losslessPossible = state.losslessPossible,
                        focusChanged = focusChanged,
                        overwriteEnabled = state.overwriteEnabled
                    )

                    EditorPanel.None -> {
                        Log.d("EditorMode", "None")
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .padding(
                        bottom = (WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() * 0.7f)
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                //noinspection MissingHapticFeedback
                EditorModeButton(
                    onClick = onOpen,
                    onLongClick = onInsert,
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Open image")
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                    ) {
                        val expanded = state.mode !is EditorPanel.None
                        //noinspection MissingHapticFeedback
                        EditorModeButton(
                            EditorPanel.Transform,
                            state.mode,
                            { onModeChange(it, null) },
                            onLongClick = {
                                if (expanded) saveExpandedDefault(EditorPanel.Transform)
                                else pendingDefaultMode.value = EditorPanel.Transform
                                if (expanded && state.mode != EditorPanel.Transform) onModeChange(
                                    EditorPanel.Transform, null
                                )
                            },
                        ) {
                            Icon(Icons.Default.Crop, "Crop")
                        }
                        //noinspection MissingHapticFeedback
                        EditorModeButton(
                            EditorPanel.Draw,
                            state.mode,
                            { onModeChange(it, null) },
                            onLongClick = {
                                if (expanded) saveExpandedDefault(EditorPanel.Draw)
                                else pendingDefaultMode.value = EditorPanel.Draw
                                if (expanded && state.mode != EditorPanel.Draw) onModeChange(
                                    EditorPanel.Draw, null
                                )
                            },
                        ) {
                            Icon(Icons.Default.Draw, "Draw")
                        }
                        //noinspection MissingHapticFeedback
                        EditorModeButton(
                            EditorPanel.Text,
                            state.mode,
                            { onModeChange(it, null) },
                            onLongClick = {
                                if (expanded) saveExpandedDefault(EditorPanel.Text)
                                else pendingDefaultMode.value = EditorPanel.Text
                                if (expanded && state.mode != EditorPanel.Text) onModeChange(
                                    EditorPanel.Text, null
                                )

                            },
                        ) {
                            Icon(Icons.Default.TextFields, "Text")
                        }
                        //noinspection MissingHapticFeedback
                        EditorModeButton(
                            EditorPanel.Stickers,
                            state.mode,
                            { onModeChange(it, null) },
                            onLongClick = {
                                if (expanded) saveExpandedDefault(EditorPanel.Stickers)
                                else pendingDefaultMode.value = EditorPanel.Stickers
                                if (expanded && state.mode != EditorPanel.Stickers) onModeChange(
                                    EditorPanel.Stickers, null
                                )
                            },
                        ) {
                            Icon(Icons.Default.Star, "Stickers")
                        }
                        //noinspection MissingHapticFeedback
                        EditorModeButton(
                            EditorPanel.Adjust,
                            state.mode,
                            { onModeChange(it, null) },
                            onLongClick = {
                                if (expanded) saveExpandedDefault(EditorPanel.Adjust)
                                else pendingDefaultMode.value = EditorPanel.Adjust
                                if (expanded && state.mode != EditorPanel.Adjust) onModeChange(
                                    EditorPanel.Adjust, null
                                )
                            },
                        ) {
                            Icon(Icons.Default.Tune, "Adjust")
                        }
                    }
                }
                EditorModeButton(
                    EditorPanel.Save,
                    state.mode,
                    onModeChange = { onModeChange(it, null) },
                    onLongClick = {
                        val next = !closeOnSave
                        onToggleCloseOnSave(next)
                        // HapticPatterns.longPress()
                        AppToasts.show(if (next) "Close after save" else "Stay after save")
                    },
                ) {
                    Icon(Icons.Default.Save, "Save")
                }
            }
        }
    }
}

@Composable
fun EditorModeButton(
    mode: EditorPanel? = null,
    currentMode: EditorPanel? = null,
    onModeChange: ((EditorPanel) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    val selected = mode != null && currentMode != null && mode::class == currentMode::class
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            //noinspection MissingHapticFeedback
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    HapticPatterns.tap()
                    if (mode != null && onModeChange != null) onModeChange(mode)
                    onClick?.invoke()
                },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
