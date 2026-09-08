package dev.zeroinput.ime

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.engine.api.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MatchingQualityTest {
    @Test fun publicSpellingFixturesReportRankingAndFalsePositiveChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val graph = (instrumentation.targetContext.applicationContext as ZeroInputApplication).graph
        val clean = listOf("nihao" to "你好", "zhongguo" to "中国", "shijie" to "世界", "gongzuo" to "工作",
            "xuexi" to "学习", "mingtian" to "明天", "jintian" to "今天", "xiexie" to "谢谢",
            "zaijian" to "再见", "pengyou" to "朋友", "shouji" to "手机", "jisuanji" to "计算机",
            "tianqi" to "天气", "shurufa" to "输入法", "kuaile" to "快乐", "beijing" to "北京")
        val typo = listOf("nihso" to "你好", "nihoa" to "你好", "nihaao" to "你好", "zhongguoo" to "中国",
            "shijiee" to "世界", "gongzuoo" to "工作", "mingtiam" to "明天", "penhyou" to "朋友")
        val all = clean + typo
        val results = (0..1).map { enabled ->
            graph.engineExecutor.submit<List<Pair<String, Int>>> {
                checkNotNull(graph.rime.createNativeOrNull(ChineseInputOptions(experimentalTypoCorrection = enabled == 1))).use { engine ->
                    engine.start(EditorContext(InputLanguage.CHINESE, false, false, null))
                    all.map { (input, expected) ->
                        engine.reset()
                        input.forEach { engine.handle(EngineKey.Character(it.toString())) }
                        engine.snapshot.candidates.firstOrNull()?.text.orEmpty() to
                            engine.snapshot.candidates.take(5).indexOfFirst { it.text == expected }
                    }
                }
            }.get(60, TimeUnit.SECONDS)
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("quality_clean_fixtures", clean.size.toString())
            putString("quality_typo_fixtures", typo.size.toString())
            for ((index, values) in results.withIndex()) {
                for ((label, subset) in listOf("clean" to values.take(clean.size), "typo" to values.drop(clean.size))) {
                    putString("quality_${index}_${label}_top1", subset.count { it.second == 0 }.toString())
                    putString("quality_${index}_${label}_top5", subset.count { it.second >= 0 }.toString())
                }
            }
            putString("quality_clean_first_changed", clean.indices.count { results[0][it].first != results[1][it].first }.toString())
        })
    }
}
