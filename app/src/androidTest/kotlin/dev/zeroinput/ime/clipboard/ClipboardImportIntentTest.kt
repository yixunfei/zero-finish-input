package dev.zeroinput.ime.clipboard

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.URLSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardImportIntentTest {
    @Test
    fun bothEntriesAcceptOnlyTheChosenPlainTextAndDropSpans() {
        val styled = SpannableString("public fixture").apply {
            setSpan(URLSpan("https://example.invalid"), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for ((action, extra) in entries) {
            val intent = Intent(action).setType("text/plain").putExtra(extra, styled)
            val request = checkNotNull(ClipboardImportIntent.parse(intent))
            assertTrue(request.beginAuthentication())
            assertTrue(request.authorize())
            assertEquals("public fixture", request.copyText())
            request.close()
        }
    }

    @Test
    fun androidTextShareClipDataIsAcceptedButAttachmentsAndConflictingTextAreRejected() {
        val shared = textIntent().apply { clipData = ClipData.newPlainText("", "public fixture") }
        ClipboardImportIntent.parse(shared).also { assertNotNull(it) }?.close()
        val clips = listOf(
            ClipData.newRawUri("", Uri.parse("content://untrusted/fixture")),
            ClipData.newIntent("", Intent(Intent.ACTION_VIEW)),
            ClipData.newPlainText("", "different fixture"),
            ClipData.newPlainText("", "public fixture").apply { addItem(ClipData.Item("second fixture")) },
        )
        clips.forEach { clip -> assertNull(ClipboardImportIntent.parse(textIntent().apply { clipData = clip })) }
    }

    @Test
    fun malformedAndOversizedExternalInputsFailClosed() {
        val invalid = listOf(
            Intent(),
            textIntent().setAction(Intent.ACTION_SEND_MULTIPLE),
            textIntent().setAction(Intent.ACTION_VIEW),
            textIntent().setType("text/html"),
            textIntent().setType("image/png"),
            textIntent().setDataAndType(Uri.parse("content://untrusted/fixture"), "text/plain"),
            textIntent().putExtra(Intent.EXTRA_STREAM, Uri.parse("content://untrusted/fixture")),
            textIntent().putExtra(Intent.EXTRA_HTML_TEXT, "<b>fixture</b>"),
            textIntent().putExtra(Intent.EXTRA_TEXT, 42),
            textIntent().putExtra(Intent.EXTRA_TEXT, ""),
            textIntent().putExtra(Intent.EXTRA_TEXT, " \n\t"),
            textIntent().putExtra(Intent.EXTRA_TEXT, "a\u0000b"),
            textIntent().putExtra(Intent.EXTRA_TEXT, "\uD800"),
            textIntent().putExtra(Intent.EXTRA_TEXT, "\uDC00"),
            textIntent().putExtra(Intent.EXTRA_TEXT, "x".repeat(8_193)),
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "fixture"),
        )
        invalid.forEach { assertNull(ClipboardImportIntent.parse(it)) }
        val maximum = checkNotNull(ClipboardImportIntent.parse(textIntent().putExtra(Intent.EXTRA_TEXT, "x".repeat(8_192))))
        assertEquals(8_192, maximum.length)
        maximum.close()
    }

    @Test
    @Suppress("DEPRECATION")
    fun manifestExportsBothImportActionsWithoutExportingManagementOrAuthentication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        for ((action, _) in entries) {
            val intent = Intent(action).setType("text/plain").setPackage(context.packageName)
            val handlers = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            assertEquals(listOf(ClipboardImportActivity::class.java.name), handlers.map { it.activityInfo.name })
            val info = handlers.single().activityInfo
            assertTrue(info.exported)
            assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
            assertTrue(info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        }
        for (name in listOf("settings.SecureClipboardManagerActivity", "auth.SecureClipboardUnlockActivity")) {
            val info = manager.getActivityInfo(ComponentName(context.packageName, "dev.zeroinput.ime.$name"), 0)
            assertFalse(info.exported)
        }
    }

    private fun textIntent() = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "public fixture")

    private val entries = listOf(
        Intent.ACTION_PROCESS_TEXT to Intent.EXTRA_PROCESS_TEXT,
        Intent.ACTION_SEND to Intent.EXTRA_TEXT,
    )
}
