package dev.zeroinput.ime.core

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class EditorLayout { TEXT, NUMBER, PHONE, DATETIME }
enum class EnterAction { NEW_LINE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

/** Only public editor configuration crosses into presentation; no editor text is retained. */
data class EditorInputOptions(
    val layout: EditorLayout = EditorLayout.TEXT,
    val signed: Boolean = false,
    val decimal: Boolean = false,
    val enterAction: EnterAction = EnterAction.NEW_LINE,
) {
    companion object {
        fun from(info: EditorInfo): EditorInputOptions {
            val layout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER -> EditorLayout.NUMBER
                InputType.TYPE_CLASS_PHONE -> EditorLayout.PHONE
                InputType.TYPE_CLASS_DATETIME -> EditorLayout.DATETIME
                else -> EditorLayout.TEXT
            }
            return EditorInputOptions(layout,
                info.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0,
                info.inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0,
                enterAction(info))
        }

        fun enterAction(info: EditorInfo): EnterAction {
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.NEW_LINE
            return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_GO -> EnterAction.GO
                EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
                EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
                EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
                EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
                EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
                else -> EnterAction.NEW_LINE
            }
        }
    }
}
