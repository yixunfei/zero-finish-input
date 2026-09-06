package dev.zeroinput.ime.clipboardguard

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import dev.zeroinput.ime.R

internal class ClipboardOverlaySettingsView(context: Context) : LinearLayout(context) {
    var onOptions: (ClipboardGuardOptions) -> Unit = {}
    var onPermission: () -> Unit = {}
    var onPreview: () -> Unit = {}
    private var options = ClipboardGuardOptions()
    private var rendering = false
    private val enabled = MaterialSwitch(context).apply {
        setText(R.string.clipboard_guard_overlay)
        textSize = 16f
        minHeight = dp(52)
        isSaveEnabled = false
        filterTouchesWhenObscured = true
        setOnCheckedChangeListener { _, checked -> update(options.copy(overlayReminder = checked)) }
    }
    private val permissionStatus = TextView(context).apply { setText(R.string.clipboard_guard_overlay_permission_missing); textSize = 14f }
    private val permission = button(R.string.clipboard_guard_overlay_permission) { onPermission() }
    private val preview = button(R.string.clipboard_guard_overlay_preview) { onPreview() }
    private val positions = ClipboardOverlayPosition.entries
    private val seconds = listOf(3, 5, 10)
    private val position = selection(listOf(R.string.clipboard_guard_overlay_top, R.string.clipboard_guard_overlay_bottom)
        .map(context::getString)) { update(options.copy(overlayPosition = positions[it])) }
    private val duration = selection(seconds.map { resources.getQuantityString(R.plurals.clipboard_guard_overlay_seconds, it, it) }) {
        update(options.copy(overlaySeconds = seconds[it]))
    }

    init {
        orientation = VERTICAL
        addView(enabled, row())
        addView(permissionStatus, row())
        addView(permission, row())
        addView(TextView(context).apply { setText(R.string.clipboard_guard_overlay_position); textSize = 14f }, row())
        addView(position, row())
        addView(TextView(context).apply { setText(R.string.clipboard_guard_overlay_duration); textSize = 14f }, row())
        addView(duration, row())
        addView(preview, row())
    }

    fun render(value: ClipboardGuardOptions, allowed: Boolean) {
        rendering = true
        options = value
        enabled.isChecked = value.overlayReminder
        permissionStatus.visibility = if (value.overlayReminder && !allowed) VISIBLE else GONE
        permission.visibility = permissionStatus.visibility
        position.setSelection(positions.indexOf(value.overlayPosition))
        duration.setSelection(seconds.indexOf(value.overlaySeconds))
        preview.isEnabled = value.overlayReminder && allowed
        rendering = false
    }

    private fun update(next: ClipboardGuardOptions) { if (!rendering && next != options) onOptions(next) }

    private fun selection(labels: List<String>, changed: (Int) -> Unit) = Spinner(context).apply {
        minimumHeight = dp(48)
        isSaveEnabled = false
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { changed(position) }
        }
    }

    private fun button(label: Int, clicked: () -> Unit) = MaterialButton(context).apply {
        setText(label)
        isAllCaps = false
        letterSpacing = 0f
        minHeight = dp(48)
        filterTouchesWhenObscured = true
        setOnClickListener { clicked() }
    }

    private fun row() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
