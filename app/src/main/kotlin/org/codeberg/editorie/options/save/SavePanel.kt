package org.codeberg.editorie.options.save

// SPDX-License-Identifier: MIT

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.codeberg.editorie.App
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.util.AppToasts
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private val WIDE_BREAKPOINT = 480.dp

private fun cycleDeletionMode(current: DeletionMode): DeletionMode =
    if (current == DeletionMode.Trash) DeletionMode.Permanent else DeletionMode.Trash

private fun DeletionMode.toastMessage() = when (this) {
    DeletionMode.Trash -> "Trash on overwrite"
    DeletionMode.Permanent -> "Permanent delete on overwrite"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FileNameField(
    customFileName: String,
    onFileNameChange: (String) -> Unit,
    exportFormat: ExportFormat,
    onExportFormatChange: (ExportFormat) -> Unit,
    exportQuality: Int,
    onExportQualityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    canLossless: Boolean,
    focusChanged: (Boolean) -> Unit = {},
) {
    val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var formatMenuExpanded by remember { mutableStateOf(false) }
    var fileNameFocused by remember { mutableStateOf(false) }
    var visibleSlider by remember { mutableStateOf(false) }
    val isAdjustable = (exportFormat != ExportFormat.PNG)
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = (visibleSlider && isAdjustable && !canLossless),
            enter = expandVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ) + fadeIn(
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
            ),
            exit = shrinkVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ) + fadeOut(
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(
                        top = 4.dp, bottom = 16.dp
                    )
            ) {
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(52.dp)
                )
                Slider(
                    value = exportQuality.toFloat(),
                    onValueChange = { HapticPatterns.tap(); onExportQualityChange(it.roundToInt()) },
                    valueRange = 10f..100f,
                    steps = 17,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$exportQuality",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        OutlinedTextField(
            value = customFileName.substringBeforeLast('.', customFileName),
            onValueChange = onFileNameChange,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            placeholder = { Text("Filename") },
            singleLine = true,
            shape = RoundedCornerShape(48.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onFocusChanged {
                    fileNameFocused = it.isFocused
                    focusChanged(it.isFocused)
                },
            trailingIcon = {
                AnimatedVisibility(
                    visible = !fileNameFocused,
                    enter = fadeIn(fastSpatialSpec),
                    exit = fadeOut(fastSpatialSpec),
                ) {
                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var longPressConsumed by remember { mutableStateOf(false) }
                            TextButton(onClick = {
                                if (longPressConsumed) {
                                    longPressConsumed = false
                                    return@TextButton
                                }
                                HapticPatterns.tap()
                                val next = ExportFormat.entries[(ExportFormat.entries.indexOf(
                                    exportFormat
                                ) + 1) % ExportFormat.entries.size]
                                onExportFormatChange(next)
                            }, modifier = Modifier.pointerInput(isAdjustable, canLossless) {
                                awaitEachGesture {
                                    awaitFirstDown(pass = PointerEventPass.Initial)
                                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                    val timedOut = withTimeoutOrNull(longPressTimeout) {
                                        do {
                                            val event =
                                                awaitPointerEvent(pass = PointerEventPass.Initial)
                                        } while (event.changes.any { it.pressed })
                                        false
                                    } == null
                                    if (timedOut) {
                                        if (isAdjustable && !canLossless) {
                                            longPressConsumed = true
                                            // HapticPatterns.longPress()
                                            visibleSlider = !visibleSlider
                                        } else if (canLossless) {
                                            longPressConsumed = true
                                            AppToasts.show("You don't need to set quality on lossless panel")
                                            // HapticPatterns.longPress()
                                        }
                                    }

                                }
                            }) {
                                Text(
                                    text = exportFormat.label,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (formatMenuExpanded) 180f else 0f,
                                animationSpec = fastSpatialSpec,
                                label = "chevronRotation"
                            )
                            IconButton(onClick = {
                                // HapticPatterns.tap()
                                formatMenuExpanded = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select format",
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = chevronRotation
                                    })
                            }
                        }
                        DropdownMenu(
                            expanded = formatMenuExpanded, onDismissRequest = {
                                HapticPatterns.tap(); formatMenuExpanded = false
                            }) {
                            ExportFormat.entries.forEach { fmt ->
                                val isSelected = exportFormat == fmt
                                DropdownMenuItem(text = {
                                    Text(
                                        text = fmt.label,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }, trailingIcon = {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    } else {
                                        Box(Modifier.size(24.dp))
                                    }
                                }, onClick = {
                                    HapticPatterns.tap()
                                    onExportFormatChange(fmt)
                                    formatMenuExpanded = false
                                })
                            }
                        }
                    }
                }
            })
    }
}

@SuppressLint("MissingHapticFeedback")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverwriteSplitButton(
    modifier: Modifier = Modifier,
    deletionMode: DeletionMode,
    onDeletionModeChange: (DeletionMode) -> Unit,
    onOverwrite: (DeletionMode) -> Unit,
    onSaveAs: () -> Unit,
    exifDateOnly: Boolean,
    onExifDateOnlyChange: (Boolean) -> Unit,
    fillWidth: Boolean = false,
    losslessPossible: Boolean,
    losslessEnabled: Boolean,
    overwriteEnabled: Boolean,
    onLosslessToggle: (Boolean) -> Unit,
) {
    val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var actionMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val scope = rememberCoroutineScope()
        SplitButtonDefaults.LeadingButton(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            interactionSource = interactionSource,
            onClick = {},
            modifier = Modifier
                .then(if (fillWidth) Modifier.weight(1f) else Modifier.wrapContentWidth())
                .height(48.dp)
                .pointerInput(deletionMode) {
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        down.consume()
                        val press = PressInteraction.Press(down.position)
                        scope.launch { interactionSource.emit(press) }
                        try {
                            val timedOut = withTimeoutOrNull(longPressTimeout) {
                                do {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            } == null
                            if (!timedOut) {
                                scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                                HapticPatterns.tap()
                                if (overwriteEnabled) onOverwrite(deletionMode)
                                else AppToasts.show("What are you overwriting..?")
                            } else {
                                val next = cycleDeletionMode(deletionMode)
                                onDeletionModeChange(next)
                                AppToasts.show(next.toastMessage())
//                                HapticPatterns.longPress()

                                do {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })

                                scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                            }
                        } catch (c: CancellationException) {
                            scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                            throw c
                        }
                    }
                }) {
            Icon(
                imageVector = when (deletionMode) {
                    DeletionMode.Trash -> Icons.Default.Delete
                    DeletionMode.Permanent -> Icons.Default.DeleteForever
                }, contentDescription = null
            )
            Spacer(Modifier.width(4.dp))
            Text("Overwrite")
        }
        Box {
            //noinspection MissingHapticFeedback
            SplitButtonDefaults.TrailingButton(
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.height(48.dp),
                checked = actionMenuExpanded,
                onCheckedChange = { HapticPatterns.tap(); actionMenuExpanded = it }) {
                val chevronRotation by animateFloatAsState(
                    targetValue = if (actionMenuExpanded) 180f else 0f,
                    animationSpec = fastSpatialSpec,
                    label = "actionChevronRotation"
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "More save options",
                    modifier = Modifier
                        .size(SplitButtonDefaults.TrailingIconSize)
                        .graphicsLayer { rotationZ = chevronRotation })
            }
            DropdownMenu(
                expanded = actionMenuExpanded,
                onDismissRequest = { HapticPatterns.tap(); actionMenuExpanded = false }) {
                DropdownMenuItem(text = { Text("Date only EXIF") }, leadingIcon = {
                    if (exifDateOnly) Icon(Icons.Default.Check, contentDescription = null)
                    else Box(Modifier.size(24.dp))
                }, onClick = {
                    HapticPatterns.tap()
                    onExifDateOnlyChange(!exifDateOnly)
                })
                if (losslessPossible) {
                    DropdownMenuItem(text = { Text("Lossless") }, leadingIcon = {
                        if (losslessEnabled) Icon(
                            Icons.Default.Check, contentDescription = null
                        )
                        else Box(Modifier.size(24.dp))
                    }, onClick = {
                        HapticPatterns.tap()
                        onLosslessToggle(!losslessEnabled)
                    })
                }
                HorizontalDivider()
                DropdownMenuItem(text = {
                    Text(
                        when (deletionMode) {
                            DeletionMode.Trash -> "Trash"
                            DeletionMode.Permanent -> "Delete"
                        }
                    )
                }, leadingIcon = {
                    Icon(
                        imageVector = when (deletionMode) {
                            DeletionMode.Trash -> Icons.Default.Delete
                            DeletionMode.Permanent -> Icons.Default.DeleteForever
                        }, contentDescription = null
                    )
                }, onClick = {
                    HapticPatterns.tap()
                    val next = cycleDeletionMode(deletionMode)
                    onDeletionModeChange(next)
                    AppToasts.show(next.toastMessage())
                })
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Save as") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    onClick = {
                        HapticPatterns.tap()
                        actionMenuExpanded = false
                        onSaveAs()
                    })
            }
        }
    }
}

@Composable
fun SaveOptions(
    customFileName: String,
    onFileNameChange: (String) -> Unit,
    onOverwrite: (DeletionMode) -> Unit,
    onSaveAs: () -> Unit,
    exportFormat: ExportFormat,
    onExportFormatChange: (ExportFormat) -> Unit,
    exportQuality: Int,
    onExportQualityChange: (Int) -> Unit,
    exifDateOnly: Boolean,
    onExifDateOnlyChange: (Boolean) -> Unit,
    losslessEnabled: Boolean,
    canLossless: Boolean,
    losslessPossible: Boolean,
    onLosslessToggle: (Boolean) -> Unit,
    focusChanged: (Boolean) -> Unit = {},
    overwriteEnabled: Boolean
) {
    var deletionMode by remember { mutableStateOf(App.prefs.loadDeletionMode()) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        if (maxWidth >= WIDE_BREAKPOINT) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FileNameField(
                    customFileName = customFileName,
                    onFileNameChange = onFileNameChange,
                    exportFormat = exportFormat,
                    onExportFormatChange = onExportFormatChange,
                    modifier = Modifier.weight(1f),
                    exportQuality = exportQuality,
                    onExportQualityChange = onExportQualityChange,
                    canLossless = canLossless,
                    focusChanged = focusChanged,
                )
                Spacer(Modifier.width(4.dp))
                OverwriteSplitButton(
                    deletionMode = deletionMode,
                    onDeletionModeChange = {
                        deletionMode = it
                        App.prefs.saveDeletionMode(it)
                    },
                    onOverwrite = onOverwrite,
                    onSaveAs = onSaveAs,
                    exifDateOnly = exifDateOnly,
                    onExifDateOnlyChange = onExifDateOnlyChange,
                    losslessEnabled = losslessEnabled,
                    onLosslessToggle = onLosslessToggle,
                    losslessPossible = losslessPossible,
                    overwriteEnabled = overwriteEnabled,
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()
            ) {
                FileNameField(
                    customFileName = customFileName,
                    onFileNameChange = onFileNameChange,
                    exportFormat = exportFormat,
                    onExportFormatChange = onExportFormatChange,
                    modifier = Modifier.fillMaxWidth(),
                    exportQuality = exportQuality,
                    onExportQualityChange = onExportQualityChange,
                    canLossless = canLossless,
                    focusChanged = focusChanged,
                )
                OverwriteSplitButton(
                    deletionMode = deletionMode,
                    onDeletionModeChange = {
                        deletionMode = it
                        App.prefs.saveDeletionMode(it)
                    },
                    onOverwrite = onOverwrite,
                    onSaveAs = onSaveAs,
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth(),
                    exifDateOnly = exifDateOnly,
                    onExifDateOnlyChange = onExifDateOnlyChange,
                    losslessEnabled = losslessEnabled,
                    onLosslessToggle = onLosslessToggle,
                    losslessPossible = losslessPossible,
                    overwriteEnabled = overwriteEnabled,
                )
            }
        }
    }
}
