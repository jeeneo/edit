package org.codeberg.editorie.data

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import org.codeberg.editorie.options.adjust.AdjustState
import org.codeberg.editorie.options.drawing.DrawState
import org.codeberg.editorie.options.drawing.DrawTool
import org.codeberg.editorie.options.save.ExportFormat
import org.codeberg.editorie.options.stickers.StickerAssets
import org.codeberg.editorie.options.stickers.StickerOverlay
import org.codeberg.editorie.options.stickers.StickerPanelState
import org.codeberg.editorie.options.text.TextEditorState
import org.codeberg.editorie.options.text.TextOverlay
import org.codeberg.editorie.options.transform.McuInfo
import org.codeberg.editorie.options.transform.ResizeMode
import org.codeberg.editorie.options.transform.TransformState

data class EditorState(
    val isLoading: Boolean = false,
    val overwriteEnabled: Boolean = true,
    val workingBitmap: Bitmap? = null,
    val originalBitmap: Bitmap? = null,
    val initialUri: Uri? = null,
    val originalMimeType: String? = null,
    val originalFilePath: String? = null,
    val customFileName: String = "",
    val exportFormat: ExportFormat = ExportFormat.JPEG,
    val mode: EditorPanel = EditorPanel.None,
    val editorSubpanel: EditorSubpanel? = EditorSubpanel.Transform.Crop,
    val rotation: Float = 0f,
    val aspectRatio: Pair<Int, Int>? = null,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val viewCrop: Rect? = null,
    val adjust: AdjustState = AdjustState(),
    val widthInput: String = "",
    val heightInput: String = "",
    val lockAspect: Boolean = true,
    val resizeMode: ResizeMode = ResizeMode.Scale,
    val canvasAnchorX: Float = 0.5f,
    val canvasAnchorY: Float = 0.5f,
    val canvasFillColor: Color = Color.Transparent,
    val layers: List<EditorLayer> = emptyList(),
    val layerGroupMeta: Map<Long, LayerGroupMeta> = emptyMap(),
    val nextLayerOrdinal: Int = 1,
    val penColor: Color = Color.Black,
    val penWidth: Float = 8f,
    val drawTool: DrawTool = DrawTool.Brush,
    val previousDrawTool: DrawTool? = null,
    val palette: List<Color> = emptyList(),
    val textEditorState: TextEditorState = TextEditorState(),
    val stickerPanelState: StickerPanelState = StickerPanelState(catalog = StickerAssets.all),
    val exportQuality: Int = 95,
    val exifDateOnly: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLevels: Int = UndoStack.DEFAULT_MAX_UNDO,
    val losslessChain: LosslessChain? = null,
    val losslessEnabled: Boolean = false,
    val mcuInfo: McuInfo? = null,
    val showMcuGrid: Boolean = false,
    val dropperPreview: DropperPreviewData? = null,
) {
    val layerGroups: List<LayerGroupInfo>
        get() = layers.toLayerGroups(layerGroupMeta)

    val stickerOverlays: List<StickerOverlay>
        get() = layers.filterIsInstance<EditorLayer.Sticker>().map { it.overlay }

    val transformState: TransformState
        get() = TransformState(
            rotation = rotation,
            aspectRatio = aspectRatio,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            widthInput = widthInput,
            heightInput = heightInput,
            lockAspect = lockAspect,
            resizeMode = resizeMode,
            canvasAnchorX = canvasAnchorX,
            canvasAnchorY = canvasAnchorY,
            canvasFillColor = canvasFillColor,
        )

    val drawState: DrawState
        get() = DrawState(
            penColor = penColor, penWidth = penWidth, tool = drawTool, palette = palette
        )

    val losslessPossible: Boolean
        get() = losslessChain != null && layers.all { it is EditorLayer.Background } && adjust == AdjustState() && !flipHorizontal && !flipVertical && exportFormat == ExportFormat.JPEG

    val canLossless: Boolean
        get() = losslessEnabled && losslessPossible

}

data class ImageCanvasState(
    val display: ImageDisplayState,
    val overlay: ImageOverlayState,
)

data class ImageDisplayState(
    val bitmap: Bitmap,
    val rotationDeg: Float,
    val aspectRatio: Pair<Int, Int>?,
    val panel: EditorPanel,
    val subpanel: EditorSubpanel? = null,
    val viewCrop: Rect?,
    val adjust: AdjustState,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val mcuInfo: McuInfo? = null,
    val showMcuGrid: Boolean = false,
    val isFileNameFieldFocused: Boolean = false,
)

data class ImageOverlayState(
    val layers: List<EditorLayer>,
    val penColor: Color,
    val penWidth: Float,
    val drawTool: DrawTool,
    val selectedTextId: Long?,
    val selectedStickerId: Long?,
    val dropperPreview: DropperPreviewData? = null,
) {
    val textOverlays: List<TextOverlay>
        get() = layers.filterIsInstance<EditorLayer.Text>().map { it.overlay }

    val stickerOverlays: List<StickerOverlay>
        get() = layers.filterIsInstance<EditorLayer.Sticker>().map { it.overlay }
}

data class DropperPreviewData(
    val pixels: List<Int>,
    val regionLeft: Int,
    val regionTop: Int,
    val regionWidth: Int,
    val regionHeight: Int,
    val radius: Int,
    val color: Color,
    val pixelX: Int,
    val pixelY: Int,
    val hex: String,
)

sealed class EditorPanel {
    data object None : EditorPanel()
    data object Transform : EditorPanel()
    data object Draw : EditorPanel()
    data object Text : EditorPanel()
    data object Stickers : EditorPanel()
    data object Adjust : EditorPanel()
    object Save : EditorPanel()
}

sealed interface EditorSubpanel {
    enum class Transform : EditorSubpanel {
        Crop, Resize, Rotate, Mirror
    }
}

sealed class LosslessOp {
    data class Rotate(val quarterTurns: Int) : LosslessOp()
    data class Crop(val x: Int, val y: Int, val w: Int, val h: Int) : LosslessOp()
}


/*
todo: should we not normalize the image from exif to truly be 'lossless'?
 we rotate imported images from EXIF for normalization then set `TAG_ORIENTATION` to `NORMAL`
 to give a form of 'WYSIWYG' and not apply a double-rotate when viewed.
 should we not even perform rotating on import? what would someone more knowledgeable of lossless editing/EXIF expect?
 if we rotate the preview image, we would need to process edits on a sideways bitmap (should be trivial)
*/
data class LosslessChain(
    val exifRotation: Int = 0,
    val ops: List<LosslessOp> = emptyList(),
)
