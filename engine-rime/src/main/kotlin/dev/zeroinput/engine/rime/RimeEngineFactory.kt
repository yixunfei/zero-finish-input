package dev.zeroinput.engine.rime

import android.content.Context
import dev.zeroinput.engine.api.InputEngine
import dev.zeroinput.engine.api.ConfigurableChineseEngineFactory
import dev.zeroinput.engine.api.ChineseInputOptions

class RimeEngineFactory(context: Context) : ConfigurableChineseEngineFactory, AutoCloseable {
    val runtime = RimeRuntime(context)

    override val descriptor = RimeInputEngine.Descriptor

    fun warmUp(): Boolean = runtime.initialize()

    override fun isAvailable(): Boolean = runtime.isReady

    /** Creates the in-memory fallback without touching the native runtime. */
    fun createFallback(): InputEngine = FallbackPinyinEngine()

    /**
     * Creates a native engine only when the runtime is already ready.  The
     * caller is expected to run this method on a bounded background worker;
     * unlike [create], it never silently substitutes the fallback.
     */
    fun createNativeOrNull(options: ChineseInputOptions = ChineseInputOptions()): InputEngine? {
        if (!runtime.isReady) return null
        return runCatching { runtime.createEngine(options) }
            .onFailure(runtime::markFailed)
            .getOrNull()
    }

    override fun create(): InputEngine = createNativeOrNull() ?: createFallback()

    override fun create(options: ChineseInputOptions): InputEngine = createNativeOrNull(options) ?: createFallback()

    override fun close() = runtime.close()
}
