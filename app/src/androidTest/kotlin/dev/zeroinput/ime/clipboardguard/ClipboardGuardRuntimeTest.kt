package dev.zeroinput.ime.clipboardguard

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardGuardRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun defaultOffNeverCreatesPlatformAccessAndDisabledSettingsSurviveRecreation() = withFixture { fixture ->
        fixture.runtime.attachIme()
        fixture.configure(ClipboardGuardOptions(keyboardReminder = true, authenticate = true))
        await { fixture.runtime.state.options.authenticate }
        assertEquals(0, fixture.created.get())
        assertEquals(ClipboardGuardStatus.OFF, fixture.runtime.state.status)
        val restored = ClipboardGuardPreferences(fixture.preferencesFile).options
        assertTrue(restored.keyboardReminder && restored.authenticate)
        assertFalse(restored.listening || restored.notificationReminder)
        assertEquals(ClipboardClearMode.NONE, restored.clearMode)
    }

    @Test
    fun disablingWhileAnEventIsBlockedCancelsAutomaticClearAndRemovesTheListener() = withFixture { fixture ->
        fixture.start(ClipboardClearMode.AUTOMATIC)
        val block = ReadBlock()
        fixture.port.block.set(block)
        fixture.port.stamp = 1
        fixture.port.notifyChanged()
        assertTrue(block.entered.await(5, TimeUnit.SECONDS))
        fixture.configure(ClipboardGuardOptions())
        block.release.countDown()
        await { fixture.runtime.state.status == ClipboardGuardStatus.OFF && fixture.port.listener.get() == null }
        assertEquals(0, fixture.port.clears.get())
        assertEquals(null, fixture.runtime.state.ticket)
    }

    @Test
    fun changingDefaultImeRejectsAClearEvenBeforeItsObserverRuns() = withFixture { fixture ->
        fixture.start(ClipboardClearMode.CONFIRM)
        fixture.port.stamp = 1
        fixture.port.notifyChanged()
        await { fixture.runtime.state.ticket != null }
        val ticket = checkNotNull(fixture.runtime.state.ticket)
        fixture.selected.set(false)
        val outcome = AtomicReference<ClipboardClearResult>()
        fixture.runtime.clear(ticket.id, null, { true }, outcome::set)
        await { outcome.get() != null }
        assertEquals(ClipboardClearResult.EXPIRED, outcome.get())
        assertEquals(0, fixture.port.clears.get())
        fixture.port.notifyChanged()
        await { fixture.runtime.state.status == ClipboardGuardStatus.UNAVAILABLE }
        assertEquals(null, fixture.port.listener.get())
    }

    @Test
    fun detachingImeInvalidatesQueuedClearAndRuntimeCanBeReattached() = withFixture { fixture ->
        fixture.start(ClipboardClearMode.CONFIRM)
        fixture.port.stamp = 1
        fixture.port.notifyChanged()
        await { fixture.runtime.state.ticket != null }
        val ticket = checkNotNull(fixture.runtime.state.ticket)
        val block = ReadBlock()
        fixture.port.block.set(block)
        fixture.port.notifyChanged()
        assertTrue(block.entered.await(5, TimeUnit.SECONDS))
        val outcome = AtomicReference<ClipboardClearResult>()
        fixture.runtime.clear(ticket.id, null, { true }, outcome::set)
        fixture.runtime.detachIme()
        block.release.countDown()
        await { outcome.get() != null && fixture.runtime.state.status == ClipboardGuardStatus.UNAVAILABLE }
        assertEquals(ClipboardClearResult.EXPIRED, outcome.get())
        assertEquals(0, fixture.port.clears.get())
        fixture.runtime.attachIme()
        await { fixture.runtime.state.status == ClipboardGuardStatus.WAITING && fixture.port.listener.get() != null }
        assertEquals(null, fixture.runtime.state.ticket)
    }

    @Test
    fun closeReleasesMonitoringAndRejectsFurtherActions() = withFixture { fixture ->
        fixture.start(ClipboardClearMode.CONFIRM)
        fixture.runtime.close()
        await { fixture.port.listener.get() == null }
        val outcome = AtomicReference<ClipboardClearResult>()
        fixture.runtime.clear("expired", null, { true }, outcome::set)
        await { outcome.get() != null }
        assertEquals(ClipboardClearResult.EXPIRED, outcome.get())
        fixture.runtime.attachIme()
        assertEquals(null, fixture.port.listener.get())
    }

    private fun withFixture(test: (Fixture) -> Unit) {
        val fixture = Fixture()
        try { test(fixture) } finally {
            fixture.port.block.getAndSet(null)?.release?.countDown()
            fixture.runtime.close()
            await { fixture.port.listener.get() == null }
            instrumentation.targetContext.deleteSharedPreferences(fixture.name)
        }
    }

    private inner class Fixture {
        val name = "guard-test-${UUID.randomUUID()}"
        val preferencesFile = instrumentation.targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        val preferences = ClipboardGuardPreferences(preferencesFile)
        val port = FakeClipboard()
        val selected = AtomicBoolean(true)
        val created = AtomicInteger()
        val runtime = ClipboardGuardRuntime(instrumentation.targetContext, preferences,
            createClipboard = { created.incrementAndGet(); port }, isDefaultIme = selected::get)

        fun configure(options: ClipboardGuardOptions) {
            instrumentation.runOnMainSync { preferences.options = options }
        }

        fun start(mode: ClipboardClearMode) {
            configure(ClipboardGuardOptions(listening = true, clearMode = mode))
            runtime.attachIme()
            await { runtime.state.status == ClipboardGuardStatus.WAITING && port.listener.get() != null }
        }
    }

    private class ReadBlock {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
    }

    private class FakeClipboard : SystemClipboardPort {
        @Volatile var stamp: Long? = null
        val clears = AtomicInteger()
        val listener = AtomicReference<(() -> Unit)?>()
        val block = AtomicReference<ReadBlock?>()

        override fun timestamp(): Long? {
            block.getAndSet(null)?.let {
                it.entered.countDown()
                check(it.release.await(5, TimeUnit.SECONDS))
            }
            return stamp
        }

        override fun clear() { clears.incrementAndGet(); stamp = null }
        override fun listen(onChanged: () -> Unit): AutoCloseable {
            listener.set(onChanged)
            return AutoCloseable { listener.compareAndSet(onChanged, null) }
        }
        fun notifyChanged() { listener.get()?.invoke() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertTrue("Guard state did not settle", condition())
    }
}
