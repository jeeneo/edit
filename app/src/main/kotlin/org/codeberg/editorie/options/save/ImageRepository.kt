package org.codeberg.editorie.options.save

// SPDX-License-Identifier: MIT

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.data.EditorState
import org.codeberg.editorie.data.ImportedImage
import org.codeberg.editorie.data.LosslessChain
import org.codeberg.editorie.data.LosslessOp
import org.codeberg.editorie.options.adjust.AdjustOps.toMatrixValues
import org.codeberg.editorie.options.adjust.AdjustState
import org.codeberg.editorie.options.drawing.DrawnStroke
import org.codeberg.editorie.options.drawing.scaled
import org.codeberg.editorie.options.drawing.translated
import org.codeberg.editorie.options.stickers.StickerBitmapCache
import org.codeberg.editorie.options.stickers.StickerOverlay
import org.codeberg.editorie.options.text.TextOverlay
import org.codeberg.editorie.options.text.makeStaticLayout
import org.codeberg.editorie.options.text.toTextPaint
import org.codeberg.editorie.options.transform.JPEGLosslessTransform
import org.codeberg.editorie.options.transform.TransformOps
import org.codeberg.editorie.util.AppToasts
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import android.graphics.Canvas as AndroidCanvas

class ImageRepository(private val context: Context) {
    private fun runLosslessChain(origBytes: ByteArray, chain: LosslessChain): ByteArray {
        Log.d(
            "runLosslessChain",
            "exifRotation=${chain.exifRotation}, opCount=${chain.ops.size}, inputSize=${origBytes.size}"
        )
        var bytes = if (chain.exifRotation != 0) {
            JPEGLosslessTransform.rotate90Lossless(
                origBytes, chain.exifRotation, trim = false
            )?.bytes ?: origBytes
        } else origBytes
        Log.d("runLosslessChain", "applied exif pre-rotate, size=${bytes.size} bytes")

        chain.ops.forEachIndexed { index, op ->
            Log.d(
                "runLosslessChain", "applying op[$index]=$op, inputSize=${bytes.size}"
            )
            val before = bytes
            bytes = when (op) {
                is LosslessOp.Rotate -> {
                    val result = JPEGLosslessTransform.rotate90Lossless(
                        bytes, op.quarterTurns, trim = true
                    )
                    if (result == null) {
                        Log.d(
                            "runLosslessChain",
                            "rotate failed at op[$index] quarterTurns=${op.quarterTurns}, inputSize=${before.size}"
                        )
                        throw IOException("Lossless rotate failed")
                    }
                    Log.d(
                        "runLosslessChain",
                        "rotate ok, outputSize=${result.bytes.size}, offsets=(${result.x}, ${result.y}), dims=${result.w}x${result.h}"
                    )
                    result.bytes
                }

                is LosslessOp.Crop -> {
                    val result = JPEGLosslessTransform.cropLossless(
                        bytes, op.x, op.y, op.w, op.h
                    )
                    if (result == null) {
                        Log.d(
                            "runLosslessChain",
                            "crop failed at op[$index] rect=(${op.x},${op.y},${op.w},${op.h}), inputSize=${before.size}"
                        )
                        throw IOException("Lossless crop failed")
                    }
                    Log.d(
                        "runLosslessChain",
                        "crop ok, outputSize=${result.bytes.size}, offsets=(${result.x}, ${result.y}), dims=${result.w}x${result.h}"
                    )
                    result.bytes
                }
            }
        }
        Log.d("runLosslessChain", "finished, finalSize=${bytes.size} bytes")
        return bytes
    }

    suspend fun loadBitmap(uri: Uri): Pair<Bitmap?, LosslessChain?> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null to null
            val isJpeg = JPEGLosslessTransform.isValidJpeg(bytes)

            val exifRotation = try {
                ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees
            } catch (_: Exception) {
                0
            }
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null to null
            if (bitmap.width == 0 || bitmap.height == 0) {
                bitmap.recycle()
                return@withContext null to null
            }
            if (exifRotation != 0) {
                // todo: trim can now have two meaning, trim transparency or trim MCU boundaries, latter is unimplemented yet, will trim by default
                val result = TransformOps.rotate(
                    bitmap,
                    exifRotation.toFloat(),
                    jpegBytes = if (isJpeg) bytes else null,
                    trim = false
                )
                if (result.bitmap !== bitmap) bitmap.recycle()
                bitmap = result.bitmap
            }
            Log.d(
                "ImageImport",
                "Loaded bitmap from URI: $uri, size=${bitmap.width}x${bitmap.height}, isJPEG=$isJpeg, exifRotation=$exifRotation"
            )
            val chain = if (isJpeg) {
                val turns = ((exifRotation / 90) % 4 + 4) % 4
                LosslessChain(exifRotation = turns)
            } else null
            bitmap to chain
        } catch (e: Exception) {
            Log.e("ImageImport", "Failed to load bitmap from URI: $uri", e)
            null to null
        }
    }

    fun resolve(uri: Uri): ImportedImage? {
        return try {
            val realPath = getRealPathFromUri(context, uri)
            val fileName = when {
                !realPath.isNullOrBlank() -> File(realPath).name
                else -> {
                    var name: String? = null
                    context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (index != -1) name = cursor.getString(index)
                        }
                    }
                    name
                }
            }
            val mimeType = context.contentResolver.getType(uri)
            Log.d(
                "ImageImport",
                "Resolved URI: uri=$uri path=$realPath name=$fileName mimeType=$mimeType"
            )
            ImportedImage(
                uri = uri, realPath = realPath, fileName = fileName, mimeType = mimeType
            )
        } catch (e: Exception) {
            Log.e("ImageImport", "Failed to resolve URI: $uri", e)
            null
        }
    }

    suspend fun importImage(uri: Uri): Triple<ImportedImage?, Bitmap?, LosslessChain?> {
        val imported = resolve(uri)
        val (bitmap, chain) = loadBitmap(uri)
        Log.d(
            "ImportImage", "Image: uri=$uri imported=$imported losslessChain=${chain != null}"
        )
        return Triple(imported, bitmap, chain)
    }

    suspend fun overwrite(
        source: Bitmap,
        originalPath: String,
        outputFormat: ExportFormat,
        outputFilename: String,
        exportQuality: Int,
        deletionMode: DeletionMode,
        layers: List<EditorLayer>,
        viewCrop: Rect?,
        adjust: AdjustState,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        scaledDensity: Float,
        losslessChain: LosslessChain? = null,
        useLossless: Boolean = false,
    ): Result<File> = withContext(Dispatchers.IO) {
        val logTag = "overwrite"
        runCatching {
            // todo: we could allow the user to specify when (or where) it's deleted, however "Trash"
            //  isn't the same across all devices/apis. we could handle this by checking for expiry on startup.
            //  we could also set the Trash to `Android/media` which is hidden to most apps,
            //  still user accessible on modern versions, and cleaned if they uninstall the app
            val originalImage = File(originalPath)
            val directory = originalImage.parentFile ?: throw IOException("No directory")
            val lossless = (useLossless && losslessChain != null)
            val origBytes = if (lossless) originalImage.readBytes() else null

            // 60s * 60 = 1h, 1h * 24 = ~1d (somewhat), ~1d * 30 = ~30 days - https://swizec.com/blog/a-day-is-not-606024-seconds-long, "good enough"
            val expiryTimestamp = (System.currentTimeMillis() / 1000) + (60 * 60 * 24 * 30)
            val trashedFile = File(directory, ".trashed-${expiryTimestamp}-${originalImage.name}")
            if (!originalImage.renameTo(trashedFile)) {
                throw IOException("Failed to rename old file")
            }
            val outputFile = File(directory, outputFilename)
            Log.d(logTag, "Overwriting $originalImage with $outputFile")
            try {
                if (lossless && origBytes != null) {
                    outputFile.writeBytes(runLosslessChain(origBytes, losslessChain))
                } else {
                    writeRenderedBitmap(
                        outputFile,
                        source,
                        viewCrop,
                        layers,
                        adjust,
                        scaledDensity,
                        flipHorizontal,
                        flipVertical,
                        outputFormat,
                        exportQuality
                    )
                }
                MediaScannerConnection.scanFile(
                    context, arrayOf(outputFile.absolutePath), arrayOf("*/*"), null
                )
                when (deletionMode) {
                    DeletionMode.Trash -> {
                        Log.d(logTag, "Deletion panel: ${DeletionMode.Trash}")
                    }

                    DeletionMode.Permanent -> {
                        Log.d(logTag, "Deletion panel: ${DeletionMode.Permanent}")
                        if (!trashedFile.delete()) {
                            throw IOException("Failed to permanently delete")
                        } else Log.d(logTag, "Deleted $trashedFile (after trashing)")
                    }
                }
            } catch (e: Exception) {
                if (deletionMode == DeletionMode.Trash) {
                    trashedFile.renameTo(originalImage)
                }
                throw e
            }
            outputFile
        }
    }

    suspend fun saveAs(
        source: Bitmap,
        uri: Uri,
        originalUri: Uri? = null,
        format: ExportFormat,
        quality: Int,
        layers: List<EditorLayer>,
        viewCrop: Rect?,
        adjust: AdjustState,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        scaledDensity: Float,
        losslessChain: LosslessChain? = null,
        useLossless: Boolean = false
    ): Result<Unit> {
        val logTag = "saveAs"
        return withContext(Dispatchers.IO) {
            try {
                val out = context.contentResolver.openOutputStream(uri, "w")
                    ?: throw IOException("Cannot open output document")
                val origBytes = if (useLossless && losslessChain != null) {
                    originalUri?.let {
                        runCatching {
                            context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
                        }.getOrNull()
                    }
                } else null
                val losslessInputs =
                    origBytes?.let { bytes -> losslessChain?.let { c -> bytes to c } }
                if (losslessInputs != null) {
                    val (bytes, c) = losslessInputs
                    Log.d(logTag, "Saving lossless chain to $uri")
                    out.use { it.write(runLosslessChain(bytes, c)) }
                } else {
                    if (useLossless && losslessChain != null) {
                        Log.w(
                            logTag,
                            "Original unavailable for lossless save, falling back to standard export"
                        )
                    }
                    Log.d(
                        logTag, "Rendering bitmap to $uri with format=$format, quality=$quality"
                    )
                    val output = renderEditedBitmap(
                        source,
                        viewCrop ?: Rect(
                            0f, 0f, source.width.toFloat(), source.height.toFloat()
                        ),
                        layers,
                        adjust,
                        scaledDensity = scaledDensity,
                        flipHorizontal = flipHorizontal,
                        flipVertical = flipVertical
                    )
                    out.use { stream ->
                        if (!output.compress(format.compressFormat, quality, stream)) {
                            throw IOException("$uri export failed")
                        }
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun renderLayerGroupPreview(
        source: Bitmap,
        cropRect: Rect,
        groupLayers: List<EditorLayer>,
        maxDimensionPx: Int = 256,
        scaledDensity: Float = 1f,
    ): Bitmap {
        val cropW = cropRect.width.coerceAtLeast(1f)
        val cropH = cropRect.height.coerceAtLeast(1f)
        val scale = (maxDimensionPx / maxOf(cropW, cropH)).coerceAtMost(1f)
        val previewW = (cropW * scale).roundToInt().coerceAtLeast(1)
        val previewH = (cropH * scale).roundToInt().coerceAtLeast(1)
        val scaledSource = scaleCrop(source, cropRect, previewW, previewH)
        val previewLayers =
            groupLayers.translateForCrop(cropRect.left, cropRect.top).scaleForResize(scale, scale)
        return renderEditedBitmap(
            source = scaledSource,
            cropRect = Rect(0f, 0f, previewW.toFloat(), previewH.toFloat()),
            layers = previewLayers,
            adjust = AdjustState(),
            scaledDensity = scaledDensity,
        )
    }

    private fun scaleCrop(
        source: Bitmap,
        cropRect: Rect,
        previewW: Int,
        previewH: Int,
    ): Bitmap {
        val left = cropRect.left.toInt().coerceIn(0, source.width - 1)
        val top = cropRect.top.toInt().coerceIn(0, source.height - 1)
        val right = cropRect.right.toInt().coerceIn(left + 1, source.width)
        val bottom = cropRect.bottom.toInt().coerceIn(top + 1, source.height)
        val output = createBitmap(previewW, previewH)
        val canvas = AndroidCanvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            source,
            android.graphics.Rect(left, top, right, bottom),
            android.graphics.Rect(0, 0, previewW, previewH),
            paint,
        )
        return output
    }

    suspend fun performOverwrite(
        context: Context,
        state: EditorState,
        originalPath: String,
        onDone: () -> Unit,
        density: Density,
        exportQuality: Int = 95,
        deletionMode: DeletionMode,
    ): Result<Unit> {
        val source = state.workingBitmap ?: state.originalBitmap ?: return Result.failure(
            Exception("No bitmap")
        )
        val outputFormat = ExportFormat.fromMimeType(state.originalMimeType)
        val originalImage = File(originalPath)
        val outputFilename =
            withExtension(originalImage.nameWithoutExtension, outputFormat.extension)
        val repository = ImageRepository(context)
        val result = repository.overwrite(
            source = source,
            originalPath = originalPath,
            outputFormat = outputFormat,
            outputFilename = outputFilename,
            exportQuality = exportQuality,
            deletionMode = deletionMode,
            layers = state.layers,
            viewCrop = state.viewCrop,
            adjust = state.adjust,
            flipHorizontal = state.flipHorizontal,
            flipVertical = state.flipVertical,
            scaledDensity = density.density * density.fontScale,
            losslessChain = state.losslessChain,
            useLossless = state.canLossless,
        )
        withContext(Dispatchers.Main) {
            AppToasts.show(
                if (result.isSuccess) "Image saved" else "Save failed",
                if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            )
            if (result.isSuccess) onDone()
        }
        return result.map { }
    }

    fun buildStrokePath(
        points: List<Offset>, offsetX: Int, offsetY: Int
    ): Path {
        return Path().apply {
            val first = points.first()
            moveTo(first.x - offsetX, first.y - offsetY)
            if (points.size == 2) {
                val second = points[1]
                lineTo(second.x - offsetX, second.y - offsetY)
            } else {
                for (i in 0 until points.size - 1) {
                    val cur = points[i]
                    val nxt = points[i + 1]
                    val midX = (cur.x + nxt.x) / 2f - offsetX
                    val midY = (cur.y + nxt.y) / 2f - offsetY
                    quadTo(cur.x - offsetX, cur.y - offsetY, midX, midY)
                }
                val last = points.last()
                lineTo(last.x - offsetX, last.y - offsetY)
            }
        }
    }

    fun renderStroke(
        canvas: AndroidCanvas,
        stroke: DrawnStroke,
        left: Int,
        top: Int,
    ) {
        if (stroke.points.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.strokeWidthPx
            color = stroke.color.toArgb()
        }
        canvas.drawPath(buildStrokePath(stroke.points, left, top), paint)
    }

    fun renderTextOverlay(
        canvas: AndroidCanvas,
        overlay: TextOverlay,
        left: Int,
        top: Int,
        scaledDensity: Float,
    ) {
        val tp = overlay.toTextPaint(overlay.fontSizeSp * scaledDensity, overlay.color.toArgb())
        val layout = makeStaticLayout(
            overlay.text,
            tp,
            overlay.boxWidthPx.toInt().coerceAtLeast(1),
            overlay.align,
            overlay.wordWrap
        )
        val autoH = layout.height.toFloat()
        val boxH = overlay.boxHeightPx ?: autoH
        val x = overlay.positionPx.x - left
        val y = overlay.positionPx.y - top
        val centerY = y + boxH / 2f
        val centerX = x + overlay.boxWidthPx / 2f
        canvas.withTranslation(centerX, centerY) {
            rotate(overlay.rotation)
            clipRect(
                -overlay.boxWidthPx / 2f, -boxH / 2f, overlay.boxWidthPx / 2f, boxH / 2f
            )
            translate(-overlay.boxWidthPx / 2f, -boxH / 2f)
            layout.draw(this)
        }
    }

    fun renderStickerOverlay(
        canvas: AndroidCanvas,
        overlay: StickerOverlay,
        left: Int,
        top: Int,
    ) {
        val x = overlay.positionPx.x - left
        val y = overlay.positionPx.y - top
        val bmp = StickerBitmapCache.get(
            overlay = overlay,
            widthPx = overlay.widthPx.toInt().coerceAtLeast(1),
            heightPx = overlay.heightPx.toInt().coerceAtLeast(1)
        ) ?: return
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        val centerX = x + overlay.widthPx / 2f
        val centerY = y + overlay.heightPx / 2f

        canvas.withTranslation(centerX, centerY) {
            rotate(overlay.rotation)
            drawBitmap(bmp, -bmpW / 2f, -bmpH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                isDither = true
            })
        }
    }

    fun renderEditedBitmap(
        source: Bitmap,
        cropRect: Rect,
        layers: List<EditorLayer>,
        adjust: AdjustState,
        scaledDensity: Float = 1f,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
    ): Bitmap {
        val left = cropRect.left.toInt().coerceIn(0, source.width - 1)
        val top = cropRect.top.toInt().coerceIn(0, source.height - 1)
        val right = cropRect.right.toInt().coerceIn(left + 1, source.width)
        val bottom = cropRect.bottom.toInt().coerceIn(top + 1, source.height)
        val output = createBitmap(right - left, bottom - top)
        val canvas = AndroidCanvas(output)

        val matrix = Matrix().apply {
            postTranslate(-left.toFloat(), -top.toFloat())
            val cx = output.width / 2f
            val cy = output.height / 2f
            postScale(
                if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f, cx, cy
            )
        }

        val layerRectF = RectF(0f, 0f, output.width.toFloat(), output.height.toFloat())
        val layerCount = canvas.saveLayer(layerRectF, null)
        layers.forEach { layer ->
            when (layer) {
                is EditorLayer.Background -> {
                    canvas.drawBitmap(
                        source, matrix, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            colorFilter =
                                ColorMatrixColorFilter(ColorMatrix(adjust.toMatrixValues()))
                        })
                }

                is EditorLayer.Stroke -> renderStroke(canvas, layer.stroke, left, top)
                is EditorLayer.EraseStroke -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = layer.color.toArgb()
                    }
                    canvas.drawPath(layer.path.translated(-left.toFloat(), -top.toFloat()), paint)
                }

                is EditorLayer.Text -> renderTextOverlay(
                    canvas, layer.overlay, left, top, scaledDensity
                )

                is EditorLayer.Sticker -> renderStickerOverlay(canvas, layer.overlay, left, top)
            }
        }
        canvas.restoreToCount(layerCount)
        return output
    }

    private fun writeRenderedBitmap(
        outputFile: File,
        source: Bitmap,
        viewCrop: Rect?,
        layers: List<EditorLayer>,
        adjust: AdjustState,
        scaledDensity: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        outputFormat: ExportFormat,
        exportQuality: Int,
    ) {
        val imageRect = Rect(0f, 0f, source.width.toFloat(), source.height.toFloat())
        val selection = viewCrop ?: imageRect
        val output = renderEditedBitmap(
            source = source,
            cropRect = selection,
            adjust = adjust,
            scaledDensity = scaledDensity,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            layers = layers,
        )
        outputFile.outputStream().use { stream ->
            if (!output.compress(outputFormat.compressFormat, exportQuality, stream)) {
                throw IOException("Failed to write image")
            }
        }
    }
}

fun List<EditorLayer>.translateForCrop(dx: Float, dy: Float): List<EditorLayer> = map { layer ->
    when (layer) {
        is EditorLayer.Stroke -> layer.copy(
            stroke = layer.stroke.copy(
                points = layer.stroke.points.map { Offset(it.x - dx, it.y - dy) })
        )

        is EditorLayer.EraseStroke -> layer.copy(path = layer.path.translated(-dx, -dy))

        is EditorLayer.Text -> layer.copy(
            overlay = layer.overlay.copy(positionPx = layer.overlay.positionPx - Offset(dx, dy))
        )

        is EditorLayer.Sticker -> layer.copy(
            overlay = layer.overlay.copy(positionPx = layer.overlay.positionPx - Offset(dx, dy))
        )

        is EditorLayer.Background -> layer
    }
}

fun List<EditorLayer>.scaleForResize(scaleX: Float, scaleY: Float): List<EditorLayer> {
    val avg = (scaleX + scaleY) / 2f
    return map { layer ->
        when (layer) {
            is EditorLayer.Stroke -> layer.copy(
                stroke = layer.stroke.copy(
                    points = layer.stroke.points.map { Offset(it.x * scaleX, it.y * scaleY) },
                    strokeWidthPx = layer.stroke.strokeWidthPx * avg,
                )
            )

            is EditorLayer.EraseStroke -> layer.copy(path = layer.path.scaled(scaleX, scaleY))

            is EditorLayer.Text -> layer.copy(
                overlay = layer.overlay.copy(
                    positionPx = Offset(
                        layer.overlay.positionPx.x * scaleX, layer.overlay.positionPx.y * scaleY
                    ),
                    boxWidthPx = layer.overlay.boxWidthPx * scaleX,
                    boxHeightPx = layer.overlay.boxHeightPx?.let { it * scaleY },
                    fontSizeSp = layer.overlay.fontSizeSp * avg,
                )
            )

            is EditorLayer.Sticker -> layer.copy(
                overlay = layer.overlay.copy(
                    positionPx = Offset(
                        layer.overlay.positionPx.x * scaleX, layer.overlay.positionPx.y * scaleY
                    ),
                    widthPx = layer.overlay.widthPx * scaleX,
                    heightPx = layer.overlay.heightPx * scaleY,
                    renderMode = layer.overlay.renderMode.copy(
                        outlineThicknessPx = layer.overlay.renderMode.outlineThicknessPx * avg,
                    ),
                )
            )

            is EditorLayer.Background -> layer
        }
    }
}

