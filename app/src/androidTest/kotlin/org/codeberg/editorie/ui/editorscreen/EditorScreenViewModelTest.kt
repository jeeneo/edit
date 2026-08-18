package org.codeberg.editorie.ui.editorscreen

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.codeberg.editorie.data.EditorLayer
import org.codeberg.editorie.data.EditorPanel
import org.codeberg.editorie.data.EditorState
import org.codeberg.editorie.data.UserPreferences
import org.codeberg.editorie.options.save.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorScreenViewModelTest {

    private lateinit var vm: EditorScreenViewModel
    private val state: EditorState get() = vm.uiState.value

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        vm = EditorScreenViewModel()
    }

    private fun awaitState(timeoutMs: Long = 5000, predicate: (EditorState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(vm.uiState.value)) return
            Thread.sleep(20)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    private fun drawStroke(a: Offset, b: Offset? = null) {
        vm.strokeEnd(listOfNotNull(a, b), strokeWidthPx = 5f)
    }

    @Test
    fun initialStateHasNoImageAndNoUndo() {
        assertNull(state.workingBitmap)
        assertFalse(state.canUndo)
        assertTrue(state.layers.isEmpty())
        assertEquals(EditorPanel.None, state.mode)
    }

    @Test
    fun strokeEndAddsLayerAndEnablesUndo() {
        drawStroke(Offset(0f, 0f), Offset(10f, 10f))
        assertEquals(1, state.layers.size)
        assertTrue(state.canUndo)
    }

    @Test
    fun singlePointStrokeIsRejected() {
        drawStroke(Offset(0f, 0f))
        assertTrue(state.layers.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun undoRemovesLastStroke() {
        drawStroke(Offset(0f, 0f), Offset(10f, 10f))
        drawStroke(Offset(20f, 20f), Offset(30f, 30f))
        assertEquals(2, state.layers.size)

        vm.undo()
        assertEquals(1, state.layers.size)
        vm.undo()
        assertTrue(state.layers.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun undoOnEmptyStackIsNoop() {
        vm.undo()
        assertTrue(state.layers.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun undoLevelsLimitDepth() {
        vm.setUndoLevels(3)
        repeat(5) { i ->
            drawStroke(Offset(i.toFloat(), 0f), Offset(i.toFloat(), 10f))
        }
        assertEquals(5, state.layers.size)

        vm.undo()
        vm.undo()
        vm.undo()
        assertFalse(state.canUndo)
        assertEquals(2, state.layers.size)
    }

    @Test
    fun clearStrokesRemovesOnlyStrokes() {
        drawStroke(Offset(0f, 0f), Offset(10f, 10f))
        vm.clearStrokes()
        assertTrue(state.layers.none { it is EditorLayer.Stroke })
        vm.undo()
        assertEquals(1, state.layers.size)
    }

    @Test
    fun strokesInOneSessionShareAGroup() {
        drawStroke(Offset(0f, 0f), Offset(1f, 1f))
        drawStroke(Offset(2f, 2f), Offset(3f, 3f))
        assertEquals(1, state.layerGroups.size)
        assertEquals(2, state.layerGroups.single().layers.size)
    }

    @Test
    fun commitLayerGroupSessionStartsNewGroup() {
        drawStroke(Offset(0f, 0f), Offset(1f, 1f))
        vm.commitLayerGroupSession()
        drawStroke(Offset(2f, 2f), Offset(3f, 3f))
        assertEquals(2, state.layerGroups.size)
        assertNotEquals(state.layers[0].groupId, state.layers[1].groupId)
    }

    @Test
    fun renameLayerGroupTrimsAndCaps() {
        drawStroke(Offset(0f, 0f), Offset(1f, 1f))
        val groupId = state.layers.single().groupId

        vm.renameLayerGroup(groupId, "  my name  ")
        assertEquals("my name", state.layerGroups.single().displayName)

        vm.renameLayerGroup(groupId, "x".repeat(100))
        assertEquals(60, state.layerGroups.single().displayName.length)

        vm.renameLayerGroup(groupId, "   ")
        assertNull(state.layerGroups.single().customName)
    }

    @Test
    fun deleteLayerGroupRemovesItsLayers() {
        drawStroke(Offset(0f, 0f), Offset(1f, 1f))
        vm.commitLayerGroupSession()
        drawStroke(Offset(2f, 2f), Offset(3f, 3f))
        val firstGroup = state.layers[0].groupId

        vm.deleteLayerGroup(firstGroup)
        assertEquals(1, state.layers.size)
        assertTrue(state.layers.none { it.groupId == firstGroup })
        assertFalse(state.layerGroupMeta.containsKey(firstGroup))
    }

    @Test
    fun moveLayerGroupReordersGroups() {
        drawStroke(Offset(0f, 0f), Offset(1f, 1f))
        vm.commitLayerGroupSession()
        drawStroke(Offset(2f, 2f), Offset(3f, 3f))
        val first = state.layers[0].groupId
        val second = state.layers[1].groupId

        vm.moveLayerGroup(first, +1)
        assertEquals(listOf(second, first), state.layerGroups.map { it.groupId })
    }

    @Test
    fun moveLayerWithinGroup() {
        drawStroke(Offset(0f, 0f), Offset(1f, 1f))
        drawStroke(Offset(2f, 2f), Offset(3f, 3f))
        val firstId = state.layers[0].id
        val secondId = state.layers[1].id

        vm.moveLayer(firstId, +1)
        assertEquals(listOf(secondId, firstId), state.layers.map { it.id })
    }

    @Test
    fun setModetogglesOffWhenRepeated() {
        vm.setMode(EditorPanel.Draw)
        assertEquals(EditorPanel.Draw, state.mode)
        vm.setMode(EditorPanel.Draw)
        assertEquals(EditorPanel.None, state.mode)
    }

    @Test
    fun setModeSwitchesBetweenModes() {
        vm.setMode(EditorPanel.Draw)
        vm.setMode(EditorPanel.Adjust)
        assertEquals(EditorPanel.Adjust, state.mode)
    }

    @Test
    fun invalidCropRectIsIgnored() {
        vm.onCropChanged(Rect(50f, 50f, 10f, 10f))
        assertNull(state.viewCrop)
    }

    @Test
    fun validCropRectIsStored() {
        val rect = Rect(10f, 10f, 50f, 50f)
        vm.onCropChanged(rect)
        assertEquals(rect, state.viewCrop)
        vm.onCropChanged(null)
        assertNull(state.viewCrop)
    }

    @Test
    fun fileNameFollowsExportFormat() {
        vm.setFileName("photo.jpeg")
        vm.setExportFormat(ExportFormat.PNG)
        assertEquals("photo.png", state.customFileName)
        assertEquals(ExportFormat.PNG, state.exportFormat)

        vm.setExportFormat(ExportFormat.WEBP)
        assertEquals("photo.webp", state.customFileName)
    }

    @Test
    fun exportQualityIsPersisted() {
        vm.setExportQuality(70)
        assertEquals(70, state.exportQuality)
        assertEquals(70, EditorScreenViewModel().uiState.value.exportQuality)
    }

    @Test
    fun losslessToggleIntent() {
        vm.setLosslessEnabled(true)
        assertTrue(state.losslessEnabled)
        vm.setLosslessEnabled(false)
        assertFalse(state.losslessEnabled)
    }

    @Test
    fun createImageProducesBitmapOfRequestedSize() {
        vm.createImage(100, 50, Color.Red)
        awaitState { it.workingBitmap != null }
        assertEquals(100, state.workingBitmap!!.width)
        assertEquals(50, state.workingBitmap!!.height)
        assertEquals("100", state.widthInput)
        assertEquals("50", state.heightInput)
        assertEquals(ExportFormat.PNG, state.exportFormat)
        assertEquals("image.png", state.customFileName)
    }

    @Test
    fun createImageRejectsInvalidDimensions() {
        vm.createImage(0, 10, Color.Red)
        Thread.sleep(200)
        assertNull(state.workingBitmap)
    }

    @Test
    fun applyTransformRotatesBitmapAndIsUndoable() {
        vm.createImage(100, 50, Color.Red)
        awaitState { it.workingBitmap != null }

        vm.setTransformState(state.transformState.copy(rotation = 90f))
        runBlocking { vm.applyTransform() }

        assertEquals(50, state.workingBitmap!!.width)
        assertEquals(100, state.workingBitmap!!.height)
        assertEquals(0f, state.rotation)
        assertEquals("50", state.widthInput)
        assertTrue(state.canUndo)

        vm.undo()
        assertEquals(100, state.workingBitmap!!.width)
        assertEquals(50, state.workingBitmap!!.height)
    }

    @Test
    fun applyTransformWithoutChangesIsNoop() {
        vm.createImage(40, 40, Color.Red)
        awaitState { it.workingBitmap != null }
        val bitmap = state.workingBitmap

        runBlocking { vm.applyTransform() }
        assertEquals(bitmap, state.workingBitmap)
        assertFalse(state.canUndo)
    }

    @Test
    fun applyTransformCropsToSelection() {
        vm.createImage(100, 100, Color.Red)
        awaitState { it.workingBitmap != null }

        vm.onCropChanged(Rect(10f, 20f, 60f, 50f))
        runBlocking { vm.applyTransform() }

        assertEquals(50, state.workingBitmap!!.width)
        assertEquals(30, state.workingBitmap!!.height)
        assertNull(state.viewCrop)
    }

    @Test
    fun closeImageResetsState() {
        vm.createImage(40, 40, Color.Red)
        awaitState { it.workingBitmap != null }
        drawStroke(Offset(0f, 0f), Offset(1f, 1f))

        vm.closeImage()
        assertNull(state.workingBitmap)
        assertTrue(state.layers.isEmpty())
        assertFalse(state.canUndo)
        assertEquals(EditorPanel.None, state.mode)
        assertEquals("", state.customFileName)
    }
}
