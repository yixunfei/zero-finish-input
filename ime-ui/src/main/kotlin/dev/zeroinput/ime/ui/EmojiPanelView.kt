package dev.zeroinput.ime.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class EmojiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    var onEmojiSelected: (String) -> Unit = {}
    var onSearchModeChanged: (Boolean) -> Unit = {}
    var onUserInteraction: () -> Unit = {}

    var isSearchActive: Boolean = false
        private set

    private var query = ""
    private var category = EmojiCategory.SMILEYS
    private var recentEntries: List<EmojiEntry> = emptyList()
    private val adapter = EmojiAdapter { onEmojiSelected(it) }
    private val queryLabel = TextView(context).apply {
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        layoutParams = LayoutParams(0, dp(40), 1f)
    }
    private val gridLayout = GridLayoutManager(context, DEFAULT_COLUMNS)
    private val grid = RecyclerView(context).apply {
        layoutManager = gridLayout
        adapter = this@EmojiPanelView.adapter
        overScrollMode = View.OVER_SCROLL_NEVER
        itemAnimator = null
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
    }

    init {
        orientation = VERTICAL
        addView(createSearchRow())
        addView(grid)
        addView(createCategoryRow())
        refresh()
    }

    fun setRecent(values: List<String>) {
        recentEntries = EmojiCatalog.recent(values)
        if (category == EmojiCategory.RECENT && recentEntries.isEmpty()) category = EmojiCategory.SMILEYS
        refresh()
    }

    fun appendQuery(value: String) {
        if (!isSearchActive || query.length >= MAX_QUERY_LENGTH) return
        query += value.lowercase()
        refresh()
    }

    fun removeQueryCharacter() {
        if (!isSearchActive || query.isEmpty()) return
        query = query.dropLast(1)
        refresh()
    }

    fun clearQuery() {
        onUserInteraction()
        query = ""
        refresh()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        gridLayout.spanCount = (width / dp(48)).coerceIn(5, 10)
    }

    private fun createSearchRow(): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
        addView(iconButton("⌕", "搜索 emoji") { toggleSearch() })
        addView(queryLabel)
        addView(iconButton("×", "清除搜索") { clearQuery() })
    }

    private fun createCategoryRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        EmojiCategory.entries.forEach { item ->
            row.addView(iconButton(item.marker, item.description) {
                onUserInteraction()
                category = item
                if (item == EmojiCategory.RECENT && recentEntries.isEmpty()) category = EmojiCategory.SMILEYS
                refresh()
            })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
        }
    }

    private fun toggleSearch() {
        onUserInteraction()
        isSearchActive = !isSearchActive
        if (!isSearchActive) query = ""
        onSearchModeChanged(isSearchActive)
        refresh()
    }

    private fun refresh() {
        queryLabel.text = if (isSearchActive) query.ifEmpty { "emoji" } else category.description
        val values = when {
            isSearchActive -> EmojiCatalog.search(query)
            category == EmojiCategory.RECENT -> recentEntries
            else -> EmojiCatalog.entries.filter { it.category == category }
        }
        adapter.submit(values)
    }

    private fun iconButton(label: String, description: String, action: () -> Unit) = MaterialButton(context).apply {
        text = label
        contentDescription = description
        textSize = 19f
        letterSpacing = 0f
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0
        setPadding(0, 0, 0, 0)
        setSingleLine()
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LayoutParams(dp(48), dp(48))
        setOnClickListener { action() }
    }

    private fun resolveColor(attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return values.getColor(0, fallback).also { values.recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val DEFAULT_COLUMNS = 8
        const val MAX_QUERY_LENGTH = 32
    }
}
