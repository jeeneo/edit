@file:Suppress("SpellCheckingInspection")

package org.codeberg.editorie.ui.editorscreen

// SPDX-License-Identifier: MIT

import android.content.Context
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.codeberg.editorie.App
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.EditorState
import org.codeberg.editorie.data.EditorSubpanel
import org.codeberg.editorie.data.LayerGroupMeta
import org.codeberg.editorie.data.LosslessOp
import org.codeberg.editorie.data.UndoEntry
import org.codeberg.editorie.data.UndoStack
import org.codeberg.editorie.options.adjust.AdjustState
import org.codeberg.editorie.options.drawing.DrawState
import org.codeberg.editorie.options.drawing.DrawTool
import org.codeberg.editorie.options.drawing.DrawingOps
import org.codeberg.editorie.options.save.DeletionMode
import org.codeberg.editorie.options.save.ExportFormat
import org.codeberg.editorie.options.save.ImageRepository
import org.codeberg.editorie.options.save.translateForCrop
import org.codeberg.editorie.options.save.withExtension
import org.codeberg.editorie.options.stickers.StickerAsset
import org.codeberg.editorie.options.stickers.StickerAssets
import org.codeberg.editorie.options.stickers.StickerOps
import org.codeberg.editorie.options.stickers.StickerPanelState
import org.codeberg.editorie.options.stickers.StickerSource
import org.codeberg.editorie.options.text.TextEditorState
import org.codeberg.editorie.options.text.TextOps
import org.codeberg.editorie.options.text.TextOverlay
import org.codeberg.editorie.options.transform.JPEGLosslessTransform
import org.codeberg.editorie.options.transform.ResizeMode
import org.codeberg.editorie.options.transform.TransformOps
import org.codeberg.editorie.options.transform.TransformState
import org.codeberg.editorie.util.AppToasts
import java.io.IOException
import kotlin.math.roundToInt

class EditorScreenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditorState(
            penColor = App.prefs.loadPenColor(),
            palette = App.prefs.loadPalette(),
            penWidth = App.prefs.loadPenWidth(),
            textEditorState = TextEditorState(color = App.prefs.loadTextColor()),
            stickerPanelState = StickerPanelState(
                catalog = StickerAssets.all,
                defaultFill = App.prefs.loadStickerFillColor(),
                defaultOutline = App.prefs.loadStickerOutlineColor(),
                defaultOutlineThickness = App.prefs.loadStickerOutlineThickness(),
            ),
            exportQuality = App.prefs.loadExportQuality(),
            exifDateOnly = App.prefs.loadExifDateOnly(),
            undoLevels = App.prefs.loadUndoLevels(),
        )
    )
    val uiState: StateFlow<EditorState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            StickerAssets.thumbailPreload()
        }
    }

    private class PendingEditTracker {
        private var snapshot: List<EditorLayer>? = null
        fun begin(current: List<EditorLayer>) {
            if (snapshot == null) snapshot = current
        }

        fun commit(): List<EditorLayer>? {
            val s = snapshot
            snapshot = null
            return s
        }

        fun clear() {
            snapshot = null
        }
    }

    private var undoStack = UndoStack(maxDepth = App.prefs.loadUndoLevels())
    private val textEdits = PendingEditTracker()
    private val stickerEdits = PendingEditTracker()
    private var currentLayerGroupId: Long? = null
    private val state get() = _uiState.value
    private fun update(block: EditorState.() -> EditorState) {
        _uiState.update { prev -> prev.block() }
    }

    private fun pushLayerSnapshot() {
        undoStack = undoStack.push(UndoEntry.LayersChange(state.layers))
        update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
    }

    private fun UndoEntry.snapshotAfter(current: EditorState): UndoEntry = when (this) {
        is UndoEntry.LayersChange -> UndoEntry.LayersChange(current.layers)
        is UndoEntry.BitmapChange -> UndoEntry.BitmapChange(
            bitmap = current.workingBitmap ?: bitmap,
            layers = current.layers,
            losslessChain = current.losslessChain,
        )
    }

    private fun currentGroupId(): Long {
        currentLayerGroupId?.let { return it }
        val id = System.nanoTime()
        currentLayerGroupId = id
        val ordinal = state.nextLayerOrdinal
        update {
            copy(
                layerGroupMeta = layerGroupMeta + (id to LayerGroupMeta(ordinal)),
                nextLayerOrdinal = ordinal + 1,
            )
        }
        return id
    }

    fun commitLayerGroupSession() {
        currentLayerGroupId = null
    }

    private fun beginTextEditIfNeeded() {
        textEdits.begin(state.layers)
    }

    fun commitTextEdit() {
        textEdits.commit()?.let { pending ->
            undoStack = undoStack.push(UndoEntry.LayersChange(pending))
            update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
        }
    }

    private fun EditorState.syncLayerSelections(): EditorState {
        val textId = textEditorState.selectedId?.takeIf { id ->
            layers.any { it is EditorLayer.Text && it.id == id }
        }
        val stickerOverlay = stickerPanelState.selectedId?.let { id ->
            layers.filterIsInstance<EditorLayer.Sticker>().firstOrNull { it.id == id }
        }
        return copy(
            textEditorState = textEditorState.copy(selectedId = textId),
            stickerPanelState = stickerPanelState.copy(
                selectedId = stickerOverlay?.id,
                editTarget = stickerOverlay?.overlay,
            )
        )
    }

    fun loadImage(context: Context, uri: Uri) {
        update { copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                val repository = ImageRepository(context)
                val (imported, loaded, chain) = repository.importImage(uri)
                if (loaded == null) return@launch
                val resolved = imported ?: throw IOException("Failed to resolve image URI: $uri")
                val mcuInfo = if (resolved.mimeType == "image/jpeg") {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?.let { JPEGLosslessTransform.mcuSize(it) }
                    }.getOrNull()
                } else {
                    null
                }
                val format = ExportFormat.fromMimeType(resolved.mimeType)
                undoStack = UndoStack(maxDepth = App.prefs.loadUndoLevels())
                textEdits.clear()
                stickerEdits.clear()
                currentLayerGroupId = null
                val bgGroupId = System.nanoTime()
                val bgLayer = EditorLayer.Background(id = bgGroupId, groupId = bgGroupId)
                val (defaultToolMode, defaultToolPanel) = App.prefs.loadDefaultTool()
                update {
                    copy(
                        workingBitmap = loaded,
                        originalBitmap = loaded,
                        initialUri = resolved.uri,
                        originalMimeType = resolved.mimeType,
                        originalFilePath = resolved.realPath,
                        customFileName = withExtension(
                            resolved.fileName ?: "", format.extension
                        ),
                        exportFormat = format,
                        mode = defaultToolMode,
                        editorSubpanel = defaultToolPanel,
                        widthInput = loaded.width.toString(),
                        heightInput = loaded.height.toString(),
                        layers = listOf(bgLayer),
                        layerGroupMeta = mapOf(
                            bgGroupId to LayerGroupMeta(
                                ordinal = 1, customName = "Background"
                            )
                        ),
                        nextLayerOrdinal = 2,
                        textEditorState = TextEditorState(color = App.prefs.loadTextColor()),
                        stickerPanelState = StickerPanelState(
                            catalog = StickerAssets.all,
                            defaultFill = App.prefs.loadStickerFillColor(),
                            defaultOutline = App.prefs.loadStickerOutlineColor(),
                            defaultOutlineThickness = App.prefs.loadStickerOutlineThickness(),
                        ),
                        viewCrop = null,
                        rotation = 0f,
                        flipHorizontal = false,
                        flipVertical = false,
                        adjust = AdjustState(),
                        losslessChain = chain,
                        losslessEnabled = false,
                        mcuInfo = mcuInfo,
                        canUndo = false,
                        canRedo = false,
                        isLoading = false,
                        exifDateOnly = App.prefs.loadExifDateOnly(),
                        overwriteEnabled = true
                    )
                }
            }.onFailure { e ->
                Log.e("loadImage", "Failed to load image from URI: $uri", e)
                update { copy(isLoading = false) }
            }
        }
    }

    fun closeImage() {
        state.workingBitmap?.let { }
        undoStack = UndoStack(maxDepth = App.prefs.loadUndoLevels())
        textEdits.clear()
        stickerEdits.clear()
        currentLayerGroupId = null
        update {
            copy(
                workingBitmap = null,
                originalBitmap = null,
                initialUri = null,
                originalMimeType = null,
                originalFilePath = null,
                customFileName = "",

                mode = EditorPanel.None,
                editorSubpanel = null,

                widthInput = "",
                heightInput = "",
                layers = emptyList(),
                layerGroupMeta = emptyMap(),
                nextLayerOrdinal = 1,
                textEditorState = TextEditorState(color = App.prefs.loadTextColor()),
                stickerPanelState = StickerPanelState(
                    catalog = StickerAssets.all,
                    defaultFill = App.prefs.loadStickerFillColor(),
                    defaultOutline = App.prefs.loadStickerOutlineColor(),
                    defaultOutlineThickness = App.prefs.loadStickerOutlineThickness(),
                ),
                viewCrop = null,
                rotation = 0f,
                flipHorizontal = false,
                flipVertical = false,
                adjust = AdjustState(),
                losslessChain = null,
                losslessEnabled = false,
                mcuInfo = null,
                showMcuGrid = false,
                canUndo = false,
                canRedo = false,
                isLoading = false,
                overwriteEnabled = true,
            )
        }
    }

    fun createImage(w: Int, h: Int, fillColor: Color) {
        if (w < 1 || h < 1) return
        update { copy(isLoading = true) }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                val bmp = createBitmap(w, h)
                if (fillColor.alpha > 0f) {
                    Canvas(bmp).drawColor(fillColor.toArgb())
                }
                bmp
            }
            undoStack = UndoStack(maxDepth = App.prefs.loadUndoLevels())
            textEdits.clear()
            stickerEdits.clear()
            currentLayerGroupId = null
            val bgGroupId = System.nanoTime()
            val bgLayer = EditorLayer.Background(id = bgGroupId, groupId = bgGroupId)
            val (defaultToolMode, defaultTransformPanel) = App.prefs.loadDefaultTool()

            update {
                copy(
                    workingBitmap = bitmap,
                    originalBitmap = bitmap,
                    initialUri = null,
                    originalMimeType = null,
                    originalFilePath = null,
                    customFileName = "image.png",
                    exportFormat = ExportFormat.PNG,
                    mode = defaultToolMode,
                    editorSubpanel = defaultTransformPanel,
                    widthInput = w.toString(),
                    heightInput = h.toString(),
                    layers = listOf(bgLayer),
                    layerGroupMeta = mapOf(
                        bgGroupId to LayerGroupMeta(
                            ordinal = 1, customName = "Background"
                        )
                    ),
                    nextLayerOrdinal = 2,
                    textEditorState = TextEditorState(color = App.prefs.loadTextColor()),
                    stickerPanelState = StickerPanelState(
                        catalog = StickerAssets.all,
                        defaultFill = App.prefs.loadStickerFillColor(),
                        defaultOutline = App.prefs.loadStickerOutlineColor(),
                        defaultOutlineThickness = App.prefs.loadStickerOutlineThickness(),
                    ),
                    viewCrop = null,
                    rotation = 0f,
                    flipHorizontal = false,
                    flipVertical = false,
                    adjust = AdjustState(),
                    mcuInfo = null,
                    showMcuGrid = losslessEnabled,
                    canUndo = false,
                    canRedo = false,
                    isLoading = false,
                    overwriteEnabled = false
                )
            }
        }
    }

    fun setMode(newMode: EditorPanel, resumeGroupId: Long? = null) {
        val currentMode = state.mode
        val next = if (resumeGroupId != null) newMode
        else if (currentMode::class == newMode::class) EditorPanel.None
        else newMode
        if (next::class != currentMode::class) commitLayerGroupSession()
        if (resumeGroupId != null) {
            currentLayerGroupId = resumeGroupId
        }
        val wasInCrop = currentMode is EditorPanel.Transform
        val movingAway = newMode !is EditorPanel.Transform
        val togglingOff = currentMode::class == newMode::class && currentMode !is EditorPanel.None
        if (wasInCrop && (movingAway || togglingOff)) {
            viewModelScope.launch { applyTransform() }
        }
        if (currentMode is EditorPanel.Text && next !is EditorPanel.Text) {
            commitTextEdit()
            update { copy(textEditorState = textEditorState.copy(selectedId = null, draft = "")) }
        }
        if (currentMode is EditorPanel.Stickers && next !is EditorPanel.Stickers) {
            commitStickerEdit()
            update {
                copy(
                    stickerPanelState = stickerPanelState.copy(
                        selectedId = null,
                        editTarget = null,
                    )
                )
            }
        }
        update { copy(mode = next) }
    }

    fun setLosslessEnabled(enabled: Boolean) {
        update { copy(losslessEnabled = enabled) }
    }

    fun setExifDateOnly(dateOnly: Boolean) {
        App.prefs.saveExifDateOnly(dateOnly)
        update { copy(exifDateOnly = dateOnly) }
    }

    fun setTransformState(transformState: TransformState) {
        if (transformState.rotation != state.rotation) update { copy(viewCrop = null) }

        update {
            copy(
                rotation = transformState.rotation,
                aspectRatio = transformState.aspectRatio,
                flipHorizontal = transformState.flipHorizontal,
                flipVertical = transformState.flipVertical,
                widthInput = transformState.widthInput,
                heightInput = transformState.heightInput,
                lockAspect = transformState.lockAspect,
                resizeMode = transformState.resizeMode,
                canvasAnchorX = transformState.canvasAnchorX,
                canvasAnchorY = transformState.canvasAnchorY,
                canvasFillColor = transformState.canvasFillColor,
            )
        }
    }

    fun onCropChanged(selectionPx: Rect?) {
        if (selectionPx != null) {
            if (selectionPx.left >= selectionPx.right || selectionPx.top >= selectionPx.bottom) {
                Log.w("Crop", "Ignoring invalid crop rect: $selectionPx")
                return
            }
        }
        update { copy(viewCrop = selectionPx) }
    }

    suspend fun applyTransform() {
        val original = state.workingBitmap ?: return
        val rotation = state.rotation
        val viewCrop = state.viewCrop
        val flipH = state.flipHorizontal
        val flipV = state.flipVertical
        val hasCrop = viewCrop != null && viewCrop.let { vc ->
            vc.left < vc.right && vc.top < vc.bottom && (vc.left > 0f || vc.top > 0f || vc.right < original.width.toFloat() || vc.bottom < original.height.toFloat())
        }
        if (!hasCrop && rotation % 360f == 0f && !flipH && !flipV) return
        val currentLayers = state.layers
        val oldChain = state.losslessChain
        undoStack = undoStack.push(
            UndoEntry.BitmapChange(original, currentLayers, oldChain)
        )
        update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }

        val newBitmap = withContext(Dispatchers.Default) {
            var current = original
            var trimOffsetX = 0f
            var trimOffsetY = 0f
            if (rotation % 360f != 0f) {
                val rotated = TransformOps.rotate(current, rotation, trim = true)
                if (rotated.bitmap !== current) {
                    @Suppress("KotlinConstantConditions") if (current !== original) current.recycle()
                }
                trimOffsetX = rotated.trimOffsetX.toFloat()
                trimOffsetY = rotated.trimOffsetY.toFloat()
                current = rotated.bitmap
            }

            if (flipH || flipV) {
                val flipped = TransformOps.flip(current, flipH, flipV)
                if (flipped !== current) {
                    if (current !== original) current.recycle()
                }
                current = flipped
            }

            if (hasCrop) {
                val cropped = TransformOps.crop(current, viewCrop)
                if (cropped.bitmap !== current) {
                    if (current !== original) current.recycle()
                }
                current = cropped.bitmap
            }

            val cropLeft = viewCrop?.left ?: 0f
            val cropTop = viewCrop?.top ?: 0f
            val totalDx = cropLeft - trimOffsetX
            val totalDy = cropTop - trimOffsetY
            val translatedLayers = if (totalDx != 0f || totalDy != 0f) {
                currentLayers.translateForCrop(totalDx, totalDy)
            } else currentLayers

            current to translatedLayers
        }

        val (finalBitmap, finalLayers) = newBitmap

        val newChain = when {
            oldChain == null -> null
            flipH || flipV -> null
            rotation % 90f != 0f -> null
            else -> {
                var ops = oldChain.ops
                val quarterTurns = (rotation / 90f).roundToInt().mod(4)
                if (quarterTurns != 0) ops = ops + LosslessOp.Rotate(quarterTurns)
                if (hasCrop) ops = ops + LosslessOp.Crop(
                    x = viewCrop.left.toInt(), y = viewCrop.top.toInt(),
                    w = viewCrop.width.toInt(), h = viewCrop.height.toInt(),
                )
                oldChain.copy(ops = ops)
            }
        }

        update {
            copy(
                workingBitmap = finalBitmap,
                layers = finalLayers,
                rotation = 0f,
                flipHorizontal = false,
                flipVertical = false,
                viewCrop = null,
                losslessChain = newChain,
                mcuInfo = null,
                widthInput = finalBitmap.width.toString(),
                heightInput = finalBitmap.height.toString(),
            )
        }
    }


    fun canvasResize(newW: Int, newH: Int) {
        val original = state.workingBitmap ?: return
        if (newW < 1 || newH < 1) return
        val oldChain = state.losslessChain
        undoStack = undoStack.push(UndoEntry.BitmapChange(original, state.layers, oldChain))
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                TransformOps.canvasResize(
                    source = original,
                    newWidth = newW,
                    newHeight = newH,
                    layers = state.layers,
                    anchorX = state.canvasAnchorX,
                    anchorY = state.canvasAnchorY,
                    fillColor = state.canvasFillColor,
                )
            }
            update {
                copy(
                    workingBitmap = result.bitmap,
                    layers = result.layers,
                    viewCrop = null,
                    losslessChain = null,
                    mcuInfo = null,
                    widthInput = newW.toString(),
                    heightInput = newH.toString(),
                    canUndo = undoStack.canUndo,
                    canRedo = undoStack.canRedo,
                )
            }
        }
    }

    fun applyResize(w: Int, h: Int) {
        val original = state.workingBitmap ?: return
        val unchanged = w == original.width && h == original.height
        if (unchanged) return
        when (state.resizeMode) {
            ResizeMode.Canvas -> {
                canvasResize(w, h)
                return
            }

            ResizeMode.Scale -> {
                val isUpscale = w > original.width || h > original.height
                if (isUpscale) {
                    AppToasts.show("Cannot scale up past original size (${original.width}×${original.height})")
                    return
                }
                val targetWidth = w.coerceIn(1, original.width)
                val targetHeight = h.coerceIn(1, original.height)
                val oldChain = state.losslessChain
                undoStack = undoStack.push(
                    UndoEntry.BitmapChange(
                        original, state.layers, oldChain
                    )
                )
                viewModelScope.launch {
                    val result = withContext(Dispatchers.Default) {
                        TransformOps.bitmapResize(
                            source = original,
                            newWidth = targetWidth,
                            newHeight = targetHeight,
                            layers = state.layers,
                            originalWidth = original.width,
                            originalHeight = original.height,
                        )
                    }
                    update {
                        copy(
                            workingBitmap = result.bitmap,
                            layers = result.layers,
                            viewCrop = null,
                            losslessChain = null,
                            mcuInfo = null,
                            widthInput = targetWidth.toString(),
                            heightInput = targetHeight.toString(),
                            canUndo = undoStack.canUndo,
                            canRedo = undoStack.canRedo,
                        )
                    }
                }
            }
        }
    }

    fun undo() {
        textEdits.clear()
        stickerEdits.clear()
        val (entry, next) = undoStack.pop()
        undoStack = next
        if (entry == null) {
            update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
            return
        }
        val redoSnapshot = entry.snapshotAfter(state)
        undoStack = undoStack.pushRedo(redoSnapshot)
        when (entry) {
            is UndoEntry.LayersChange -> update {
                copy(layers = entry.layers).syncLayerSelections()
            }

            is UndoEntry.BitmapChange -> {
                update {
                    copy(
                        workingBitmap = entry.bitmap,
                        layers = entry.layers,
                        viewCrop = null,
                        losslessChain = entry.losslessChain,
                        mcuInfo = null,
                        widthInput = entry.bitmap.width.toString(),
                        heightInput = entry.bitmap.height.toString(),
                    ).syncLayerSelections()
                }
            }
        }
        update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
    }

    fun redo() {
        textEdits.clear()
        stickerEdits.clear()
        val (entry, next) = undoStack.redo()
        undoStack = next
        if (entry == null) {
            update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
            return
        }
        val undoSnapshot = entry.snapshotAfter(state)
        undoStack = undoStack.pushUndo(undoSnapshot)
        when (entry) {
            is UndoEntry.LayersChange -> update {
                copy(layers = entry.layers).syncLayerSelections()
            }

            is UndoEntry.BitmapChange -> {
                update {
                    copy(
                        workingBitmap = entry.bitmap,
                        layers = entry.layers,
                        viewCrop = null,
                        losslessChain = entry.losslessChain,
                        mcuInfo = null,
                        widthInput = entry.bitmap.width.toString(),
                        heightInput = entry.bitmap.height.toString(),
                    ).syncLayerSelections()
                }
            }
        }
        update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
    }

    fun setUndoLevels(levels: Int) {
        App.prefs.saveUndoLevels(levels)
        undoStack = undoStack.copy(maxDepth = levels)
        update {
            copy(
                undoLevels = levels, canUndo = undoStack.canUndo, canRedo = undoStack.canRedo
            )
        }
    }

    fun strokeEnd(points: List<Offset>, strokeWidthPx: Float) {
        if (state.drawTool == DrawTool.Eraser) {
            val erased =
                DrawingOps.eraseFromLayersExact(state.layers, points, strokeWidthPx) ?: return
            pushLayerSnapshot()
            update { copy(layers = erased) }
            return
        }
        val newLayer = DrawingOps.buildStrokeLayer(
            points, strokeWidthPx,
            penColor = state.penColor,
            tool = state.drawTool,
            groupId = currentGroupId(),
        ) ?: return
        pushLayerSnapshot()
        update { copy(layers = layers + newLayer) }
    }

    fun clearStrokes() {
        pushLayerSnapshot()
        update {
            copy(
                layers = layers.filterNot {
                    it is EditorLayer.Stroke || it is EditorLayer.EraseStroke
                },
            )
        }
    }

    fun setDrawState(drawState: DrawState) {
        if (drawState.tool is DrawTool.Eyedropper) {
            update { copy(previousDrawTool = drawTool) }
        }
        App.prefs.savePenWidth(drawState.penWidth)
        App.prefs.savePenColor(drawState.penColor)
        update {
            copy(
                penColor = drawState.penColor,
                penWidth = drawState.penWidth,
                drawTool = drawState.tool,
            )
        }
    }

    fun eyedropperPick(pixelPos: Offset, scaledDensity: Float, context: Context) {
        viewModelScope.launch {
            val sampled = withContext(Dispatchers.Default) {
                DrawingOps.sampleColor(
                    state.workingBitmap,
                    state.layers,
                    state.adjust,
                    scaledDensity,
                    pixelPos.x.toInt(),
                    pixelPos.y.toInt(),
                    context = context
                )
            }
            App.prefs.savePenColor(sampled)
            update {
                copy(
                    penColor = sampled,
                    drawTool = state.previousDrawTool ?: DrawTool.Brush,
                    previousDrawTool = null,
                    dropperPreview = null,
                )
            }
        }
    }

    fun eyedropperPreview(pixelPos: Offset, scaledDensity: Float, context: Context) {
        val x = pixelPos.x.toInt()
        val y = pixelPos.y.toInt()
        val last = state.dropperPreview
        if (last != null && last.pixelX == x && last.pixelY == y) return
        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                DrawingOps.samplePreviewRegion(
                    state.workingBitmap,
                    state.layers,
                    state.adjust,
                    scaledDensity,
                    x,
                    y,
                    context = context
                )
            }
            update { copy(dropperPreview = preview) }
        }
    }

    fun addText(density: Density) {
        val wb = state.workingBitmap ?: return
        commitTextEdit()
        pushLayerSnapshot()
        val viewCrop = state.viewCrop ?: Rect(0f, 0f, wb.width.toFloat(), wb.height.toFloat())
        val newOverlay = TextOps.buildOverlay("Text", state.textEditorState, viewCrop, density)
        update {
            copy(
                layers = layers + EditorLayer.Text(newOverlay, groupId = currentGroupId()),
                textEditorState = textEditorState.copy(selectedId = newOverlay.id, draft = ""),
            )
        }
    }

    fun moveText(id: Long, delta: Offset) {
        beginTextEditIfNeeded()
        update {
            copy(layers = layers.map {
                if (it is EditorLayer.Text && it.id == id) {
                    it.copy(overlay = it.overlay.copy(positionPx = it.overlay.positionPx + delta))
                } else it
            })
        }
    }

    fun updateTextBox(id: Long, pos: Offset, width: Float, height: Float?, rotation: Float) {
        beginTextEditIfNeeded()
        update {
            copy(layers = layers.map {
                if (it is EditorLayer.Text && it.id == id) it.copy(
                    overlay = it.overlay.copy(
                        positionPx = pos,
                        boxWidthPx = width,
                        boxHeightPx = height,
                        rotation = rotation,
                    )
                ) else it
            })
        }
    }

    fun selectText(id: Long?) {
        if (state.textEditorState.selectedId != id) {
            commitTextEdit()
        }
        update {
            copy(
                textEditorState = textEditorState.copy(
                    selectedId = id, draft = if (id == null) "" else textEditorState.draft
                )
            )
        }
    }

    fun deleteSelectedText() {
        val id = state.textEditorState.selectedId ?: return
        commitTextEdit()
        pushLayerSnapshot()
        update {
            copy(
                layers = layers.filterNot { it is EditorLayer.Text && it.id == id },
                textEditorState = textEditorState.copy(selectedId = null),
            )
        }
    }

    fun setTextState(newState: TextEditorState) {
        val prev = state.textEditorState
        if (prev == newState) return
        if (prev.color != newState.color) {
            App.prefs.saveTextColor(newState.color)
        }
        if (prev.selectedId != newState.selectedId) {
            commitTextEdit()
        }
        if (newState.selectedId != null) {
            beginTextEditIfNeeded()
        }
        update {
            copy(
                textEditorState = newState,
                layers = TextOps.applyDraftToLayers(layers, newState),
            )
        }
    }

    fun insertSticker(asset: StickerAsset) {
        commitStickerEdit()
        val wb = state.workingBitmap ?: return
        val vc = state.viewCrop ?: Rect(0f, 0f, wb.width.toFloat(), wb.height.toFloat())
        pushLayerSnapshot()
        val panel = state.stickerPanelState
        val newSticker = StickerOps.placeNewSticker(asset.source, vc).let {
            it.copy(
                renderMode = it.renderMode.copy(
                    fillColor = panel.defaultFill ?: it.renderMode.fillColor,
                    outlineColor = panel.defaultOutline ?: it.renderMode.outlineColor,
                    outlineThicknessPx = panel.defaultOutlineThickness,
                )
            )
        }
        update {
            copy(
                layers = layers + EditorLayer.Sticker(
                    newSticker, groupId = currentGroupId()
                )
            )
        }
        selectSticker(newSticker.id)
    }

    fun loadOverlayImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val repository = ImageRepository(context)
            val (bitmap, _) = repository.loadBitmap(uri)
            if (bitmap == null) return@launch
            val wb = state.workingBitmap ?: return@launch
            val vc = state.viewCrop ?: Rect(0f, 0f, wb.width.toFloat(), wb.height.toFloat())
            pushLayerSnapshot()
            val newSticker = StickerOps.placeOverlayImage(
                bitmap.width, bitmap.height, StickerSource.Bitmap(bitmap), vc
            )
            update {
                copy(
                    mode = EditorPanel.Stickers,
                    layers = layers + EditorLayer.Sticker(newSticker, groupId = currentGroupId()),
                    stickerPanelState = stickerPanelState.copy(
                        selectedId = newSticker.id,
                        editTarget = newSticker,
                    )
                )
            }
        }
    }

    fun deleteSticker(id: Long) {
        commitStickerEdit()
        pushLayerSnapshot()
        update {
            copy(
                layers = layers.filterNot { it is EditorLayer.Sticker && it.id == id },
            )
        }
    }

    fun moveLayer(id: Long, delta: Int) {
        pushLayerSnapshot()
        update {
            val idx = layers.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return@update this
            val groupId = layers[idx].groupId
            val groupIndices = layers.indices.filter { layers[it].groupId == groupId }
            val groupFirst = groupIndices.first()
            val groupLast = groupIndices.last()
            val newIdx = when (delta) {
                Int.MAX_VALUE -> groupLast
                Int.MIN_VALUE -> groupFirst
                else -> (idx + delta).coerceIn(groupFirst, groupLast)
            }
            if (idx == newIdx) return@update this
            val mutable = layers.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(newIdx, item)
            copy(layers = mutable)
        }
    }

    fun renameLayerGroup(groupId: Long, name: String) {
        val trimmed = name.trim().take(60)
        update {
            copy(
                layerGroupMeta = layerGroupMeta + (groupId to (layerGroupMeta[groupId]
                    ?: LayerGroupMeta(0)).copy(customName = trimmed.ifBlank { null }))
            )
        }
    }

    fun deleteLayerGroup(groupId: Long) {
        commitTextEdit()
        commitStickerEdit()
        pushLayerSnapshot()
        update {
            copy(
                layers = layers.filterNot { it.groupId == groupId },
                layerGroupMeta = layerGroupMeta - groupId,
            ).syncLayerSelections()
        }
    }

    fun moveLayerGroup(groupId: Long, delta: Int) {
        pushLayerSnapshot()
        update {
            val grouped = layers.groupBy { it.groupId }
            val order = grouped.keys.toMutableList()
            val idx = order.indexOf(groupId).takeIf { it >= 0 } ?: return@update this
            val newIdx = (idx + delta).coerceIn(0, order.lastIndex)
            if (idx == newIdx) return@update this
            order.add(newIdx, order.removeAt(idx))
            copy(layers = order.flatMap { grouped.getValue(it) })
        }
    }

    fun selectSticker(id: Long?) {
        if (id == null) {
            commitStickerEdit()
        }
        val overlay = state.stickerOverlays.firstOrNull { it.id == id }
        update {
            copy(
                stickerPanelState = stickerPanelState.copy(
                    selectedId = id,
                    editTarget = overlay,
                )
            )
        }
    }

    fun updateStickerBox(id: Long, pos: Offset, w: Float, h: Float, rot: Float) {
        update {
            val updatedLayers = layers.map {
                if (it is EditorLayer.Sticker && it.id == id) it.copy(
                    overlay = it.overlay.copy(
                        positionPx = pos, widthPx = w, heightPx = h, rotation = rot
                    )
                ) else it
            }
            copy(
                layers = updatedLayers,
                stickerPanelState = if (stickerPanelState.selectedId == id) {
                    stickerPanelState.copy(
                        editTarget = updatedLayers.filterIsInstance<EditorLayer.Sticker>()
                            .firstOrNull { it.id == id }?.overlay
                    )
                } else stickerPanelState
            )
        }
    }

    fun setStickerPanelState(newState: StickerPanelState) {
        val oldEditTarget = state.stickerPanelState.editTarget
        val newEditTarget = newState.editTarget
        val overlaysChanged =
            oldEditTarget != null && newEditTarget != null && oldEditTarget != newEditTarget
        if (overlaysChanged) {
            stickerEdits.begin(state.layers)
        }
        val rm = newEditTarget?.renderMode
        val oldRm = oldEditTarget?.renderMode
        val defaults = if (rm != null) {
            var changed = false
            if (rm.fillColor != oldRm?.fillColor) {
                App.prefs.saveStickerFillColor(rm.fillColor)
                changed = true
            }
            if (rm.outlineColor != oldRm?.outlineColor) {
                App.prefs.saveStickerOutlineColor(rm.outlineColor)
                changed = true
            }
            if (rm.outlineThicknessPx != oldRm?.outlineThicknessPx) changed = true
            if (changed) newState.copy(
                defaultFill = rm.fillColor,
                defaultOutline = rm.outlineColor,
                defaultOutlineThickness = rm.outlineThicknessPx,
            ) else null
        } else null
        update {
            copy(
                canUndo = undoStack.canUndo,
                canRedo = undoStack.canRedo,
                stickerPanelState = defaults ?: newState,
                layers = if (overlaysChanged) {
                    layers.map {
                        if (it is EditorLayer.Sticker && it.id == newEditTarget.id) {
                            it.copy(overlay = newEditTarget)
                        } else it
                    }
                } else layers,
            )
        }
    }

    fun onTextEditOverlay(overlay: TextOverlay) {
        setTextState(
            state.textEditorState.copy(
                draft = overlay.text,
                fontSizeSp = overlay.fontSizeSp,
                color = overlay.color,
                fontWeight = overlay.fontWeight,
                italic = overlay.italic,
                underline = overlay.underline,
                strikethrough = overlay.strikethrough,
                align = overlay.align,
                wordWrap = overlay.wordWrap,
                selectedId = overlay.id,
                fontFamily = overlay.fontFamily,
                letterSpacing = overlay.letterSpacing,
            )
        )
    }

    fun commitStickerEdit() {
        val target = state.stickerPanelState.editTarget
        if (target != null) {
            App.prefs.saveStickerOutlineThickness(target.renderMode.outlineThicknessPx)
        }
        stickerEdits.commit()?.let { pending ->
            undoStack = undoStack.push(UndoEntry.LayersChange(pending))
            update { copy(canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
        }
    }

    fun setAdjust(adjustState: AdjustState) {
        update { copy(adjust = adjustState) }
    }

    fun setActivePanel(panel: EditorSubpanel?) {
        if (state.mode is EditorPanel.Transform) {
            viewModelScope.launch {
                applyTransform()
                update { copy(editorSubpanel = panel) }
            }
        } else {
            update { copy(editorSubpanel = panel) }
        }
    }

    fun addColor(color: Color, index: Int? = null) {
        val updated = index?.let { App.prefs.setColorAt(it, color) } ?: App.prefs.addColor(color)
        update { copy(palette = updated) }
    }

    fun removeColor(color: Color) {
        val current = state.palette
        if (current.size > 1) {
            val updated = App.prefs.removeColor(color)
            update { copy(palette = updated) }
        } else {
            AppToasts.show("At least one color required")
        }
    }

    fun setFileName(name: String) {
        update { copy(customFileName = name) }
    }

    fun setExportFormat(format: ExportFormat) {
        update {
            copy(
                customFileName = withExtension(
                    customFileName, format.extension
                ),
                exportFormat = format,
            )
        }
    }

    fun setExportQuality(quality: Int) {
        App.prefs.saveExportQuality(quality)
        update { copy(exportQuality = quality) }
    }

    fun saveAs(uri: Uri, context: Context, density: Density, onDone: () -> Unit) {
        val source = state.workingBitmap ?: state.originalBitmap ?: return
        update { copy(isLoading = true) }
        viewModelScope.launch {
            val repository = ImageRepository(context)
            val result = repository.saveAs(
                source = source,
                uri = uri,
                originalUri = state.initialUri,
                format = state.exportFormat,
                quality = state.exportQuality,
                layers = state.layers,
                viewCrop = state.viewCrop,
                adjust = state.adjust,
                flipHorizontal = state.flipHorizontal,
                flipVertical = state.flipVertical,
                scaledDensity = density.density * density.fontScale,
                losslessChain = state.losslessChain,
                useLossless = state.canLossless
            )
            update { copy(isLoading = false) }
            AppToasts.show(
                if (result.isSuccess) "Saved" else "Save failed: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_SHORT
            )
            if (result.isFailure) {
                Log.e("SaveAs", "Failed to save image", result.exceptionOrNull())
            }
            if (result.isSuccess) onDone()
        }
    }

    fun overwrite(
        context: Context,
        density: Density,
        deletionMode: DeletionMode,
        onDone: () -> Unit,
    ) {
        val filePath = state.originalFilePath ?: return
        val repository = ImageRepository(context)
        viewModelScope.launch {
            repository.performOverwrite(
                context = context,
                state = state,
                originalPath = filePath,
                onDone = onDone,
                density = density,
                exportQuality = state.exportQuality,
                deletionMode = deletionMode,
            )
        }
    }
}
