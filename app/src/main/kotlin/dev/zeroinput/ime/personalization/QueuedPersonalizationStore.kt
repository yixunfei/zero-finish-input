package dev.zeroinput.ime.personalization

import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.engine.api.PersonalSuggestion
import dev.zeroinput.engine.api.PersonalizationStore
import dev.zeroinput.ime.concurrency.BoundedExecutors
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class QueuedPersonalizationStore(
    private val delegate: PersonalizationStore,
    private val preload: () -> Unit,
    private val executor: ExecutorService = BoundedExecutors.singleThread(
        name = "zeroinput-personalization",
        queueCapacity = MAX_PENDING_OPERATIONS,
    ),
) : PersonalizationStore, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val dataRevision = AtomicLong(0L)
    @Volatile
    private var ready = false
    private val stateLock = Any()
    private val submissionLock = Any()
    private val delegateLock = Any()
    private val suggestionCache = object : LinkedHashMap<QueryKey, List<PersonalSuggestion>>(
        MAX_CACHED_QUERIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<QueryKey, List<PersonalSuggestion>>?,
        ): Boolean = size > MAX_CACHED_QUERIES
    }
    private val pendingQueries = HashMap<QueryKey, QueryStamp>()
    private var preloadGeneration: Long? = null
    private val suggestionListeners = CopyOnWriteArrayList<() -> Unit>()

    override fun suggestionsFor(
        prefix: String,
        language: InputLanguage,
        limit: Int,
    ): List<PersonalSuggestion> {
        require(limit in 0..MAX_SUGGESTION_LIMIT)
        val normalized = prefix.trim().lowercase()
        if (normalized.isEmpty() || limit == 0 || closed.get()) return emptyList()

        schedulePreload()
        if (!ready) return emptyList()

        val key = QueryKey(normalized, language)
        val cached = synchronized(stateLock) { suggestionCache[key] }
        if (cached != null) return cached.take(limit)

        // A cache miss is deliberately non-blocking. The delegate may still
        // touch encrypted storage or sort a large dictionary, so never call
        // it from the IME key path.
        scheduleSuggestionQuery(key, dataRevision.get())
        return emptyList()
    }

    override fun learn(
        shortcut: String,
        value: String,
        language: InputLanguage,
        learningAllowed: Boolean,
    ) {
        if (!learningAllowed || closed.get()) return
        val operationGeneration = generation.get()
        schedulePreload()
        // A query that is waiting behind this write would otherwise keep the
        // old revision marked as pending and suppress the first query for the
        // newly learned value.  It is safe to drop that marker: the queued
        // query will re-check the revision before publishing anything.
        invalidateSuggestionCache(clearPending = true)
        enqueue {
            if (isGenerationCurrent(operationGeneration)) {
                runCatching {
                    withDelegate {
                        delegate.learn(shortcut, value, language, learningAllowed = true)
                    }
                }
                dataRevision.incrementAndGet()
                invalidateSuggestionCache(clearPending = true)
            }
        }
    }

    override fun recordUse(id: String, learningAllowed: Boolean) {
        if (!learningAllowed || closed.get()) return
        val operationGeneration = generation.get()
        schedulePreload()
        invalidateSuggestionCache(clearPending = true)
        enqueue {
            if (isGenerationCurrent(operationGeneration)) {
                runCatching {
                    withDelegate { delegate.recordUse(id, learningAllowed = true) }
                }
                dataRevision.incrementAndGet()
                invalidateSuggestionCache(clearPending = true)
            }
        }
    }

    override fun invalidatePendingWrites() {
        // A settings/privacy change can happen while a learn or usage update
        // is waiting in the executor.  Advance the same generation used by
        // clear() so that operation is discarded before it reaches the
        // encrypted delegate.
        generation.incrementAndGet()
        ready = false
        invalidateSuggestionCache(clearPending = true)
    }

    override fun clear() {
        enqueueClear(await = false)
    }

    /**
     * Clears the encrypted delegate and waits for durable completion.  This
     * is intended for a settings worker, never for the IME input thread.
     * Calling code can therefore give the user an accurate completion signal
     * while normal key handling remains fully asynchronous.
     */
    fun clearAndAwait() {
        val task = enqueueClear(await = true) ?: return
        try {
            task.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            // Invalidate a task that is already running before stopping the
            // executor.  shutdownNow() only removes queued tasks; the extra
            // generation check keeps a running callback from publishing into
            // a destroyed store.
            generation.incrementAndGet()
            ready = false
            synchronized(stateLock) {
                suggestionCache.clear()
                pendingQueries.clear()
                preloadGeneration = null
            }
            suggestionListeners.clear()
            executor.shutdownNow()
            BoundedExecutors.purge(executor)
        }
    }

    /**
     * Observes completion of a background suggestion query. The callback is
     * invoked on the personalization worker and must only enqueue UI work.
     */
    fun addSuggestionListener(listener: () -> Unit): AutoCloseable {
        suggestionListeners += listener
        return object : AutoCloseable {
            override fun close() {
                suggestionListeners -= listener
            }
        }
    }

    private fun enqueue(operation: () -> Unit): Boolean {
        return synchronized(submissionLock) {
            if (closed.get()) return@synchronized false
            try {
                executor.execute(operation)
                true
            } catch (_: RejectedExecutionException) {
                // A bounded queue can reject optional learning during a burst;
                // callers keep the input path usable and a later interaction can
                // retry preload instead of leaving the store permanently stuck.
                false
            }
        }
    }

    private fun enqueueClear(await: Boolean): java.util.concurrent.Future<*>? {
        if (closed.get()) {
            if (await) error("Personalization store is closed")
            return null
        }
        generation.incrementAndGet()
        ready = false
        // Hide the old in-memory view immediately.  The encrypted delegate is
        // cleared on the worker, but callers must not continue seeing stale
        // personal candidates during that interval.
        invalidateSuggestionCache(clearPending = true)
        // A clear operation also serves as the initialization barrier when
        // no preload has been requested yet.  This prevents a later key event
        // from scheduling a preload that could re-expose data before clearing.
        val clearGeneration = generation.get()
        synchronized(stateLock) { preloadGeneration = clearGeneration }
        val operation = {
            if (!closed.get()) {
                // If clearing fails, keeping the store unavailable is safer
                // than exposing data that the user asked us to remove.
                runCatching { withDelegate { delegate.clear() } }
                    .onSuccess {
                        dataRevision.incrementAndGet()
                        synchronized(stateLock) {
                            if (preloadGeneration == clearGeneration) preloadGeneration = null
                            suggestionCache.clear()
                        }
                        if (clearGeneration == generation.get() && !closed.get()) ready = true
                    }
                    .onFailure {
                        synchronized(stateLock) {
                            if (preloadGeneration == clearGeneration) preloadGeneration = null
                        }
                        ready = false
                    }
                    .getOrThrow()
            }
        }
        var runSynchronously = false
        val task = synchronized(submissionLock) {
            // Learning and query work is optional; a user-requested clear is
            // a control operation and must not wait behind stale queued work.
            // Any task already running is generation-invalidated and is also
            // serialized with clear by delegateLock below.
            BoundedExecutors.cancelQueued(executor)
            try {
                if (await) {
                    executor.submit(operation)
                } else {
                    executor.execute(operation)
                    null
                }
            } catch (error: RejectedExecutionException) {
                if (await) {
                    // This is only the durable settings path. The caller is
                    // already on a worker, so a final synchronous attempt is
                    // preferable to reporting success while old data remains.
                    runSynchronously = true
                    null
                } else {
                    synchronized(stateLock) {
                        if (preloadGeneration == clearGeneration) preloadGeneration = null
                    }
                    null
                }
            }
        }
        if (runSynchronously) {
            operation()
        }
        return task
    }

    private fun schedulePreload() {
        if (ready || closed.get()) return
        val operationGeneration = generation.get()
        synchronized(stateLock) {
            if (closed.get() || ready || preloadGeneration == operationGeneration) return
            preloadGeneration = operationGeneration
        }
        if (!enqueue {
            if (closed.get() || operationGeneration != generation.get()) {
                synchronized(stateLock) {
                    if (preloadGeneration == operationGeneration) preloadGeneration = null
                }
                return@enqueue
            }
            val result = runCatching { withDelegate { preload() } }
            var becameReady = false
            synchronized(stateLock) {
                if (preloadGeneration == operationGeneration) preloadGeneration = null
                if (operationGeneration == generation.get() && result.isSuccess && !closed.get()) {
                    suggestionCache.clear()
                    ready = true
                    becameReady = true
                } else if (operationGeneration == generation.get()) {
                    ready = false
                }
            }
            if (becameReady) notifySuggestionListeners()
        }) {
            synchronized(stateLock) {
                if (preloadGeneration == operationGeneration) preloadGeneration = null
            }
        }
    }

    private fun scheduleSuggestionQuery(key: QueryKey, queryRevision: Long) {
        val queryGeneration = generation.get()
        val queryStamp = QueryStamp(queryGeneration, queryRevision)
        synchronized(stateLock) {
            if (!isQueryCurrent(queryGeneration, queryRevision) || suggestionCache.containsKey(key)) return
            if (pendingQueries[key] == queryStamp) return
            pendingQueries[key] = queryStamp
        }
        if (!enqueue {
                var notify = false
                try {
                    if (isQueryCurrent(queryGeneration, queryRevision)) {
                        val values = runCatching {
                            withDelegate {
                                delegate.suggestionsFor(
                                    key.prefix,
                                    key.language,
                                    MAX_SUGGESTION_LIMIT,
                                )
                            }
                        }.getOrDefault(emptyList()).toList()
                        synchronized(stateLock) {
                            if (isQueryCurrent(queryGeneration, queryRevision)) {
                                suggestionCache[key] = values
                                notify = true
                            }
                        }
                    }
                } finally {
                    synchronized(stateLock) {
                        if (pendingQueries[key] == queryStamp) pendingQueries.remove(key)
                    }
                }
                if (notify) {
                    suggestionListeners.forEach { listener -> runCatching(listener) }
                }
            }) {
            synchronized(stateLock) {
                if (pendingQueries[key] == queryStamp) pendingQueries.remove(key)
            }
        }
    }

    private fun isQueryCurrent(queryGeneration: Long, queryRevision: Long): Boolean =
        !closed.get() && ready &&
            queryGeneration == generation.get() && queryRevision == dataRevision.get()

    private fun isGenerationCurrent(operationGeneration: Long): Boolean =
        !closed.get() && operationGeneration == generation.get()

    private fun notifySuggestionListeners() {
        if (closed.get()) return
        suggestionListeners.forEach { listener -> runCatching { listener() } }
    }

    private inline fun <T> withDelegate(block: () -> T): T = synchronized(delegateLock) { block() }

    private fun invalidateSuggestionCache(clearPending: Boolean = false) {
        synchronized(stateLock) {
            suggestionCache.clear()
            if (clearPending) pendingQueries.clear()
        }
    }

    private data class QueryKey(
        val prefix: String,
        val language: InputLanguage,
    )

    private data class QueryStamp(
        val generation: Long,
        val revision: Long,
    )

    private companion object {
        const val MAX_PENDING_OPERATIONS = 64
        const val MAX_CACHED_QUERIES = 64
        const val MAX_SUGGESTION_LIMIT = 50
    }
}
