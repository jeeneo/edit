@file:Suppress("KotlinConstantConditions")

package org.codeberg.editorie.ui.canvas

// SPDX-License-Identifier: MIT
// co-created with deepseek-v4-flash

import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import org.codeberg.editorie.BuildConfig
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.HapticPatterns
import kotlin.math.abs
import kotlin.math.atan2
import android.graphics.Canvas as AndroidCanvas

const val TRANSFORM_ROTATION_HANDLE = 30f
const val TRANSFORM_HANDLE_HIT_SCALE = 1.5f
const val TRANSFORM_CORNER_HIT_SCALE = 2.8f
const val TRANSFORM_BORDER_STROKE_SCALE = 2f
const val TRANSFORM_CORNER_ARM_SCALE = 2.5f
const val TRANSFORM_HANDLE_THICKNESS_SCALE = 24f

enum class BoxHandle { TL, TR, BL, BR, T, B, L, R, ROT }

data class ScreenBox(val topLeft: Offset, val width: Float, val height: Float) {
    val center: Offset get() = Offset(topLeft.x + width / 2f, topLeft.y + height / 2f)
}

data class BoxGeom(
    val positionPx: Offset,
    val widthPx: Float,
    val heightPx: Float,
    val rotation: Float,
)

data class TransformBoxColors(
    val handleColor: Int,
    val gridLines: Int,
    val scrim: Int,
)

sealed interface CropDrag {
    data class Edge(val handle: BoxHandle) : CropDrag
    data object Move : CropDrag
    data class PendingEdge(val primary: BoxHandle, val alt: BoxHandle) : CropDrag
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lenSq = ab.x * ab.x + ab.y * ab.y
    if (lenSq < 0.001f) return (p - a).getDistance()
    val t = (((p - a).x * ab.x + (p - a).y * ab.y) / lenSq).coerceIn(0f, 1f)
    return (p - (a + Offset(ab.x * t, ab.y * t))).getDistance()
}

fun transformBoxHandleHit(
    localTap: Offset,
    boxWidthPx: Float,
    boxHeightPx: Float,
    hitRadiusPx: Float,
    rotationHandleOffsetPx: Float,
    showRotationHandle: Boolean = true,
    edgeInsetPx: Float = 0f
): BoxHandle? {
    val midX = boxWidthPx / 2f
    val midY = boxHeightPx / 2f
    val baseR = hitRadiusPx / 2f
    val armLenX = (baseR * TRANSFORM_CORNER_ARM_SCALE).coerceAtMost(midX)
    val armLenY = (baseR * TRANSFORM_CORNER_ARM_SCALE).coerceAtMost(midY)
    val barHalf = hitRadiusPx * 2f
    fun near(point: Offset) = (localTap - point).getDistance() < hitRadiusPx
    fun nearSeg(a: Offset, b: Offset) = distanceToSegment(localTap, a, b) < hitRadiusPx

    fun nearEdge(
        segA: Offset, segB: Offset, inwardAxis: Offset
    ): Boolean {
        val segMid = Offset((segA.x + segB.x) / 2f, (segA.y + segB.y) / 2f)
        val segHalfLen = (segB - segA).getDistance() / 2f
        val toTap = localTap - segMid
        val alongDist = abs(toTap.x * inwardAxis.y - toTap.y * inwardAxis.x)
        val inwardDist = toTap.x * inwardAxis.x + toTap.y * inwardAxis.y
        return alongDist <= segHalfLen && inwardDist >= -hitRadiusPx && inwardDist <= hitRadiusPx + edgeInsetPx
    }

    if (showRotationHandle && near(Offset(midX, -rotationHandleOffsetPx))) return BoxHandle.ROT
    if (nearSeg(Offset(0f, 0f), Offset(armLenX, 0f)) || nearSeg(
            Offset(0f, 0f), Offset(0f, armLenY)
        )
    ) return BoxHandle.TL
    if (nearSeg(Offset(boxWidthPx, 0f), Offset(boxWidthPx - armLenX, 0f)) || nearSeg(
            Offset(boxWidthPx, 0f), Offset(boxWidthPx, armLenY)
        )
    ) return BoxHandle.TR
    if (nearSeg(Offset(0f, boxHeightPx), Offset(armLenX, boxHeightPx)) || nearSeg(
            Offset(0f, boxHeightPx), Offset(0f, boxHeightPx - armLenY)
        )
    ) return BoxHandle.BL
    if (nearSeg(
            Offset(boxWidthPx, boxHeightPx), Offset(boxWidthPx - armLenX, boxHeightPx)
        ) || nearSeg(Offset(boxWidthPx, boxHeightPx), Offset(boxWidthPx, boxHeightPx - armLenY))
    ) return BoxHandle.BR
    if (midX > barHalf) {
        if (nearEdge(
                Offset(midX - barHalf, 0f), Offset(midX + barHalf, 0f), Offset(0f, 1f)
            )
        ) return BoxHandle.T
        if (nearEdge(
                Offset(midX - barHalf, boxHeightPx),
                Offset(midX + barHalf, boxHeightPx),
                Offset(0f, -1f)
            )
        ) return BoxHandle.B
    }
    if (midY > barHalf) {
        if (nearEdge(
                Offset(0f, midY - barHalf), Offset(0f, midY + barHalf), Offset(1f, 0f)
            )
        ) return BoxHandle.L
        if (nearEdge(
                Offset(boxWidthPx, midY - barHalf),
                Offset(boxWidthPx, midY + barHalf),
                Offset(-1f, 0f)
            )
        ) return BoxHandle.R
    }
    return null
}

fun transformBoxAnchorLocal(handle: BoxHandle, widthPx: Float, heightPx: Float): Offset =
    when (handle) {
        BoxHandle.R -> Offset(0f, heightPx / 2f)
        BoxHandle.L -> Offset(widthPx, heightPx / 2f)
        BoxHandle.B -> Offset(widthPx / 2f, 0f)
        BoxHandle.T -> Offset(widthPx / 2f, heightPx)
        BoxHandle.BR -> Offset(0f, 0f)
        BoxHandle.BL -> Offset(widthPx, 0f)
        BoxHandle.TR -> Offset(0f, heightPx)
        BoxHandle.TL -> Offset(widthPx, heightPx)
        else -> Offset(widthPx / 2f, heightPx / 2f)
    }

private class TransformHandlePaints {
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    val handleBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun configure(colors: TransformBoxColors, zoom: Float) {
        border.color = colors.handleColor
        border.strokeWidth = TRANSFORM_BORDER_STROKE_SCALE / zoom
        border.pathEffect = DashPathEffect(floatArrayOf(8f / zoom, 4f / zoom), 0f)
        line.color = colors.handleColor
        line.strokeWidth = TRANSFORM_BORDER_STROKE_SCALE / zoom
        line.strokeCap = Paint.Cap.ROUND
        fill.color = colors.handleColor
        handleBody.color = colors.handleColor
        handleBody.strokeWidth = TRANSFORM_HANDLE_THICKNESS_SCALE / zoom
        handleBody.strokeCap = Paint.Cap.ROUND
    }
}

fun drawTransformHandles(
    nativeCanvas: AndroidCanvas,
    widthPx: Float,
    heightPx: Float,
    zoom: Float,
    handlePx: Float,
    rotationHandleOffsetPx: Float,
    colors: TransformBoxColors,
    showRotationHandle: Boolean = true,
    mode: EditorPanel? = null
) {
    val midX = widthPx / 2f
    val midY = heightPx / 2f
    val baseR = handlePx / (2f * zoom)
    val armLenX = (baseR * TRANSFORM_CORNER_ARM_SCALE).coerceAtMost(midX)
    val armLenY = (baseR * TRANSFORM_CORNER_ARM_SCALE).coerceAtMost(midY)
    val barHalf = TRANSFORM_HANDLE_THICKNESS_SCALE / zoom
    val paints = TransformHandlePaints()
    paints.configure(colors, zoom)
    if (mode != EditorPanel.Transform) {
        nativeCanvas.drawRoundRect(
            0f, 0f, widthPx, heightPx, 4f / zoom, 4f / zoom, paints.border
        )
    }
    if (showRotationHandle) {
        nativeCanvas.drawLine(midX, 0f, midX, -rotationHandleOffsetPx, paints.line)
        nativeCanvas.drawCircle(midX, -rotationHandleOffsetPx, baseR, paints.fill)
    }
    val corners = listOf(
        Triple(Offset(0f, 0f), Offset(armLenX, 0f), Offset(0f, armLenY)),
        Triple(Offset(widthPx, 0f), Offset(widthPx - armLenX, 0f), Offset(widthPx, armLenY)),
        Triple(Offset(0f, heightPx), Offset(armLenX, heightPx), Offset(0f, heightPx - armLenY)),
        Triple(
            Offset(widthPx, heightPx),
            Offset(widthPx - armLenX, heightPx),
            Offset(widthPx, heightPx - armLenY)
        )
    )
    val edges = mutableListOf<Pair<Offset, Offset>>()
    if (midX > barHalf) {
        edges.add(Offset(midX - barHalf, 0f) to Offset(midX + barHalf, 0f))
        edges.add(Offset(midX - barHalf, heightPx) to Offset(midX + barHalf, heightPx))
    }
    if (midY > barHalf) {
        edges.add(Offset(0f, midY - barHalf) to Offset(0f, midY + barHalf))
        edges.add(Offset(widthPx, midY - barHalf) to Offset(widthPx, midY + barHalf))
    }
    for ((corner, arm1, arm2) in corners) {
        nativeCanvas.drawLine(corner.x, corner.y, arm1.x, arm1.y, paints.handleBody)
        nativeCanvas.drawLine(corner.x, corner.y, arm2.x, arm2.y, paints.handleBody)
    }
    for ((start, end) in edges) {
        nativeCanvas.drawLine(start.x, start.y, end.x, end.y, paints.handleBody)
    }
}

suspend inline fun AwaitPointerEventScope.transformBoxRotationDrag(
    downId: PointerId,
    downContent: Offset,
    center: Offset,
    initialRotation: Float,
    toContent: (Offset) -> Offset,
    snapRotation: (Float) -> Float,
    crossinline onRotationChanged: (Float) -> Unit
) {
    val initialAngle = atan2(downContent.y - center.y, downContent.x - center.x)
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == downId } ?: break
        if (!change.pressed) break
        change.consume()
        val currentContent = toContent(change.position)
        val currentAngle = atan2(currentContent.y - center.y, currentContent.x - center.x)
        val deltaAngle = Math.toDegrees((currentAngle - initialAngle).toDouble()).toFloat()
        onRotationChanged(snapRotation(initialRotation + deltaAngle))
    }
}

suspend inline fun AwaitPointerEventScope.transformBoxResizeDrag(
    downId: PointerId,
    downPosition: Offset,
    hitHandle: BoxHandle,
    zoom: Float,
    sourceRect: Rect,
    displayRect: Rect,
    rotateOffset: (Offset, Float) -> Offset,
    minWidthPx: Float,
    minHeightPx: Float,
    provide: () -> BoxGeom?,
    crossinline onChange: (positionPx: Offset, widthPx: Float, heightPx: Float, rotation: Float) -> Unit
) {
    val initial = provide() ?: return
    val oldW = initial.widthPx
    val oldH = initial.heightPx
    val oldCenterPx = initial.positionPx + Offset(oldW / 2f, oldH / 2f)
    val anchorLocalOld = transformBoxAnchorLocal(hitHandle, oldW, oldH)
    val anchorPx = oldCenterPx + rotateOffset(
        anchorLocalOld - Offset(oldW / 2f, oldH / 2f), initial.rotation
    )
    var lastPos = downPosition
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == downId } ?: break
        if (!change.pressed) break
        val delta = change.position - lastPos
        lastPos = change.position
        change.consume()

        val current = provide() ?: break
        val rotation = current.rotation
        val localDelta = rotateOffset(Offset(delta.x / zoom, delta.y / zoom), -rotation)
        val pixelScaleX = sourceRect.width / displayRect.width
        val pixelScaleY = sourceRect.height / displayRect.height
        val localPixelDelta = Offset(localDelta.x * pixelScaleX, localDelta.y * pixelScaleY)
        var dW = 0f
        var dH = 0f
        when (hitHandle) {
            BoxHandle.R, BoxHandle.TR, BoxHandle.BR -> dW = localPixelDelta.x
            BoxHandle.L, BoxHandle.TL, BoxHandle.BL -> dW = -localPixelDelta.x
            else -> {}
        }
        when (hitHandle) {
            BoxHandle.B, BoxHandle.BL, BoxHandle.BR -> dH = localPixelDelta.y
            BoxHandle.T, BoxHandle.TL, BoxHandle.TR -> dH = -localPixelDelta.y
            else -> {}
        }
        val newW = (current.widthPx + dW).coerceAtLeast(minWidthPx)
        val newH = (current.heightPx + dH).coerceAtLeast(minHeightPx)
        val anchorRelNew =
            transformBoxAnchorLocal(hitHandle, newW, newH) - Offset(newW / 2f, newH / 2f)
        val newCenterPx = anchorPx - rotateOffset(anchorRelNew, rotation)
        onChange(newCenterPx - Offset(newW / 2f, newH / 2f), newW, newH, rotation)
    }
}

class OverlayGestureSpec<T>(
    val items: () -> List<T>,
    val selectedId: () -> Long?,
    val idOf: (T) -> Long,
    val rotationOf: (T) -> Float,
    val screenBox: (T) -> ScreenBox,
    val resizeGeom: (T) -> BoxGeom,
    val minWidthPx: Float,
    val minHeightPx: Float,
    val gestureInsetPx: Float = 0f,
    val onSelect: (Long?) -> Unit,
    val onTap: (T) -> Unit = {},
    val onMove: (T, Offset) -> Unit,
    val onResize: (handle: BoxHandle, id: Long, positionPx: Offset, widthPx: Float, heightPx: Float, rotation: Float) -> Unit,
    val onRotate: (T, Float) -> Unit,
)

private suspend fun AwaitPointerEventScope.awaitSecondFingerOrUp(downId: PointerId): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.any { it.id != downId }) return true
        if (pressed.none { it.id == downId }) return false
    }
}

suspend fun <T> PointerInputScope.detectOverlayGestures(
    spec: OverlayGestureSpec<T>,
    zoom: Float,
    handlePx: Float,
    sourceRect: Rect,
    displayRect: Rect,
    toContent: (Offset) -> Offset,
    screenToLocal: (Offset, Offset, Float, Float, Float) -> Offset,
    rotateOffset: (Offset, Float) -> Offset,
    snapRotation: (Float) -> Float,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (spec.gestureInsetPx > 0f) {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            if (down.position.x < spec.gestureInsetPx || down.position.x > w - spec.gestureInsetPx || down.position.y > h - spec.gestureInsetPx) {
                return@awaitEachGesture
            }
        }
        val downContent = toContent(down.position)
        down.consume()

        val selectedId = spec.selectedId()
        val selected = selectedId?.let { id -> spec.items().firstOrNull { spec.idOf(it) == id } }

        var hitHandle: BoxHandle? = null
        if (selected != null) {
            val box = spec.screenBox(selected)
            val hitR = handlePx * TRANSFORM_HANDLE_HIT_SCALE / zoom
            val rotHandleOffset = TRANSFORM_ROTATION_HANDLE.dp.toPx() / zoom
            val localTap = screenToLocal(
                downContent, box.center, box.width, box.height, spec.rotationOf(selected)
            )
            hitHandle = transformBoxHandleHit(
                localTap = localTap,
                boxWidthPx = box.width,
                boxHeightPx = box.height,
                hitRadiusPx = hitR,
                rotationHandleOffsetPx = rotHandleOffset
            )
            if (hitHandle != null) HapticPatterns.tap()
        }

        if (hitHandle != null && selected != null) {
            val id = spec.idOf(selected)
            if (hitHandle == BoxHandle.ROT) {
                val center = spec.screenBox(selected).center
                val ov = spec.items().firstOrNull { spec.idOf(it) == id } ?: return@awaitEachGesture
                transformBoxRotationDrag(
                    downId = down.id,
                    downContent = downContent,
                    center = center,
                    initialRotation = spec.rotationOf(ov),
                    toContent = toContent,
                    snapRotation = snapRotation
                ) { rot -> spec.onRotate(ov, rot) }
            } else {
                val handle = hitHandle
                transformBoxResizeDrag(
                    downId = down.id,
                    downPosition = down.position,
                    hitHandle = handle,
                    zoom = zoom,
                    sourceRect = sourceRect,
                    displayRect = displayRect,
                    rotateOffset = rotateOffset,
                    minWidthPx = spec.minWidthPx,
                    minHeightPx = spec.minHeightPx,
                    provide = {
                        spec.items().firstOrNull { spec.idOf(it) == id }
                            ?.let { spec.resizeGeom(it) }
                    }) { pos, w, h, rot -> spec.onResize(handle, id, pos, w, h, rot) }
            }
        } else {
            val hit = spec.items().lastOrNull { item ->
                val box = spec.screenBox(item)
                val local = screenToLocal(
                    downContent, box.center, box.width, box.height, spec.rotationOf(item)
                )
                local.x in 0f..box.width && local.y >= 0f && local.y <= box.height
            }
            if (hit != null) {
                spec.onSelect(spec.idOf(hit))
                spec.onTap(hit)
                var lastPos: Offset
                var didDrag = false
                val drag = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume(); didDrag = true
                }
                if (drag != null && didDrag) {
                    lastPos = drag.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.position - lastPos
                        lastPos = change.position
                        change.consume()
                        val deltaPx = Offset(
                            delta.x / zoom * (sourceRect.width / displayRect.width),
                            delta.y / zoom * (sourceRect.height / displayRect.height)
                        )
                        spec.onMove(hit, deltaPx)
                    }
                } else {
                    spec.onTap(hit)
                }
            } else {
                if (awaitSecondFingerOrUp(down.id)) {
                    return@awaitEachGesture
                }
                spec.onSelect(null)
            }
        }
    }
}

private data class PEdgeBounds(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val leewayX: Float,
    val leewayY: Float,
    val cornerExcludeX: Float,
    val cornerExcludeY: Float,
    val centerExcludeX: Float,
    val centerExcludeY: Float,
)

private fun calculatePEdgeBounds(
    selection: Rect,
    handlePx: Float,
    zoom: Float,
    sourceRect: Rect,
    displayRect: Rect,
): PEdgeBounds {
    val pixelToContentX = displayRect.width / sourceRect.width
    val pixelToContentY = displayRect.height / sourceRect.height
    val srcPerDisp = sourceRect.width / displayRect.width

    val lineLeewayPx =
        (handlePx / zoom) * srcPerDisp + selection.width * 0.015f + selection.height * 0.015f + minOf(
            selection.width / 2f, selection.height / 2f
        ) * 0.015f

    val screenCropFraction = minOf(
        selection.width / sourceRect.width, selection.height / sourceRect.height
    ).coerceIn(0.1f, 1f)

    val cornerHitRContent = handlePx * TRANSFORM_CORNER_HIT_SCALE / zoom
    val midBarHalfContent = handlePx * TRANSFORM_HANDLE_HIT_SCALE * 2f * screenCropFraction / zoom

    return PEdgeBounds(
        left = (selection.left - sourceRect.left) * pixelToContentX + displayRect.left,
        right = (selection.right - sourceRect.left) * pixelToContentX + displayRect.left,
        top = (selection.top - sourceRect.top) * pixelToContentY + displayRect.top,
        bottom = (selection.bottom - sourceRect.top) * pixelToContentY + displayRect.top,
        leewayX = lineLeewayPx * pixelToContentX,
        leewayY = lineLeewayPx * pixelToContentY,
        cornerExcludeX = cornerHitRContent,
        cornerExcludeY = cornerHitRContent,
        centerExcludeX = midBarHalfContent,
        centerExcludeY = midBarHalfContent,
    )
}

fun Modifier.debugOverlay(
    cropRectPx: Rect?,
    handlePx: Float,
    zoom: Float,
    pan: Offset,
    sourceRect: Rect,
    displayRect: Rect,
): Modifier = this.drawWithContent {
    drawContent()
    // hide unless really needed
    @Suppress("SimplifyBooleanWithConstants")
    if (BuildConfig.DEBUG && false) {
        val selection = cropRectPx ?: return@drawWithContent
        val bounds = calculatePEdgeBounds(selection, handlePx, zoom, sourceRect, displayRect)
        val pedgeColor = Color.Magenta.copy(alpha = 0.35f)

        val l = bounds.left * zoom + pan.x
        val r = bounds.right * zoom + pan.x
        val t = bounds.top * zoom + pan.y
        val b = bounds.bottom * zoom + pan.y
        val midxD = (l + r) / 2f
        val midyD = (t + b) / 2f
        val lx = bounds.leewayX * zoom
        val ly = bounds.leewayY * zoom
        val cx = bounds.cornerExcludeX * zoom
        val cy = bounds.cornerExcludeY * zoom
        val ex = bounds.centerExcludeX * zoom
        val ey = bounds.centerExcludeY * zoom

        val topY = t - ly
        val topH = ly * 2
        val t1L = l + cx
        val t1R = midxD - ex
        val t2L = midxD + ex
        val t2R = r - cx
        if (t1R > t1L) drawRect(
            pedgeColor, Offset(t1L, topY), Size(t1R - t1L, topH)
        )
        if (t2R > t2L) drawRect(
            pedgeColor, Offset(t2L, topY), Size(t2R - t2L, topH)
        )

        val botY = b - ly
        val b1L = l + cx
        val b1R = midxD - ex
        val b2L = midxD + ex
        val b2R = r - cx
        if (b1R > b1L) drawRect(
            pedgeColor, Offset(b1L, botY), Size(b1R - b1L, topH)
        )
        if (b2R > b2L) drawRect(
            pedgeColor, Offset(b2L, botY), Size(b2R - b2L, topH)
        )

        val leftX = l - lx
        val leftW = lx * 2
        val l1T = t + cy
        val l1B = midyD - ey
        val l2T = midyD + ey
        val l2B = b - cy
        if (l1B > l1T) drawRect(
            pedgeColor, Offset(leftX, l1T), Size(leftW, l1B - l1T)
        )
        if (l2B > l2T) drawRect(
            pedgeColor, Offset(leftX, l2T), Size(leftW, l2B - l2T)
        )

        val rightX = r - lx
        val r1T = t + cy
        val r1B = midyD - ey
        val r2T = midyD + ey
        val r2B = b - cy
        if (r1B > r1T) drawRect(
            pedgeColor, Offset(rightX, r1T), Size(leftW, r1B - r1T)
        )
        if (r2B > r2T) drawRect(
            pedgeColor, Offset(rightX, r2T), Size(leftW, r2B - r2T)
        )
        val screenCropFraction = minOf(
            selection.width / sourceRect.width, selection.height / sourceRect.height
        ).coerceIn(0.1f, 1f)
        val currentEdgeInset = handlePx * TRANSFORM_HANDLE_HIT_SCALE * 3f * screenCropFraction
        val currentMidBarHalf = handlePx * TRANSFORM_HANDLE_HIT_SCALE * 2f * screenCropFraction
        val hitR = handlePx * TRANSFORM_HANDLE_HIT_SCALE
        val midHandleColor = Color.Cyan.copy(alpha = 0.4f)
        drawRect(
            midHandleColor,
            Offset(midxD - currentMidBarHalf, t - hitR),
            Size(currentMidBarHalf * 2, hitR * 2 + currentEdgeInset)
        )
        drawRect(
            midHandleColor,
            Offset(midxD - currentMidBarHalf, b - hitR - currentEdgeInset),
            Size(currentMidBarHalf * 2, hitR * 2 + currentEdgeInset)
        )
        drawRect(
            midHandleColor,
            Offset(l - hitR, midyD - currentMidBarHalf),
            Size(hitR * 2 + currentEdgeInset, currentMidBarHalf * 2)
        )
        drawRect(
            midHandleColor,
            Offset(r - hitR - currentEdgeInset, midyD - currentMidBarHalf),
            Size(hitR * 2 + currentEdgeInset, currentMidBarHalf * 2)
        )

        val cornerColor = Color.Yellow.copy(alpha = 0.35f)
        val cornerHitR = handlePx * TRANSFORM_CORNER_HIT_SCALE
        val corners = listOf(
            Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)
        )
        for (pos in corners) {
            drawRect(
                cornerColor,
                Offset(pos.x - cornerHitR, pos.y - cornerHitR),
                Size(cornerHitR * 2, cornerHitR * 2)
            )
        }
    }
}

fun PointerEvent.pointerPan(): Offset {
    val active = changes.filter { it.pressed }
    if (active.size < 2) return Offset.Zero
    val current =
        active.fold(Offset.Zero) { total, change -> total + change.position } / active.size.toFloat()
    val previous =
        active.fold(Offset.Zero) { total, change -> total + change.previousPosition } / active.size.toFloat()
    return current - previous
}

fun PointerEvent.pointerZoom(): Float {
    val active = changes.filter { it.pressed }
    if (active.size < 2) return 1f
    val currentCenter =
        active.fold(Offset.Zero) { total, change -> total + change.position } / active.size.toFloat()
    val previousCenter =
        active.fold(Offset.Zero) { total, change -> total + change.previousPosition } / active.size.toFloat()
    val currentDistance = active.sumOf { (it.position - currentCenter).getDistance().toDouble() }
        .toFloat() / active.size
    val previousDistance =
        active.sumOf { (it.previousPosition - previousCenter).getDistance().toDouble() }
            .toFloat() / active.size
    return if (previousDistance == 0f) 1f else currentDistance / previousDistance
}
