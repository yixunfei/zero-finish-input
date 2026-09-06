package dev.zeroinput.ime

import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.view.WindowCompat
import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseKeyboardLayout
import dev.zeroinput.ime.settings.ChineseEngineChoice
import dev.zeroinput.engine.rime.RimeRuntimeState
import dev.zeroinput.ime.auth.AuthenticationBroker
import dev.zeroinput.ime.concurrency.BoundedExecutors
import dev.zeroinput.ime.core.InputCommand
import dev.zeroinput.ime.core.InputSessionController
import dev.zeroinput.ime.input.AndroidEditorConnection
import dev.zeroinput.ime.settings.MainActivity
import dev.zeroinput.ime.settings.SecureClipboardManagerActivity
import dev.zeroinput.ime.ui.KeyboardAction
import dev.zeroinput.ime.ui.SecureClipboardItemUi
import dev.zeroinput.ime.ui.ZeroInputView
import dev.zeroinput.ime.ui.InputEngineStatus
import java.util.Locale
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ZeroInputService : InputMethodService() {
    private val graph: AppGraph
        get() = (application as ZeroInputApplication).graph

    private var inputView: ZeroInputView? = null
    private var controller: InputSessionController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val secureClipboardExecutor = BoundedExecutors.singleThread(
        name = "zeroinput-secure-clipboard",
        queueCapacity = 1,
    )
    /**
     * Local encrypted indexes are intentionally kept off the IME main thread.
     * Keystore access can block on first use even when the payload is small.
     */
    private val localDataExecutor = BoundedExecutors.singleThread(
        name = "zeroinput-local-data",
        queueCapacity = 2,
    )
    @Volatile
    private var recentEmojiCache: List<String> = emptyList()
    @Volatile
    private var secureClipboardCache: List<SecureClipboardItemUi> = emptyList()
    @Volatile
    private var localPanelRevision = 0L
    private var localPanelTask: Future<*>? = null
    @Volatile
    private var sessionSequence = 0L
    /** Monotonic UI interaction counter used to invalidate pending pastes. */
    private var interactionSequence = 0L
    @Volatile
    private var activeSession: InputSession? = null
    private var languagePackObserver: AutoCloseable? = null
    private var settingsObserver: AutoCloseable? = null
    private var personalizationObserver: AutoCloseable? = null
    private var personalizationSuggestionObserver: AutoCloseable? = null
    private val personalizationRefreshPending = AtomicBoolean(false)
    private var runtimeObserver: AutoCloseable? = null
    private var engineWarmupCoordinator: EngineWarmupCoordinator? = null
    private var engineWarmupDelivery: EngineWarmupResultDelivery? = null
    private var engineWarmupTicket: Long? = null
    private var engineWarmupContext: EngineWarmupRequest? = null
    private var installedEngineWarmupContext: EngineWarmupRequest? = null
    private var unavailableEngineWarmupContext: EngineWarmupRequest? = null
    private var engineWarmupInFlight = false
    private var engineWarmupRetry = false
    private var languagePackReloadPending = false
    private var engineReloadPending = false
    private var sessionChineseOptions = ChineseInputOptions()
    private var sessionChineseEngine = ChineseEngineChoice.RIME
    private var nativeRetryRequested = false
    @Volatile
    private var secureClipboardRequest: SecureClipboardRequest? = null
    private var secureClipboardAuth: AuthenticationBroker.RequestHandle? = null
    /**
     * Invalidates asynchronous writes when the active privacy/session
     * context changes.  It is deliberately independent from the UI
     * interaction counter: ordinary key presses should not silently drop a
     * legitimate emoji-history write just because the worker was busy.
     */
    private val personalizationWriteGeneration = AtomicLong(0L)

    init {
        // InputMethodService selects Theme_InputMethod during onCreate and
        // does not apply the manifest's component theme to its SoftInputWindow.
        // Set the MaterialComponents-compatible theme before that lifecycle
        // step so MaterialButton can be inflated safely on every API level.
        setTheme(R.style.Theme_ZeroInput_InputMethod)
    }

    override fun onCreate() {
        super.onCreate()
        engineWarmupDelivery = EngineWarmupResultDelivery(
            post = { runnable -> mainHandler.post(runnable) },
            deliver = ::handleEngineWarmupResult,
        )
        engineWarmupCoordinator = EngineWarmupCoordinator(
            executor = graph.engineExecutor,
            prepare = graph::prepareEngine,
            dispatch = { result ->
                // Engine results are never applied from the worker.  Keeping
                // the handoff on the IME looper makes controller state and
                // InputConnection ownership single-threaded.
                val delivery = engineWarmupDelivery
                if (delivery != null) {
                    delivery.offer(result)
                } else {
                    (result as? EngineWarmupResult.Prepared)?.engine?.close()
                }
            },
        )
        // Language-pack discovery is deliberately asynchronous.  If the IME
        // starts before discovery completes, retry the selected pack when the
        // registry publishes its snapshot instead of silently sticking to the
        // base engine for the lifetime of the session.
        languagePackObserver = graph.addLanguagePackListener {
            mainHandler.post {
                val session = activeSession ?: return@post
                // Pack enable/disable/delete may be initiated from Settings
                // while this IME session remains alive.  Reconciliation can
                // replace the engine, so invalidate any pending authenticated
                // action tied to the old UI/engine state first.
                registerInteraction()
                reconcileLanguagePackSession(session)
            }
        }
        settingsObserver = graph.settings.addChangeListener {
            // SharedPreferences callbacks can arrive before the posted main
            // turn below. Advance the generation immediately so a queued
            // personal-data write cannot win a race with a privacy change.
            invalidatePendingPersonalization()
            mainHandler.post {
                // Settings can be changed while the authentication activity
                // is in the foreground (for example by another settings
                // window). Treat every change as a new UI state so a grant
                // can never outlive the configuration under which it began.
                registerInteraction()
                inputView?.cancelPendingGestures()
                val session = activeSession
                if (session != null) {
                    syncSessionPrivacy()
                    reconcileChineseOptions()
                    scheduleEngineWarmup(session)
                    if (session.controller.state.language != graph.settings.lastLanguage ||
                        session.controller.state.languagePackKey != graph.settings.lastLanguagePackKey
                    ) {
                        reconcileLanguagePackSession(session)
                    }
                    inputView?.let(::renderLocalPanels)
                }
            }
        }
        personalizationObserver = graph.addPersonalizationListener {
            invalidatePendingPersonalization()
            mainHandler.post {
                // Clearing personal data changes the visible candidate/history
                // state. Invalidate actions started before the clear.
                if (activeSession != null) registerInteraction()
                activeSession?.controller?.reset()
                inputView?.let(::renderLocalPanels)
            }
        }
        personalizationSuggestionObserver = graph.addPersonalizationSuggestionListener {
            schedulePersonalizationRefresh()
        }
        runtimeObserver = graph.rime.runtime.addStateListener { state ->
            mainHandler.post {
                val session = activeSession ?: return@post
                // A session opened while native Rime was warming up starts on
                // the safe fallback engine.  Prepare the native replacement
                // off the IME thread and install it only when the context is
                // still current.
                if (state == RimeRuntimeState.READY) scheduleEngineWarmup(session, force = true)
                renderEngineStatus()
            }
        }
    }

    override fun onCreateInputView(): View {
        val view = ZeroInputView(this)
        inputView = view
        bindView(view)
        renderLocalPanels(view)
        controller?.state?.let(view::renderSession)
        renderEngineStatus()
        return view
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        endInputSession(reset = false)
        val token = ++sessionSequence
        val connectionBinding = SessionConnectionBinding(currentInputConnection)
        val currentSubtype = getSystemService(InputMethodManager::class.java)?.currentInputMethodSubtype
        val subtypeLanguage = languageForSubtype(currentSubtype)
        val initialLanguage = subtypeLanguage ?: graph.settings.lastLanguage
        if (subtypeLanguage != null && subtypeLanguage != graph.settings.lastLanguage) {
            // Android can deliver the subtype callback before this lifecycle
            // method, or omit it while restoring an IME. Persist the system's
            // choice so later key/settings callbacks cannot switch the session
            // back to the previous language. A pack selected for the old
            // language is no longer valid in that case.
            graph.settings.lastLanguage = subtypeLanguage
            graph.settings.lastLanguagePackKey = null
        }
        val initialPackKey = graph.settings.lastLanguagePackKey
        sessionChineseOptions = graph.settings.chineseInputOptions
        sessionChineseEngine = graph.settings.chineseEngine
        val newController = InputSessionController(
            connection = AndroidEditorConnection {
                if (activeSession?.token == token) connectionBinding.resolve(currentInputConnection) else null
            },
            // The first frame must not wait for librime construction or a
            // language-pack dictionary scan.  The warm-up coordinator will
            // replace this in-memory engine when the worker is ready.
            engineProvider = graph::createImmediateEngine,
            personalization = graph.personalization,
            onStateChanged = { state ->
                inputView?.renderSession(state)
                renderEngineStatus()
            },
            languagePackProvider = { null },
            languagePackDiscoveryComplete = graph::isLanguagePackDiscoveryComplete,
            deferHeavyEngineCreation = true,
        )
        val session = InputSession(token, newController, connectionBinding, attribute.packageName)
        activeSession = session
        controller = newController
        newController.start(
            editorInfo = attribute,
            initialLanguage = initialLanguage,
            privacyConfiguration = graph.settings.privacyConfiguration(),
            languagePackKey = initialPackKey,
        )
        scheduleEngineWarmup(session, force = true)
        inputView?.let(::renderLocalPanels)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateNavigationBarAppearance()
        syncSessionPrivacy()
        reconcileChineseOptions()
        inputView?.let(::renderLocalPanels)
    }

    private fun updateNavigationBarAppearance() {
        val imeWindow = window?.window ?: return
        val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(imeWindow, imeWindow.decorView).isAppearanceLightNavigationBars = !isNight
    }

    override fun onFinishInput() {
        endInputSession(reset = true)
        inputView?.let(::renderLocalPanels)
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputView?.cancelPendingGestures()
        super.onFinishInputView(finishingInput)
    }

    override fun onUnbindInput() {
        // A client can disappear without a new editor starting immediately.
        // Invalidate the session here as well so pending authentication can
        // never target a connection that is no longer owned by that client.
        endInputSession(reset = false)
        inputView?.let(::renderLocalPanels)
        super.onUnbindInput()
    }

    override fun onDestroy() {
        endInputSession(reset = false)
        languagePackObserver?.close()
        languagePackObserver = null
        settingsObserver?.close()
        settingsObserver = null
        personalizationObserver?.close()
        personalizationObserver = null
        personalizationSuggestionObserver?.close()
        personalizationSuggestionObserver = null
        runtimeObserver?.close()
        runtimeObserver = null
        // Close the owner of results already posted to the main looper before
        // removing callbacks.  Otherwise a removed callback could orphan a
        // prepared native engine after the coordinator has released it.
        engineWarmupDelivery?.close()
        engineWarmupDelivery = null
        engineWarmupCoordinator?.close()
        engineWarmupCoordinator = null
        cancelSecureClipboardRequest()
        secureClipboardExecutor.shutdownNow()
        BoundedExecutors.purge(secureClipboardExecutor)
        localPanelTask?.cancel(true)
        BoundedExecutors.purge(localDataExecutor)
        localDataExecutor.shutdownNow()
        personalizationRefreshPending.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        inputView = null
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * ZeroInput is a touch-first keyboard. Some Android 16 devices expose a
     * physical/virtual qwerty configuration even when no usable hardware
     * keyboard is attached; the framework default would then skip creating
     * the input view entirely.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        // Keep the framework bookkeeping while overriding the hard-keyboard
        // heuristic for touch-first devices.
        super.onEvaluateInputViewShown()
        return true
    }

    /**
     * The default implementation also suppresses implicit show requests when
     * a hard-keyboard configuration is reported. Returning true keeps the
     * IME window available after switching from system settings and on OEM
     * builds that report that configuration conservatively.
     */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean = true

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        // A subtype change is an input-surface interaction even when the
        // locale is not one of our built-in labels.  It must invalidate a
        // pending authenticated paste just like a keyboard-page change.
        registerInteraction()
        invalidatePendingPersonalization()
        languageForSubtype(newSubtype)?.let(::switchTo)
        activeSession?.let { scheduleEngineWarmup(it, force = true) }
    }

    @Suppress("DEPRECATION")
    private fun languageForSubtype(subtype: InputMethodSubtype?): InputLanguage? {
        val values = sequenceOf(subtype?.languageTag.orEmpty(), subtype?.locale.orEmpty())
            .map(String::trim)
            .filter(String::isNotEmpty)
        return values.map { value ->
            value.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        }.mapNotNull { language ->
            when (language) {
                "en" -> InputLanguage.ENGLISH
                "zh" -> InputLanguage.CHINESE
                else -> null
            }
        }.firstOrNull()
    }

    private fun bindView(view: ZeroInputView) {
        view.onUserInteraction = ::registerInteraction
        view.onKeyboardAction = ::handleKeyboardAction
        view.onClearCompositionRequested = {
            registerInteraction()
            val hadComposition = controller?.state?.snapshot?.isComposing == true
            syncSessionPrivacy()
            val composing = controller?.state?.snapshot?.isComposing == true
            if (composing) controller?.reset()
            maybeReloadEngine()
            hadComposition || composing
        }
        view.onEngineRetryRequested = ::retryEngine
        view.onScriptSwitchRequested = {
            registerInteraction()
            val current = graph.settings.chineseInputOptions
            graph.settings.chineseInputOptions = current.copy(script =
                if (current.script == dev.zeroinput.engine.api.ChineseScript.SIMPLIFIED)
                    dev.zeroinput.engine.api.ChineseScript.TRADITIONAL
                else dev.zeroinput.engine.api.ChineseScript.SIMPLIFIED)
            reconcileChineseOptions()
        }
        view.onCandidateSelected = { handleControllerCommand(InputCommand.SelectCandidate(it)) }
        view.onReadingSelected = { handleControllerCommand(InputCommand.SelectReading(it)) }
        view.onLayoutSwitchRequested = {
            registerInteraction()
            val options = graph.settings.chineseInputOptions
            graph.settings.chineseInputOptions = options.copy(keyboardLayout =
                if (options.keyboardLayout == ChineseKeyboardLayout.FULL) ChineseKeyboardLayout.NINE_KEY else ChineseKeyboardLayout.FULL)
            reconcileChineseOptions()
            activeSession?.let { scheduleEngineWarmup(it) }
        }
        view.onCandidatePageChanged = { handleControllerCommand(InputCommand.ChangeCandidatePage(it)) }
        view.onEmojiSelected = ::commitEmoji
        view.onSecureClipboardSelected = ::unlockAndCommitSecureItem
        view.onSettingsRequested = {
            registerInteraction()
            launchActivity(MainActivity::class.java)
        }
        view.onSecureClipboardManagementRequested = {
            launchActivity(SecureClipboardManagerActivity::class.java)
        }
    }

    private fun handleKeyboardAction(action: KeyboardAction) {
        registerInteraction()
        syncSessionPrivacy()
        maybeReloadLanguagePack()
        if (graph.settings.hapticFeedbackEnabled) {
            inputView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        when (action) {
            is KeyboardAction.Text -> controller?.handle(InputCommand.Text(action.value))
            is KeyboardAction.LiteralText -> controller?.handle(InputCommand.LiteralText(action.value))
            KeyboardAction.Backspace -> controller?.handle(InputCommand.Backspace)
            KeyboardAction.Space -> controller?.handle(InputCommand.Space)
            KeyboardAction.Enter -> controller?.handle(InputCommand.Enter)
            KeyboardAction.SwitchLanguage -> {
                controller?.switchLanguage()
                controller?.state?.language?.let {
                    graph.settings.lastLanguage = it
                    graph.settings.lastLanguagePackKey = null
                    selectSystemSubtype(it)
                }
            }
            KeyboardAction.OpenSettings -> launchActivity(MainActivity::class.java)
            KeyboardAction.ShowSecureClipboard -> Unit
            KeyboardAction.ShowEmoji -> Unit
            KeyboardAction.Shift,
            KeyboardAction.ShowLetters,
            KeyboardAction.ShowSymbols,
            KeyboardAction.ShowMoreSymbols,
            -> Unit
        }
        maybeReloadLanguagePack()
    }

    private fun handleControllerCommand(command: InputCommand) {
        registerInteraction()
        syncSessionPrivacy()
        maybeReloadLanguagePack()
        controller?.handle(command)
        maybeReloadLanguagePack()
    }

    @Suppress("DEPRECATION")
    private fun selectSystemSubtype(language: InputLanguage) {
        val manager = getSystemService(InputMethodManager::class.java) ?: return
        val method = manager.inputMethodList.firstOrNull {
            it.packageName == packageName && it.serviceName == ZeroInputService::class.java.name
        } ?: return
        val subtype = (0 until method.subtypeCount).asSequence()
            .map(method::getSubtypeAt)
            .firstOrNull { languageForSubtype(it) == language }
            ?: return
        // Keep Android's selection in sync so a new editor or configuration
        // change restores the language chosen on the keyboard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchInputMethod(method.id, subtype)
        } else {
            val token = window?.window?.attributes?.token ?: return
            manager.setInputMethodAndSubtype(token, method.id, subtype)
        }
    }

    private fun commitEmoji(value: String) {
        registerInteraction()
        syncSessionPrivacy()
        val session = activeSession ?: return
        val connection = currentInputConnection ?: return
        val boundConnection = session.connectionBinding.resolve(connection) ?: return
        if (!isSessionActive(session, boundConnection)) return
        val personalizationAllowed = session.controller.state.privacy.personalizationAllowed
        val emojiRevision = graph.emojiHistory.currentRevision()
        val writeGeneration = personalizationWriteGeneration.get()
        session.controller.reset()
        if (!isSessionActive(session, boundConnection)) return
        boundConnection.commitText(value, 1)
        if (personalizationAllowed) {
            // Recording history is encrypted I/O and must not delay the key
            // event that just committed the emoji.
            runCatching {
                BoundedExecutors.purge(localDataExecutor)
                localDataExecutor.execute {
                    if (personalizationWriteGeneration.get() == writeGeneration) {
                        runCatching { graph.emojiHistory.recordIfRevision(value, emojiRevision) }
                    }
                    mainHandler.post {
                        if (activeSession === session &&
                            personalizationWriteGeneration.get() == writeGeneration &&
                            inputView != null
                        ) {
                            inputView?.let(::renderLocalPanels)
                        }
                    }
                }
            }
        }
        inputView?.let(::renderLocalPanels)
    }

    private fun syncSessionPrivacy() {
        val session = activeSession ?: return
        if (session.controller.updatePrivacy(graph.settings.privacyConfiguration())) {
            inputView?.cancelPendingGestures()
            // A prepared engine carries the old policy. Invalidate it before
            // publishing the new state, then let the worker build a context
            // that matches the tightened policy.
            cancelEngineWarmup(clearInstalled = true)
            invalidatePendingPersonalization()
            // A policy change can happen while the authentication activity is
            // in the foreground.  Invalidate any grant started under the old
            // policy before publishing the new session state.
            registerInteraction()
            inputView?.let(::renderLocalPanels)
            scheduleEngineWarmup(session, force = true)
        }
    }

    private fun unlockAndCommitSecureItem(id: String) {
        registerInteraction()
        syncSessionPrivacy()
        if (!graph.settings.secureClipboardEnabled) {
            launchActivity(SecureClipboardManagerActivity::class.java)
            return
        }
        val session = activeSession ?: return
        if (session.controller.state.privacy.isSensitive) return
        val connection = currentInputConnection ?: return
        val boundConnection = session.connectionBinding.resolve(connection) ?: return
        if (!isSessionActive(session, boundConnection)) return
        if (secureClipboardRequest != null) return

        val request = SecureClipboardRequest(
            id = id,
            session = session,
            connection = boundConnection,
            interaction = interactionSequence,
        )
        secureClipboardRequest = request
        secureClipboardAuth = AuthenticationBroker.requestCancellable(this) authCallback@{ grant ->
            if (secureClipboardRequest !== request) return@authCallback
            secureClipboardAuth = null
            if (grant == null || !graph.settings.secureClipboardEnabled ||
                !isSessionActive(request.session, request.connection) ||
                request.interaction != interactionSequence
            ) {
                secureClipboardRequest = null
                return@authCallback
            }
            runCatching {
                val task = secureClipboardExecutor.submit {
                    if (secureClipboardRequest !== request ||
                        !graph.settings.secureClipboardEnabled ||
                        !isSessionActive(request.session, request.connection)
                    ) return@submit
                    val result = runCatching { graph.secureClipboard.read(request.id, grant) }
                    mainHandler.post {
                        if (secureClipboardRequest !== request) return@post
                        secureClipboardRequest = null
                        if (!isSessionActive(request.session, request.connection) ||
                            !graph.settings.secureClipboardEnabled ||
                            request.interaction != interactionSequence
                        ) return@post
                        val value = result.getOrNull()
                        if (value == null) {
                            Toast.makeText(this, R.string.operation_failed, Toast.LENGTH_SHORT).show()
                            inputView?.let(::renderLocalPanels)
                            return@post
                        }
                        request.session.controller.reset()
                        if (!isSessionActive(request.session, request.connection)) return@post
                        request.connection.commitText(value, 1)
                        inputView?.returnToKeyboard()
                    }
                }
                request.task = task
            }.onFailure {
                clearSecureClipboardRequest(request)
            }
        }
    }

    private fun renderLocalPanels(view: ZeroInputView) {
        val session = activeSession
        val personalizationAllowed = session?.controller?.state?.privacy?.personalizationAllowed == true
        val sensitive = session?.controller?.state?.privacy?.isSensitive == true
        // Do not offer authenticated private snippets from a password/PIN
        // editor.  This prevents an accidental secure-clipboard paste into a
        // credential field while preserving the feature in ordinary editors.
        val enabled = session != null && graph.settings.secureClipboardEnabled && !sensitive
        if (!personalizationAllowed) recentEmojiCache = emptyList()
        if (!enabled) secureClipboardCache = emptyList()
        view.renderRecentEmoji(if (personalizationAllowed) recentEmojiCache else emptyList())
        view.renderSecureClipboard(enabled, if (enabled) secureClipboardCache else emptyList())
        scheduleLocalPanelRefresh(personalizationAllowed, enabled)
    }

    private fun scheduleLocalPanelRefresh(personalizationAllowed: Boolean, secureClipboardEnabled: Boolean) {
        val revision = ++localPanelRevision
        localPanelTask?.cancel(false)
        BoundedExecutors.purge(localDataExecutor)
        localPanelTask = runCatching {
            localDataExecutor.submit {
                val recent = if (personalizationAllowed) {
                    runCatching { graph.emojiHistory.recent() }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val secureItems = if (secureClipboardEnabled) {
                    runCatching {
                        graph.secureClipboard.summaries().map {
                            SecureClipboardItemUi(it.id, it.displayName)
                        }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                mainHandler.post {
                    if (revision != localPanelRevision || inputView == null) return@post
                    val currentPersonalizationAllowed =
                        activeSession?.controller?.state?.privacy?.personalizationAllowed == true
                    val currentSession = activeSession
                    val currentSecureClipboardEnabled = currentSession != null &&
                        graph.settings.secureClipboardEnabled &&
                        currentSession.controller.state.privacy.isSensitive.not()
                    // Do not retain personal panel data in memory after the
                    // session has become private/disabled, even if the worker
                    // completed a read that started under the old policy.
                    recentEmojiCache = if (currentPersonalizationAllowed) recent else emptyList()
                    secureClipboardCache = if (currentSecureClipboardEnabled) secureItems else emptyList()
                    inputView?.renderRecentEmoji(
                        if (currentPersonalizationAllowed) recentEmojiCache else emptyList(),
                    )
                    inputView?.renderSecureClipboard(
                        currentSecureClipboardEnabled,
                        if (currentSecureClipboardEnabled) secureClipboardCache else emptyList(),
                    )
                }
            }
        }.getOrNull()
    }

    /**
     * Coalesces worker notifications into one main-thread refresh and binds
     * that refresh to the session that requested the query.  A burst of
     * prefixes therefore cannot enqueue an unbounded stream of UI work, and a
     * callback from an old editor cannot repaint a newly opened editor.
     */
    private fun schedulePersonalizationRefresh() {
        val session = activeSession ?: return
        if (!personalizationRefreshPending.compareAndSet(false, true)) return
        if (!mainHandler.post {
                personalizationRefreshPending.set(false)
                if (activeSession !== session) return@post
                if (!session.controller.state.privacy.personalizationAllowed) return@post
                session.controller.refreshPersonalization()
            }
        ) {
            personalizationRefreshPending.set(false)
        }
    }

    private fun scheduleEngineWarmup(session: InputSession, force: Boolean = false) {
        if (activeSession !== session) return
        reconcileChineseOptions()
        val request = session.warmupRequest(sessionChineseOptions, nativeRetryRequested, sessionChineseEngine)
        if (!request.privacy.suggestionsAllowed) {
            cancelEngineWarmup(clearInstalled = true)
            return
        }
        if (session.controller.state.snapshot.isComposing) {
            engineWarmupRetry = true
            return
        }
        if (installedEngineWarmupContext == request) return
        if (!force && unavailableEngineWarmupContext == request) return
        if (engineWarmupInFlight && engineWarmupContext == request) return

        if (nativeRetryRequested && graph.rime.runtime.state == RimeRuntimeState.FAILED) {
            session.controller.reloadEngineIfIdle()
        }

        unavailableEngineWarmupContext = null
        engineWarmupRetry = false
        engineWarmupContext = request
        engineWarmupInFlight = true
        val ticket = engineWarmupCoordinator?.request(request)
        if (ticket == null) {
            engineWarmupInFlight = false
            unavailableEngineWarmupContext = request
        } else {
            engineWarmupTicket = ticket
        }
        renderEngineStatus()
    }

    private fun handleEngineWarmupResult(result: EngineWarmupResult) {
        val currentRequest = engineWarmupContext
        if (!engineWarmupInFlight || result.ticket != engineWarmupTicket || result.request != currentRequest) {
            (result as? EngineWarmupResult.Prepared)?.engine?.close()
            return
        }
        engineWarmupInFlight = false
        engineWarmupTicket = null
        when (result) {
            is EngineWarmupResult.Unavailable -> {
                unavailableEngineWarmupContext = result.request
            }

            is EngineWarmupResult.Prepared -> {
                val session = activeSession
                if (session == null || !isWarmupSessionCurrent(session, result.request) ||
                    session.controller.state.snapshot.isComposing
                ) {
                    result.engine.close()
                    engineWarmupRetry = true
                    return
                }
                var transferred = false
                try {
                    // Authentication must not outlive the engine state that authorized it.
                    registerInteraction()
                    transferred = session.controller.adoptPreparedEngine(result.engine)
                    if (transferred) {
                        installedEngineWarmupContext = result.request
                        unavailableEngineWarmupContext = null
                        engineWarmupRetry = false
                        inputView?.let(::renderLocalPanels)
                    } else {
                        engineWarmupRetry = true
                    }
                } finally {
                    if (!transferred) result.engine.close()
                }
            }
        }
        renderEngineStatus()
    }

    private fun isWarmupSessionCurrent(
        session: InputSession?,
        request: EngineWarmupRequest,
    ): Boolean = session != null && activeSession === session &&
        session.token == request.sessionToken &&
        session.packageName == request.packageName &&
        session.controller.state.language == request.language &&
            session.controller.state.languagePackKey == request.languagePackKey &&
            session.controller.state.privacy == request.privacy &&
            sessionChineseOptions == request.chineseOptions &&
            graph.settings.chineseInputOptions == request.chineseOptions &&
            sessionChineseEngine == request.chineseEngine && graph.settings.chineseEngine == request.chineseEngine

    private fun cancelEngineWarmup(clearInstalled: Boolean = false) {
        engineWarmupCoordinator?.cancel()
        engineWarmupTicket = null
        engineWarmupContext = null
        engineWarmupInFlight = false
        engineWarmupRetry = false
        unavailableEngineWarmupContext = null
        if (clearInstalled) installedEngineWarmupContext = null
    }

    private fun switchTo(language: InputLanguage) {
        val languageChanged = controller?.state?.language != language
        if (languageChanged) {
            cancelEngineWarmup(clearInstalled = true)
            val packKey = graph.settings.lastLanguagePackKey.takeIf {
                graph.settings.lastLanguage == language
            }
            controller?.setLanguage(language, packKey)
        }
        // A repeated subtype callback for the same language must not discard
        // a language pack selected for that language. Only a persisted
        // cross-language change makes the old pack invalid.
        if (graph.settings.lastLanguage != language) {
            graph.settings.lastLanguage = language
            graph.settings.lastLanguagePackKey = null
        }
        activeSession?.let { scheduleEngineWarmup(it, force = true) }
    }

    private fun launchActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun endInputSession(reset: Boolean) {
        inputView?.cancelPendingGestures()
        registerInteraction()
        nativeRetryRequested = false
        invalidatePendingPersonalization()
        cancelEngineWarmup(clearInstalled = true)
        localPanelRevision++
        localPanelTask?.cancel(false)
        BoundedExecutors.purge(localDataExecutor)
        recentEmojiCache = emptyList()
        secureClipboardCache = emptyList()
        activeSession?.let { session ->
            if (reset) session.controller.reset()
            session.controller.close()
        }
        activeSession = null
        controller = null
        languagePackReloadPending = false
        engineReloadPending = false
        cancelSecureClipboardRequest()
    }

    private fun reconcileLanguagePackSession(session: InputSession) {
        val configuredLanguage = graph.settings.lastLanguage
        val configuredKey = graph.settings.lastLanguagePackKey
        val currentLanguage = session.controller.state.language
        val currentKey = session.controller.state.languagePackKey
        if (configuredLanguage != currentLanguage || configuredKey != currentKey) {
            if (session.controller.state.snapshot.isComposing) {
                languagePackReloadPending = true
                cancelEngineWarmup(clearInstalled = true)
            } else {
                cancelEngineWarmup(clearInstalled = true)
                session.controller.setLanguage(configuredLanguage, configuredKey)
                languagePackReloadPending = false
                scheduleEngineWarmup(session, force = true)
            }
        } else if (configuredKey != null && graph.languagePackRegistry.contains(configuredKey)) {
            languagePackReloadPending = false
            scheduleEngineWarmup(session)
        } else if (configuredKey != null && graph.isLanguagePackDiscoveryComplete()) {
            // The registry has reached a terminal state and the selected key
            // is no longer available. Clear it before preparing the built-in
            // engine so the stale package cannot be resurrected by a callback.
            graph.settings.lastLanguagePackKey = null
            languagePackReloadPending = true
        } else {
            // Discovery may still be in flight.  Preserve the requested key
            // and retry after the registry callback instead of clearing it.
            languagePackReloadPending = configuredKey != null
        }
        inputView?.let(::renderLocalPanels)
    }

    private fun maybeReloadLanguagePack() {
        val session = activeSession ?: return
        val language = graph.settings.lastLanguage
        val key = graph.settings.lastLanguagePackKey
        if (language != session.controller.state.language || key != session.controller.state.languagePackKey) {
            if (session.controller.state.snapshot.isComposing) return
            cancelEngineWarmup(clearInstalled = true)
            session.controller.setLanguage(language, key)
            languagePackReloadPending = false
            scheduleEngineWarmup(session, force = true)
            inputView?.let(::renderLocalPanels)
            return
        }
        if (key == null) {
            languagePackReloadPending = false
            scheduleEngineWarmup(session)
            return
        }
        if (!graph.languagePackRegistry.contains(key)) return
        languagePackReloadPending = false
        scheduleEngineWarmup(session)
    }

    private fun maybeReloadEngine() {
        val session = activeSession ?: return
        if (graph.settings.lastLanguagePackKey != null) {
            engineReloadPending = false
            return
        }
        engineReloadPending = false
        scheduleEngineWarmup(session)
    }

    private fun reconcileChineseOptions() {
        val configured = graph.settings.chineseInputOptions
        inputView?.renderChineseOptions(configured)
        val chosenEngine = graph.settings.chineseEngine
        if (configured == sessionChineseOptions && chosenEngine == sessionChineseEngine) return
        val session = activeSession ?: return
        if (session.controller.state.snapshot.isComposing) {
            renderEngineStatus()
            return
        }
        cancelEngineWarmup(clearInstalled = true)
        sessionChineseOptions = configured
        sessionChineseEngine = chosenEngine
        if (session.controller.state.language == InputLanguage.CHINESE && session.controller.state.languagePackKey == null) {
            // Retire the native session before the worker can deploy a different prism.
            session.controller.reloadEngineIfIdle()
        }
        renderEngineStatus()
    }

    private fun renderEngineStatus() {
        val session = activeSession
        val state = session?.controller?.state
        val status = when {
            state == null || !state.privacy.suggestionsAllowed || state.language != InputLanguage.CHINESE ||
                state.languagePackKey != null -> InputEngineStatus.HIDDEN
            sessionChineseOptions != graph.settings.chineseInputOptions || sessionChineseEngine != graph.settings.chineseEngine -> InputEngineStatus.PENDING_CONFIGURATION
            (sessionChineseEngine == ChineseEngineChoice.RIME && graph.rime.runtime.state == RimeRuntimeState.FAILED) ||
                unavailableEngineWarmupContext != null -> InputEngineStatus.FAILED
            installedEngineWarmupContext == session.warmupRequest(sessionChineseOptions, nativeRetryRequested, sessionChineseEngine) -> InputEngineStatus.READY
            else -> InputEngineStatus.PREPARING
        }
        inputView?.renderEngineStatus(status)
        inputView?.renderChineseOptions(graph.settings.chineseInputOptions)
        inputView?.renderActiveLayout(sessionChineseOptions.keyboardLayout)
    }

    private fun retryEngine() {
        registerInteraction()
        val session = activeSession ?: return
        nativeRetryRequested = true
        cancelEngineWarmup(clearInstalled = true)
        scheduleEngineWarmup(session, force = true)
        renderEngineStatus()
    }

    /** Records an interaction and cancels work that was authorized in an
     * older UI state.  All callers run on the IME main thread. */
    private fun registerInteraction() {
        interactionSequence++
        cancelSecureClipboardRequest()
    }

    private fun invalidatePendingPersonalization() {
        personalizationWriteGeneration.incrementAndGet()
        graph.personalization.invalidatePendingWrites()
    }

    private fun cancelSecureClipboardRequest() {
        secureClipboardAuth?.close()
        secureClipboardAuth = null
        secureClipboardRequest?.task?.cancel(true)
        BoundedExecutors.purge(secureClipboardExecutor)
        secureClipboardRequest = null
    }

    private fun clearSecureClipboardRequest(request: SecureClipboardRequest) {
        if (secureClipboardRequest === request) {
            secureClipboardAuth?.close()
            secureClipboardAuth = null
            request.task?.cancel(true)
            BoundedExecutors.purge(secureClipboardExecutor)
            secureClipboardRequest = null
        }
    }

    private fun isSessionActive(session: InputSession, connection: InputConnection): Boolean =
        activeSession === session && session.token == sessionSequence &&
            session.connectionBinding.connection === connection &&
            currentInputConnection === connection

    private data class InputSession(
        val token: Long,
        val controller: InputSessionController,
        val connectionBinding: SessionConnectionBinding,
        val packageName: String?,
    ) {
        fun warmupRequest(options: ChineseInputOptions, retry: Boolean, engine: ChineseEngineChoice): EngineWarmupRequest = EngineWarmupRequest(
            sessionToken = token,
            language = controller.state.language,
            languagePackKey = controller.state.languagePackKey,
            packageName = packageName,
            privacy = controller.state.privacy,
            chineseOptions = options,
            retryInitialization = retry,
            chineseEngine = engine,
        )
    }

    private class SessionConnectionBinding(initial: InputConnection?) {
        /**
         * The connection is captured when the input session starts.  A null
         * connection is deliberately not resolved later: doing so could bind
         * an authentication result to a different editor after a client
         * switch.
         */
        val connection: InputConnection? = initial

        fun resolve(current: InputConnection?): InputConnection? =
            connection?.takeIf { it === current }
    }

    private data class SecureClipboardRequest(
        val id: String,
        val session: InputSession,
        val connection: InputConnection,
        val interaction: Long,
    ) {
        @Volatile
        var task: Future<*>? = null
    }
}
