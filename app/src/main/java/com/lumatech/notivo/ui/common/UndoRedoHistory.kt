package com.lumatech.notivo.ui.common

/**
 * Tracks text snapshots for Undo/Redo operations in the note editor.
 */
class UndoRedoHistory(private val maxHistory: Int = 50) {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var currentSnapshot: String = ""

    fun reset(initialText: String) {
        undoStack.clear()
        redoStack.clear()
        currentSnapshot = initialText
    }

    fun recordChange(newText: String) {
        if (newText == currentSnapshot) return
        undoStack.addLast(currentSnapshot)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        currentSnapshot = newText
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): String? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentSnapshot)
        currentSnapshot = undoStack.removeLast()
        return currentSnapshot
    }

    fun redo(): String? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentSnapshot)
        currentSnapshot = redoStack.removeLast()
        return currentSnapshot
    }
}
