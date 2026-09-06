package dev.zeroinput.ime.input

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.zeroinput.ime.core.EditorConnection

class AndroidEditorConnection(
    private val current: () -> InputConnection?,
) : EditorConnection {
    override fun setComposingText(text: String) {
        current()?.setComposingText(text, 1)
    }

    override fun finishComposingText() {
        current()?.finishComposingText()
    }

    override fun commitText(text: String) {
        current()?.commitText(text, 1)
    }

    override fun deleteBeforeCursor() {
        val connection = current() ?: return
        val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val deleteLength = if (
            before.length >= 2 &&
            Character.isLowSurrogate(before[before.lastIndex]) &&
            Character.isHighSurrogate(before[before.lastIndex - 1])
        ) {
            2
        } else {
            1
        }
        if (!connection.deleteSurroundingText(deleteLength, 0)) sendKey(connection, KeyEvent.KEYCODE_DEL)
    }

    override fun performEditorAction(actionId: Int): Boolean =
        current()?.performEditorAction(actionId) == true

    override fun sendEnterKey() {
        current()?.let { sendKey(it, KeyEvent.KEYCODE_ENTER) }
    }

    private fun sendKey(connection: InputConnection, keyCode: Int) {
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
