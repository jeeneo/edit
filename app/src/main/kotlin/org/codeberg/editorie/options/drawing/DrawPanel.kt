package org.codeberg.editorie.options.drawing

// SPDX-License-Identifier: MIT

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.codeberg.editorie.R
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.ui.ColorPickerBar
import org.codeberg.editorie.ui.bottombar.RedoIconButton
import org.codeberg.editorie.ui.bottombar.UndoIconButton

@Composable
fun DrawOptions(
    drawState: DrawState,
    onDrawStateChange: (DrawState) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearStrokes: () -> Unit,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
    onSetUndoLevels: (Int) -> Unit,
    currentUndoLevels: Int,
    hasStrokes: Boolean,
    onSizePreviewChange: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val alpha by animateFloatAsState(
            targetValue = if (!hasStrokes) 0.3f else 1f,
            animationSpec = tween(durationMillis = 220),
            label = "selectedControlsAlpha",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            //noinspection MissingHapticFeedback
            OptionIconButton(
                selected = drawState.tool is DrawTool.Brush,
                onClick = { onDrawStateChange(drawState.copy(tool = DrawTool.Brush)) }) {
                Icon(
                    if (drawState.tool == DrawTool.Brush) Icons.Filled.Brush else Icons.Outlined.Brush,
                    "Brush"
                )
            }
            //noinspection MissingHapticFeedback
            OptionIconButton(
                selected = drawState.tool is DrawTool.Eraser,
                onClick = { onDrawStateChange(drawState.copy(tool = DrawTool.Eraser)) }) {
                Icon(
                    painter = if (drawState.tool == DrawTool.Eraser) painterResource(id = R.drawable.ic_erase_filled) else painterResource(
                        id = R.drawable.ic_erase_outline
                    ), contentDescription = "Eraser"
                )
            }
            //noinspection MissingHapticFeedback
            OptionIconButton(
                selected = drawState.tool is DrawTool.Eyedropper,
                onClick = { onDrawStateChange(drawState.copy(tool = DrawTool.Eyedropper)) }) {
                Icon(
                    if (drawState.tool == DrawTool.Eyedropper) Icons.Filled.Colorize else Icons.Outlined.Colorize,
                    "Eyedropper"
                )
            }
            Spacer(Modifier.weight(1f))
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
            Surface(
                onClick = { HapticPatterns.tap(); onClearStrokes() },
                enabled = hasStrokes,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .height(52.dp)
                    .alpha(alpha),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete text",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AnimatedContent(
            targetState = drawState.tool::class,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tool_settings"
        ) { toolClass ->
            when (toolClass) {
                DrawTool.Brush::class -> {
                    Spacer(Modifier.height(0.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sliderInteractionSource = remember { MutableInteractionSource() }
            val isDraggingSlider by sliderInteractionSource.collectIsDraggedAsState()
            LaunchedEffect(isDraggingSlider) { onSizePreviewChange(isDraggingSlider) }

            Text(
                "${drawState.penWidth.toInt()}px",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.widthIn(min = 48.dp)
            )
            //noinspection MissingHapticFeedback
            Slider(
                value = drawState.penWidth,
                onValueChange = { onDrawStateChange(drawState.copy(penWidth = it)) },
                valueRange = 2f..140f,
                interactionSource = sliderInteractionSource,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPickerBar(
                activeColor = drawState.penColor,
                palette = drawState.palette,
                onColorSelected = { onDrawStateChange(drawState.copy(penColor = it)) },
                onAddColor = onAddColor,
                onRemoveColor = onRemoveColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun OptionIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = { HapticPatterns.tap(); onClick() },
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.size(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon() }
    }
}
