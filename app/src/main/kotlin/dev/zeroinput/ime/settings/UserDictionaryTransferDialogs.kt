package dev.zeroinput.ime.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.zeroinput.ime.R

internal object UserDictionaryTransferDialogs {
    fun export(context: Context, onConfirmed: () -> Unit): AlertDialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.user_dictionary_export)
        .setMessage(R.string.user_dictionary_export_warning)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.user_dictionary_export) { _, _ -> onConfirmed() }
        .create()

    fun import(context: Context, onConfirmed: () -> Unit): AlertDialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.user_dictionary_import)
        .setMessage(R.string.user_dictionary_import_notice)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.user_dictionary_import) { _, _ -> onConfirmed() }
        .create()
}
