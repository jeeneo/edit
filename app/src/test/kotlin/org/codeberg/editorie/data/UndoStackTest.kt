package org.codeberg.editorie.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    private fun entry(vararg layers: EditorLayer) = UndoEntry.LayersChange(layers.toList())

    @Test
    fun emptyStackCannotUndo() {
        val stack = UndoStack()
        assertFalse(stack.canUndo)
        assertTrue(stack.entries.isEmpty())
    }

    @Test
    fun popOnEmptyReturnsNull() {
        val (entry, next) = UndoStack().pop()
        assertNull(entry)
        assertTrue(next.entries.isEmpty())
    }

    @Test
    fun pushEnablesUndo() {
        val stack = UndoStack().push(entry())
        assertTrue(stack.canUndo)
        assertEquals(1, stack.entries.size)
    }

    @Test
    fun pushIsImmutable() {
        val original = UndoStack()
        original.push(entry())
        assertFalse(original.canUndo)
    }

    @Test
    fun popReturnsLastPushed() {
        val first = entry()
        val second = entry()
        val stack = UndoStack().push(first).push(second)

        val (popped, next) = stack.pop()
        assertSame(second, popped)
        assertEquals(1, next.entries.size)

        val (popped2, next2) = next.pop()
        assertSame(first, popped2)
        assertFalse(next2.canUndo)
    }

    @Test
    fun maxDepthDropsOldestEntries() {
        val entries = List(3) { entry() }
        var stack = UndoStack(maxDepth = 2)
        entries.forEach { stack = stack.push(it) }

        assertEquals(2, stack.entries.size)
        assertSame(entries[1], stack.entries[0])
        assertSame(entries[2], stack.entries[1])
    }

    @Test
    fun defaultMaxDepthIsTwenty() {
        assertEquals(20, UndoStack.DEFAULT_MAX_UNDO)
        var stack = UndoStack()
        repeat(25) { stack = stack.push(entry()) }
        assertEquals(20, stack.entries.size)
    }

    @Test
    fun redoOnEmptyReturnsNull() {
        val (entry, next) = UndoStack().redo()
        assertNull(entry)
        assertFalse(next.canRedo)
    }

    @Test
    fun pushRedoEnablesRedoAndRedoRestores() {
        val before = entry()
        val after = entry()
        val stack = UndoStack().push(before).pop().second.pushRedo(after)

        assertTrue(stack.canRedo)
        assertFalse(stack.canUndo)

        val (redone, afterRedo) = stack.redo()
        assertSame(after, redone)
        assertFalse(afterRedo.canRedo)
    }

    @Test
    fun pushClearsRedo() {
        val before = entry()
        val after = entry()
        val second = entry()
        val afterUndo = UndoStack().push(before).pop().second.pushRedo(after)
        assertTrue(afterUndo.canRedo)

        val afterPush = afterUndo.push(second)
        assertFalse(afterPush.canRedo)
        assertSame(second, afterPush.entries.last())
    }

    @Test
    fun redoSequenceRestoresInOrder() {
        val firstBefore = entry()
        val firstAfter = entry()
        val secondBefore = entry()
        val secondAfter = entry()
        val stack = UndoStack()
            .push(firstBefore)
            .pop().second
            .pushUndo(firstAfter)
            .push(secondBefore)
            .pop().second
            .pushUndo(secondAfter)
            .pushRedo(firstAfter)
            .pushRedo(secondAfter)

        assertTrue(stack.canUndo)
        assertTrue(stack.canRedo)

        val (r1, s3) = stack.redo()
        assertSame(secondAfter, r1)
        val (r2, s4) = s3.redo()
        assertSame(firstAfter, r2)
        assertFalse(s4.canRedo)
    }

    @Test
    fun pushUndoRestoresUndoHistory() {
        val before = entry()
        val after = entry()
        val stack = UndoStack().push(before).pop().second.pushRedo(after)

        val (redone, afterRedo) = stack.redo()
        assertSame(after, redone)

        val afterPushUndo = afterRedo.pushUndo(before)
        assertTrue(afterPushUndo.canUndo)
        assertSame(before, afterPushUndo.entries.last())
    }
}
