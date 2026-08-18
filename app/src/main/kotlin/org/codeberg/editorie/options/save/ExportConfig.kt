package org.codeberg.editorie.options.save

// SPDX-License-Identifier: MIT

import android.graphics.Bitmap
import android.os.Build

enum class ExportFormat(
    val label: String,
    val mimeType: String,
    val extension: String
) {
    JPEG("JPEG", "image/jpeg", "jpeg"),
    PNG("PNG", "image/png", "png"),
    WEBP("WebP", "image/webp", "webp");
    // SVG("SVG", "image/svg+xml", "svg");

    val compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            JPEG -> Bitmap.CompressFormat.JPEG
            PNG -> Bitmap.CompressFormat.PNG
            WEBP -> webpCompressFormat()
            // SVG -> throw IllegalStateException("SVG has no CompressFormat")
        }

    companion object {
        fun fromMimeType(mimeType: String?): ExportFormat =
            entries.firstOrNull { it.mimeType == mimeType } ?: JPEG
    }
}

private fun webpCompressFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    }

sealed interface DeletionMode {
    data object Trash : DeletionMode
    data object Permanent : DeletionMode
}
