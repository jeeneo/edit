package org.codeberg.editorie.data

// SPDX-License-Identifier: MIT

import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.codeberg.editorie.App

object HapticPatterns {
    private val vibrator: Vibrator?
        get() = App.ctx.getSystemService(Vibrator::class.java)
    private val useHaptics get() = App.prefs.loadHaptic()
    private const val TAP_DURATION_MS = 20L

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun vibratePredefined(effectId: Int) {
        val vibrator = vibrator ?: return
        val effect = VibrationEffect.createPredefined(effectId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
            )
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(effect)
        }
    }

    private fun vibrateOldSdk() {
        if (!useHaptics) return
        val vibrator = vibrator ?: return
        @Suppress("DEPRECATION") vibrator.vibrate(TAP_DURATION_MS)
    }

    private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray) {
        val vibrator = vibrator ?: return
        if (!useHaptics) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION") vibrator.vibrate(
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            )
        } else {
            var total = 0L
            for (t in timings) total += t
            @Suppress("DEPRECATION") vibrator.vibrate(total)
        }
    }

    fun tap() {
        if (!useHaptics) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_TICK)
        } else {
            vibrateOldSdk()
        }
    }

    fun longPress() {
        if (!useHaptics) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            vibrateOldSdk()
        }
    }

    fun tickLight() {
        if (!useHaptics) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibratePredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            vibrateOldSdk()
        }
    }

    fun tickMedium() = vibrateWaveform(longArrayOf(0L, 8L), intArrayOf(0, 240))
}

data class ImportedImage(
    val uri: Uri, val realPath: String?, val fileName: String?, val mimeType: String?
)

data class UndoStack(
    val entries: List<UndoEntry> = emptyList(),
    val redoEntries: List<UndoEntry> = emptyList(),
    val maxDepth: Int = DEFAULT_MAX_UNDO,
) {
    companion object {
        const val DEFAULT_MAX_UNDO = 20
    }

    val canUndo: Boolean get() = entries.isNotEmpty()
    val canRedo: Boolean get() = redoEntries.isNotEmpty()

    fun push(entry: UndoEntry): UndoStack =
        copy(entries = (entries + entry).takeLast(maxDepth), redoEntries = emptyList())

    fun pop(): Pair<UndoEntry?, UndoStack> =
        entries.lastOrNull() to copy(entries = entries.dropLast(1))

    fun redo(): Pair<UndoEntry?, UndoStack> =
        redoEntries.lastOrNull() to copy(redoEntries = redoEntries.dropLast(1))

    fun pushRedo(entry: UndoEntry): UndoStack =
        copy(redoEntries = (redoEntries + entry).takeLast(maxDepth))

    fun pushUndo(entry: UndoEntry): UndoStack =
        copy(entries = (entries + entry).takeLast(maxDepth))
}

@Composable
fun butter(): List<String> = remember {
    listOf(
        "Congrats you found me",
        "You found me again",
        "Ok",
        "Stop finding me",
        "Why",
        "Why me?",
        "The butter list has 7 elements",
        "Funsies <333",
        "Colon three :3",
        "\"Tell everyone within shouting distance and reward yourself with a trip to the refrigerator.\"",
        "Drink some water",
        "Focus on blinking",
        "Do you sometimes spell things on the roof of your mouth using your tongue?",
        "You should be doing something",
        "Like editing",
        "You getting those grandma sleep hours?",
        "Beneath the ethereal glow of the porcelain sanctuary's incandescent luminescence, John gracefully descended upon the throne of contemplation, his weary form finding solace within the hallowed confines of this most intimate chamber of reflection, where the whispered secrets of humanity's most vulnerable moments echoed through the ages like a symphony of existential necessity.",
        "Toasters don't toast toast, toast toasts toast, or does toast toast toast?",
        "Fishermen don't fish fish, fish fish fish.",
        "Her vocabulary was as bad as, like, whatever",
        "The hailstones leaped from the pavement, just like maggots when you fry them in hot grease.",
        "The dandelion swayed in the gentle breeze like an oscillating electric fan set on medium.",
        "Hello again",
        "Wa-che rs a%r*?e wa-(_ \";!\" n(#_@'na Bees \uD83D\uDC1D",
    )
}
