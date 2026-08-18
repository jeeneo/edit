package org.codeberg.editorie.ui.editorscreen

// SPDX-License-Identifier: MIT

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.codeberg.editorie.data.HapticPatterns
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private fun randomDoobleColor(colorScheme: ColorScheme): Color {
    val colors = listOf(
        colorScheme.primary, colorScheme.secondary, colorScheme.tertiary, colorScheme.error,
        colorScheme.inversePrimary, colorScheme.onPrimary, colorScheme.primaryContainer,
        colorScheme.onPrimaryContainer, colorScheme.secondary, colorScheme.onSecondary,
        colorScheme.secondaryContainer, colorScheme.onSecondaryContainer, colorScheme.tertiary,
        colorScheme.onTertiary, colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer,
        colorScheme.error, colorScheme.onError, colorScheme.errorContainer,
        colorScheme.onErrorContainer, colorScheme.background, colorScheme.onBackground,
        colorScheme.surface, colorScheme.onSurface, colorScheme.surfaceVariant,
        colorScheme.onSurfaceVariant, colorScheme.outline, colorScheme.outlineVariant,
        colorScheme.surfaceContainerLowest, colorScheme.surfaceContainerLow,
        colorScheme.surfaceContainer, colorScheme.surfaceContainerHigh,
        colorScheme.surfaceContainerHighest, colorScheme.inverseSurface,
        colorScheme.inverseOnSurface, colorScheme.inversePrimary,
    )
    return colors.random()
}

private fun randomUnitVector(): Pair<Float, Float> {
    val angle = Random.nextFloat() * 2 * PI.toFloat()
    return cos(angle) to sin(angle)
}

private fun bounceWithinRange(
    value: Float, dir: Float, min: Float, max: Float
): Triple<Float, Float, Boolean> {
    return when {
        value > max -> Triple(max, -abs(dir), true)
        value < min -> Triple(min, abs(dir), true)
        else -> Triple(value, dir, false)
    }
}

@Composable
fun Dooble(
    size: Dp,
    screenMaxWidth: Dp,
    screenMaxHeight: Dp,
    onDrag: () -> Unit = {},
) {
    val density = LocalDensity.current
    val screenOverflow = 100.dp
    val driftSpeed = (52 + (Random.nextFloat() * 20)).dp
    val overflowPx = with(density) { screenOverflow.toPx() }
    val driftSpeedPx = with(density) { driftSpeed.toPx() }
    val circleSizePx = with(density) { size.toPx() }
    val screenWidthPx = with(density) { screenMaxWidth.toPx() }
    val screenHeightPx = with(density) { screenMaxHeight.toPx() }
    val minX = -overflowPx
    val maxX = screenWidthPx - circleSizePx + overflowPx
    val minY = -overflowPx
    val maxY = screenHeightPx - circleSizePx + overflowPx
    val totalWidth = maxX - minX
    val totalHeight = maxY - minY
    val offsetX = remember { mutableFloatStateOf(minX + Random.nextFloat() * totalWidth) }
    val offsetY = remember { mutableFloatStateOf(minY + Random.nextFloat() * totalHeight) }
    var dirX by remember { mutableFloatStateOf(randomUnitVector().first) }
    var dirY by remember { mutableFloatStateOf(randomUnitVector().second) }
    var currentSpeedPx by remember { mutableFloatStateOf(driftSpeedPx) }
    var isDragging by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val color = remember(colorScheme) { randomDoobleColor(colorScheme) }

    LaunchedEffect(isDragging) {
        if (isDragging) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos
                if (currentSpeedPx > driftSpeedPx) {
                    val decayPerSecond = 2.5f
                    currentSpeedPx =
                        driftSpeedPx + (currentSpeedPx - driftSpeedPx) * exp(-decayPerSecond * dt)
                    if (currentSpeedPx - driftSpeedPx < 1f) currentSpeedPx = driftSpeedPx
                }
                val (nx, newDirX, bouncedX) = bounceWithinRange(
                    offsetX.floatValue + dirX * currentSpeedPx * dt, dirX, minX, maxX,
                )
                val (ny, newDirY, bouncedY) = bounceWithinRange(
                    offsetY.floatValue + dirY * currentSpeedPx * dt, dirY, minY, maxY,
                )
                dirX = newDirX
                dirY = newDirY
                offsetX.floatValue = nx
                offsetY.floatValue = ny
                if (bouncedX || bouncedY) {
                    // HapticPatterns.tap() // seems to be annoying, might tweak later
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(offsetX.floatValue.roundToInt(), offsetY.floatValue.roundToInt())
            }
            .size(size)
            .background(color, CircleShape)
            .pointerInput(Unit) {
                val velocityTracker = VelocityTracker()
                var dragTotal = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        HapticPatterns.tap()
                        velocityTracker.resetTracking()
                        dragTotal = Offset.Zero
                        onDrag()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                        velocityTracker.addPosition(change.uptimeMillis, dragTotal)
                        offsetX.floatValue += dragAmount.x
                        offsetY.floatValue += dragAmount.y
                        onDrag()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity()
                        val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
                        if (speed > driftSpeedPx) {
                            dirX = velocity.x / speed
                            dirY = velocity.y / speed
                            currentSpeedPx = speed
                        } else {
                            val dragLen =
                                sqrt(dragTotal.x * dragTotal.x + dragTotal.y * dragTotal.y)
                            if (dragLen > 0f) {
                                dirX = dragTotal.x / dragLen
                                dirY = dragTotal.y / dragLen
                            }
                            currentSpeedPx = driftSpeedPx
                        }
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                )
            },
    )
}

class FocusVisibilityController(
    private val hideAfterActivityMs: Long,
    private val revealAfterInactivityMs: Long,
    private val scope: CoroutineScope,
) {
    private var visibleState = mutableStateOf(true)
    val isContentVisible: Boolean get() = visibleState.value
    private var batchStartedAt = 0L
    private var job: Job? = null

    fun onDrag() {
        job?.cancel()
        job = scope.launch {
            delay(revealAfterInactivityMs.milliseconds)
            batchStartedAt = 0L
            visibleState.value = true
            job = null
        }
        if (!visibleState.value) return
        if (batchStartedAt == 0L) {
            batchStartedAt = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - batchStartedAt >= hideAfterActivityMs) {
            visibleState.value = false
        }
    }
}

@Composable
fun focusController(
    hideAfterActivityMs: Long = 1_000L,
    revealAfterInactivityMs: Long = 2_500L,
): FocusVisibilityController {
    val scope = rememberCoroutineScope()
    return remember(hideAfterActivityMs, revealAfterInactivityMs) {
        FocusVisibilityController(hideAfterActivityMs, revealAfterInactivityMs, scope)
    }
}
