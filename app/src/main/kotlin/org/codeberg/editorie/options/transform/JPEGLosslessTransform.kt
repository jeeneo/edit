package org.codeberg.editorie.options.transform

// SPDX-License-Identifier: MIT

data class NativeCropResult(
    val bytes: ByteArray,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NativeCropResult
        if (x != other.x) return false
        if (y != other.y) return false
        if (w != other.w) return false
        if (h != other.h) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + w
        result = 31 * result + h
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class McuInfo(
    val mcuWidth: Int,
    val mcuHeight: Int,
    val imageWidth: Int,
    val imageHeight: Int,
)

object JPEGLosslessTransform {
    init {
        System.loadLibrary("lossless")
    }

    private val cropResultTemplate = NativeCropResult(ByteArray(0), 0, 0, 0, 0)
    private val mcuInfoTemplate = McuInfo(0, 0, 0, 0)

    private external fun nativeCropLossless(
        inputBytes: ByteArray, x: Int, y: Int, w: Int, h: Int, template: NativeCropResult
    ): NativeCropResult?

    private external fun nativeRotate90Lossless(
        inputBytes: ByteArray, quarterTurns: Int, trim: Boolean, template: NativeCropResult
    ): NativeCropResult?

    private external fun nativeGetMcuSize(
        inputBytes: ByteArray, template: McuInfo
    ): McuInfo

    fun cropLossless(jpegBytes: ByteArray, x: Int, y: Int, w: Int, h: Int): NativeCropResult? =
        nativeCropLossless(jpegBytes, x, y, w, h, cropResultTemplate)

    fun rotate90Lossless(
        jpegBytes: ByteArray, quarterTurns: Int, trim: Boolean
    ): NativeCropResult? = nativeRotate90Lossless(jpegBytes, quarterTurns, trim, cropResultTemplate)

    fun mcuSize(jpegBytes: ByteArray): McuInfo = nativeGetMcuSize(jpegBytes, mcuInfoTemplate)

    fun isValidJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
}
