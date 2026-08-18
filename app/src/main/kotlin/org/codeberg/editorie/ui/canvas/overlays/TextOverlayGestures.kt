package org.codeberg.editorie.ui.canvas.overlays

// SPDX-License-Identifier: MIT

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.codeberg.editorie.options.text.TextOverlay
import org.codeberg.editorie.options.text.makeStaticLayout
import org.codeberg.editorie.options.text.toTextPaint
import org.codeberg.editorie.ui.canvas.BoxGeom
import org.codeberg.editorie.ui.canvas.BoxHandle
import org.codeberg.editorie.ui.canvas.CanvasViewport
import org.codeberg.editorie.ui.canvas.OverlayGestureSpec
import org.codeberg.editorie.ui.canvas.ScreenBox
import org.codeberg.editorie.ui.canvas.detectOverlayGestures
import org.codeberg.editorie.ui.canvas.rotateOffset
import org.codeberg.editorie.ui.canvas.screenToLocal
import kotlin.math.abs

fun measureTextBoxHeightPx(
    overlay: TextOverlay, widthPx: Float, displayRect: Rect, sourceRect: Rect, density: Float
): Float {
    val screenBoxW = widthPx * displayRect.width / sourceRect.width
    val fontPxInImage = overlay.fontSizeSp * density
    val screenFontPx = fontPxInImage * (displayRect.width / sourceRect.width)
    val tp = overlay.toTextPaint(screenFontPx)
    return makeStaticLayout(
        overlay.text, tp, screenBoxW.toInt().coerceAtLeast(1), overlay.align, overlay.wordWrap
    ).height.toFloat()
}

fun computeTextScreenBox(
    overlay: TextOverlay, viewport: CanvasViewport, density: Float
): ScreenBox {
    val sp = viewport.fromPixel(overlay.positionPx)
    val screenBoxWFloat =
        overlay.boxWidthPx * viewport.displayRect.width / viewport.sourceRect.width
    val screenBoxH =
        overlay.boxHeightPx?.let { it * viewport.displayRect.height / viewport.sourceRect.height }
            ?: measureTextBoxHeightPx(
                overlay, overlay.boxWidthPx, viewport.displayRect, viewport.sourceRect, density
            )
    return ScreenBox(sp, screenBoxWFloat, screenBoxH)
}

fun snapTextRotation(rotation: Float, snapThreshold: Float = 8f): Float {
    val normalized = rotation % 360f
    val effective = if (normalized < 0f) normalized + 360f else normalized
    val targets = listOf(0f, 90f, 180f, 270f, 360f)
    for (target in targets) {
        val diff = abs(effective - target)
        if (diff <= snapThreshold || diff >= 360f - snapThreshold) return if (target == 360f) 0f else target
    }
    return rotation
}

@Composable
fun Modifier.textOverlayGestures(
    viewport: CanvasViewport,
    density: Float,
    handlePx: Float,
    textOverlays: List<TextOverlay>,
    selectedTextId: Long?,
    onTextSelect: (Long?) -> Unit,
    onTextEdit: (TextOverlay) -> Unit,
    onTextMove: (Long, Offset) -> Unit,
    onTextBoxChange: (Long, Offset, Float, Float?, Float) -> Unit,
): Modifier {
    val latestTextOverlays by rememberUpdatedState(textOverlays)
    val latestSelectedId by rememberUpdatedState(selectedTextId)
    return this.pointerInput(viewport.displayRect, viewport.zoom, viewport.sourceRect) {
        val textSpec = OverlayGestureSpec(
            items = { latestTextOverlays },
            selectedId = { latestSelectedId },
            idOf = { it.id },
            rotationOf = { it.rotation },
            screenBox = { computeTextScreenBox(it, viewport, density) },
            resizeGeom = { t ->
                val h = t.boxHeightPx ?: (measureTextBoxHeightPx(
                    t, t.boxWidthPx, viewport.displayRect, viewport.sourceRect, density
                ) * viewport.sourceRect.height / viewport.displayRect.height)
                BoxGeom(t.positionPx, t.boxWidthPx, h, t.rotation)
            },
            minWidthPx = 50f,
            minHeightPx = 20f,
            gestureInsetPx = 22.dp.toPx(),
            onSelect = { onTextSelect(it) },
            onTap = { onTextEdit(it) },
            onMove = { ov, deltaPx -> onTextMove(ov.id, deltaPx) },
            onResize = { handle, id, pos, w, h, rot ->
                val outH = if (handle == BoxHandle.L || handle == BoxHandle.R) {
                    latestTextOverlays.firstOrNull { it.id == id }?.boxHeightPx
                } else h
                onTextBoxChange(id, pos, w, outH, rot)
            },
            onRotate = { ov, rot ->
                onTextBoxChange(
                    ov.id, ov.positionPx, ov.boxWidthPx, ov.boxHeightPx, rot
                )
            },
        )
        detectOverlayGestures(
            spec = textSpec,
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
