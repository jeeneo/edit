package org.codeberg.editorie.ui.canvas.overlays

// SPDX-License-Identifier: MIT

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import org.codeberg.editorie.options.stickers.StickerOverlay
import org.codeberg.editorie.ui.canvas.BoxGeom
import org.codeberg.editorie.ui.canvas.CanvasViewport
import org.codeberg.editorie.ui.canvas.OverlayGestureSpec
import org.codeberg.editorie.ui.canvas.ScreenBox
import org.codeberg.editorie.ui.canvas.detectOverlayGestures
import org.codeberg.editorie.ui.canvas.rotateOffset
import org.codeberg.editorie.ui.canvas.screenToLocal

@Composable
fun Modifier.stickerOverlayGestures(
    viewport: CanvasViewport,
    handlePx: Float,
    stickerOverlays: List<StickerOverlay>,
    selectedStickerId: Long?,
    onStickerSelect: (Long?) -> Unit,
    onStickerBoxChange: (Long, Offset, Float, Float, Float) -> Unit,
): Modifier {
    val latestStickers by rememberUpdatedState(stickerOverlays)
    val latestSelectedId by rememberUpdatedState(selectedStickerId)
    return this.pointerInput(viewport.displayRect, viewport.zoom, viewport.sourceRect) {
        val stickerSpec = OverlayGestureSpec(
            items = { latestStickers },
            selectedId = { latestSelectedId },
            idOf = { it.id },
            rotationOf = { it.rotation },
            screenBox = { s ->
                ScreenBox(
                    viewport.fromPixel(s.positionPx),
                    s.widthPx * viewport.displayRect.width / viewport.sourceRect.width,
                    s.heightPx * viewport.displayRect.height / viewport.sourceRect.height
                )
            },
            resizeGeom = { BoxGeom(it.positionPx, it.widthPx, it.heightPx, it.rotation) },
            minWidthPx = 40f,
            minHeightPx = 40f,
            onSelect = { onStickerSelect(it) },
            onMove = { ov, deltaPx ->
                latestStickers.firstOrNull { it.id == ov.id }?.let { cur ->
                    onStickerBoxChange(
                        cur.id,
                        cur.positionPx + deltaPx,
                        cur.widthPx,
                        cur.heightPx,
                        cur.rotation
                    )
                }
            },
            onResize = { _, id, pos, w, h, rot ->
                onStickerBoxChange(id, pos, w, h, rot)
            },
            onRotate = { ov, rot ->
                onStickerBoxChange(
                    ov.id, ov.positionPx, ov.widthPx, ov.heightPx, rot
                )
            },
        )
        detectOverlayGestures(
            spec = stickerSpec,
            zoom = viewport.zoom,
            handlePx = handlePx,
            sourceRect = viewport.sourceRect,
            displayRect = viewport.displayRect,
            toContent = viewport::toContent,
            screenToLocal = ::screenToLocal,
            rotateOffset = ::rotateOffset,
            snapRotation = ::snapTextRotation,
        )
    }
}
