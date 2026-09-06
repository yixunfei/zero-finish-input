package dev.zeroinput.ime.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.zeroinput.engine.api.Candidate
import dev.zeroinput.engine.api.EngineSnapshot

internal class ReadingChoicesView(context: Context) : ScrollView(context) {
    var onSelected: (Int) -> Unit = {}
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val buttons = mutableListOf<CandidateItemView>()
    private var previousInput = ""
    private var previousReadings: List<String> = emptyList()

    init { addView(rows); isVerticalScrollBarEnabled = false }

    fun render(snapshot: EngineSnapshot) {
        if (snapshot.rawInput == previousInput && snapshot.readings == previousReadings) return
        previousInput = snapshot.rawInput
        previousReadings = snapshot.readings
        while (buttons.size < snapshot.readings.size) {
            buttons += CandidateItemView(context).also { view ->
                view.textSize = 14f
                view.setPadding(dp(3), 0, dp(3), 0)
                view.onSelected = { onSelected(it) }
                rows.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
            }
        }
        buttons.forEachIndexed { index, view ->
            val reading = snapshot.readings.getOrNull(index)
            view.visibility = if (reading == null) View.GONE else View.VISIBLE
            if (reading == null) view.clear() else {
                view.bind(Candidate("${snapshot.rawInput}:$reading", reading), index, false)
                view.contentDescription = context.getString(R.string.select_pinyin_reading, reading)
            }
        }
        scrollTo(0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
