package org.codeberg.editorie.options.transform

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.codeberg.editorie.App
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.EditorSubpanel
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.ui.Checkerboard
import org.codeberg.editorie.ui.ColorEditorDialog
import org.codeberg.editorie.ui.bottombar.RedoIconButton
import org.codeberg.editorie.ui.bottombar.RoundedActionButton
import org.codeberg.editorie.ui.bottombar.UndoIconButton
import org.codeberg.editorie.util.AppToasts
import org.codeberg.editorie.util.MATH_SYMBOLS
import org.codeberg.editorie.util.evaluateMath
import java.util.Locale.getDefault
import kotlin.math.roundToInt

@Composable
fun TransformOptions(
    transformState: TransformState,
    onTransformStateChange: (TransformState) -> Unit,
    onApplyResize: (Int, Int) -> Unit,
    onUndoResize: () -> Unit,
    onRedoResize: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    imageBitmap: Bitmap?,
    activeSubpanel: EditorSubpanel.Transform?,
    onActivePanelChange: (EditorSubpanel.Transform?) -> Unit,
    onSetUndoLevels: (Int) -> Unit,
    currentUndoLevels: Int,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = activeSubpanel, transitionSpec = {
                slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
            }, label = "crop_options"
        ) { subpanel ->
            when (subpanel) {
                EditorSubpanel.Transform.Crop -> CropPanel(
                    aspectRatio = transformState.aspectRatio,
                    onAspectRatio = { onTransformStateChange(transformState.copy(aspectRatio = it)) },
                    onUndo = onUndoResize,
                    onRedo = onRedoResize,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onSetUndoLevels = onSetUndoLevels,
                    currentUndoLevels = currentUndoLevels,
                )

                EditorSubpanel.Transform.Resize -> ResizePanel(
                    transformState = transformState,
                    onTransformStateChange = onTransformStateChange,
                    onApplyResize = onApplyResize,
                    onUndoResize = onUndoResize,
                    onRedoResize = onRedoResize,
                    imageBitmap = imageBitmap,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onSetUndoLevels = onSetUndoLevels,
                    currentUndoLevels = currentUndoLevels,
                )

                EditorSubpanel.Transform.Rotate -> RotationPanel(
                    rotation = transformState.rotation,
                    onRotation = { onTransformStateChange(transformState.copy(rotation = it)) },
                )

                EditorSubpanel.Transform.Mirror -> MirrorPanel(
                    flipHorizontal = transformState.flipHorizontal,
                    onFlipHorizontal = { onTransformStateChange(transformState.copy(flipHorizontal = it)) },
                    flipVertical = transformState.flipVertical,
                    onFlipVertical = { onTransformStateChange(transformState.copy(flipVertical = it)) },
                )

                null -> Unit
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorSubpanel.Transform.entries.forEach { key ->
                val icon = when (key) {
                    EditorSubpanel.Transform.Crop -> Icons.Default.Crop
                    EditorSubpanel.Transform.Resize -> Icons.Default.PhotoSizeSelectLarge
                    EditorSubpanel.Transform.Rotate -> Icons.AutoMirrored.Filled.RotateRight
                    EditorSubpanel.Transform.Mirror -> Icons.Default.Flip
                }
                //noinspection MissingHapticFeedback
                RoundedActionButton(
                    icon = icon, label = key.name, active = activeSubpanel == key,
                    onClick = {
                        onActivePanelChange(if (activeSubpanel == key) null else key)
                    },
                    onLongClick = {
                        App.prefs.saveDefaultMode(EditorPanel.Transform, key)
                        if (activeSubpanel != key) onActivePanelChange(key)
                        AppToasts.show("Subpanel set to ${key.name.lowercase(getDefault())}")
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MirrorPanel(
    flipHorizontal: Boolean,
    onFlipHorizontal: (Boolean) -> Unit,
    flipVertical: Boolean,
    onFlipVertical: (Boolean) -> Unit,
) {
    val toggleColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.primary
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
    ) {
        ToggleButton(
            checked = flipHorizontal, onCheckedChange = {
                onFlipHorizontal(it)
                HapticPatterns.tap()
            }, colors = toggleColors
        ) {
            Text("Flip H")
        }
        ToggleButton(
            checked = flipVertical, onCheckedChange = {
                onFlipVertical(it)
                HapticPatterns.tap()
            }, colors = toggleColors
        ) {
            Text("Flip V")
        }
    }
}

@Composable
private fun ResizePanel(
    transformState: TransformState,
    onTransformStateChange: (TransformState) -> Unit,
    onApplyResize: (Int, Int) -> Unit,
    onUndoResize: () -> Unit,
    onRedoResize: () -> Unit,
    imageBitmap: Bitmap?,
    canUndo: Boolean,
    canRedo: Boolean,
    onSetUndoLevels: (Int) -> Unit,
    currentUndoLevels: Int,
) {
    val showColorEditor = remember { mutableStateOf(false) }
    if (showColorEditor.value) {
        ColorEditorDialog(
            initial = transformState.canvasFillColor,
            onDismiss = { showColorEditor.value = false },
            onConfirm = { color ->
                onTransformStateChange(transformState.copy(canvasFillColor = color))
                showColorEditor.value = false
            },
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            ResizeMode.entries.forEach { mode ->
                ToggleButton(
                    checked = transformState.resizeMode == mode,
                    onCheckedChange = {
                        HapticPatterns.tap()
                        onTransformStateChange(
                            transformState.copy(
                                resizeMode = mode
                            )
                        )
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.primary
                    ),
                ) { Text(if (mode == ResizeMode.Scale) "Scale" else "Canvas") }
            }
            UndoIconButton(
                enabled = canUndo,
                onUndo = onUndoResize,
                onSetUndoLevels = onSetUndoLevels,
                currentLevels = currentUndoLevels,
            )
            RedoIconButton(
                enabled = canRedo,
                onRedo = onRedoResize,
            )
            if (transformState.resizeMode == ResizeMode.Canvas) {
                val fillColor = transformState.canvasFillColor
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                HapticPatterns.tap()
                                showColorEditor.value = true
                            })
                        },
                    shape = CircleShape,
                    color = fillColor,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    if (fillColor.alpha == 0f) Checkerboard()
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
        ) {
            OutlinedTextField(
                value = transformState.widthInput, onValueChange = { newVal ->
                    val filtered =
                        newVal.filter { c -> c.isDigit() || c in "$MATH_SYMBOLS " }.take(20)
                    val hasMathSymbols = filtered.any { it in MATH_SYMBOLS }
                    if (transformState.lockAspect && transformState.resizeMode == ResizeMode.Scale && !hasMathSymbols) {
                        val bmp = imageBitmap ?: return@OutlinedTextField
                        val w = evaluateMath(filtered)
                        if (w != null) {
                            val h = (w * bmp.height / bmp.width.toFloat()).roundToInt()
                            onTransformStateChange(
                                transformState.copy(
                                    widthInput = filtered, heightInput = h.toString()
                                )
                            )
                        } else {
                            onTransformStateChange(transformState.copy(widthInput = filtered))
                        }
                    } else {
                        onTransformStateChange(transformState.copy(widthInput = filtered))
                    }
                }, label = { Text("W") }, singleLine = true, keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ), keyboardActions = KeyboardActions(
                    onDone = {
                        evaluateMath(transformState.widthInput)?.let { evaluated ->
                            val str = evaluated.toString()
                            val hasMathSymbols =
                                transformState.widthInput.any { it in MATH_SYMBOLS }
                            if (transformState.lockAspect && hasMathSymbols.not()) {
                                val bmp = imageBitmap ?: return@KeyboardActions
                                val h = (evaluated * bmp.height / bmp.width.toFloat()).roundToInt()
                                onTransformStateChange(
                                    transformState.copy(
                                        widthInput = str, heightInput = h.toString()
                                    )
                                )
                            } else {
                                onTransformStateChange(transformState.copy(widthInput = str))
                            }
                        }
                    }), modifier = Modifier.width(100.dp)
            )
            IconButton(
                onClick = {
                    onTransformStateChange(transformState.copy(lockAspect = !transformState.lockAspect))
                    HapticPatterns.tap()
                }) {
                Icon(
                    if (transformState.lockAspect) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Toggle aspect lock"
                )
            }
            OutlinedTextField(
                value = transformState.heightInput, onValueChange = { newVal ->
                    val filtered =
                        newVal.filter { c -> c.isDigit() || c in "$MATH_SYMBOLS " }.take(20)
                    val hasMathSymbols = filtered.any { it in MATH_SYMBOLS }
                    if (transformState.lockAspect && !hasMathSymbols) {
                        val bmp = imageBitmap ?: return@OutlinedTextField
                        val h = evaluateMath(filtered)
                        if (h != null) {
                            val w = (h * bmp.width / bmp.height.toFloat()).roundToInt()
                            onTransformStateChange(
                                transformState.copy(
                                    heightInput = filtered, widthInput = w.toString()
                                )
                            )
                        } else {
                            onTransformStateChange(transformState.copy(heightInput = filtered))
                        }
                    } else {
                        onTransformStateChange(transformState.copy(heightInput = filtered))
                    }
                }, singleLine = true, label = { Text("H") }, keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ), keyboardActions = KeyboardActions(
                    onDone = {
                        evaluateMath(transformState.heightInput)?.let { evaluated ->
                            val str = evaluated.toString()
                            val hasMathSymbols =
                                transformState.widthInput.any { it in MATH_SYMBOLS }
                            if (transformState.lockAspect && !hasMathSymbols) {
                                val bmp = imageBitmap ?: return@KeyboardActions
                                val w = (evaluated * bmp.width / bmp.height.toFloat()).roundToInt()
                                onTransformStateChange(
                                    transformState.copy(
                                        heightInput = str, widthInput = w.toString()
                                    )
                                )
                            } else {
                                onTransformStateChange(transformState.copy(heightInput = str))
                            }
                        }
                    }), modifier = Modifier.width(100.dp)
            )
            Button(
                onClick = {
                    HapticPatterns.tap()
                    val w = evaluateMath(transformState.widthInput) ?: return@Button
                    val h = evaluateMath(transformState.heightInput) ?: return@Button
                    onTransformStateChange(
                        transformState.copy(widthInput = w.toString(), heightInput = h.toString())
                    )
                    onApplyResize(w, h)
                }, shapes = ButtonShapes(
                    shape = RoundedCornerShape(21.dp), pressedShape = RoundedCornerShape(8.dp)
                ), contentPadding = ButtonDefaults.SmallContentPadding
            ) { Text("Apply") }
        }
    }
}

@Composable
private fun CropPanel(
    aspectRatio: Pair<Int, Int>?,
    onAspectRatio: (Pair<Int, Int>?) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onSetUndoLevels: (Int) -> Unit,
    currentUndoLevels: Int,
) {
    val toggleColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.primary
    )
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToggleButton(
            checked = aspectRatio == null,
            onCheckedChange = {
                HapticPatterns.tap()
                onAspectRatio(null)
            },
            colors = toggleColors,
        ) { Text("Free") }
        listOf(1 to 1, 4 to 3, 16 to 9, 3 to 2).forEach { ratio ->
            ToggleButton(
                checked = aspectRatio == ratio,
                onCheckedChange = {
                    HapticPatterns.tap()
                    onAspectRatio(ratio)
                },
                colors = toggleColors,
            ) { Text("${ratio.first}:${ratio.second}") }
        }
        UndoIconButton(
            enabled = canUndo,
            onUndo = onUndo,
            onSetUndoLevels = onSetUndoLevels,
            currentLevels = currentUndoLevels,
        )
        RedoIconButton(
            enabled = canRedo,
            onRedo = onRedo,
        )
    }
}

@Composable
fun RotationPanel(
    rotation: Float, onRotation: (Float) -> Unit
) {
    val displayAngle = normalizeAngle(rotation)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "%.1f°".format(displayAngle),
            style = MaterialTheme.typography.labelLarge,
        )
        RotationDial(
            angleDeg = displayAngle,
            onAngleChange = { newDisplay -> onRotation(newDisplay) },
            onAngleSettled = { newDisplay -> onRotation(newDisplay) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            listOf(0f, 90f, 180f, 270f).forEach { degrees ->
                val target = normalizeAngle(degrees)
                ToggleButton(
                    checked = displayAngle == target,
                    onCheckedChange = { HapticPatterns.tap(); onRotation(target) },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("${degrees.toInt()}°") }
            }
        }
    }
}

@Composable
fun RotationDial(
    angleDeg: Float,
    onAngleChange: (Float) -> Unit,
    onAngleSettled: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentAngle by rememberUpdatedState(angleDeg)
    val updatedOnAngleChange by rememberUpdatedState(onAngleChange)
    val updatedOnAngleSettled by rememberUpdatedState(onAngleSettled)
    val dialModifier = modifier
        .fillMaxWidth()
        .height(56.dp)
        .pointerInput(Unit) {
            var gestureAngle = 0f
            var lastTick = 0
            detectDragGestures(onDragStart = {
                gestureAngle = currentAngle
                lastTick = currentAngle.roundToInt()
            }, onDrag = { change, dragAmount ->
                change.consume()
                val pxPerDeg = size.width / 70f
                gestureAngle = normalizeAngle(gestureAngle - dragAmount.x / pxPerDeg)
                updatedOnAngleChange(gestureAngle)
                val tick = gestureAngle.roundToInt()
                if (tick != lastTick) {
                    lastTick = tick
                    if (tick % 10 == 0) HapticPatterns.tickMedium() else HapticPatterns.tap()
                }
            }, onDragEnd = {
                val snapped = normalizeAngle(gestureAngle.roundToInt().toFloat())
                if (snapped != gestureAngle) HapticPatterns.tap()
                updatedOnAngleSettled(snapped)
            })
        }
    Canvas(modifier = dialModifier) {
        drawDial(angleDeg)
    }
}

private fun normalizeAngle(a: Float): Float {
    var r = a % 360f
    if (r > 180f) r -= 360f
    if (r < -180f) r += 360f
    return r
}

private fun DrawScope.drawDial(angleDeg: Float) {
    val pxPerDeg = size.width / 70f
    val centerY = size.height / 2f
    val centerX = size.width / 2f
    val primaryColor = Color.White
    val tickColor = Color(0xFF888888.toInt())

    for (deg in -180..180) {
        val offsetX = (deg - angleDeg) * pxPerDeg + centerX
        if (offsetX < 0f || offsetX > size.width) continue
        val isMajor = deg % 90 == 0
        val isMinor = deg % 10 == 0
        val tickH = when {
            isMajor -> size.height * 0.55f
            isMinor -> size.height * 0.35f
            else -> size.height * 0.18f
        }
        drawLine(
            color = if (isMajor) primaryColor else tickColor,
            start = Offset(offsetX, centerY - tickH / 2f),
            end = Offset(offsetX, centerY + tickH / 2f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    drawLine(
        color = primaryColor,
        start = Offset(centerX, 0f),
        end = Offset(centerX, size.height),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
