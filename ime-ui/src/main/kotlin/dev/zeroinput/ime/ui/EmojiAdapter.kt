package dev.zeroinput.ime.ui

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

internal class EmojiAdapter(
    private val onSelected: (String) -> Unit,
) : ListAdapter<EmojiEntry, EmojiAdapter.Holder>(Difference) {

    fun submit(values: List<EmojiEntry>) {
        submitList(values.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val size = (48 * parent.resources.displayMetrics.density).toInt()
        val view = TextView(parent.context).apply {
            gravity = Gravity.CENTER
            textSize = 26f
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size)
            isClickable = true
            isFocusable = true
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        holder.text.text = entry.value
        holder.text.contentDescription = entry.keywords.substringBefore(' ')
        holder.text.setOnClickListener { onSelected(entry.value) }
    }

    class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    private object Difference : DiffUtil.ItemCallback<EmojiEntry>() {
        override fun areItemsTheSame(oldItem: EmojiEntry, newItem: EmojiEntry): Boolean =
            oldItem.value == newItem.value

        override fun areContentsTheSame(oldItem: EmojiEntry, newItem: EmojiEntry): Boolean =
            oldItem == newItem
    }
}
