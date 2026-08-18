package org.codeberg.editorie.ui.canvas

// SPDX-License-Identifier: MIT

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class CanvasViewport internal constructor(
    private var flingDensity: Density,
    private val scope: CoroutineScope,
) {
    var fullRect: Rect = Rect.Zero
    var sourceRect: Rect = Rect.Zero

    var canvasSize by mutableStateOf(IntSize.Zero)
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    val displayRect: Rect
        get() = if (canvasSize == IntSize.Zero) Rect.Zero
        else computeDisplay(canvasSize, sourceRect)

    val maxZoom: Float
        get() {
            val dr = displayRect
            return if (dr.width > 0f && dr.height > 0f) {
                val pixelRatioX = sourceRect.width / dr.width
                val pixelRatioY = sourceRect.height / dr.height
                maxOf(pixelRatioX, pixelRatioY).times(4f).coerceAtLeast(4f)
            } else 16f
        }

    fun toContent(screen: Offset): Offset = (screen - pan) / zoom

    fun toPixel(screen: Offset): Offset {
        val content = toContent(screen)
        return Offset(
            ((content.x - displayRect.left) / displayRect.width * sourceRect.width + sourceRect.left).coerceIn(
                sourceRect.left, sourceRect.right
            ),
            ((content.y - displayRect.top) / displayRect.height * sourceRect.height + sourceRect.top).coerceIn(
                sourceRect.top, sourceRect.bottom
            )
        )
    }

    fun fromPixel(pixel: Offset): Offset {
        return Offset(
            (pixel.x - sourceRect.left) / sourceRect.width * displayRect.width + displayRect.left,
            (pixel.y - sourceRect.top) / sourceRect.height * displayRect.height + displayRect.top
        )
    }

    fun pixelRectToDisplay(pixelRect: Rect): Rect {
        val tl = fromPixel(Offset(pixelRect.left, pixelRect.top))
        val br = fromPixel(Offset(pixelRect.right, pixelRect.bottom))
        return Rect(tl, br)
    }

    fun screenToPixelWidth(penWidth: Float): Float =
        if (displayRect.width > 0f) penWidth * (sourceRect.width / displayRect.width)
        else penWidth

    fun clampPan(rawPan: Offset, z: Float): Offset {
        if (z <= 1f || canvasSize == IntSize.Zero) return Offset.Zero
        fun axisRange(
            scaledOrigin: Float, scaledSize: Float, viewport: Float
        ): ClosedFloatingPointRange<Float> {
            return if (scaledSize <= viewport) {
                val v = (viewport - scaledSize) / 2f - scaledOrigin
                v..v
            } else {
                (viewport - scaledSize - scaledOrigin)..(-scaledOrigin)
            }
        }

        val xRange =
            axisRange(displayRect.left * z, displayRect.width * z, canvasSize.width.toFloat())
        val yRange =
            axisRange(displayRect.top * z, displayRect.height * z, canvasSize.height.toFloat())
        return Offset(rawPan.x.coerceIn(xRange), rawPan.y.coerceIn(yRange))
    }

    suspend fun flingPan(velocity: Velocity) {
        var previous = pan
        AnimationState(
            typeConverter = Offset.VectorConverter,
            initialValue = previous,
            initialVelocityVector = AnimationVector2D(velocity.x, velocity.y)
        ).animateDecay(splineBasedDecay(flingDensity)) {
            pan = clampPan(previous + (value - previous), zoom)
            previous = value
        }
    }

    suspend fun animateZoomTo(target: Float, focal: Offset) {
        val startZoom = zoom
        val startPan = pan
        AnimationState(initialValue = startZoom).animateTo(
            targetValue = target.coerceIn(1f, maxZoom),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        ) {
            val factor = value / startZoom
            pan = clampPan((startPan - focal) * factor + focal, value)
            zoom = value
        }
        if (zoom == 1f) pan = Offset.Zero
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        scope.launch { block() }
    }
}

@Composable
fun rememberCanvasViewport(fullRect: Rect, sourceRect: Rect): CanvasViewport {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewport = remember { CanvasViewport(density, scope) }
    viewport.fullRect = fullRect
    viewport.sourceRect = sourceRect
    return viewport
}
