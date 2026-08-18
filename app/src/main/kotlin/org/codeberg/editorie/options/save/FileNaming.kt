package org.codeberg.editorie.options.save

// SPDX-License-Identifier: MIT

fun withExtension(fileName: String, extension: String): String {
    if (extension.isEmpty()) return fileName
    val dotIndex = fileName.lastIndexOf('.')
    val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
    return "$base.$extension"
}
