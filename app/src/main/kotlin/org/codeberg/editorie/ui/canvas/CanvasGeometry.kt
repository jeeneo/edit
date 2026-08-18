package org.codeberg.editorie.ui.canvas

// SPDX-License-Identifier: MIT

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin

fun computeDisplay(size: IntSize, source: Rect): Rect {
    val scale = minOf(size.width / source.width, size.height / source.height)
    val width = source.width * scale
    val height = source.height * scale
    val left = (size.width - width) / 2f
    val top = (size.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

fun applyAspect(rect: Rect, aspectRatio: Pair<Int, Int>?): Rect {
    val ratio = aspectRatio ?: return rect
    val target = ratio.first / ratio.second.toFloat()
    return if (rect.width / rect.height > target) {
        rect.copy(right = rect.left + rect.height * target)
    } else {
        rect.copy(bottom = rect.top + rect.width / target)
    }
}

fun clampPixel(rect: Rect, fullRect: Rect): Rect {
    val left = rect.left.coerceIn(fullRect.left, fullRect.right - 1f)
    val top = rect.top.coerceIn(fullRect.top, fullRect.bottom - 1f)
    val right = rect.right.coerceIn(left + 1f, fullRect.right)
    val bottom = rect.bottom.coerceIn(top + 1f, fullRect.bottom)
    return Rect(left, top, right, bottom)
}

fun clampPixelMove(r: Rect, sourceRect: Rect): Rect {
    val w = r.width
    val h = r.height
    val maxL = sourceRect.left + (sourceRect.width - w).coerceAtLeast(0f)
    val maxT = sourceRect.top + (sourceRect.height - h).coerceAtLeast(0f)
    val l = r.left.coerceIn(sourceRect.left, maxL)
    val t = r.top.coerceIn(sourceRect.top, maxT)
    return Rect(l, t, l + w, t + h)
}

fun clampPixelRatio(
    r: Rect, target: Float, anchorLeft: Boolean, anchorTop: Boolean, sourceRect: Rect
): Rect {
    val sw = sourceRect.width
    val sh = sourceRect.height
    val w = minOf(r.width, sw, sh * target).coerceAtLeast(1f)
    val h = w / target
    val l = if (anchorLeft) r.left else r.right - w
    val t = if (anchorTop) r.top else r.bottom - h
    val maxL = sourceRect.left + (sw - w).coerceAtLeast(0f)
    val maxT = sourceRect.top + (sh - h).coerceAtLeast(0f)
    val cl = l.coerceIn(sourceRect.left, maxL)
    val ct = t.coerceIn(sourceRect.top, maxT)
    return Rect(cl, ct, cl + w, ct + h)
}

fun rotateOffset(offset: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = cos(rad)
    val sin = sin(rad)
    return Offset(
        (offset.x * cos - offset.y * sin).toFloat(), (offset.x * sin + offset.y * cos).toFloat()
    )
}

fun screenToLocal(
    screenPoint: Offset, center: Offset, boxW: Float, boxH: Float, rotationDeg: Float
): Offset {
    val dx = screenPoint.x - center.x
    val dy = screenPoint.y - center.y
    val rotated = rotateOffset(Offset(dx, dy), -rotationDeg)
    return Offset(rotated.x + boxW / 2f, rotated.y + boxH / 2f)
}
