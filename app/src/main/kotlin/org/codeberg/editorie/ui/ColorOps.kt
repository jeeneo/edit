package org.codeberg.editorie.ui

// SPDX-License-Identifier: MIT

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.codeberg.editorie.data.HapticPatterns
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ColorEditorDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    onDelete: (() -> Unit)? = null,
    checkIcon: Boolean = false
) {
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val delta = max - min

        val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))

        val h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta).mod(6f))
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }

        return floatArrayOf(h, s, l)
    }

    fun hexFromHsl(h: Float, s: Float, l: Float): String {
        val rgb = hslToRgb(h, s, l)
        return "%02X%02X%02X".format(
            (rgb[0] * 255).roundToInt().coerceIn(0, 255),
            (rgb[1] * 255).roundToInt().coerceIn(0, 255),
            (rgb[2] * 255).roundToInt().coerceIn(0, 255),
        )
    }

    val initHsl = remember {
        val c = if (initial.alpha == 0f) Color.White else initial
        rgbToHsl(c.red, c.green, c.blue)
    }
    var h by remember { mutableFloatStateOf(initHsl[0]) }
    var s by remember { mutableFloatStateOf(initHsl[1]) }
    var l by remember { mutableFloatStateOf(initHsl[2]) }
    var a by remember { mutableFloatStateOf(initial.alpha) }

    var hexText by remember { mutableStateOf(hexFromHsl(initHsl[0], initHsl[1], initHsl[2])) }
    var hexError by remember { mutableStateOf(false) }

    fun syncHexFromHsl() {
        hexText = hexFromHsl(h, s, l)
        hexError = false
    }

    fun syncHslFromHex(hex: String) {
        val clean = hex.trimStart('#')
        if (clean.length == 6) {
            val v = clean.toLongOrNull(16)
            if (v != null) {
                val r = ((v shr 16) and 0xFF) / 255f
                val g = ((v shr 8) and 0xFF) / 255f
                val b = (v and 0xFF) / 255f
                val hsl = rgbToHsl(r, g, b)
                h = hsl[0]
                s = hsl[1]
                l = hsl[2]
                hexError = false
                return
            }
        }
        hexError = clean.isNotEmpty()
    }

    val previewRgb = remember(h, s, l) { hslToRgb(h, s, l) }
    val preview = Color(previewRgb[0], previewRgb[1], previewRgb[2], a)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Edit color",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (onDelete != null) {
                        IconButton(onClick = { HapticPatterns.tap(); onDelete() }) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete color",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(MaterialTheme.shapes.large)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.large,
                        )
                ) {
                    Checkerboard()
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(preview)
                    )
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { raw ->
                        hexText = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                        syncHslFromHex(hexText)
                    },
                    label = { Text("Hex") },
                    prefix = { Text("#") },
                    isError = hexError,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("H", Modifier.width(16.dp), style = MaterialTheme.typography.labelMedium)
                    HueSlider(
                        hue = h,
                        onHueChange = { h = it; syncHexFromHsl() },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("S", Modifier.width(16.dp), style = MaterialTheme.typography.labelMedium)
                    //noinspection MissingHapticFeedback
                    Slider(
                        value = s,
                        onValueChange = { s = it; syncHexFromHsl() },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        (s * 100).roundToInt().toString(),
                        Modifier.width(22.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("L", Modifier.width(16.dp), style = MaterialTheme.typography.labelMedium)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Slider(
                            value = l,
                            onValueChange = { raw ->
                                val snapThreshold = 0.03f
                                val newL = if (abs(raw - 0.5f) < snapThreshold) {
                                    if (l != 0.5f) HapticPatterns.tickLight()
                                    0.5f
                                } else {
                                    raw
                                }
                                l = newL
                                syncHexFromHsl()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        (l * 100).roundToInt().toString(),
                        Modifier.width(22.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "A", Modifier.width(16.dp), style = MaterialTheme.typography.labelMedium
                    )
                    //noinspection MissingHapticFeedback
                    Slider(
                        value = a,
                        onValueChange = { a = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        (a * 255).toInt().toString(),
                        Modifier.width(22.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        TextButton(
                            onClick = { HapticPatterns.tap(); onDismiss() }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            HapticPatterns.tap()
                            onConfirm(preview)
                        }) {
                            Icon(
                                if (checkIcon) Icons.Default.Check else Icons.Default.Add,
                                if (checkIcon) "Set color" else "Add color",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun hslToComposeColor(h: Float, s: Float = 1f, l: Float = 0.5f): Color {
        val rgb = hslToRgb(h, s, l)
        return Color(rgb[0], rgb[1], rgb[2])
    }

    val trackHeight = 4.dp
    val thumbDiameter = 20.dp
    BoxWithConstraints(
        modifier = modifier
            .height(thumbDiameter)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onHueChange(fraction * 360f)
                    },
                ) { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onHueChange(fraction * 360f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.Center)
        ) {
            val colors = (0..12).map { i -> hslToComposeColor(i * 30f, 1f, 0.5f) }
            drawRoundRect(
                brush = Brush.horizontalGradient(colors),
                cornerRadius = CornerRadius(trackHeight.toPx() / 2f),
            )
        }
        val fraction = (hue / 360f).coerceIn(0f, 1f)
        val offsetX = (maxWidth - thumbDiameter) * fraction
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .size(thumbDiameter)
                .clip(CircleShape)
                .background(hslToComposeColor(hue))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - abs(hp.mod(2f) - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return floatArrayOf(r1 + m, g1 + m, b1 + m)
}

@Composable
fun ColorPickerBar(
    modifier: Modifier = Modifier,
    activeColor: Color?,
    palette: List<Color>,
    onColorSelected: (Color) -> Unit,
    onNullSelected: (() -> Unit)? = null,
    onResetColor: (() -> Unit)? = null,
    onAddColor: (Color, Int?) -> Unit,
    onRemoveColor: (Color) -> Unit,
) {
    data class ColorTarget(
        val color: Color,
        val paletteIndex: Int? = null,
        val isNew: Boolean = false,
    )

    val colorSelected by rememberUpdatedState(onColorSelected)
    val onNullSelectable by rememberUpdatedState(onNullSelected)
    val resetColor by rememberUpdatedState(onResetColor)
    val currentOnAddColor by rememberUpdatedState(onAddColor)
    val currentOnRemoveColor by rememberUpdatedState(onRemoveColor)
    val editTarget = remember { mutableStateOf<ColorTarget?>(null) }

    fun closeEditor() {
        editTarget.value = null
    }

    editTarget.value?.let { target ->
        ColorEditorDialog(
            initial = target.color,
            onDismiss = { closeEditor() },
            onConfirm = { newColor ->
                currentOnAddColor(newColor, target.paletteIndex)
                colorSelected(newColor)
                closeEditor()
            },
            onDelete = if (target.paletteIndex != null) {
                { currentOnRemoveColor(target.color); closeEditor() }
            } else null,
            checkIcon = !target.isNew,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(2.dp))
        ColorSwatch(
            color = if (activeColor == Color.Unspecified) Color.White else activeColor,
            isActive = true,
            borderColor = MaterialTheme.colorScheme.primary,
            onClick = {
                val target = activeColor ?: return@ColorSwatch
                editTarget.value = ColorTarget(target)
                HapticPatterns.tap()
            },
        )
        VerticalDivider(Modifier.height(24.dp))
        if (resetColor != null) {
            val default = activeColor == Color.Unspecified
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            HapticPatterns.tap()
                            resetColor?.invoke()
                            Log.d("resetColors", "$activeColor")
                        })
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    if (default) 3.dp else 1.dp,
                    if (default) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "R",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (default) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.copy(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                    )
                }
            }
        }
        if (onNullSelectable != null) {
            ColorSwatch(
                color = null,
                isActive = activeColor == null,
                borderColor = if (activeColor == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                onClick = { HapticPatterns.tap(); onNullSelectable?.invoke() },
            )
        }
        palette.forEachIndexed { index, color ->
            val isActive = activeColor != null && color.toArgb() == activeColor.toArgb()
            ColorSwatch(
                color = color,
                isActive = isActive,
                borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                onClick = { HapticPatterns.tap(); colorSelected(color) },
                onLongPress = {
                    HapticPatterns.longPress()
                    editTarget.value = ColorTarget(color, paletteIndex = index)
                },
            )
        }
        Spacer(Modifier.width(2.dp))
    }
}

@Composable
private fun ColorSwatch(
    color: Color?,
    isActive: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 3.dp else 1.5.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium
        ),
        label = "swatch_border",
    )
    Box(
        modifier = Modifier
            .size(32.dp)
            .pointerInput(color) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress?.invoke() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(borderColor)
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(borderWidth)
                .clip(CircleShape)
        ) {
            Checkerboard()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color ?: Color.Transparent)
            )
        }
    }
}

@Composable
fun Checkerboard(lightness: Float = 1f) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val white = Color.White.copy(
            red = Color.White.red * lightness,
            green = Color.White.green * lightness,
            blue = Color.White.blue * lightness,
        )
        val lightGray = Color.LightGray.copy(
            red = Color.LightGray.red * lightness,
            green = Color.LightGray.green * lightness,
            blue = Color.LightGray.blue * lightness,
        )
        drawRect(white)
        drawRect(
            lightGray,
            topLeft = Offset(size.width / 2, 0f),
            size = Size(size.width / 2, size.height / 2)
        )
        drawRect(
            lightGray,
            topLeft = Offset(0f, size.height / 2),
            size = Size(size.width / 2, size.height / 2)
        )
    }
}
