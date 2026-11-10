package dev.bastian.pixela.domain.history

import dev.bastian.pixela.domain.Document

/** Undo and redo stacks of [Command]s, capped at [limit] steps. */
class History(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoSize: Int get() = undoStack.size

    /** Applies [command] and records it. */
    fun execute(document: Document, command: Command) {
        command.apply(document)
        record(command)
    }

    /** Records a command whose effect is already on the document (a stroke drawn live). */
    fun record(command: Command) {
        undoStack.addLast(command)
        redoStack.clear()
        while (undoStack.size > limit) undoStack.removeFirst()
    }

    fun undo(document: Document): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        command.revert(document)
        redoStack.addLast(command)
        return true
    }

    fun redo(document: Document): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        command.apply(document)
        undoStack.addLast(command)
        return true
    }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
