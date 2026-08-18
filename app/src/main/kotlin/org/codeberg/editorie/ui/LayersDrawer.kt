package org.codeberg.editorie.ui

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.codeberg.editorie.App
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.EditorState
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.data.LayerGroupInfo
import org.codeberg.editorie.options.save.ImageRepository
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val LayersEdgeZone = 48.dp

@Composable
fun EditorLayersDrawer(
    state: EditorState,
    onRenameLayerGroup: (Long, String) -> Unit,
    onMoveLayerGroup: (Long, Int) -> Unit,
    onDeleteLayerGroup: (Long) -> Unit,
    onModeChange: (EditorPanel, Long?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val drawerWidth = (maxWidth * 0.55f).coerceAtMost(400.dp)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val offset = remember(drawerWidthPx) { Animatable(-drawerWidthPx) }
        val fraction = (1f + offset.value / drawerWidthPx).coerceIn(0f, 1f)
        fun closeDrawer() {
            scope.launch { offset.animateTo(-drawerWidthPx, tween(220)) }
        }

        val closeAction by rememberUpdatedState(::closeDrawer)
        BackHandler(enabled = fraction > 0.02f) {
            closeDrawer()
        }
        Box(
            Modifier
                .fillMaxSize()
                .layersDrawerGesture(
                    offset = offset,
                    widthPx = drawerWidthPx,
                    edgeX = with(density) { LayersEdgeZone.toPx() },
                    scope = scope,
                    isOpenGetter = { offset.value > -drawerWidthPx * 0.02f },
                ),
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = fraction * 0.3f))
                    .then(
                        if (fraction > 0.02f) {
                            Modifier.pointerInput(Unit) { detectTapGestures { closeAction() } }
                        } else Modifier),
            )
            ModalDrawerSheet(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
                    .offset { IntOffset(offset.value.roundToInt(), 0) },
            ) {
                LayersDrawerContent(
                    state,
                    onRenameLayerGroup,
                    onMoveLayerGroup,
                    onDeleteLayerGroup,
                    onModeChange,
                    closeAction,
                )
            }
        }
    }
}

private fun Modifier.layersDrawerGesture(
    offset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    widthPx: Float,
    edgeX: Float,
    scope: CoroutineScope,
    isOpenGetter: () -> Boolean,
): Modifier = pointerInput(offset, widthPx, edgeX) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!isOpenGetter() && down.position.x >= edgeX) return@awaitEachGesture
        if (down.isConsumed && !isOpenGetter()) return@awaitEachGesture
        val id = down.id
        val startX = down.position.x
        val startY = down.position.y
        val holdJob = if (isOpenGetter()) null else scope.launch {
            delay(400.milliseconds)
            HapticPatterns.tap()
            offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
        val velocity = VelocityTracker()
        var engaged = false
        var lastX = startX
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id } ?: break
            if (!change.pressed) break
            velocity.addPosition(change.uptimeMillis, change.position)
            if (!engaged) {
                if (change.isConsumed) {
                    holdJob?.cancel()
                    break
                }
                val dx = change.position.x - startX
                val dy = change.position.y - startY
                if (abs(dx) > slop) {
                    holdJob?.cancel()
                    engaged = true
                } else if (abs(dy) > slop) {
                    holdJob?.cancel()
                    break
                } else {
                    continue
                }
            }
            change.consume()
            val deltaX = change.position.x - lastX
            lastX = change.position.x
            scope.launch { offset.snapTo((offset.value + deltaX).coerceIn(-widthPx, 0f)) }
        }
        holdJob?.cancel()
        if (engaged) {
            val flung = velocity.calculateVelocity().x
            val target = if (offset.value > -widthPx * 0.5f || flung > 300f) {
                0f
            } else -widthPx
            scope.launch {
                offset.animateTo(
                    target,
                    if (target == 0f) spring(stiffness = Spring.StiffnessMediumLow) else tween(220),
                )
            }
        }
    }
}

@Composable
private fun LayersDrawerContent(
    state: EditorState,
    onRenameLayerGroup: (Long, String) -> Unit,
    onMoveLayerGroup: (Long, Int) -> Unit,
    onDeleteLayerGroup: (Long) -> Unit,
    onModeChange: (EditorPanel, Long?) -> Unit,
    closeDrawer: () -> Unit,
) {
    val bitmap = state.workingBitmap
    val areLayers = (bitmap != null && !state.layerGroups.isEmpty())
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (areLayers) "Layers" else "Layers (none)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )

    }
    if (areLayers) {
        LazyColumn {
            items(state.layerGroups.reversed(), key = { it.groupId }) { group ->
                LayerGroupRow(
                    group = group,
                    sourceBitmap = bitmap,
                    viewCrop = state.viewCrop,
                    onRenameLayerGroup = onRenameLayerGroup,
                    onMoveLayerGroup = onMoveLayerGroup,
                    onDeleteLayerGroup = onDeleteLayerGroup,
                    onModeChange = onModeChange,
                    closeDrawer = closeDrawer,
                )
            }
        }
    }
}

@Composable
private fun LayerGroupRow(
    group: LayerGroupInfo,
    sourceBitmap: Bitmap,
    viewCrop: Rect?,
    onRenameLayerGroup: (Long, String) -> Unit,
    onMoveLayerGroup: (Long, Int) -> Unit,
    onDeleteLayerGroup: (Long) -> Unit,
    onModeChange: (EditorPanel, Long?) -> Unit,
    closeDrawer: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val showRenameDialog = remember { mutableStateOf(false) }
    val isBackground = remember(group.layers) {
        group.layers.any { it is EditorLayer.Background }
    }

    if (showRenameDialog.value) {
        RenameLayerDialog(
            initialName = group.displayName,
            onDismiss = { showRenameDialog.value = false },
            onConfirm = { name ->
                onRenameLayerGroup(group.groupId, name)
                showRenameDialog.value = false
            },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        LayerRectanglePreview(
            group = group,
            sourceBitmap = sourceBitmap,
            viewCrop = viewCrop,
            onModeChange = onModeChange,
            closeDrawer = closeDrawer,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(6.dp))
            Text(
                group.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { HapticPatterns.tap(); menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Layer actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { HapticPatterns.tap(); menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        HapticPatterns.tap(); menuExpanded = false; showRenameDialog.value = true
                    }, modifier = Modifier, leadingIcon = {
                        Icon(
                            Icons.Filled.DriveFileRenameOutline,
                            contentDescription = "Layer actions"
                        )
                    })
                    if (!isBackground) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            HapticPatterns.tap(); menuExpanded = false
                            onDeleteLayerGroup(group.groupId)
                        }, leadingIcon = {
                            Icon(
                                Icons.Filled.Delete, contentDescription = "Layer actions"
                            )
                        })
                    }
                    DropdownMenuItem(text = { Text("Move up") }, onClick = {
                        HapticPatterns.tap(); menuExpanded = false
                        onMoveLayerGroup(group.groupId, +1)
                    }, leadingIcon = {
                        Icon(
                            Icons.Filled.KeyboardArrowUp, contentDescription = "Layer actions"
                        )
                    })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = {
                        HapticPatterns.tap(); menuExpanded = false
                        onMoveLayerGroup(group.groupId, -1)
                    }, leadingIcon = {
                        Icon(
                            Icons.Filled.KeyboardArrowDown, contentDescription = "Layer actions"
                        )
                    })
                }
            }
        }
    }
}

@Composable
fun LayerRectanglePreview(
    modifier: Modifier = Modifier,
    group: LayerGroupInfo,
    sourceBitmap: Bitmap,
    viewCrop: Rect?,
    onModeChange: (EditorPanel, Long?) -> Unit = { _, _ -> },
    closeDrawer: () -> Unit = {},
) {
    val density = LocalDensity.current
    val cropRect = remember(viewCrop, sourceBitmap) {
        viewCrop ?: Rect(0f, 0f, sourceBitmap.width.toFloat(), sourceBitmap.height.toFloat())
    }
    val preview by produceState<Bitmap?>(
        initialValue = null, group.layers, cropRect, sourceBitmap, density
    ) {
        value = withContext(Dispatchers.Default) {
            ImageRepository(App.ctx).renderLayerGroupPreview(
                sourceBitmap, cropRect, group.layers,
                scaledDensity = density.density * density.fontScale,
            )
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                HapticPatterns.tap()
                onModeChange(group.toolMode, group.groupId)
                closeDrawer()
            },
    ) {
        Checkerboard(0.5f)
        preview?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = group.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun RenameLayerDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename layer") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            Button(
                onClick = { HapticPatterns.tap(); onConfirm(text) }, shapes = ButtonShapes(
                    shape = RoundedCornerShape(21.dp), pressedShape = RoundedCornerShape(8.dp)
                ), contentPadding = ButtonDefaults.SmallContentPadding
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(
                onClick = { HapticPatterns.tap(); onDismiss() }) { Text("Cancel") }
        },
    )
}
