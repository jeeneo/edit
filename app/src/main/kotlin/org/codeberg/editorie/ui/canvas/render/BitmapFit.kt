package org.codeberg.editorie.ui.canvas.render

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.codeberg.editorie.options.transform.TransformOps.scaleBitmap
import kotlin.math.sqrt

const val MAX_DRAW_BITMAP_BYTES = 96_000_000L

fun downscaleFactor(effW: Int, effH: Int, maxBytes: Long): Float {
    val bytes = effW.toLong() * effH * 4
    if (bytes <= maxBytes) return 1f
    return sqrt(maxBytes.toFloat() / bytes)
}

fun fitToByteBudget(
    bitmap: Bitmap,
    rotationDeg: Float,
    maxBytes: Long = MAX_DRAW_BITMAP_BYTES,
): Pair<Bitmap, Float> {
    val effW = if (rotationDeg % 180f != 0f) bitmap.height else bitmap.width
    val effH = if (rotationDeg % 180f != 0f) bitmap.width else bitmap.height
    val scale = downscaleFactor(effW, effH, maxBytes)
    if (scale == 1f) return Pair(bitmap, 1f)
    val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Pair(scaleBitmap(bitmap, w, h), scale)
}

@Composable
fun rememberDrawBitmap(
    displayBitmap: Bitmap,
    rotationDeg: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
): Pair<Bitmap, Float> =
    remember(displayBitmap, rotationDeg, flipHorizontal, flipVertical) {
        fitToByteBudget(displayBitmap, rotationDeg)
    }
