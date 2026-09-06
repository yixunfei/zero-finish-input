package dev.zeroinput.ime.testing

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** Instrumentation-only editor, with no saved text or exported entry point. */
class InputFixtureActivity : Activity() {
    lateinit var editor: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        editor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSaveEnabled = false
        }
        setContentView(editor)
        editor.requestFocus()
        editor.post { getSystemService(InputMethodManager::class.java).showSoftInput(editor, 0) }
    }

    override fun onSaveInstanceState(outState: Bundle) { /* Fixture text is never saved. */ }
}
