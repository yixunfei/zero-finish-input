package dev.zeroinput.ime

import android.os.Bundle
import android.os.Debug
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.engine.api.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Aggregate measurements from public constructed text; never captures real editor data. */
@RunWith(AndroidJUnit4::class)
class InputResourceTest {
    @Test fun normalInputResources() = measure(ChineseInputOptions(), "normal")
    @Test fun experimentalInputResources() = measure(ChineseInputOptions(experimentalTypoCorrection = true), "typo")

    private fun measure(options: ChineseInputOptions, label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val graph = (instrumentation.targetContext.applicationContext as ZeroInputApplication).graph
        val report = graph.engineExecutor.submit<Bundle> {
            val start = System.nanoTime()
            checkNotNull(graph.rime.createNativeOrNull(options)).use { engine ->
                val prepared = System.nanoTime() - start
                engine.start(EditorContext(InputLanguage.CHINESE, false, false, null))
                repeat(10) { type(engine, "nihaozhongguoshijie"); engine.reset() }
                val before = Debug.getRuntimeStats()
                val memoryBefore = memory()
                val cpu = Process.getElapsedCpuTime()
                val samples = ArrayList<Long>()
                repeat(100) {
                    for (ch in "nihaozhongguoshijie") {
                        val keyStart = System.nanoTime()
                        engine.handle(EngineKey.Character(ch.toString()))
                        samples += System.nanoTime() - keyStart
                    }
                    engine.reset()
                }
                val elapsedCpu = Process.getElapsedCpuTime() - cpu
                val after = Debug.getRuntimeStats()
                val memoryAfter = memory()
                val paging = ArrayList<Long>()
                type(engine, "neng")
                repeat(80) {
                    if (engine.snapshot.hasNextPage) {
                        val pageStart = System.nanoTime()
                        engine.changePage(PageDirection.NEXT)
                        paging += System.nanoTime() - pageStart
                    }
                }
                Bundle().apply {
                    putString("${label}_prepare_ms", (prepared / 1_000_000).toString())
                    putString("${label}_cpu_ms", elapsedCpu.toString())
                    putString("${label}_pss_before_kb", memoryBefore.toString())
                    putString("${label}_pss_after_kb", memoryAfter.toString())
                    putString("${label}_native_heap_kb", (Debug.getNativeHeapAllocatedSize() / 1024).toString())
                    for (metric in listOf("art.gc.bytes-allocated", "art.gc.gc-count", "art.gc.gc-time")) {
                        putString("${label}_$metric", ((after[metric]?.toLongOrNull() ?: 0) -
                            (before[metric]?.toLongOrNull() ?: 0)).toString())
                    }
                    distribution(this, label, samples)
                    distribution(this, "${label}_page", paging)
                }
            }
        }.get(90, TimeUnit.SECONDS)
        instrumentation.sendStatus(0, report)
    }

    private fun memory(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
    private fun type(engine: InputEngine, value: String) { value.forEach { engine.handle(EngineKey.Character(it.toString())) } }
    private fun distribution(report: Bundle, label: String, samples: List<Long>) {
        if (samples.isEmpty()) return
        val sorted = samples.sorted()
        report.putString("${label}_samples", samples.size.toString())
        for (percentile in listOf(50, 95, 99, 100)) {
            report.putString("${label}_p${percentile}_us", (sorted[(sorted.size * percentile / 100)
                .coerceAtMost(sorted.lastIndex)] / 1000).toString())
        }
    }
}
