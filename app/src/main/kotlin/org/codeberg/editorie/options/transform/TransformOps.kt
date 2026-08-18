package org.codeberg.editorie.options.transform

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.options.drawing.translated
import org.codeberg.editorie.options.save.scaleForResize
import kotlin.math.roundToInt

object TransformOps {
    private const val TAG = "TransformOps"

    data class RotateResult(
        val bitmap: Bitmap,
        val trimOffsetX: Int = 0,
        val trimOffsetY: Int = 0,
    )

    data class TrimResult(val bitmap: Bitmap, val offsetX: Int, val offsetY: Int)
    data class CropResult(val bitmap: Bitmap)
    data class ResizeResult(
        val bitmap: Bitmap,
        val layers: List<EditorLayer>,
    )

    fun rotate(
        source: Bitmap, rotation: Float, trim: Boolean = false,
        jpegBytes: ByteArray? = null,
    ): RotateResult {
        Log.d(TAG, "Called with rotation=$rotation, jpegBytes=${jpegBytes != null}")
        if (rotation % 360f == 0f) return RotateResult(source)
        if (jpegBytes != null && rotation % 90f == 0f) {
            Log.d(TAG, "Attempting lossless JPEG rotation: $rotation degrees")
            val quarterTurns = ((rotation / 90f).roundToInt() % 4 + 4) % 4
            val lossless = JPEGLosslessTransform.rotate90Lossless(jpegBytes, quarterTurns, trim)
            if (lossless != null) {
                val bitmap = BitmapFactory.decodeByteArray(lossless.bytes, 0, lossless.bytes.size)
                Log.d(TAG, "Lossless JPEG rotation successful: ${lossless.bytes.size} bytes")
                if (bitmap != null) return RotateResult(bitmap, lossless.x, lossless.y)
            }
        }
        Log.d(TAG, "Performing standard bitmap rotation: $rotation degrees")
        val rotated = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, Matrix().apply { postRotate(rotation) }, true
        )
        if (!trim) return RotateResult(rotated)
        val trimmed = trimTransparentEdges(rotated)
        if (trimmed.bitmap !== rotated) rotated.recycle()
        return RotateResult(trimmed.bitmap, trimmed.offsetX, trimmed.offsetY)
    }

    fun flip(source: Bitmap, flipH: Boolean, flipV: Boolean): Bitmap {
        if (!flipH && !flipV) return source
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, Matrix().apply {
                postScale(
                    if (flipH) -1f else 1f,
                    if (flipV) -1f else 1f,
                    source.width / 2f,
                    source.height / 2f,
                )
            }, true
        )
    }

    fun crop(source: Bitmap, cropRect: Rect?): CropResult {
        if (cropRect == null) return CropResult(source)
        val l = cropRect.left.toInt().coerceIn(0, source.width - 1)
        val t = cropRect.top.toInt().coerceIn(0, source.height - 1)
        val r = cropRect.right.toInt().coerceIn(l + 1, source.width)
        val b = cropRect.bottom.toInt().coerceIn(t + 1, source.height)
        val cropped = Bitmap.createBitmap(source, l, t, r - l, b - t)
        return CropResult(cropped)
    }

    fun bitmapResize(
        source: Bitmap,
        newWidth: Int,
        newHeight: Int,
        layers: List<EditorLayer>,
        originalWidth: Int,
        originalHeight: Int,
    ): ResizeResult {
        val scaleX = newWidth.toFloat() / originalWidth
        val scaleY = newHeight.toFloat() / originalHeight
        val scaledBitmap = scaleBitmap(source, newWidth, newHeight)
        val scaledLayers = layers.scaleForResize(scaleX, scaleY)
        return ResizeResult(scaledBitmap, scaledLayers)
    }

    fun scaleBitmap(src: Bitmap, w: Int, h: Int): Bitmap {
        fun drawScaled(bmp: Bitmap, dw: Int, dh: Int): Bitmap {
            val dst = createBitmap(dw, dh, bmp.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(dst)
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            canvas.drawBitmap(
                bmp,
                android.graphics.Rect(0, 0, bmp.width, bmp.height),
                android.graphics.Rect(0, 0, dw, dh),
                paint
            )
            return dst
        }

        var current = src
        while (current.width > w * 2 || current.height > h * 2) {
            val halfW = (current.width / 2).coerceAtLeast(w)
            val halfH = (current.height / 2).coerceAtLeast(h)
            val next = drawScaled(current, halfW, halfH)
            if (current !== src) current.recycle()
            current = next
        }
        return drawScaled(current, w, h).also {
            if (current !== src) current.recycle()
        }
    }

    fun trimTransparentEdges(src: Bitmap): TrimResult {
        val w = src.width
        val h = src.height
        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (android.graphics.Color.alpha(pixels[y * w + x]) > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (minX > maxX || minY > maxY) return TrimResult(src, 0, 0)
        if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) return TrimResult(
            src, 0, 0
        )
        val out = Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
        return TrimResult(out, minX, minY)
    }

    fun canvasResize(
        source: Bitmap,
        newWidth: Int,
        newHeight: Int,
        layers: List<EditorLayer>,
        anchorX: Float,
        anchorY: Float,
        fillColor: Color,
    ): ResizeResult {
        val newBitmap = createBitmap(newWidth, newHeight)
        val canvas = Canvas(newBitmap)
        if (fillColor.alpha > 0f) {
            canvas.drawColor(fillColor.toArgb())
        }
        val offsetX = ((newWidth - source.width) * anchorX).roundToInt()
        val offsetY = ((newHeight - source.height) * anchorY).roundToInt()
        canvas.drawBitmap(source, offsetX.toFloat(), offsetY.toFloat(), null)
        val movedLayers = if (offsetX != 0 || offsetY != 0) {
            layers.map { layer ->
                val dx = offsetX.toFloat()
                val dy = offsetY.toFloat()
                when (layer) {
                    is EditorLayer.Stroke -> layer.copy(
                        stroke = layer.stroke.copy(
                            points = layer.stroke.points.map { it + Offset(dx, dy) })
                    )

                    is EditorLayer.EraseStroke -> layer.copy(path = layer.path.translated(dx, dy))

                    is EditorLayer.Text -> layer.copy(
                        overlay = layer.overlay.copy(
                            positionPx = layer.overlay.positionPx + Offset(dx, dy)
                        )
                    )

                    is EditorLayer.Sticker -> layer.copy(
                        overlay = layer.overlay.copy(
                            positionPx = layer.overlay.positionPx + Offset(dx, dy)
                        )
                    )

                    is EditorLayer.Background -> layer
                }
            }
        } else layers
        return ResizeResult(newBitmap, movedLayers)
    }
}
