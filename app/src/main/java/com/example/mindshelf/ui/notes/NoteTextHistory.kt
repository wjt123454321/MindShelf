package com.example.mindshelf.ui.notes

data class NoteSnapshot(
    val title: String,
    val content: String,
)

class NoteTextHistory(
    initial: NoteSnapshot,
    private val maxSize: Int = 64,
) {
    private val undoStack = ArrayDeque<NoteSnapshot>()
    private val redoStack = ArrayDeque<NoteSnapshot>()
    private var baseline = initial

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(snapshot: NoteSnapshot) {
        if (snapshot == baseline) return
        undoStack.addLast(baseline)
        while (undoStack.size > maxSize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        baseline = snapshot
    }

    fun undo(): NoteSnapshot? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(baseline)
        baseline = undoStack.removeLast()
        return baseline
    }

    fun redo(): NoteSnapshot? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(baseline)
        baseline = redoStack.removeLast()
        return baseline
    }

    fun reset(snapshot: NoteSnapshot) {
        undoStack.clear()
        redoStack.clear()
        baseline = snapshot
    }
}
