package org.codeberg.editorie.ui.canvas.gestures

// SPDX-License-Identifier: MIT

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.ui.canvas.BoxHandle
import org.codeberg.editorie.ui.canvas.CanvasViewport
import org.codeberg.editorie.ui.canvas.CropDrag
import org.codeberg.editorie.ui.canvas.TRANSFORM_CORNER_ARM_SCALE
import org.codeberg.editorie.ui.canvas.TRANSFORM_CORNER_HIT_SCALE
import org.codeberg.editorie.ui.canvas.TRANSFORM_HANDLE_HIT_SCALE
import org.codeberg.editorie.ui.canvas.TRANSFORM_HANDLE_THICKNESS_SCALE
import org.codeberg.editorie.ui.canvas.clampPixel
import org.codeberg.editorie.ui.canvas.clampPixelMove
import org.codeberg.editorie.ui.canvas.clampPixelRatio
import org.codeberg.editorie.ui.canvas.debugOverlay
import kotlin.math.abs

@Stable
class CropGestureState {
    var selection by mutableStateOf<Rect?>(null)
    var dragHandle by mutableStateOf<CropDrag?>(null)
    var dragAccum by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberCropGestureState(initial: Rect?): CropGestureState =
    remember { CropGestureState().apply { selection = initial } }

internal fun resolveCropDragHandle(
    pixelTap: Offset,
    selection: Rect,
    sourceRect: Rect,
    displayRect: Rect,
    zoom: Float,
    handlePx: Float,
): CropDrag? {
    val srcPerDisp = sourceRect.width / displayRect.width
    val lineLeewayPx =
        (handlePx / zoom) * srcPerDisp + selection.width * 0.015f + selection.height * 0.015f + minOf(
            selection.width / 2f, selection.height / 2f
        ) * 0.015f
    val leewayPx = (handlePx * TRANSFORM_HANDLE_HIT_SCALE / zoom) * srcPerDisp
    val cornerLeewayPx = (handlePx * TRANSFORM_CORNER_HIT_SCALE / zoom) * srcPerDisp

    val baseR = handlePx / zoom / 2f
    val cornerExcludeXPx = (baseR * TRANSFORM_CORNER_ARM_SCALE).coerceAtMost(selection.width / 2f)
    val cornerExcludeYPx = (baseR * TRANSFORM_CORNER_ARM_SCALE).coerceAtMost(selection.height / 2f)
    val barHalf = TRANSFORM_HANDLE_THICKNESS_SCALE / zoom
    val centerExcludeXPx = if (selection.width / 2f > barHalf) barHalf else 0f
    val centerExcludeYPx = if (selection.height / 2f > barHalf) barHalf else 0f

    val screenCropFraction = minOf(
        selection.width / sourceRect.width, selection.height / sourceRect.height
    ).coerceIn(0.1f, 1f)
    val midBarHalfPx =
        (handlePx * TRANSFORM_HANDLE_HIT_SCALE * 2f * screenCropFraction) / zoom * srcPerDisp
    val edgeInsetPx =
        (handlePx * TRANSFORM_HANDLE_HIT_SCALE * 3f * screenCropFraction) / zoom * srcPerDisp

    val midX = (selection.left + selection.right) / 2f
    val midY = (selection.top + selection.bottom) / 2f

    fun near(point: Offset): Boolean {
        val dx = pixelTap.x - point.x
        val dy = pixelTap.y - point.y
        return dx * dx + dy * dy < cornerLeewayPx * cornerLeewayPx
    }

    fun nearMidEdge(point: Offset, inward: Offset): Boolean {
        val dx = pixelTap.x - point.x
        val dy = pixelTap.y - point.y
        val alongDist = abs(dx * inward.y - dy * inward.x)
        val inwardDist = dx * inward.x + dy * inward.y
        return alongDist <= midBarHalfPx && inwardDist >= -leewayPx && inwardDist <= leewayPx + edgeInsetPx
    }

    fun resolveHorizontalEdge(edgeY: Float): BoxHandle? {
        val withinY = abs(pixelTap.y - edgeY) <= lineLeewayPx
        val distL = pixelTap.x - selection.left
        val distR = selection.right - pixelTap.x
        val withinX =
            distL > (cornerExcludeXPx - lineLeewayPx) && distR > (cornerExcludeXPx - lineLeewayPx)
        if (!withinY || !withinX) return null
        if (pixelTap.x > midX - centerExcludeXPx && pixelTap.x < midX + centerExcludeXPx) return null
        return if (pixelTap.x < midX) BoxHandle.L else BoxHandle.R
    }

    fun resolveVerticalEdge(edgeX: Float): BoxHandle? {
        val withinX = abs(pixelTap.x - edgeX) <= lineLeewayPx
        val distT = pixelTap.y - selection.top
        val distB = selection.bottom - pixelTap.y
        val withinY =
            distT > (cornerExcludeYPx - lineLeewayPx) && distB > (cornerExcludeYPx - lineLeewayPx)
        if (!withinX || !withinY) return null
        if (pixelTap.y > midY - centerExcludeYPx && pixelTap.y < midY + centerExcludeYPx) return null
        return if (pixelTap.y < midY) BoxHandle.T else BoxHandle.B
    }

    val expanded = Rect(
        selection.left - leewayPx,
        selection.top - leewayPx,
        selection.right + leewayPx,
        selection.bottom + leewayPx
    )
    return when {
        near(selection.topLeft) -> CropDrag.Edge(BoxHandle.TL)
        near(selection.topRight) -> CropDrag.Edge(BoxHandle.TR)
        near(selection.bottomLeft) -> CropDrag.Edge(BoxHandle.BL)
        near(selection.bottomRight) -> CropDrag.Edge(BoxHandle.BR)
        nearMidEdge(Offset(midX, selection.top), Offset(0f, 1f)) -> CropDrag.Edge(BoxHandle.T)
        nearMidEdge(Offset(midX, selection.bottom), Offset(0f, -1f)) -> CropDrag.Edge(BoxHandle.B)
        nearMidEdge(Offset(selection.left, midY), Offset(1f, 0f)) -> CropDrag.Edge(BoxHandle.L)
        nearMidEdge(Offset(selection.right, midY), Offset(-1f, 0f)) -> CropDrag.Edge(BoxHandle.R)
        else -> {
            val hTop = resolveHorizontalEdge(selection.top)?.let {
                CropDrag.PendingEdge(BoxHandle.T, it)
            }
            val hBot = resolveHorizontalEdge(selection.bottom)?.let {
                CropDrag.PendingEdge(BoxHandle.B, it)
            }
            val vLeft = resolveVerticalEdge(selection.left)?.let {
                CropDrag.PendingEdge(BoxHandle.L, it)
            }
            val vRight = resolveVerticalEdge(selection.right)?.let {
                CropDrag.PendingEdge(BoxHandle.R, it)
            }
            hTop ?: hBot ?: vLeft ?: vRight
            ?: if (expanded.contains(pixelTap)) CropDrag.Move else null
        }
    }
}

data class CropResizeResult(
    val next: Rect,
    val movedRaw: Rect,
    val clampedX: Boolean,
    val clampedY: Boolean,
)

internal fun resolvePendingCropEdge(
    handle: CropDrag,
    aspectRatio: Pair<Int, Int>?,
    accum: Offset,
): CropDrag {
    if (handle is CropDrag.PendingEdge) {
        if (aspectRatio != null) return CropDrag.Edge(handle.primary)
        val dragIsAlongEdge = when (handle.primary) {
            BoxHandle.T, BoxHandle.B -> abs(accum.x) > abs(accum.y)
            else -> abs(accum.y) > abs(accum.x)
        }
        return CropDrag.Edge(if (dragIsAlongEdge) handle.alt else handle.primary)
    }
    return handle
}

internal fun resizeCropSelection(
    handle: CropDrag,
    selection: Rect,
    pixelDelta: Offset,
    aspectRatio: Pair<Int, Int>?,
    sourceRect: Rect,
    fullRect: Rect,
): CropResizeResult {
    val edgeHandle = (handle as? CropDrag.Edge)?.handle
    val movedRaw: Rect
    val next: Rect
    if (aspectRatio == null || handle is CropDrag.Move) {
        val moved = when {
            handle is CropDrag.Move -> selection.translate(pixelDelta.x, pixelDelta.y)
            edgeHandle == BoxHandle.TL -> selection.copy(
                left = selection.left + pixelDelta.x, top = selection.top + pixelDelta.y
            )

            edgeHandle == BoxHandle.TR -> selection.copy(
                right = selection.right + pixelDelta.x, top = selection.top + pixelDelta.y
            )

            edgeHandle == BoxHandle.BL -> selection.copy(
                left = selection.left + pixelDelta.x, bottom = selection.bottom + pixelDelta.y
            )

            edgeHandle == BoxHandle.BR -> selection.copy(
                right = selection.right + pixelDelta.x, bottom = selection.bottom + pixelDelta.y
            )

            edgeHandle == BoxHandle.T -> selection.copy(top = selection.top + pixelDelta.y)
            edgeHandle == BoxHandle.B -> selection.copy(bottom = selection.bottom + pixelDelta.y)
            edgeHandle == BoxHandle.L -> selection.copy(left = selection.left + pixelDelta.x)
            edgeHandle == BoxHandle.R -> selection.copy(right = selection.right + pixelDelta.x)
            else -> selection
        }
        movedRaw = moved
        next = if (aspectRatio != null) clampPixelMove(moved, sourceRect)
        else clampPixel(moved, fullRect)
    } else {
        val target = aspectRatio.first / aspectRatio.second.toFloat()
        when (edgeHandle) {
            BoxHandle.R -> {
                val w =
                    (selection.right + pixelDelta.x - selection.left).coerceAtLeast(1f)
                val raw = Rect(
                    selection.left,
                    selection.top,
                    selection.left + w,
                    selection.top + w / target
                )
                movedRaw = raw
                next = clampPixelRatio(
                    raw,
                    target,
                    anchorLeft = true,
                    anchorTop = true,
                    sourceRect = sourceRect
                )
            }

            BoxHandle.L -> {
                val w =
                    (selection.right - selection.left - pixelDelta.x).coerceAtLeast(1f)
                val raw = Rect(
                    selection.right - w, selection.top, selection.right, selection.top + w / target
                )
                movedRaw = raw
                next = clampPixelRatio(
                    raw,
                    target,
                    anchorLeft = false,
                    anchorTop = true,
                    sourceRect = sourceRect
                )
            }

            BoxHandle.B -> {
                val h =
                    (selection.bottom + pixelDelta.y - selection.top).coerceAtLeast(1f)
                val raw = Rect(
                    selection.left,
                    selection.top,
                    selection.left + h * target,
                    selection.top + h
                )
                movedRaw = raw
                next = clampPixelRatio(
                    raw,
                    target,
                    anchorLeft = true,
                    anchorTop = true,
                    sourceRect = sourceRect
                )
            }

            BoxHandle.T -> {
                val h =
                    (selection.bottom - selection.top - pixelDelta.y).coerceAtLeast(1f)
                val raw = Rect(
                    selection.left,
                    selection.bottom - h,
                    selection.left + h * target,
                    selection.bottom
                )
                movedRaw = raw
                next = clampPixelRatio(
                    raw,
                    target,
                    anchorLeft = true,
                    anchorTop = false,
                    sourceRect = sourceRect
                )
            }

            else -> {
                val diagX =
                    if (edgeHandle == BoxHandle.TL || edgeHandle == BoxHandle.BL) -target else target
                val diagY =
                    if (edgeHandle == BoxHandle.TL || edgeHandle == BoxHandle.TR) -1f else 1f
                val proj =
                    (pixelDelta.x * diagX + pixelDelta.y * diagY) / (target * target + 1f)
                val dx = proj * diagX
                val dy = proj * diagY
                val moved = when (edgeHandle) {
                    BoxHandle.TL -> selection.copy(
                        left = selection.left + dx, top = selection.top + dy
                    )

                    BoxHandle.TR -> selection.copy(
                        right = selection.right + dx, top = selection.top + dy
                    )

                    BoxHandle.BL -> selection.copy(
                        left = selection.left + dx, bottom = selection.bottom + dy
                    )

                    BoxHandle.BR -> selection.copy(
                        right = selection.right + dx, bottom = selection.bottom + dy
                    )

                    else -> selection
                }
                movedRaw = moved
                next = clampPixelRatio(
                    moved,
                    target,
                    anchorLeft = edgeHandle == BoxHandle.TR || edgeHandle == BoxHandle.BR,
                    anchorTop = edgeHandle == BoxHandle.BL || edgeHandle == BoxHandle.BR,
                    sourceRect = sourceRect,
                )
            }
        }
    }
    return CropResizeResult(
        next = next,
        movedRaw = movedRaw,
        clampedX = next.left != movedRaw.left || next.right != movedRaw.right,
        clampedY = next.top != movedRaw.top || next.bottom != movedRaw.bottom,
    )
}

fun Modifier.cropGestures(
    state: CropGestureState,
    viewport: CanvasViewport,
    aspectRatio: Pair<Int, Int>?,
    handlePx: Float,
    onCropChanged: (Rect, Rect) -> Unit,
): Modifier =
    this
        .pointerInput(aspectRatio, viewport.displayRect, viewport.zoom, viewport.sourceRect) {
            var wasClampedX = false
            var wasClampedY = false
            detectDragGestures(
                onDragStart = { offset ->
                    val pixelTap = viewport.toPixel(offset)
                    val selection = state.selection ?: return@detectDragGestures
                    state.dragAccum = Offset.Zero
                    state.dragHandle = resolveCropDragHandle(
                        pixelTap,
                        selection,
                        viewport.sourceRect,
                        viewport.displayRect,
                        viewport.zoom,
                        handlePx,
                    )
                    if (state.dragHandle != null) HapticPatterns.tap()
                },
                onDrag = { change, delta ->
                    change.consume()
                    val selection = state.selection ?: return@detectDragGestures
                    val pixelDelta = Offset(
                        delta.x / viewport.zoom * (viewport.sourceRect.width / viewport.displayRect.width),
                        delta.y / viewport.zoom * (viewport.sourceRect.height / viewport.displayRect.height)
                    )
                    val startDrag = state.dragHandle ?: return@detectDragGestures
                    if (startDrag is CropDrag.PendingEdge) {
                        if (aspectRatio == null) state.dragAccum += pixelDelta
                        state.dragHandle =
                            resolvePendingCropEdge(startDrag, aspectRatio, state.dragAccum)
                    }
                    val resolved = state.dragHandle ?: return@detectDragGestures
                    val result = resizeCropSelection(
                        resolved,
                        selection,
                        pixelDelta,
                        aspectRatio,
                        viewport.sourceRect,
                        viewport.fullRect,
                    )
                    val edgeHandle = (resolved as? CropDrag.Edge)?.handle
                    val hitClamp = when (edgeHandle) {
                        BoxHandle.L, BoxHandle.R -> result.clampedX && !wasClampedX
                        BoxHandle.T, BoxHandle.B -> result.clampedY && !wasClampedY
                        else -> (result.clampedX && !wasClampedX) || (result.clampedY && !wasClampedY)
                    }
                    if (hitClamp) HapticPatterns.tap()
                    wasClampedX = result.clampedX
                    wasClampedY = result.clampedY
                    state.selection = result.next
                },
                onDragEnd = {
                    state.selection?.let { onCropChanged(it, viewport.displayRect) }
                    state.dragHandle = null
                    state.dragAccum = Offset.Zero
                    wasClampedX = false
                    wasClampedY = false
                },
            )
        }
        .debugOverlay(
            cropRectPx = state.selection,
            handlePx = handlePx,
            zoom = viewport.zoom,
            pan = viewport.pan,
            sourceRect = viewport.sourceRect,
            displayRect = viewport.displayRect,
        )
