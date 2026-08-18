package org.codeberg.editorie.data

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import org.codeberg.editorie.options.drawing.DrawnStroke
import org.codeberg.editorie.options.stickers.StickerOverlay
import org.codeberg.editorie.options.text.TextOverlay

sealed interface EditorLayer {
    val id: Long
    val groupId: Long

    data class Stroke(val stroke: DrawnStroke, override val groupId: Long) : EditorLayer {
        override val id: Long get() = stroke.id
    }

    data class EraseStroke(
        val path: Path,
        val color: Color,
        override val groupId: Long,
        override val id: Long = System.nanoTime(),
    ) : EditorLayer

    data class Text(val overlay: TextOverlay, override val groupId: Long) : EditorLayer {
        override val id: Long get() = overlay.id
    }

    data class Sticker(val overlay: StickerOverlay, override val groupId: Long) : EditorLayer {
        override val id: Long get() = overlay.id
    }

    data class Background(
        override val id: Long,
        override val groupId: Long,
    ) : EditorLayer
}

val EditorLayer.toolMode: EditorPanel
    get() = when (this) {
        is EditorLayer.Stroke -> EditorPanel.Draw
        is EditorLayer.EraseStroke -> EditorPanel.Draw
        is EditorLayer.Text -> EditorPanel.Text
        is EditorLayer.Sticker -> EditorPanel.Stickers
        is EditorLayer.Background -> EditorPanel.None
    }

data class LayerGroupMeta(
    val ordinal: Int,
    val customName: String? = null,
)

data class LayerGroupInfo(
    val groupId: Long,
    val layers: List<EditorLayer>,
    val ordinal: Int,
    val customName: String?,
) {
    val toolMode: EditorPanel get() = layers.first().toolMode
    val toolLabel: String
        get() = when (toolMode) {
            EditorPanel.Draw -> "Draw"
            EditorPanel.Text -> "Text"
            EditorPanel.Stickers -> "Sticker"
            else -> "Layer"
        }
    val displayName: String get() = customName ?: "New layer $ordinal $toolLabel"
}

fun List<EditorLayer>.toLayerGroups(meta: Map<Long, LayerGroupMeta>): List<LayerGroupInfo> =
    groupBy { it.groupId }.map { (groupId, members) ->
        val m = meta[groupId]
        LayerGroupInfo(
            groupId = groupId,
            layers = members,
            ordinal = m?.ordinal ?: 0,
            customName = m?.customName,
        )
    }

sealed class UndoEntry {
    data class LayersChange(val layers: List<EditorLayer>) : UndoEntry()
    data class BitmapChange(
        val bitmap: Bitmap,
        val layers: List<EditorLayer>,
        val losslessChain: LosslessChain? = null,
    ) : UndoEntry()
}
