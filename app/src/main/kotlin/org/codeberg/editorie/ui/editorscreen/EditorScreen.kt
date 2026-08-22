@file:Suppress("SpellCheckingInspection")

package org.codeberg.editorie.ui.editorscreen

// SPDX-License-Identifier: MIT

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skydoves.cloudy.cloudy
import com.skydoves.cloudy.rememberSky
import com.skydoves.cloudy.sky
import kotlinx.coroutines.launch
import org.codeberg.editorie.App
import org.codeberg.editorie.R
import org.codeberg.editorie.data.AppTheme
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.EditorSubpanel
import org.codeberg.editorie.data.HapticPatterns
import org.codeberg.editorie.data.ImageCanvasState
import org.codeberg.editorie.data.ImageDisplayState
import org.codeberg.editorie.data.ImageOverlayState
import org.codeberg.editorie.data.LocalAppTheme
import org.codeberg.editorie.data.LocalOnThemeChange
import org.codeberg.editorie.data.butter
import org.codeberg.editorie.options.save.DeletionMode
import org.codeberg.editorie.ui.Checkerboard
import org.codeberg.editorie.ui.ColorEditorDialog
import org.codeberg.editorie.ui.EditorLayersDrawer
import org.codeberg.editorie.ui.bottombar.BottomEditorBar
import org.codeberg.editorie.ui.canvas.ImageCanvas
import org.codeberg.editorie.util.AppToasts
import org.codeberg.editorie.util.MATH_SYMBOLS
import org.codeberg.editorie.util.evaluateMath
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(incomingIntent: Intent, viewModel: EditorScreenViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var hasHandledIncomingIntent by rememberSaveable { mutableStateOf(false) }
    val closeOnSave = rememberSaveable { mutableStateOf(App.prefs.loadCloseOnSave()) }
    val distraction = rememberSaveable { mutableStateOf(App.prefs.loadDistraction()) }
    val haptics = rememberSaveable { mutableStateOf(App.prefs.loadHaptic()) }
    var isFileNameFieldFocused by remember { mutableStateOf(false) }
    var showPenSizePreview by remember { mutableStateOf(false) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        viewModel.loadImage(context, uri)
    }

    val insertLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        viewModel.loadOverlayImage(context, uri)
    }

    val requestSaveAs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let {
            viewModel.saveAs(it, context, density) {
                if (closeOnSave.value) {
                    activity.finishAndRemoveTask()
                    exitProcess(0)
                }
            }
        }
    }

    fun imagePickIntent() = Intent(Intent.ACTION_PICK).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
    }
    LaunchedEffect(incomingIntent) {
        if (hasHandledIncomingIntent) return@LaunchedEffect
        hasHandledIncomingIntent = true
        val intentAction = incomingIntent.action
        val intentUri = when (intentAction) {
            Intent.ACTION_EDIT, Intent.ACTION_VIEW -> incomingIntent.data
            Intent.ACTION_SEND -> {
                if (incomingIntent.type?.startsWith("image/") == true) {
                    IntentCompat.getParcelableExtra(
                        incomingIntent, Intent.EXTRA_STREAM, Uri::class.java
                    )
                } else null
            }

            else -> null
        }
        if (intentUri != null) {
            viewModel.loadImage(context, intentUri)
        } else if (uiState.workingBitmap == null && !uiState.isLoading) {
            pickLauncher.launch(imagePickIntent())
        }
    }

    LaunchedEffect(uiState.mode) {
        if (uiState.mode !is EditorPanel.Text) focusManager.clearFocus()
    }

    fun onOverwrite(mode: DeletionMode) = viewModel.overwrite(
        context, density, mode
    ) {
        if (closeOnSave.value) {
            activity.finishAndRemoveTask()
            exitProcess(0)
        }
    }

    BackHandler(enabled = uiState.workingBitmap != null) {
        when (uiState.mode) {
            is EditorPanel.Transform if uiState.editorSubpanel != null && uiState.editorSubpanel != EditorSubpanel.Transform.Crop -> viewModel.setActivePanel(
                EditorSubpanel.Transform.Crop
            )

            is EditorPanel.Stickers if uiState.stickerPanelState.editTarget != null -> viewModel.selectSticker(
                null
            )

            EditorPanel.None -> viewModel.closeImage()
            else -> viewModel.setMode(EditorPanel.None)
        }
    }

    val toast = remember { SnackbarHostState() }
    val knife = remember { mutableIntStateOf(0) }
    val butter = butter()
    LaunchedEffect(knife.intValue) {
        if (knife.intValue == 0) return@LaunchedEffect
        butter.getOrNull(knife.intValue - 1)?.let { msg ->
            toast.showSnackbar(msg, duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(uiState.workingBitmap == null) {
        if (uiState.workingBitmap == null) {
            toast.currentSnackbarData?.dismiss()
        }
    }

    EditorLayersDrawer(
        state = uiState,
        onRenameLayerGroup = viewModel::renameLayerGroup,
        onMoveLayerGroup = viewModel::moveLayerGroup,
        onDeleteLayerGroup = viewModel::deleteLayerGroup,
        onModeChange = viewModel::setMode,
    ) {
        Scaffold(snackbarHost = {
            SnackbarHost(hostState = toast) { data ->
                val offsetAnim = remember { Animatable(0f) }
                val scope = rememberCoroutineScope()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetAnim.value.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { scope.launch { offsetAnim.stop() } },
                                onDragEnd = {
                                    scope.launch {
                                        val threshold = size.width * 0.3f
                                        val offset = offsetAnim.value
                                        if (offset > threshold || offset < -threshold) {
                                            offsetAnim.animateTo(
                                                if (offset > 0f) size.width.toFloat() else -size.width.toFloat(),
                                                tween(durationMillis = 200)
                                            )
                                            data.dismiss()
                                        } else {
                                            offsetAnim.animateTo(0f, spring())
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { offsetAnim.animateTo(0f, spring()) }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    scope.launch {
                                        offsetAnim.snapTo(
                                            (offsetAnim.value + dragAmount).coerceIn(
                                                -size.width.toFloat(), size.width.toFloat()
                                            )
                                        )
                                    }
                                })
                        }) {
                    if (uiState.workingBitmap == null) {
                        Snackbar(
                            modifier = Modifier.padding(12.dp),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                        ) {
                            Text(data.visuals.message)
                        }
                    }

                }
            }
        }, bottomBar = {
            if (!uiState.isLoading && uiState.workingBitmap != null) {
                BottomEditorBar(
                    state = uiState,
                    onTransformStateChange = viewModel::setTransformState,
                    onApplyResize = viewModel::applyResize,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onActivePanelChange = viewModel::setActivePanel,
                    onSetUndoLevels = viewModel::setUndoLevels,
                    onDrawStateChange = viewModel::setDrawState,
                    onClearStrokes = viewModel::clearStrokes,
                    onAddColor = viewModel::addColor,
                    onRemoveColor = viewModel::removeColor,
                    onTextStateChange = viewModel::setTextState,
                    onCommitTextEdit = viewModel::commitTextEdit,
                    onAddText = { viewModel.addText(density) },
                    onDeleteText = viewModel::deleteSelectedText,
                    onMoveLayer = viewModel::moveLayer,
                    onAdjustChange = viewModel::setAdjust,
                    onStickerStateChange = viewModel::setStickerPanelState,
                    onCommitStickerEdit = viewModel::commitStickerEdit,
                    onInsertSticker = viewModel::insertSticker,
                    onDeleteSticker = viewModel::deleteSticker,
                    focusChanged = { isFileNameFieldFocused = it },
                    onExportFormatChange = viewModel::setExportFormat,
                    onFileNameChange = viewModel::setFileName,
                    onOverwrite = ::onOverwrite,
                    onSaveAs = { requestSaveAs.launch(uiState.customFileName) },
                    onExportQualityChange = viewModel::setExportQuality,
                    onExifDateOnlyChange = viewModel::setExifDateOnly,
                    onLosslessToggle = viewModel::setLosslessEnabled,
                    onOpen = { pickLauncher.launch(imagePickIntent()) },
                    onInsert = { insertLauncher.launch(imagePickIntent()) },
                    onModeChange = viewModel::setMode,
                    closeOnSave = closeOnSave.value,
                    onToggleCloseOnSave = { closeOnSave.value = it; App.prefs.saveCloseOnSave(it) },
                    onToggleEyedropperAutoSwitch = viewModel::setEyedropperAutoSwitch,
                    onDrawSizePreviewChange = { showPenSizePreview = it },
                )
            }
        }) { padding ->
            Box(Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> LoadingIndicator(
                        Modifier
                            .align(Alignment.Center)
                            .size(86.dp)
                    )

                    uiState.workingBitmap == null -> {
                        var showCreateDialog by remember { mutableStateOf(false) }
                        var showThemeDialog by remember { mutableStateOf(false) }
                        val focusController = focusController()
                        val view = LocalView.current
                        SideEffect {
                            val window = (view.context as Activity).window
                            val controller = WindowCompat.getInsetsController(window, view)
                            if (focusController.isContentVisible) {
                                controller.show(WindowInsetsCompat.Type.systemBars())
                            } else {
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        }
                        if (showCreateDialog) {
                            CreateImageDialog(
                                onDismiss = { showCreateDialog = false },
                                onCreate = { w, h, color ->
                                    viewModel.createImage(w, h, color)
                                    showCreateDialog = false
                                },
                            )
                        }
                        if (showThemeDialog) {
                            val onThemeChange = LocalOnThemeChange.current
                            SettingsDialog(
                                current = LocalAppTheme.current,
                                onDismiss = { showThemeDialog = false },
                                onSelect = { onThemeChange(it) },
                                distraction = distraction.value,
                                onDistractionChange = {
                                    distraction.value = it; App.prefs.saveDistraction(it)
                                },
                                haptics = haptics.value,
                                onHapticsChange = { haptics.value = it; App.prefs.saveHaptic(it) },
                            )
                        }
                        val dooblieThings = remember { (4..6).random() }
                        val sky = rememberSky()
                        Box(Modifier.fillMaxSize()) {
                            if (distraction.value) {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sky(sky)
                                ) {
                                    val circleSize = 190.dp
                                    for (i in 0 until dooblieThings) {
                                        key(i) {
                                            Dooble(
                                                size = circleSize,
                                                screenMaxWidth = maxWidth,
                                                screenMaxHeight = maxHeight,
                                                onDrag = focusController::onDrag,
                                            )
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .cloudy(
                                        sky = sky,
                                        radius = 172,
                                        tint = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                    ),
                            ) {
                                AnimatedVisibility(
                                    visible = focusController.isContentVisible,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(padding),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        IconButton(onClick = {
                                            HapticPatterns.tap()
                                            showThemeDialog = true
                                        }) {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = "Theme",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(142.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {
                                                        HapticPatterns.tap()
                                                        knife.intValue++
                                                    }), contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(108.dp)
                                                    .scale(1.8f)
                                            )
                                        }
                                        Row {
                                            Button(
                                                onClick = {
                                                    HapticPatterns.tap(); pickLauncher.launch(
                                                    imagePickIntent()
                                                )
                                                },
                                                shapes = ButtonShapes(
                                                    shape = RoundedCornerShape(21.dp),
                                                    pressedShape = RoundedCornerShape(8.dp)
                                                ),
                                                contentPadding = ButtonDefaults.SmallContentPadding
                                            ) {
                                                Text("Open")
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Button(
                                                onClick = {
                                                    HapticPatterns.tap()
                                                    showCreateDialog = true
                                                },
                                                shapes = ButtonShapes(
                                                    shape = RoundedCornerShape(21.dp),
                                                    pressedShape = RoundedCornerShape(8.dp)
                                                ),
                                                contentPadding = ButtonDefaults.SmallContentPadding
                                            ) { Text("Create") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> ImageCanvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        state = ImageCanvasState(
                            display = ImageDisplayState(
                                bitmap = uiState.workingBitmap!!,
                                rotationDeg = uiState.rotation,
                                aspectRatio = uiState.aspectRatio,
                                panel = uiState.mode,
                                viewCrop = uiState.viewCrop,
                                adjust = uiState.adjust,
                                flipHorizontal = uiState.flipHorizontal,
                                flipVertical = uiState.flipVertical,
                                subpanel = uiState.editorSubpanel,
                                mcuInfo = uiState.mcuInfo,
                                showMcuGrid = uiState.losslessEnabled,
                                isFileNameFieldFocused = isFileNameFieldFocused,
                            ),
                            overlay = ImageOverlayState(
                                layers = uiState.layers,
                                penColor = uiState.penColor,
                                penWidth = uiState.penWidth,
                                drawTool = uiState.drawTool,
                                selectedTextId = uiState.textEditorState.selectedId,
                                selectedStickerId = uiState.stickerPanelState.selectedId,
                                dropperPreview = uiState.dropperPreview,
                            ),
                        ),
                        onStrokeEnd = viewModel::strokeEnd,
                        onEyedropper = { offset ->
                            viewModel.eyedropperPick(
                                offset, density.density * density.fontScale, context
                            )
                        },
                        onEyedropperPreview = { offset ->
                            viewModel.eyedropperPreview(
                                offset, density.density * density.fontScale, context
                            )
                        },
                        onCropChanged = { selection, _ -> viewModel.onCropChanged(selection) },
                        onTextSelect = { id -> viewModel.selectText(id); if (id == null) focusManager.clearFocus() },
                        onDismissKeyboard = { focusManager.clearFocus() },
                        onTextEdit = viewModel::onTextEditOverlay,
                        onTextMove = viewModel::moveText,
                        onTextBoxChange = viewModel::updateTextBox,
                        onStickerSelect = viewModel::selectSticker,
                        onStickerBoxChange = viewModel::updateStickerBox,
                        showPenSizePreview = showPenSizePreview,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    current: AppTheme,
    onDismiss: () -> Unit,
    onSelect: (AppTheme) -> Unit,
    distraction: Boolean,
    onDistractionChange: (Boolean) -> Unit,
    haptics: Boolean,
    onHapticsChange: (Boolean) -> Unit,
) {
    val themeOptions = listOf(
        AppTheme.OLED to "OLED (black)",
        AppTheme.Dynamic to "Dynamic",
        AppTheme.Light to "Warm light",
        AppTheme.Dark to "Warm dark",
    )
    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            TextButton(
                onClick = {
                    HapticPatterns.tap()
                    onDismiss()
                }
            ) { Text("Close") }
        },
        title = { Text("Settings") },
        text = {
            Column {
                themeOptions.forEach { (theme, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = current == theme,
                                onClick = { HapticPatterns.tap(); onSelect(theme) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = current == theme, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                //noinspection MissingHapticFeedback
                SettingsToggleRow("Background effect", distraction, onDistractionChange)
                //noinspection MissingHapticFeedback
                SettingsToggleRow("Haptic feedback", haptics, onHapticsChange, false)
            }
        },
    )
}

@Composable
private fun SettingsToggleRow(
    label: String, value: Boolean, onValueChange: (Boolean) -> Unit, haptic: Boolean = true
) {
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = value,
                onValueChange = {
                    if (haptic) HapticPatterns.tap()
                    onValueChange(it)
                },
                role = Role.Checkbox,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = value, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}


@Composable
fun CreateImageDialog(
    onDismiss: () -> Unit,
    onCreate: (width: Int, height: Int, color: Color) -> Unit,
) {
    var widthInput by remember { mutableStateOf("1080") }
    var heightInput by remember { mutableStateOf("1080") }
    var fillColor by remember { mutableStateOf(App.prefs.loadNewImageColor()) }
    val showColorEditor = remember { mutableStateOf(false) }

    if (showColorEditor.value) {
        ColorEditorDialog(
            initial = fillColor,
            onDismiss = { showColorEditor.value = false },
            onConfirm = {
                fillColor = it; App.prefs.saveNewImageColor(it); showColorEditor.value = false
            },
            checkIcon = true
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Create", style = MaterialTheme.typography.headlineSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = widthInput,
                        onValueChange = {
                            widthInput =
                                it.filter { c -> c.isDigit() || c in MATH_SYMBOLS }.take(20)
                        },
                        label = { Text("Width") },
                        suffix = { Text("px") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                evaluateMath(widthInput)?.let { widthInput = it.toString() }
                            }),
                        modifier = Modifier.weight(1f),
                    )
                    Text("×", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = {
                            heightInput =
                                it.filter { c -> c.isDigit() || c in MATH_SYMBOLS }.take(20)
                        },
                        label = { Text("Height") },
                        suffix = { Text("px") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                evaluateMath(heightInput)?.let { heightInput = it.toString() }
                            }),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Background", style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    HapticPatterns.tap(); showColorEditor.value = true
                                })
                            },
                        shape = CircleShape,
                        color = if (fillColor.alpha == 0f) Color.Transparent else fillColor,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        if (fillColor.alpha == 0f) Checkerboard()
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { HapticPatterns.tap(); onDismiss() }
                    ) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            HapticPatterns.tap()
                            val evaluatedWidth = evaluateMath(widthInput)
                            val evaluatedHeight = evaluateMath(heightInput)
                            if (evaluatedWidth == null || evaluatedHeight == null) return@Button
                            if (evaluatedWidth < 1 || evaluatedHeight < 1) return@Button
                            if (evaluatedWidth >= 10000 || evaluatedHeight >= 10000) {
                                AppToasts.show("Woah there")
                                return@Button
                            }
                            widthInput = evaluatedWidth.toString()
                            heightInput = evaluatedHeight.toString()
                            onCreate(evaluatedWidth, evaluatedHeight, fillColor)
                        }, shapes = ButtonShapes(
                            shape = RoundedCornerShape(21.dp),
                            pressedShape = RoundedCornerShape(8.dp)
                        ), contentPadding = ButtonDefaults.SmallContentPadding
                    ) { Text("Create") }
                }
            }
        }
    }
}
