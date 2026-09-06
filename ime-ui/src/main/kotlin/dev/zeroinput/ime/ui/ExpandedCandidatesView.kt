package dev.zeroinput.ime.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import dev.zeroinput.engine.api.EngineSnapshot
import dev.zeroinput.engine.api.PageDirection

internal class ExpandedCandidatesView(context: Context) : LinearLayout(context) {
    var onCandidateSelected: (Int) -> Unit = {}
    var onPageChanged: (PageDirection) -> Unit = {}
    private val grid = GridLayout(context).apply { columnCount = 3 }
    private val scroll = ScrollView(context).apply {
        isFillViewport = true
        addView(grid)
    }
    private val previous = panelIconButton(context, android.R.drawable.ic_media_previous, R.string.previous_candidates) {
        onPageChanged(PageDirection.PREVIOUS)
    }
    private val next = panelIconButton(context, android.R.drawable.ic_media_next, R.string.next_candidates) {
        onPageChanged(PageDirection.NEXT)
    }
    private val buttons = mutableListOf<CandidateItemView>()
    private var lastSnapshot: EngineSnapshot? = null

    init {
        orientation = VERTICAL
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(previous, LayoutParams(dp(48), dp(48)))
            addView(Space(context), LayoutParams(0, 1, 1f))
            addView(next, LayoutParams(dp(48), dp(48)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
    }

    fun render(snapshot: EngineSnapshot) {
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        while (buttons.size < snapshot.candidates.size) {
            val index = buttons.size
            buttons += CandidateItemView(context).also { button ->
                button.onSelected = { onCandidateSelected(it) }
                button.setSingleLine(false)
                button.maxLines = 2
                button.textSize = 16f
                grid.addView(button, GridLayout.LayoutParams(
                    GridLayout.spec(index / 3), GridLayout.spec(index % 3, 1f),
                ).apply { width = 0; height = dp(48) })
            }
        }
        buttons.forEachIndexed { index, button ->
            val candidate = snapshot.candidates.getOrNull(index)
            button.visibility = if (candidate == null) View.GONE else View.VISIBLE
            if (candidate != null) button.bind(candidate, index, index == snapshot.highlightedIndex) else button.clear()
        }
        previous.isEnabled = snapshot.hasPreviousPage
        previous.alpha = if (previous.isEnabled) 1f else 0.35f
        next.isEnabled = snapshot.hasNextPage
        next.alpha = if (next.isEnabled) 1f else 0.35f
        scroll.scrollTo(0, 0)
    }

    fun clear() { render(EngineSnapshot.Empty) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
