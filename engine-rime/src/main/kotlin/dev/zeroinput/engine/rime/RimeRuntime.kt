package dev.zeroinput.engine.rime

import android.content.Context
import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseKeyboardLayout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RimeRuntime(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private val activeEngines = AtomicInteger(0)
    private var configurationInstaller: RimeConfigurationInstaller? = null
    private var preparedSchemaId: String? = null
    private val nineKeyReadings by lazy {
        applicationContext.assets.open("pinyin-syllables.txt").bufferedReader().use {
            NineKeyReadings(it.readLines())
        }
    }

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var initializationError: Throwable? = null
        private set

    @Volatile
    var state: RimeRuntimeState = RimeRuntimeState.NOT_INITIALIZED
        private set

    @Volatile
    private var runtimeVersion: String = "unavailable"

    private val stateListeners = CopyOnWriteArrayList<(RimeRuntimeState) -> Unit>()

    fun addStateListener(listener: (RimeRuntimeState) -> Unit): AutoCloseable {
        stateListeners += listener
        runCatching { listener(state) }
        return object : AutoCloseable {
            override fun close() {
                stateListeners -= listener
            }
        }
    }

    fun initialize(): Boolean = synchronized(lock) {
        if (isReady) return true
        if (activeEngines.get() != 0) return false
        setState(RimeRuntimeState.INITIALIZING)
        initializationError = null
        if (!BuildConfig.HAS_NATIVE_RIME) {
            initializationError = IllegalStateException("Native Rime is unavailable in this build")
            isReady = false
            setState(RimeRuntimeState.FAILED)
            return false
        }
        if (!NativeRimeBridge.isLoaded) {
            initializationError = NativeRimeBridge.loadFailure()
                ?: IllegalStateException("Native Rime library could not be loaded")
            isReady = false
            setState(RimeRuntimeState.FAILED)
            return false
        }

        return try {
            val directories = RimeAssetInstaller(applicationContext).install()
            configurationInstaller = RimeConfigurationInstaller(directories)
            isReady = NativeRimeBridge.nativeInitialize(
                directories.shared.absolutePath,
                directories.user.absolutePath,
            )
            if (!isReady) {
                runCatching { NativeRimeBridge.nativeFinalize() }
                initializationError = IllegalStateException("Native Rime initialization returned false")
                setState(RimeRuntimeState.FAILED)
            } else {
                val probeSession = NativeRimeBridge.nativeCreateSession()
                if (probeSession == 0L) {
                    runCatching { NativeRimeBridge.nativeFinalize() }
                    isReady = false
                    initializationError = IllegalStateException("Rime could not create an input session")
                    setState(RimeRuntimeState.FAILED)
                    return false
                }
                NativeRimeBridge.nativeDestroySession(probeSession)
                runtimeVersion = runCatching { NativeRimeBridge.nativeVersion() }
                    .getOrDefault("unavailable")
                    .ifBlank { "unavailable" }
                initializationError = null
                preparedSchemaId = "zeroinput_pinyin"
                setState(RimeRuntimeState.READY)
            }
            isReady
        } catch (error: Throwable) {
            runCatching { NativeRimeBridge.nativeFinalize() }
            isReady = false
            initializationError = error
            setState(RimeRuntimeState.FAILED)
            false
        }
    }

    fun version(): String = if (isReady) runtimeVersion else "unavailable"

    internal fun createEngine(options: ChineseInputOptions): RimeInputEngine? = synchronized(lock) {
        check(isReady) { "Rime runtime is not initialized" }
        val id = PinyinAlgebra.schemaId(options)
        if (id != preparedSchemaId) {
            // Deploying a prism may perform substantial I/O under librime's lock.
            // The service first retires the idle native engine to a memory fallback.
            if (activeEngines.get() != 0) return null
            setState(RimeRuntimeState.INITIALIZING)
            try {
                val installer = checkNotNull(configurationInstaller)
                if (id != "zeroinput_pinyin") {
                    val (_, file) = installer.prepare(options)
                    check(NativeRimeBridge.nativeDeploySchema(file.absolutePath)) { "Input configuration deployment failed" }
                }
                installer.removeUnused(setOf(id))
                preparedSchemaId = id
            } catch (error: Exception) {
                markFailed(error)
                return null
            }
            setState(RimeRuntimeState.READY)
        }
        try {
            RimeInputEngine(id, options, ::markFailed, ::releaseEngine,
                if (options.keyboardLayout == ChineseKeyboardLayout.NINE_KEY) nineKeyReadings else null)
                .also { activeEngines.incrementAndGet() }
        } catch (error: Exception) {
            markFailed(error)
            null
        }
    }

    private fun releaseEngine() {
        if (activeEngines.decrementAndGet() == 0 && isReady && state == RimeRuntimeState.READY) {
            // A superseded prepared session can briefly delay a new configuration.
            // Its release makes that preparation retryable without polling or waits.
            setState(RimeRuntimeState.READY)
        }
    }

    override fun close() = synchronized(lock) {
        if (isReady) runCatching { NativeRimeBridge.nativeFinalize() }
        isReady = false
        preparedSchemaId = null
        configurationInstaller = null
        runtimeVersion = "unavailable"
        initializationError = null
        setState(RimeRuntimeState.NOT_INITIALIZED)
    }

    /** Marks a runtime failure discovered while creating or using an engine. */
    internal fun markFailed(error: Throwable) = synchronized(lock) {
        if (isReady) runCatching { NativeRimeBridge.nativeFinalize() }
        isReady = false
        runtimeVersion = "unavailable"
        initializationError = error
        setState(RimeRuntimeState.FAILED)
    }

    private fun setState(value: RimeRuntimeState) {
        state = value
        stateListeners.forEach { listener ->
            runCatching { listener(value) }
        }
    }
}

enum class RimeRuntimeState {
    NOT_INITIALIZED,
    INITIALIZING,
    READY,
    FAILED,
}
