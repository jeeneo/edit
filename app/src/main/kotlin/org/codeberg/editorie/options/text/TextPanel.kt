package org.codeberg.editorie.options.text

// SPDX-License-Identifier: MIT

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.ui.bottombar.LayerMoveButtonGroup

@Composable
fun TextOptions(
    state: TextEditorState,
    onChange: (TextEditorState) -> Unit,
    onCommit: () -> Unit,
    onDeleteText: () -> Unit,
    onAddText: () -> Unit,
    onMoveLayer: (Int) -> Unit,
    palette: List<Color>,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
) {
    val showEditDialog = remember { mutableStateOf(false) }
    val itemHeight = 52.dp
    val enabled = state.selectedId != null
    val alpha by animateFloatAsState(
        targetValue = if (!enabled) 0.3f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "selectedControlsAlpha",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = {
                if (enabled) {
                    HapticPatterns.tap(); showEditDialog.value = true
                } else {
                    onAddText(); HapticPatterns.tap(); showEditDialog.value = true
                }
            },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f)
                .height(itemHeight),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    if (enabled) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = "Remove text",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .alpha(0.6f)
                        .size(21.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (enabled && state.draft.isBlank()) {
                        "Tap to edit"
                    } else if (enabled) {
                        state.draft
                    } else {
                        "Tap to add"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.7f),
                )

            }
        }

        Surface(
            onClick = { onDeleteText(); HapticPatterns.tap() },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .height(itemHeight)
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
        LayerMoveButtonGroup(
            enabled = enabled,
            alpha = alpha,
            buttonWidth = itemHeight,
            buttonHeight = itemHeight / 2 - 1.dp,
            moveUp = { onMoveLayer(+1) },
            moveTopmost = { onMoveLayer(Int.MAX_VALUE) },
            moveDown = { onMoveLayer(-1) },
            moveBottommost = { onMoveLayer(Int.MIN_VALUE) },
        )
    }

    if (showEditDialog.value && state.selectedId != null) {
        TextEditDialog(
            state = state,
            onChange = onChange,
            onCommit = onCommit,
            onDeleteText = { onDeleteText(); showEditDialog.value = false },
            onDismiss = { showEditDialog.value = false },
            palette = palette,
            onAddColor = onAddColor,
            onRemoveColor = onRemoveColor,
        )
    }
}
