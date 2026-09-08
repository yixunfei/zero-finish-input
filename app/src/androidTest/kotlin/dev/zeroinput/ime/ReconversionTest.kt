package dev.zeroinput.ime

import android.text.InputType
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.engine.api.*
import dev.zeroinput.ime.core.*
import dev.zeroinput.ime.core.privacy.PrivacyConfiguration
import dev.zeroinput.ime.input.AndroidEditorConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReconversionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun nativeCommitCanBeReopenedAndReselectedWithoutDuplicatingOrDeletingSurroundingText() {
        val graph = (instrumentation.targetContext.applicationContext as ZeroInputApplication).graph
        val engine = graph.engineExecutor.submit<InputEngine> {
            checkNotNull(graph.rime.createNativeOrNull())
        }.get(60, TimeUnit.SECONDS)
        onMain {
            val editor = editor("前缀")
            val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            val connection = checkNotNull(editor.onCreateInputConnection(info))
            val adapter = AndroidEditorConnection(2, 2) { connection }
            val controller = InputSessionController(adapter, { engine }, EmptyPersonalization)
            controller.start(info, InputLanguage.CHINESE, PrivacyConfiguration())
            try {
                "nihao".forEach { controller.handle(InputCommand.Text(it.toString())) }
                controller.handle(InputCommand.SelectCandidate(controller.state.snapshot.candidates.indexOfFirst { it.text == "你好" }))
                adapter.updateSelection(editor.selectionStart, editor.selectionEnd)
                assertEquals("前缀你好", editor.text.toString())
                assertTrue(controller.state.canReconvert)
                controller.handle(InputCommand.ReconvertLast)
                assertTrue(controller.state.snapshot.isComposing)
                assertEquals(2, BaseInputConnection.getComposingSpanStart(editor.text))
                controller.handle(InputCommand.SelectSyllable)
                controller.handle(InputCommand.SelectCandidate(controller.state.snapshot.candidates.indexOfFirst { it.text == "你" }))
                controller.handle(InputCommand.SelectCandidate(controller.state.snapshot.candidates.indexOfFirst { it.text == "好" }))
                assertEquals("前缀你好", editor.text.toString())
                assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.text))
            } finally { controller.close() }
        }
    }

    @Test fun changedTextCursorAndConnectionRejectReopening() = onMain {
        for (mutation in 0..2) {
            val editor = editor("")
            var connection = checkNotNull(editor.onCreateInputConnection(EditorInfo()))
            val adapter = AndroidEditorConnection(0, 0) { connection }
            adapter.commitText("你好")
            adapter.updateSelection(2, 2)
            when (mutation) {
                0 -> { editor.setText("世界"); editor.setSelection(2) }
                1 -> { editor.setSelection(0); adapter.updateSelection(0, 0) }
                2 -> connection = checkNotNull(editor("").onCreateInputConnection(EditorInfo()))
            }
            val before = editor.text.toString()
            assertFalse(adapter.reopenCommittedText("你好"))
            assertEquals(before, editor.text.toString())
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.text))
        }
    }

    private fun editor(value: String) = EditText(instrumentation.targetContext).apply {
        inputType = InputType.TYPE_CLASS_TEXT
        setText(value)
        setSelection(value.length)
    }

    private fun onMain(action: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        checkNotNull(result).getOrThrow()
    }

    private object EmptyPersonalization : PersonalizationStore {
        override fun suggestionsFor(prefix: String, language: InputLanguage, limit: Int) = emptyList<PersonalSuggestion>()
        override fun learn(shortcut: String, value: String, language: InputLanguage, learningAllowed: Boolean) = Unit
        override fun recordUse(id: String, learningAllowed: Boolean) = Unit
    }
}
