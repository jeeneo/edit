package org.codeberg.editorie.options.adjust

// SPDX-License-Identifier: MIT

import android.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import android.graphics.ColorMatrix as AndroidColorMatrix

internal object AdjustOps {
    fun AndroidColorMatrix.postScaleTranslate(scale: Float, translate: Float) {
        postConcat(
            AndroidColorMatrix(
                floatArrayOf(
                    scale,
                    0f,
                    0f,
                    0f,
                    translate,
                    0f,
                    scale,
                    0f,
                    0f,
                    translate,
                    0f,
                    0f,
                    scale,
                    0f,
                    translate,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
                )
            )
        )
    }

    fun AdjustState.toMatrixValues(): FloatArray {
        val matrix = AndroidColorMatrix()
        if (invert) matrix.postScaleTranslate(-1f, 255f)
        matrix.postConcat(AndroidColorMatrix().apply { setSaturation(saturation) })
        matrix.postConcat(hueColorMatrix(hue))
        val contrastTranslate = (-0.5f * contrast + 0.5f) * 255f
        matrix.postConcat(scaleTranslateMatrix(contrast, contrastTranslate))
        matrix.postConcat(translateMatrix(brightness, brightness, brightness))
        matrix.postConcat(scaleTranslateMatrix(luminosity, 0f))
        matrix.postConcat(temperatureColorMatrix(temperature))
        matrix.postConcat(tintColorMatrix(tint))
        val exposureFactor = 2f.pow(exposure)
        matrix.postConcat(scaleTranslateMatrix(exposureFactor, 0f))
        val whitesGain = 1f + whites * 0.005f
        matrix.postConcat(scaleTranslateMatrix(whitesGain, 0f))
        val blacksGain = 1f - blacks * 0.005f
        matrix.postConcat(scaleTranslateMatrix(blacksGain, 255f * (1f - blacksGain)))
        val fadeScale = 1f - fade * 0.35f
        val fadeLift = fade * 40f
        matrix.postConcat(scaleTranslateMatrix(fadeScale, fadeLift))
        return matrix.array
    }


    fun temperatureColorMatrix(temp: Float): AndroidColorMatrix {
        val rScale: Float
        val gScale: Float
        val bScale: Float

        if (temp >= 0f) {
            rScale = 1f + temp * 0.004f
            gScale = 1f + temp * 0.001f
            bScale = 1f - temp * 0.006f
        } else {
            rScale = 1f + temp * 0.006f
            gScale = 1f + temp * 0.001f
            bScale = 1f - temp * 0.004f
        }

        return AndroidColorMatrix(
            floatArrayOf(
                rScale, 0f, 0f, 0f, 0f,
                0f, gScale, 0f, 0f, 0f,
                0f, 0f, bScale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    fun tintColorMatrix(tint: Float): AndroidColorMatrix {
        // Green <-> magenta white-balance axis. Positive shifts toward magenta
        // (boost red/blue, cut green); negative shifts toward green.
        val rbScale = 1f + tint * 0.002f
        val gScale = 1f - tint * 0.004f

        return AndroidColorMatrix(
            floatArrayOf(
                rbScale, 0f, 0f, 0f, 0f,
                0f, gScale, 0f, 0f, 0f,
                0f, 0f, rbScale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    private fun scaleTranslateMatrix(scale: Float, translate: Float): AndroidColorMatrix {
        return AndroidColorMatrix(
            floatArrayOf(
                scale,
                0f,
                0f,
                0f,
                translate,
                0f,
                scale,
                0f,
                0f,
                translate,
                0f,
                0f,
                scale,
                0f,
                translate,
                0f,
                0f,
                0f,
                1f,
                0f
            )
        )
    }

    private fun translateMatrix(r: Float, g: Float, b: Float): AndroidColorMatrix {
        return AndroidColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, r, 0f, 1f, 0f, 0f, g, 0f, 0f, 1f, 0f, b, 0f, 0f, 0f, 1f, 0f
            )
        )
    }

    fun hueColorMatrix(degrees: Float): AndroidColorMatrix {
        val cos = cos(Math.toRadians(degrees.toDouble())).toFloat()
        val sin = sin(Math.toRadians(degrees.toDouble())).toFloat()
        val red = 0.213f
        val green = 0.715f
        val blue = 0.072f
        return ColorMatrix(
            floatArrayOf(
                red + cos * (1 - red) + sin * -red,
                green + cos * -green + sin * -green,
                blue + cos * -blue + sin * (1 - blue),
                0f,
                0f,
                red + cos * -red + sin * 0.143f,
                green + cos * (1 - green) + sin * 0.14f,
                blue + cos * -blue + sin * -0.283f,
                0f,
                0f,
                red + cos * -red + sin * -(1 - red),
                green + cos * -green + sin * green,
                blue + cos * (1 - blue) + sin * blue,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f
            )
        )
    }

}
