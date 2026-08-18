@file:Suppress("SpellCheckingInspection")

package org.codeberg.editorie.ui.bottombar

// SPDX-License-Identifier: MIT

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.util.AppToasts

@SuppressLint("MissingHapticFeedback")
@Composable
fun RoundedActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    // todo: fix the color misalignment in light mode for this and the matching ToggleButtons
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val buttonSizeDp = 72.dp
    val circleRadius = buttonSizeDp / 2
    val pressedRadius = 16.dp
    val cornerRadius = remember { Animatable(circleRadius, Dp.VectorConverter) }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            cornerRadius.animateTo(
                pressedRadius,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            )
        } else {
            cornerRadius.animateTo(
                circleRadius,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh,
                ),
            )
        }
    }
    val scope = rememberCoroutineScope()
    fun pulseShape() {
        scope.launch {
            cornerRadius.animateTo(
                pressedRadius,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            cornerRadius.animateTo(
                circleRadius,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(cornerRadius.value),
            modifier = Modifier
                .size(buttonSizeDp)
                .clip(RoundedCornerShape(cornerRadius.value))
                .combinedClickable(
                    interactionSource = interactionSource,
                    onClick = {
                        HapticPatterns.tap()
                        onClick()
                        pulseShape()
                    },
                    onLongClick = onLongClick,
                ),
            color = if (active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (active) primary else onSurfaceVariant,
            tonalElevation = if (active) 3.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, contentDescription = label, modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) primary else onSurfaceVariant,
        )
    }
}

@Composable
fun UndoIconButton(
    enabled: Boolean,
    onUndo: () -> Unit,
    onSetUndoLevels: (Int) -> Unit,
    currentLevels: Int,
) {
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        UndoLevelsDialog(
            current = currentLevels,
            onDismiss = { showDialog.value = false },
            onConfirm = { levels ->
                onSetUndoLevels(levels)
                showDialog.value = false
            },
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = {
                        if (enabled) {
                            onUndo()
                            HapticPatterns.tap()
                        }
                    },
                    onLongPress = {
                        HapticPatterns.longPress()
                        showDialog.value = true
                    },
                )
            },
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Undo,
            contentDescription = "Undo",
            tint = if (enabled) LocalContentColor.current
            else LocalContentColor.current.copy(alpha = 0.4f),
        )
    }
}

@Composable
fun RedoIconButton(
    enabled: Boolean,
    onRedo: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = {
                        if (enabled) {
                            onRedo()
                            HapticPatterns.tap()
                        }
                    },
                )
            },
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Redo,
            contentDescription = "Redo",
            tint = if (enabled) LocalContentColor.current
            else LocalContentColor.current.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun UndoLevelsDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()?.coerceIn(1, 100)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Undo history") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(3) },
                label = { Text("Max undo steps (1–100)") },
                isError = parsed == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onConfirm(it); HapticPatterns.tap() } },
                shapes = ButtonShapes(
                    shape = RoundedCornerShape(21.dp), pressedShape = RoundedCornerShape(8.dp)
                ),
                contentPadding = ButtonDefaults.SmallContentPadding,
                enabled = parsed != null,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(
                onClick = { HapticPatterns.tap(); onDismiss() }) { Text("Cancel") }
        },
    )
}

@SuppressLint("MissingHapticFeedback")
@Composable
fun LayerMoveButtonGroup(
    moveUp: () -> Unit,
    moveTopmost: () -> Unit,
    moveDown: () -> Unit,
    moveBottommost: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alpha: Float = 1f,
    buttonWidth: Dp = 52.dp,
    buttonHeight: Dp = 25.dp,
    spacerSize: Dp = 2.dp,
    outerRadius: Dp = 12.dp,
    innerRadius: Dp = 4.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier = modifier) {
        LayerMoveButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Move layer up",
            enabled = enabled,
            alpha = alpha,
            width = buttonWidth,
            height = buttonHeight,
            shape = RoundedCornerShape(
                topStart = outerRadius, topEnd = outerRadius,
                bottomStart = innerRadius, bottomEnd = innerRadius,
            ),
            containerColor = containerColor,
            contentColor = contentColor,
            onClick = { AppToasts.show("Moved up"); moveUp() },
            onLongPress = { AppToasts.show("Moved to top"); moveTopmost() },
        )
        Spacer(Modifier.height(spacerSize))
        LayerMoveButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Move layer down",
            enabled = enabled,
            alpha = alpha,
            width = buttonWidth,
            height = buttonHeight,
            shape = RoundedCornerShape(
                topStart = innerRadius, topEnd = innerRadius,
                bottomStart = outerRadius, bottomEnd = outerRadius,
            ),
            containerColor = containerColor,
            contentColor = contentColor,
            onClick = { AppToasts.show("Moved down"); moveDown() },
            onLongPress = { AppToasts.show("Moved to bottom"); moveBottommost() },
        )
    }
}

@Composable
private fun LayerMoveButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    alpha: Float,
    width: Dp,
    height: Dp,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .alpha(alpha)
            .clip(shape)
            .background(containerColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onClick(); HapticPatterns.tap() },
                onLongClick = { onLongPress(); HapticPatterns.longPress() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = contentColor)
    }
}
