package dev.zeroinput.ime.core

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class EditorInputOptionsTest {
    @Test fun numberPasswordKeepsNumericGeometryAndDecimalAndSignAreExplicit() {
        fun options(type: Int) = EditorInputOptions.from(EditorInfo().apply { inputType = type })
        assertEquals(EditorLayout.NUMBER, options(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD).layout)
        assertFalse(options(InputType.TYPE_CLASS_NUMBER).decimal)
        assertFalse(options(InputType.TYPE_CLASS_NUMBER).signed)
        val decimal = options(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        assertTrue(decimal.decimal && decimal.signed)
        assertEquals(EditorLayout.PHONE, options(InputType.TYPE_CLASS_PHONE).layout)
        assertEquals(EditorLayout.TEXT, options(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS).layout)
    }

    @Test fun enterPresentationHonorsSuppressedActions() {
        val editor = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEND }
        assertEquals(EnterAction.SEND, EditorInputOptions.from(editor).enterAction)
        editor.imeOptions = editor.imeOptions or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals(EnterAction.NEW_LINE, EditorInputOptions.from(editor).enterAction)
    }
}
