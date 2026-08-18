@file:Suppress("SpellCheckingInspection")

package org.codeberg.editorie.ui.canvas

// SPDX-License-Identifier: MIT

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.codeberg.editorie.R
import org.codeberg.editorie.data.AppTheme
import org.codeberg.editorie.data.DropperPreviewData
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.EditorSubpanel
import org.codeberg.editorie.data.ImageCanvasState
import org.codeberg.editorie.data.LocalAppTheme
import org.codeberg.editorie.options.adjust.AdjustOps.toMatrixValues
import org.codeberg.editorie.options.drawing.DrawTool
import org.codeberg.editorie.options.text.TextOverlay
import org.codeberg.editorie.ui.canvas.gestures.cropGestures
import org.codeberg.editorie.ui.canvas.gestures.defaultZoomPanGestures
import org.codeberg.editorie.ui.canvas.gestures.drawToolGestures
import org.codeberg.editorie.ui.canvas.gestures.rememberCropGestureState
import org.codeberg.editorie.ui.canvas.gestures.rememberPendingPointsState
import org.codeberg.editorie.ui.canvas.gestures.saveFieldGestures
import org.codeberg.editorie.ui.canvas.gestures.zoomPanGestures
import org.codeberg.editorie.ui.canvas.overlays.stickerOverlayGestures
import org.codeberg.editorie.ui.canvas.overlays.textOverlayGestures
import org.codeberg.editorie.ui.canvas.render.CanvasRenderContext
import org.codeberg.editorie.ui.canvas.render.drawAllLayers
import org.codeberg.editorie.ui.canvas.render.drawCropOverlay
import org.codeberg.editorie.ui.canvas.render.drawDropperOverlay
import org.codeberg.editorie.ui.canvas.render.drawMCUOverlay
import org.codeberg.editorie.ui.canvas.render.drawPenSizePreview
import org.codeberg.editorie.ui.canvas.render.drawTransparencyMap
import org.codeberg.editorie.ui.canvas.render.rememberCheckerPaint
import org.codeberg.editorie.ui.canvas.render.rememberDrawBitmap

@Composable
fun ImageCanvas(
    modifier: Modifier,
    state: ImageCanvasState,
    onStrokeEnd: (List<Offset>, Float) -> Unit,
    onEyedropper: (Offset) -> Unit,
    onCropChanged: (selection: Rect?, bounds: Rect) -> Unit,
    onTextSelect: (Long?) -> Unit,
    onDismissKeyboard: () -> Unit,
    onTextEdit: (TextOverlay) -> Unit,
    onTextMove: (Long, Offset) -> Unit,
    onTextBoxChange: (Long, Offset, Float, Float?, Float) -> Unit,
    onStickerSelect: (Long?) -> Unit,
    onStickerBoxChange: (Long, Offset, Float, Float, Float) -> Unit,
    onEyedropperPreview: (Offset) -> Unit = {},
    showPenSizePreview: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
) {
    val display = state.display
    val overlay = state.overlay
    val handlePx = with(LocalDensity.current) { 12.dp.toPx() }
    val colorScheme = MaterialTheme.colorScheme
    val transformColors = remember(colorScheme) {
        TransformBoxColors(
            handleColor = Color(0xFFDEDEDE).toArgb(),
            gridLines = Color(0xFF303030).toArgb(),
            scrim = colorScheme.scrim.copy(alpha = 0.6f).toArgb(),
        )
    }

    val displayBitmap = remember(display.bitmap) { display.bitmap }
    val fullRect = Rect(0f, 0f, displayBitmap.width.toFloat(), displayBitmap.height.toFloat())
    val sourceRect =
        if (display.panel == EditorPanel.Transform && display.subpanel == EditorSubpanel.Transform.Crop) fullRect else (display.viewCrop
            ?: fullRect)
    val viewport = rememberCanvasViewport(fullRect, sourceRect)
    val cropState = rememberCropGestureState(display.viewCrop)
    LaunchedEffect(display.viewCrop) { cropState.selection = display.viewCrop }
    val cropRectDisplay by remember(cropState.selection, viewport.sourceRect) {
        derivedStateOf { cropState.selection?.let { viewport.pixelRectToDisplay(it) } }
    }
    val pendingPoints = rememberPendingPointsState()
    val (drawBitmap, drawScale) = rememberDrawBitmap(
        displayBitmap, display.rotationDeg, display.flipHorizontal, display.flipVertical
    )
    val drawImageBitmap = remember(drawBitmap) { drawBitmap.asImageBitmap() }
    val colorFilter =
        remember(display.adjust) { ColorFilter.colorMatrix(ColorMatrix(display.adjust.toMatrixValues())) }
    val systemDark = isSystemInDarkTheme()
    val isDark =
        LocalAppTheme.current == AppTheme.OLED || LocalAppTheme.current == AppTheme.Dark || (LocalAppTheme.current == AppTheme.Dynamic && systemDark)
    val checkerPaint = rememberCheckerPaint(isDark)

    val renderCtx = CanvasRenderContext(
        viewport = viewport,
        drawImageBitmap = drawImageBitmap,
        drawScale = drawScale,
        colorFilter = colorFilter,
        handlePx = handlePx,
        transformColors = transformColors,
        rotationDeg = display.rotationDeg,
        flipHorizontal = display.flipHorizontal,
        flipVertical = display.flipVertical,
        selectedTextId = overlay.selectedTextId,
        selectedStickerId = overlay.selectedStickerId,
    )

    val dropperPainter = painterResource(id = R.drawable.dropper)
    val loupeActive = display.panel == EditorPanel.Draw && overlay.drawTool is DrawTool.Eyedropper
    val loupeProgress = remember { Animatable(0f) }
    val loupePreview = remember { mutableStateOf<DropperPreviewData?>(null) }
    SideEffect { overlay.dropperPreview?.let { loupePreview.value = it } }
    LaunchedEffect(loupeActive, overlay.dropperPreview != null) {
        loupeProgress.animateTo(
            targetValue = if (loupeActive && overlay.dropperPreview != null) 1f else 0f,
            animationSpec = tween(durationMillis = 110),
        )
    }

    LaunchedEffect(
        displayBitmap, viewport.canvasSize, display.aspectRatio, display.panel, display.subpanel
    ) {
        if (viewport.canvasSize == IntSize.Zero) return@LaunchedEffect
        if (display.panel == EditorPanel.Transform && display.subpanel == EditorSubpanel.Transform.Crop) {
            if (display.viewCrop == null) {
                val initialPx = applyAspect(fullRect, display.aspectRatio)
                onCropChanged(clampPixel(initialPx, fullRect), viewport.displayRect)
            } else {
                val adjusted = display.aspectRatio?.let { ratio ->
                    val target = ratio.first / ratio.second.toFloat()
                    val cx = display.viewCrop.left + display.viewCrop.width / 2f
                    val cy = display.viewCrop.top + display.viewCrop.height / 2f
                    if (display.viewCrop.width / display.viewCrop.height > target) {
                        val nw = display.viewCrop.height * target; Rect(
                            cx - nw / 2f,
                            display.viewCrop.top,
                            cx + nw / 2f,
                            display.viewCrop.bottom
                        )
                    } else {
                        val nh = display.viewCrop.width / target; Rect(
                            display.viewCrop.left,
                            cy - nh / 2f,
                            display.viewCrop.right,
                            cy + nh / 2f
                        )
                    }
                } ?: display.viewCrop
                onCropChanged(clampPixel(adjusted, fullRect), viewport.displayRect)
            }
        }
    }

    val baseGestureModifier = modifier
        .onSizeChanged { viewport.canvasSize = it }
        .zoomPanGestures(viewport, onUndo, onRedo)

    val defaultGestureModifier = baseGestureModifier.defaultZoomPanGestures(viewport)
    val gestureModifier = when (display.panel) {
        EditorPanel.Transform if display.subpanel == EditorSubpanel.Transform.Crop -> baseGestureModifier.cropGestures(
            state = cropState,
            viewport = viewport,
            aspectRatio = display.aspectRatio,
            handlePx = handlePx,
            onCropChanged = onCropChanged,
        )

        EditorPanel.Text -> baseGestureModifier.textOverlayGestures(
            viewport = viewport,
            density = LocalDensity.current.density,
            handlePx = handlePx,
            textOverlays = overlay.textOverlays,
            selectedTextId = overlay.selectedTextId,
            onTextSelect = onTextSelect,
            onTextEdit = onTextEdit,
            onTextMove = onTextMove,
            onTextBoxChange = onTextBoxChange,
        )

        EditorPanel.Draw -> baseGestureModifier.drawToolGestures(
            viewport = viewport,
            pendingPoints = pendingPoints,
            drawTool = overlay.drawTool,
            penWidth = overlay.penWidth,
            onStrokeEnd = onStrokeEnd,
            onEyedropper = onEyedropper,
            onEyedropperPreview = onEyedropperPreview,
        )

        EditorPanel.Stickers -> baseGestureModifier.stickerOverlayGestures(
            viewport = viewport,
            handlePx = handlePx,
            stickerOverlays = overlay.stickerOverlays,
            selectedStickerId = overlay.selectedStickerId,
            onStickerSelect = onStickerSelect,
            onStickerBoxChange = onStickerBoxChange,
        )

        EditorPanel.Save -> if (display.isFileNameFieldFocused) {
            baseGestureModifier.saveFieldGestures(viewport, onDismissKeyboard)
        } else {
            defaultGestureModifier
        }

        else -> defaultGestureModifier
    }

    val cropMode =
        display.panel == EditorPanel.Transform && display.subpanel == EditorSubpanel.Transform.Crop

    Box(gestureModifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawTransparencyMap(viewport, checkerPaint)
            withTransform({
                translate(viewport.pan.x, viewport.pan.y); scale(
                viewport.zoom, viewport.zoom, Offset.Zero
            )
            }) {
                drawAllLayers(renderCtx, overlay, display.panel, pendingPoints.points)
                drawMCUOverlay(cropMode, display.showMcuGrid, display.mcuInfo, viewport)
                drawCropOverlay(cropMode, viewport, cropRectDisplay, transformColors, handlePx)
                drawPenSizePreview(
                    viewport = viewport,
                    panel = display.panel,
                    drawTool = overlay.drawTool,
                    penWidth = overlay.penWidth,
                    penColor = overlay.penColor,
                    pendingPoints = pendingPoints.points,
                    canvasSize = viewport.canvasSize,
                    showPenSizePreview = showPenSizePreview,
                )
            }
            if (loupeProgress.value > 0f) {
                loupePreview.value?.let { preview ->
                    drawDropperOverlay(
                        viewport = viewport,
                        preview = preview,
                        progress = loupeProgress.value,
                        dropperPainter = dropperPainter,
                        background = colorScheme.background,
                    ) {
                        drawTransparencyMap(viewport, checkerPaint)
                        drawAllLayers(renderCtx, overlay, display.panel, pendingPoints.points)
                    }
                }
            }
        }
    }
}
