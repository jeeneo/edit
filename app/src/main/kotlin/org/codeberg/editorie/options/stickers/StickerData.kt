package org.codeberg.editorie.options.stickers

// SPDX-License-Identifier: MIT

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.codeberg.editorie.App

sealed class StickerSource {
    data class Asset(val assetPath: String) : StickerSource()
    class Bitmap(val bitmap: android.graphics.Bitmap) : StickerSource()
}

data class StickerRenderMode(
    val fillColor: Color? = Color.Unspecified,
    val outlineColor: Color? = null,
    val outlineThicknessPx: Float = 6f,
    val preserveAspectRatio: Boolean = true,
)

data class StickerOverlay(
    val id: Long = System.nanoTime(),
    val source: StickerSource,
    val positionPx: Offset,
    val widthPx: Float,
    val heightPx: Float,
    val rotation: Float = 0f,
    val renderMode: StickerRenderMode = StickerRenderMode(),
)

data class StickerAsset(
    val source: StickerSource,
    val displayName: String,
    val category: String,
)

object StickerAssets {
    val all: List<StickerAsset> by lazy { discoverAssets() }
    private const val ROOT = "stickers"
    private fun discoverAssets(): List<StickerAsset> {
        val ctx = App.ctx
        val assets = ctx.assets
        val result = mutableListOf<StickerAsset>()
        fun resString(name: String, fallback: String): String {
            val resId = ctx.resources.getIdentifier(name, "string", ctx.packageName)
            return if (resId != 0) ctx.getString(resId) else fallback
        }

        fun visit(dir: String) {
            val entries = runCatching { assets.list(dir) }.getOrNull() ?: return
            val relative = dir.removePrefix("$ROOT/").removePrefix(ROOT)
            for (entry in entries) {
                val path = "$dir/$entry"
                if (entry.endsWith(".svg")) {
                    val category = resString(
                        "${ROOT}_category_${relative.replace("/", "_category_")}",
                        relative.replaceFirstChar { it.uppercaseChar() })
                    val baseName = entry.removeSuffix(".svg")
                    val displayName = resString(
                        "${ROOT}_vector_$baseName",
                        baseName.replaceFirstChar { it.uppercaseChar() })
                    result += StickerAsset(StickerSource.Asset(path), displayName, category)
                } else {
                    visit(path)
                }
            }
        }
        visit(ROOT)
        return result
    }

    fun thumbailPreload(sizePx: Int = 128) {
        for (asset in all) {
            StickerBitmapCache.get(
                StickerOverlay(
                    source = asset.source,
                    positionPx = Offset.Zero,
                    widthPx = sizePx.toFloat(),
                    heightPx = sizePx.toFloat(),
                ),
                widthPx = sizePx,
                heightPx = sizePx,
            )
        }
    }
}

data class StickerPanelState(
    val catalog: List<StickerAsset> = emptyList(),
    val activeCategory: String? = null,
    val selectedId: Long? = null,
    val editTarget: StickerOverlay? = null,
    val defaultFill: Color? = null,
    val defaultOutline: Color? = null,
    val defaultOutlineThickness: Float = 6f,
)
