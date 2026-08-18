package org.codeberg.editorie.options.adjust

// SPDX-License-Identifier: MIT

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.ui.bottombar.RoundedActionButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DialButton(
    icon: ImageVector,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    defaultValue: Float = (range.start + range.endInclusive) / 2f,
) {
    val totalSweep = 270f
    val halfSweep = totalSweep / 2f
    val snapThreshDeg = 6f
    val snapFrac = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    fun normalize(v: Float) =
        ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    fun valueToRot(v: Float) = halfSweep - normalize(v) * totalSweep
    fun rotToValue(rot: Float) = range.start + ((halfSweep - rot) / totalSweep).coerceIn(
        0f, 1f
    ) * (range.endInclusive - range.start)

    val rotAnim = remember { Animatable(valueToRot(value)) }
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    val currentOnValue by rememberUpdatedState(onValue)
    val snapRots = remember { snapFrac.map { halfSweep - it * totalSweep } }
    val bouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    LaunchedEffect(value) {
        if (!isDragging && abs(rotAnim.value - valueToRot(value)) > 0.5f) rotAnim.animateTo(
            valueToRot(value), spring(stiffness = Spring.StiffnessLow)
        )
    }
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val active = (value != defaultValue)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .pointerInput(range) {
                    var lastTouchDeg = 0f
                    var lastTickIdx = (normalize(value) * 20).roundToInt().coerceIn(0, 20)
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            lastTouchDeg = (offset - Offset(
                                size.width / 2f, size.height / 2f
                            )).let { atan2(it.y, it.x) * (180f / PI.toFloat()) }
                        },
                        onDragEnd = {
                            isDragging = false
                            val nearest = snapRots.minByOrNull { abs(it - rotAnim.value) }!!
                            if (abs(nearest - rotAnim.value) < snapThreshDeg) {
                                scope.launch {
                                    rotAnim.animateTo(nearest, bouncySpring)
                                    currentOnValue(rotToValue(nearest))
                                    HapticPatterns.tickMedium()
                                }
                            }
                        },
                        onDragCancel = { isDragging = false },
                    ) { change, _ ->
                        change.consume()
                        val currDeg = (change.position - Offset(
                            size.width / 2f, size.height / 2f
                        )).let { atan2(it.y, it.x) * (180f / PI.toFloat()) }
                        var delta = currDeg - lastTouchDeg
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        lastTouchDeg = currDeg
                        val newRot = (rotAnim.value + delta).coerceIn(-halfSweep, halfSweep)
                        scope.launch { rotAnim.snapTo(newRot) }
                        currentOnValue(rotToValue(newRot))
                        val tickIdx =
                            ((halfSweep - newRot) / totalSweep * 20).roundToInt().coerceIn(0, 20)
                        if (tickIdx != lastTickIdx) {
                            lastTickIdx = tickIdx
                            val nearestSnap = snapRots.minByOrNull { abs(it - newRot) }!!
                            val isNearSnap = abs(nearestSnap - newRot) < snapThreshDeg
                            if (tickIdx % 5 == 0 && !isNearSnap) HapticPatterns.tickMedium()
                            else if (tickIdx % 5 != 0) HapticPatterns.tickLight()
                        }
                    }
                }
                .pointerInput(defaultValue) {
                    detectTapGestures(onDoubleTap = {
                        scope.launch {
                            isDragging = true
                            HapticPatterns.longPress()
                            currentOnValue(defaultValue)
                            rotAnim.animateTo(valueToRot(defaultValue), bouncySpring)
                            isDragging = false
                        }
                    })
                }) {
            val backgroundColor =
                if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            Canvas(Modifier.fillMaxSize()) {
                val radius = 36.dp.toPx()
                val arcR = radius - 5.dp.toPx()
                val arcStroke = 2.5.dp.toPx()
                drawCircle(color = backgroundColor, radius = radius)
                drawCircle(
                    color = primary,
                    radius = 2.8.dp.toPx(),
                    center = Offset(center.x, center.y - arcR)
                )
                rotate(degrees = rotAnim.value, pivot = center) {
                    val outerR = arcR - arcStroke - 1.5.dp.toPx()
                    for (i in 0..20) {
                        val aRad = ((i / 20f * totalSweep - halfSweep - 90f) * PI / 180.0).toFloat()
                        val isMajor = i % 5 == 0
                        drawLine(
                            color = if (isMajor) primary.copy(alpha = 0.80f) else onSurfaceVariant.copy(
                                alpha = 0.28f
                            ),
                            start = Offset(
                                center.x + cos(aRad) * outerR, center.y + sin(aRad) * outerR
                            ),
                            end = Offset(
                                center.x + cos(aRad) * (outerR - if (isMajor) 8.dp.toPx() else 4.dp.toPx()),
                                center.y + sin(aRad) * (outerR - if (isMajor) 8.dp.toPx() else 4.dp.toPx())
                            ),
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) primary else onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) primary else onSurfaceVariant,
        )
    }
}

@Composable
fun AdjustOptions(state: AdjustState, onChange: (AdjustState) -> Unit) {
    data class DialDef(
        val icon: ImageVector,
        val label: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val default: Float,
        val update: (AdjustState, Float) -> AdjustState,
    )

    val dials = remember(state) {
        listOf(
            DialDef(
                Icons.Default.Tune, "Sat", state.saturation, 0f..2f, 1f
            ) { s, v -> s.copy(saturation = v) },
            DialDef(
                Icons.Default.Palette, "Hue", state.hue, -180f..180f, 0f
            ) { s, v -> s.copy(hue = v) },
            DialDef(
                Icons.Default.Contrast, "Contrast", state.contrast, 0f..2f, 1f
            ) { s, v -> s.copy(contrast = v) },
            DialDef(
                Icons.Default.BrightnessLow, "Brightness", state.brightness, -255f..255f, 0f
            ) { s, v -> s.copy(brightness = v) },
            DialDef(
                Icons.Default.WbSunny, "Lum", state.luminosity, 0f..2f, 1f
            ) { s, v -> s.copy(luminosity = v) },
            DialDef(
                Icons.Default.Thermostat, "Temp", state.temperature, -100f..100f, 0f
            ) { s, v -> s.copy(temperature = v) },
            DialDef(
                Icons.Default.Opacity, "Tint", state.tint, -100f..100f, 0f
            ) { s, v -> s.copy(tint = v) },
            DialDef(
                Icons.Default.Exposure, "Exposure", state.exposure, -2f..2f, 0f
            ) { s, v -> s.copy(exposure = v) },
            DialDef(
                Icons.Default.BrightnessHigh, "Whites", state.whites, -100f..100f, 0f
            ) { s, v -> s.copy(whites = v) },
            DialDef(
                Icons.Default.Brightness2, "Blacks", state.blacks, -100f..100f, 0f
            ) { s, v -> s.copy(blacks = v) },
            DialDef(
                Icons.Default.Gradient, "Fade", state.fade, 0f..1f, 0f
            ) { s, v -> s.copy(fade = v) },
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dials.forEach { d ->
                DialButton(
                    icon = d.icon,
                    label = d.label,
                    value = d.value,
                    range = d.range,
                    defaultValue = d.default,
                    onValue = { onChange(d.update(state, it)) },
                )
            }
            @SuppressLint("MissingHapticFeedback")
            RoundedActionButton(
                icon = Icons.Default.InvertColors,
                label = "Invert",
                active = state.invert,
                onClick = { onChange(state.copy(invert = !state.invert)) },
            )
        }
    }
}
