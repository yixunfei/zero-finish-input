package dev.zeroinput.ime.core

interface EditorConnection {
    fun setComposingText(text: String)

    fun finishComposingText()

    /**
     * Removes the current composing span without committing its pre-edit
     * contents.  Android's [finishComposingText] alone makes the pre-edit
     * text permanent, which is not what an IME reset/cancel operation wants.
     */
    fun clearComposingText() {
        setComposingText("")
        finishComposingText()
    }

    fun commitText(text: String)

    fun deleteBeforeCursor()

    fun performEditorAction(actionId: Int): Boolean

    fun sendEnterKey()
}
