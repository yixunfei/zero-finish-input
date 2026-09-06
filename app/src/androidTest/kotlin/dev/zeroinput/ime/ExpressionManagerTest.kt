package dev.zeroinput.ime

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.ime.expressions.ExpressionManagerActivity
import dev.zeroinput.ime.testing.ExpressionManagerFixtureActivity
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ExpressionManagerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository get() = ExpressionManagerFixtureActivity.fixtureRepository

    @Test fun customEditorCreatesEditsAndDeletesOnlyItsPublicFixture() {
        val suffix = UUID.randomUUID().toString().take(8)
        val text = "(fixture-$suffix)"
        var activity: ExpressionManagerActivity? = null
        try {
            repository.clear()
            activity = launch()
            val firstActivity = activity
            await { roots().any { it.contentDescription?.toString() == firstActivity.getString(R.string.expression_add_custom) } }
            onMain {
                roots().first { it.contentDescription?.toString() == firstActivity.getString(R.string.expression_add_custom) }.performClick()
            }
            await { roots().filterIsInstance<EditText>().size == 3 }
            onMain {
                val fields = roots().filterIsInstance<EditText>()
                fields[0].setText(text)
                fields[1].setText("Public fixture")
                fields[2].setText("hello fixture")
                button(firstActivity.getString(R.string.save)).performClick()
            }
            await { roots().filterIsInstance<EditText>().isEmpty() && roots().filterIsInstance<TextView>().any { it.text.toString() == text } }
            val saved = repository.read().custom.single { it.value == text }
            assertEquals("hello fixture", saved.keywords)
            onMain { firstActivity.finish() }
            activity = launch(saved.id)
            await { roots().filterIsInstance<EditText>().size == 3 }
            onMain {
                val fields = roots().filterIsInstance<EditText>()
                assertEquals(text, fields[0].text.toString())
                fields[0].setText("$text!")
                button(activity.getString(R.string.save)).performClick()
            }
            await { roots().filterIsInstance<EditText>().isEmpty() && roots().filterIsInstance<TextView>().any { it.text.toString() == "$text!" } }
            onMain {
                val value = roots().filterIsInstance<TextView>().first { it.text.toString() == "$text!" }
                val row = value.parent.parent as ViewGroup
                descendants(row).first { it.contentDescription?.toString() == activity.getString(R.string.expression_delete_custom) }.performClick()
                button(activity.getString(R.string.delete)).performClick()
            }
            await { roots().filterIsInstance<TextView>().none { it.text.toString() == "$text!" } }
            assertTrue(repository.read().custom.none { it.id == saved.id })
        } finally {
            activity?.let { onMain { it.finish() } }
            repository.clear()
        }
    }

    @Test fun managementAndDraftsAreSecureAndLeavingDiscardsUnsavedContent() {
        var activity: ExpressionManagerActivity? = null
        try {
            activity = launch()
            await { roots().any { it.contentDescription?.toString() == activity.getString(R.string.expression_add_custom) } }
            onMain {
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                roots().first { it.contentDescription?.toString() == activity.getString(R.string.expression_add_custom) }.performClick()
            }
            await { roots().filterIsInstance<EditText>().size == 3 }
            val fields = mutableListOf<EditText>()
            onMain {
                fields += roots().filterIsInstance<EditText>()
                fields[0].setText("(unsaved-public-fixture)")
                assertTrue(fields.all { !it.isSaveEnabled && it.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 })
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            onMain { assertTrue(fields.all { it.text.isEmpty() }) }
        } finally { activity?.let { onMain { it.finish() } } }
    }

    private fun launch(id: String? = null) = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext, ExpressionManagerFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ExpressionManagerActivity.EDIT_ID, id)) as ExpressionManagerActivity

    private fun button(label: String) = roots().filterIsInstance<TextView>().first { it.isShown && it.text.toString() == label }
    private fun roots() = WindowInspector.getGlobalWindowViews().flatMap(::descendants).filter { it.isShown }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Expression fixture UI did not reach the expected state")
    }
    private fun onMain(action: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        checkNotNull(result).getOrThrow()
    }
}
