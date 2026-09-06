package dev.zeroinput.ime

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.engine.api.EditorContext
import dev.zeroinput.engine.api.EngineKey
import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.engine.api.Candidate
import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseScript
import dev.zeroinput.engine.api.EngineSnapshot
import dev.zeroinput.ime.core.InputSessionState
import dev.zeroinput.ime.ui.ZeroInputView
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Measures fixed public fixtures only; no editor or personal data is collected. */
@RunWith(AndroidJUnit4::class)
class InputLatencyTest {
    @Test
    fun candidateLayoutLatency() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val samples = mutableListOf<Long>()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_ZeroInput_InputMethod)
            val panel = ZeroInputView(context)
            val width = (320 * context.resources.displayMetrics.density).toInt()
            repeat(100) { round ->
                val snapshot = EngineSnapshot("nihao", "ni hao", List(8) {
                    Candidate("fixture:$round:$it", if (it % 2 == 0) "你好" else "你")
                }, hasNextPage = true)
                val start = System.nanoTime()
                panel.renderSession(InputSessionState(snapshot = snapshot))
                panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(width * 2, View.MeasureSpec.AT_MOST))
                panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
                if (round >= 20) samples += System.nanoTime() - start
            }
        }
        samples.sort()
        instrumentation.sendStatus(0, Bundle().apply {
            putString("layout_p50_us", (samples[samples.size / 2] / 1000).toString())
            putString("layout_p95_us", (samples[samples.size * 95 / 100] / 1000).toString())
        })
    }

    @Test
    fun nativeKeyLatency() = measureNative(ChineseInputOptions(), "latency")

    @Test
    fun strictPinyinKeyLatency() = measureNative(ChineseInputOptions(
        script = ChineseScript.TRADITIONAL, abbreviatedPinyin = false,
    ), "strict")

    private fun measureNative(options: ChineseInputOptions, prefix: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val graph = (instrumentation.targetContext.applicationContext as ZeroInputApplication).graph
        val samples = graph.engineExecutor.submit<List<Long>> {
            check(graph.rime.runtime.isReady)
            checkNotNull(graph.rime.createNativeOrNull(options)).use { engine ->
                engine.start(EditorContext(InputLanguage.CHINESE, false, false, null))
                val times = mutableListOf<Long>()
                repeat(70) { round ->
                    engine.reset()
                    for (character in "nihaozhongguoshijie") {
                        val start = System.nanoTime()
                        engine.handle(EngineKey.Character(character.toString()))
                        if (round >= 10) times += System.nanoTime() - start
                    }
                }
                times.sorted()
            }
        }.get(60, TimeUnit.SECONDS)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("${prefix}_samples", samples.size.toString())
            putString("${prefix}_p50_us", (samples[samples.size / 2] / 1000).toString())
            putString("${prefix}_p95_us", (samples[samples.size * 95 / 100] / 1000).toString())
        })
    }
}
