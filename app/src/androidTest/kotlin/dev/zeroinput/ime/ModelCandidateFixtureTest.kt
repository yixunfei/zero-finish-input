package dev.zeroinput.ime

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.EditorContext
import dev.zeroinput.engine.api.EngineKey
import dev.zeroinput.engine.api.InputLanguage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Exports only constructed public fixtures; never observes an editor or personal store. */
@RunWith(AndroidJUnit4::class)
class ModelCandidateFixtureTest {
    @Test fun capturePublicRimeCandidatesForModelEvaluation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val graph = (instrumentation.targetContext.applicationContext as ZeroInputApplication).graph
        val source = instrumentation.context.assets.open("cases.json").bufferedReader().use { it.readText() }
        val fixtures = JSONArray(source)
        check(fixtures.length() in 1..300)
        val result = graph.engineExecutor.submit<String> {
            checkNotNull(graph.rime.createNativeOrNull(ChineseInputOptions())).use { engine ->
                engine.start(EditorContext(InputLanguage.CHINESE, false, false, null))
                val output = JSONArray()
                repeat(fixtures.length()) { index ->
                    val fixture = fixtures.getJSONObject(index)
                    val input = fixture.getString("pinyin")
                    check(input.length in 1..64 && input.all { it in 'a'..'z' })
                    engine.reset()
                    input.forEach { engine.handle(EngineKey.Character(it.toString())) }
                    val candidates = JSONArray()
                    engine.snapshot.candidates.take(8).forEach { candidate ->
                        candidates.put(JSONObject().put("text", candidate.text).put("input", candidate.input)
                            .put("comment", candidate.comment).put("kind", candidate.kind.name))
                    }
                    output.put(JSONObject(fixture.toString()).put("candidates", candidates))
                }
                output.toString()
            }
        }.get(90, TimeUnit.SECONDS)
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)).resolve("model-research")
        check(directory.exists() || directory.mkdirs())
        directory.resolve("rime-candidates.json").writeText(result)
        instrumentation.sendStatus(0, Bundle().apply { putString("public_model_fixtures", fixtures.length().toString()) })
    }
}
