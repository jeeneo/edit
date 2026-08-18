package org.codeberg.editorie.options.save

// SPDX-License-Identifier: MIT

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

// reflection based
val EXIF_TAGS: List<String> by lazy {
    ExifInterface::class.java.fields.filter {
        it.name.startsWith("TAG_") && java.lang.reflect.Modifier.isStatic(
            it.modifiers
        )
    }.mapNotNull { it.get(null) as? String }
}

val EXIF_DATE_TAGS: Set<String> = setOf(
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
)

@SuppressLint("Recycle")
fun getRealPathFromUri(
    context: Context, contentUri: Uri
): String? {
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    return context.contentResolver.query(
        contentUri, projection, null, null, null
    )?.use { cursor ->
        val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        if (columnIndex == -1 || !cursor.moveToFirst()) {
            return null
        }
        cursor.getString(columnIndex)
    }
}

private fun getSourceModifiedMillis(context: Context, sourceUri: Uri): Long? {
    val realPath = getRealPathFromUri(context, sourceUri)
    if (!realPath.isNullOrBlank()) {
        return File(realPath).lastModified().takeIf { it > 0L }
    }
    var ms: Long? = null
    context.contentResolver.query(
        sourceUri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (idx != -1) ms = cursor.getLong(idx).takeIf { it > 0L }
        }
    }
    return ms
}

sealed class ExifTarget {
    data class FileTarget(val file: File) : ExifTarget()
    data class UriTarget(val uri: Uri) : ExifTarget()
}

fun copyExif(
    context: Context,
    sourceUri: Uri,
    destination: ExifTarget,
    dateOnly: Boolean = false,
    useLossless: Boolean = false,
    logTag: String = "copyExif"
): Result<Unit> = runCatching {
    val sourceExif = context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) }
        ?: return@runCatching

    fun <T> withDestExif(block: (ExifInterface) -> T): T {
        return when (destination) {
            is ExifTarget.FileTarget -> {
                val exif = ExifInterface(destination.file.absolutePath)
                block(exif)
            }

            is ExifTarget.UriTarget -> {
                context.contentResolver.openFileDescriptor(destination.uri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    block(exif)
                } ?: throw java.io.IOException("Cannot open ${destination.uri} for read+write")
            }
        }
    }
    withDestExif { destExif ->
        val tagsToWrite = if (dateOnly) EXIF_DATE_TAGS else EXIF_TAGS
        Log.d(logTag, "Copying EXIF tags from $sourceUri to $destination")
        for (t in tagsToWrite) {
            sourceExif.getAttribute(t)?.let { destExif.setAttribute(t, it) }
        }
        if (dateOnly) {
            Log.d(logTag, "Clearing non-date EXIF tags from $destination")
            for (t in EXIF_TAGS) {
                if (t !in EXIF_DATE_TAGS) {
                    destExif.setAttribute(t, null)
                }
            }
        }
        destExif.saveAttributes()
    }
    if (useLossless) {
        Log.d(logTag, "Lossless: normalizing EXIF rotation for $destination")
        runCatching {
            withDestExif { exif ->
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString()
                )
                exif.saveAttributes()
            }
        }.onFailure {
            Log.w(logTag, "Lossless: Failed to normalize EXIF rotation for $destination", it)
        }
    }
}

fun copyTimestamps(
    context: Context,
    sourceUri: Uri,
    outputFile: File,
): Result<Unit> = runCatching {
    val sourceModifiedMillis = getSourceModifiedMillis(context, sourceUri)
    if (sourceModifiedMillis != null) {
        outputFile.setLastModified(sourceModifiedMillis)
    }
}
