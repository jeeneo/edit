package org.codeberg.editorie.options.stickers

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.codeberg.editorie.App
import org.codeberg.editorie.R
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.ui.ColorPickerBar
import org.codeberg.editorie.ui.bottombar.LayerMoveButtonGroup
import org.codeberg.editorie.ui.bottombar.RedoIconButton
import org.codeberg.editorie.ui.bottombar.UndoIconButton

@Composable
fun StickerOptions(
    state: StickerPanelState,
    onChange: (StickerPanelState) -> Unit,
    onCommit: () -> Unit,
    onInsert: (StickerAsset) -> Unit,
    onInsertCustom: () -> Unit,
    onDelete: (Long) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveLayer: (Int) -> Unit,
    palette: List<Color>,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
    onSetUndoLevels: (Int) -> Unit,
    currentUndoLevels: Int,
) {
    AnimatedContent(
        targetState = state.editTarget != null,
        transitionSpec = {
            slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
        },
        label = "sticker_panel",
    ) { isEditing ->
        val editTarget = state.editTarget
        if (isEditing && editTarget != null) {
            StickerEditPanel(
                overlay = editTarget,
                onOverlayChange = { updated ->
                    onChange(state.copy(editTarget = updated))
                },
                onOverlayCommit = onCommit,
                onDelete = {
                    onDelete(editTarget.id)
                    onChange(state.copy(selectedId = null, editTarget = null))
                },
                onUndo = onUndo,
                onRedo = onRedo,
                onMoveLayer = onMoveLayer,
                canUndo = canUndo,
                canRedo = canRedo,
                palette = palette,
                onAddColor = onAddColor,
                onRemoveColor = onRemoveColor,
                onSetUndoLevels = onSetUndoLevels,
                currentUndoLevels = currentUndoLevels,
            )
        } else {
            StickerPickerPanel(
                catalog = state.catalog,
                activeCategory = state.activeCategory,
                onCategoryChange = { cat ->
                    onChange(state.copy(activeCategory = cat))
                },
                onInsert = onInsert,
                onInsertCustom = onInsertCustom
            )
        }
    }
}

@Composable
private fun StickerPickerPanel(
    catalog: List<StickerAsset>,
    activeCategory: String?,
    onCategoryChange: (String?) -> Unit,
    onInsert: (StickerAsset) -> Unit,
    onInsertCustom: () -> Unit,
) {
    val categories = remember(catalog) { catalog.map { it.category }.distinct().sorted() }
    val visible = remember(catalog, activeCategory) {
        if (activeCategory == null) catalog else catalog.filter { it.category == activeCategory }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (categories.size > 1) {
            val toggleColors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.primary
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToggleButton(
                    checked = activeCategory == null,
                    onCheckedChange = { HapticPatterns.tap(); onCategoryChange(null) },
                    colors = toggleColors,
                ) { Text("All") }
                categories.forEach { cat ->
                    ToggleButton(
                        checked = activeCategory == cat,
                        onCheckedChange = { HapticPatterns.tap(); onCategoryChange(cat) },
                        colors = toggleColors,
                    ) {
                        Text(
                            cat.replaceFirstChar { it.uppercaseChar() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No stickers found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.displayName + it.category }) { asset ->
                    StickerThumbnailCell(
                        asset = asset,
                        label = asset.displayName,
                        onClick = { HapticPatterns.tap(); onInsert(asset) },
                    )
                }
                item(key = "custom_sticker") {
                    StickerThumbnailCell(
                        asset = null,
                        label = App.ctx.getString(R.string.stickers_custom_sticker),
                        onClick = { HapticPatterns.tap(); onInsertCustom() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerThumbnailCell(
    asset: StickerAsset?,
    label: String,
    onClick: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(null, asset?.source) {
        value = asset?.let {
            withContext(Dispatchers.IO) { StickerRenderer.renderThumbnail(it.source) }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            //noinspection MissingHapticFeedback
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                asset == null -> {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = label,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                thumbnail != null -> {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.size(44.dp),
                    )
                }

                else -> {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp),
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label.replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StickerEditPanel(
    overlay: StickerOverlay,
    onOverlayChange: (StickerOverlay) -> Unit,
    onOverlayCommit: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveLayer: (Int) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    palette: List<Color>,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
    onSetUndoLevels: (Int) -> Unit,
    currentUndoLevels: Int,
) {
    val itemHeight = 52.dp
    val previewBitmap by produceState<Bitmap?>(
        initialValue = null,
        overlay.source,
        overlay.renderMode,
    ) {
        value = withContext(Dispatchers.IO) {
            StickerRenderer.render(
                source = overlay.source,
                widthPx = 128,
                heightPx = 128,
                renderMode = overlay.renderMode,
            )
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                previewBitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
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
            LayerMoveButtonGroup(
                moveUp = { onMoveLayer(+1) },
                moveTopmost = { onMoveLayer(Int.MAX_VALUE) },
                moveDown = { onMoveLayer(-1) },
                moveBottommost = { onMoveLayer(Int.MIN_VALUE) },
            )
            Surface(
                onClick = { HapticPatterns.tap(); onDelete() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(itemHeight)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete sticker",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable {
                HapticPatterns.tap()
                val updated = overlay.copy(
                    renderMode = overlay.renderMode.copy(
                        preserveAspectRatio = !overlay.renderMode.preserveAspectRatio
                    )
                )
                onOverlayChange(updated)
                onOverlayCommit()
            }) {
            Checkbox(
                checked = !overlay.renderMode.preserveAspectRatio,
                onCheckedChange = null,
            )
            Text("Stretch", style = MaterialTheme.typography.labelMedium)
        }

        RenderModeControls(
            overlay = overlay,
            onOverlayChange = onOverlayChange,
            onOverlayCommit = onOverlayCommit,
            palette = palette,
            onAddColor = onAddColor,
            onRemoveColor = onRemoveColor,
        )
    }
}

@Composable
private fun RenderModeControls(
    overlay: StickerOverlay,
    onOverlayChange: (StickerOverlay) -> Unit,
    onOverlayCommit: () -> Unit,
    palette: List<Color>,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
) {
    val rm = overlay.renderMode

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Fill",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(min = 48.dp)
            )
            ColorPickerBar(
                activeColor = rm.fillColor,
                palette = palette,
                onColorSelected = { onOverlayChange(overlay.copy(renderMode = rm.copy(fillColor = it))); onOverlayCommit() },
                onNullSelected = { onOverlayChange(overlay.copy(renderMode = rm.copy(fillColor = null))); onOverlayCommit() },
                onResetColor = { onOverlayChange(overlay.copy(renderMode = rm.copy(fillColor = Color.Unspecified))); onOverlayCommit() },
                onAddColor = onAddColor,
                onRemoveColor = onRemoveColor,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Outline",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(min = 48.dp)
            )
            ColorPickerBar(
                activeColor = rm.outlineColor,
                palette = palette,
                onColorSelected = { onOverlayChange(overlay.copy(renderMode = rm.copy(outlineColor = it))); onOverlayCommit() },
                onNullSelected = { onOverlayChange(overlay.copy(renderMode = rm.copy(outlineColor = null))); onOverlayCommit() },
                onAddColor = onAddColor,
                onRemoveColor = onRemoveColor,
            )
        }
        if (rm.outlineColor != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Size",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(min = 48.dp)
                )
                Text(
                    "${rm.outlineThicknessPx.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.widthIn(min = 24.dp)
                )
                //noinspection MissingHapticFeedback
                Slider(
                    value = rm.outlineThicknessPx,
                    onValueChange = {
                        onOverlayChange(overlay.copy(renderMode = rm.copy(outlineThicknessPx = it)))
                    },
                    onValueChangeFinished = onOverlayCommit,
                    valueRange = 1f..40f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
