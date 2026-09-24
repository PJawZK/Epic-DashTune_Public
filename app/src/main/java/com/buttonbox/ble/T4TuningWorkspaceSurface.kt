package com.buttonbox.ble

import android.os.Build
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Installs the T4 Tuning workspace without making dashboard HTML the data authority. */
object T4TuningWorkspaceSurface {
    private const val BASE_ASSET = "t4_tuning_workspace.js"
    private const val EXTENSION_ASSET = "t4_tuning_workspace_runtime_extension.js"
    private const val CLEANUP_ASSET = "t4_tuning_workspace_interaction_cleanup.js"
    private const val LAYOUT_ASSET = "t4_tuning_workspace_ui_layout_completion.js"
    private const val SNAPSHOT_ARRAY_VALUES_ASSET = "t4_tuning_workspace_snapshot_array_values.js"
    private const val CONNECTION_REFRESH_ASSET = "t4_tuning_workspace_connection_refresh.js"
    private const val PROJECT_STATE_ASSET = "t4_tuning_workspace_project_state.js"
    private const val OFFLINE_SURFACES_ASSET = "t4_tuning_workspace_offline_surfaces.js"
    private const val RECOVERY_ASSET = "t4_tuning_workspace_recovery.js"
    private const val TELEMETRY_ASSET = "t4_tuning_workspace_telemetry_controls.js"
    private const val TABLE_CONTROLS_ASSET = "t4_tuning_workspace_table_controls.js"
    private const val CLOSURE_END = "})();"
    private const val PROJECT_READY_EVENT = "epicdash:permanent-tuner-project-ready"
    private const val PROFILE_PERSIST_THREAD_NAME = "EpicDash-TunerProfilePersist"
    private const val PROFILE_PERSIST_MONITOR_MS = 100L

    private data class CandidateDelivery(
        val candidateId: Long,
        val deliveryId: Long,
        val installToken: Long
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private class T4LifecycleWebViewClient(
        private val delegate: WebViewClient
    ) : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            delegate.onPageFinished(view, url)
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            T4TuningWorkspaceSurface.detach(view)
            return delegate.onRenderProcessGone(view, detail)
        }
    }

    private val installTokens = WeakHashMap<WebView, Long>()
    private val detachListeners = WeakHashMap<WebView, View.OnAttachStateChangeListener>()
    private val permanentProjectRequests = WeakHashMap<WebView, Long>()
    private val installSequence = AtomicLong(0L)
    private val candidateDeliverySequence = AtomicLong(0L)
    private val candidateDeliveryLock = ReentrantLock()
    private val candidateDeliveryChanged = candidateDeliveryLock.newCondition()
    private val candidateDeliveries = LinkedHashMap<Long, CandidateDelivery>()
    private var candidatePersistencePending: Long? = null
    private var candidatePersistenceInFlight: Long? = null

    fun install(view: WebView?) {
        if (view == null) return
        val token = installSequence.incrementAndGet()
        val previousToken = synchronized(installTokens) {
            installTokens.put(view, token)
        }
        ensureLifecycleHooks(view)
        if (previousToken != null && previousToken != token) {
            PerformanceMetrics.increment("tunerCompiledProfileInstallTokenReplaced")
            cancelCandidateDeliveries(view.context.applicationContext, previousToken)
        }
        // TunerRecoveryBridge is installed by DashboardLabActivity before page load. Tuner surface
        // installation therefore never blocks on project classification and never reloads a healthy
        // WebView merely to expose recovery.
        installCombinedScript(view, token)
    }

    /**
     * Cancels every candidate-bearing payload lease owned by this WebView's current install token.
     * The attach-state and renderer-loss hooks call this automatically, while keeping the lease
     * itself keyed only by the process-local install token rather than a strong WebView reference.
     */
    fun detach(view: WebView?) {
        if (view == null) return
        val token = synchronized(installTokens) { installTokens.remove(view) }
        val listener = synchronized(detachListeners) { detachListeners.remove(view) }
        synchronized(permanentProjectRequests) { permanentProjectRequests.remove(view) }
        if (listener != null) view.removeOnAttachStateChangeListener(listener)
        if (token != null) {
            PerformanceMetrics.increment("tunerCompiledProfileInstallTokenDetached")
            cancelCandidateDeliveries(view.context.applicationContext, token)
        }
    }

    private fun ensureLifecycleHooks(view: WebView) {
        val shouldAttachListener = synchronized(detachListeners) {
            if (detachListeners.containsKey(view)) {
                false
            } else {
                val listener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit

                    override fun onViewDetachedFromWindow(v: View) {
                        T4TuningWorkspaceSurface.detach(v as? WebView)
                    }
                }
                detachListeners[view] = listener
                view.addOnAttachStateChangeListener(listener)
                true
            }
        }
        if (shouldAttachListener) {
            PerformanceMetrics.increment("tunerCompiledProfileDetachHookInstalled")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val currentClient = view.webViewClient
            if (currentClient !is T4LifecycleWebViewClient) {
                view.webViewClient = T4LifecycleWebViewClient(currentClient)
                PerformanceMetrics.increment("tunerCompiledProfileRendererHookInstalled")
            }
        }
    }

    private fun installCombinedScript(view: WebView, token: Long) {
        Thread({
            val buildStartedNs = System.nanoTime()
            val script = buildCombinedScript(view)
            PerformanceMetrics.timingUs("tunerSurfaceScriptBuild", System.nanoTime() - buildStartedNs)
            if (script == null || !installStillCurrent(view, token)) return@Thread
            view.post {
                if (!installStillCurrent(view, token)) return@post
                val evaluateStartedNs = System.nanoTime()
                view.evaluateJavascript(script) {
                    PerformanceMetrics.timingUs("tunerSurfaceScriptEvaluate", System.nanoTime() - evaluateStartedNs)
                    if (installStillCurrent(view, token)) PerformanceMetrics.increment("tunerPermanentProjectDeferredAtInstall")
                }
            }
        }, "EpicDash-TunerSurfaceInstall").apply { isDaemon = true }.start()
    }

    /**
     * A candidate-bearing permanent-workspace payload must not enter a WebView while derived-profile
     * persistence is pending or executing. If the gate was closed, wait for that native worker to
     * finish and then rebuild/re-read the lightweight bootstrap tuple so stale payloads are never used.
     */
    private fun tryRegisterCandidateDelivery(candidateId: Long, installToken: Long): CandidateDelivery? =
        candidateDeliveryLock.withLock {
            if (candidatePersistencePending != null || candidatePersistenceInFlight != null) {
                return@withLock null
            }
            val deliveryId = candidateDeliverySequence.incrementAndGet()
            val delivery = CandidateDelivery(candidateId, deliveryId, installToken)
            candidateDeliveries[deliveryId] = delivery
            PerformanceMetrics.increment("tunerCompiledProfileDeliveryRegistered")
            delivery
        }

    private fun awaitCandidateDeliveryGate(): Boolean {
        candidateDeliveryLock.lock()
        try {
            var waited = false
            while (candidatePersistencePending != null || candidatePersistenceInFlight != null) {
                if (!waited) {
                    waited = true
                    PerformanceMetrics.increment("tunerCompiledProfileDeliveryGateWaits")
                }
                try {
                    candidateDeliveryChanged.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    PerformanceMetrics.increment("tunerCompiledProfileDeliveryGateInterrupted")
                    return false
                }
            }
            return true
        } finally {
            candidateDeliveryLock.unlock()
        }
    }

    /** Caller must hold candidateDeliveryLock. Reserves exactly one native persistence worker. */
    private fun reservePersistenceIfReadyLocked(): Long? {
        if (candidateDeliveries.isNotEmpty() ||
            candidatePersistenceInFlight != null ||
            candidatePersistencePending == null
        ) return null
        val candidateId = candidatePersistencePending
        candidatePersistencePending = null
        candidatePersistenceInFlight = candidateId
        PerformanceMetrics.increment("tunerCompiledProfileDeliveryBarrierReleased")
        return candidateId
    }

    private fun launchCandidatePersistence(context: android.content.Context, candidateId: Long) {
        TunerPermanentProjectStore.persistDeferredCompiledProfileAsync(context, candidateId)
        monitorCompiledProfilePersistence(candidateId)
    }

    private fun completeCandidateDelivery(
        context: android.content.Context,
        delivery: CandidateDelivery?,
        injected: Boolean
    ) {
        if (delivery == null) return
        val startPersistenceCandidate = candidateDeliveryLock.withLock {
            val registeredDelivery = candidateDeliveries.remove(delivery.deliveryId)
            if (registeredDelivery != delivery) {
                PerformanceMetrics.increment("tunerCompiledProfileDeliveryCompletionRejected")
                return
            }
            PerformanceMetrics.increment("tunerCompiledProfileDeliveryCompleted")
            if (injected) {
                // Candidate IDs are monotonic. If callbacks complete out of order, preserve the newest
                // successfully injected candidate rather than allowing an older callback to overwrite it.
                candidatePersistencePending = maxOf(
                    candidatePersistencePending ?: delivery.candidateId,
                    delivery.candidateId
                )
            }
            reservePersistenceIfReadyLocked()
        }
        startPersistenceCandidate?.let { launchCandidatePersistence(context, it) }
    }

    private fun cancelCandidateDeliveries(context: android.content.Context, installToken: Long) {
        val startPersistenceCandidate = candidateDeliveryLock.withLock {
            var removed = 0
            val iterator = candidateDeliveries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.installToken == installToken) {
                    iterator.remove()
                    removed++
                }
            }
            if (removed > 0) {
                PerformanceMetrics.increment("tunerCompiledProfileDeliveryTokenCancellations", removed.toLong())
            }
            reservePersistenceIfReadyLocked()
        }
        startPersistenceCandidate?.let { launchCandidatePersistence(context, it) }
    }

    private fun monitorCompiledProfilePersistence(candidateId: Long) {
        Thread({
            // persistDeferredCompiledProfileAsync starts its named worker synchronously before it
            // returns. Internal retries use the same name and are launched before the previous worker
            // exits. An interrupt is remembered but never allowed to abandon gate cleanup: the monitor
            // continues until that complete worker family is gone, reopens/signals the gate, and only
            // then restores its own interrupt status.
            var interrupted = false
            while (profilePersistenceWorkerAlive()) {
                try {
                    Thread.sleep(PROFILE_PERSIST_MONITOR_MS)
                } catch (_: InterruptedException) {
                    if (!interrupted) {
                        PerformanceMetrics.increment("tunerCompiledProfileDeliveryMonitorInterrupted")
                    }
                    interrupted = true
                }
            }
            candidateDeliveryLock.withLock {
                if (candidatePersistenceInFlight == candidateId) {
                    candidatePersistenceInFlight = null
                    PerformanceMetrics.increment("tunerCompiledProfileDeliveryGateReopened")
                }
                candidateDeliveryChanged.signalAll()
            }
            if (interrupted) Thread.currentThread().interrupt()
        }, "EpicDash-TunerProfileDeliveryGate").apply { isDaemon = true }.start()
    }

    private fun profilePersistenceWorkerAlive(): Boolean =
        Thread.getAllStackTraces().keys.any { thread ->
            thread.isAlive && thread.name == PROFILE_PERSIST_THREAD_NAME
        }

    /** Request the persisted offline project only when JS is actually detached from live ECU state. */
    fun requestPermanentProject(view: WebView?): Boolean {
        if (view == null) return false
        val token = synchronized(installTokens) { installTokens[view] } ?: return false
        if (!installStillCurrent(view, token)) return false
        val accepted = synchronized(permanentProjectRequests) {
            if (permanentProjectRequests[view] == token) false
            else {
                permanentProjectRequests[view] = token
                true
            }
        }
        if (!accepted) {
            PerformanceMetrics.increment("tunerPermanentProjectRequestDeduped")
            return false
        }
        PerformanceMetrics.increment("tunerPermanentProjectRequested")
        loadPermanentProjectAsync(view, token)
        return true
    }

    private fun loadPermanentProjectAsync(view: WebView, token: Long) {
        Thread({
            PerformanceMetrics.increment("tunerProjectBootstrapStarts")
            val appContext = view.context.applicationContext
            var bootstrap: TunerPermanentProjectStore.BootstrapWorkspacePayload
            var delivery: CandidateDelivery? = null

            while (true) {
                // The store owns the single serialization and returns the exact immutable String plus
                // only the lightweight deferred-profile candidate ID. No large JSONObject crosses
                // this boundary. A blocked generation re-reads after persistence rather than injecting
                // a payload captured before the delivery gate closed.
                val bootstrapSerializeStartedNs = System.nanoTime()
                bootstrap = TunerPermanentProjectStore.bootstrapWorkspacePayload(appContext)
                PerformanceMetrics.timingUs(
                    "tunerProjectBootstrapSerialize",
                    System.nanoTime() - bootstrapSerializeStartedNs
                )
                val candidateId = bootstrap.compiledProfileCandidateId
                if (candidateId == null) break
                delivery = tryRegisterCandidateDelivery(candidateId, token)
                if (delivery != null) break
                if (!awaitCandidateDeliveryGate()) return@Thread
                PerformanceMetrics.increment("tunerProjectBootstrapRetriedAfterProfilePersist")
            }

            val payload = bootstrap.json
            PerformanceMetrics.sample("tunerProjectPayloadChars", payload.length.toLong())
            if (!installStillCurrent(view, token)) {
                PerformanceMetrics.increment("tunerProjectBootstrapDiscardedAfterPayload")
                completeCandidateDelivery(appContext, delivery, false)
                return@Thread
            }
            val script = "window.__EPIC_TUNER_PERMANENT_WORKSPACE__=$payload;" +
                "window.dispatchEvent(new CustomEvent('$PROJECT_READY_EVENT'));"
            val posted = view.post {
                if (!installStillCurrent(view, token)) {
                    PerformanceMetrics.increment("tunerProjectBootstrapDiscardedBeforeInject")
                    completeCandidateDelivery(appContext, delivery, false)
                    return@post
                }
                PerformanceMetrics.increment("tunerProjectInjectStarts")
                val injectStartedNs = System.nanoTime()
                runCatching {
                    view.evaluateJavascript(script) {
                        PerformanceMetrics.timingUs("tunerProjectInject", System.nanoTime() - injectStartedNs)
                        PerformanceMetrics.increment("tunerProjectInjectCallbacks")
                        // This exact delivery no longer owns a WebView-side large-payload phase. The
                        // coordinator starts derived-profile persistence only after every sibling
                        // delivery has likewise completed or been canceled.
                        completeCandidateDelivery(appContext, delivery, true)
                    }
                }.onFailure {
                    PerformanceMetrics.increment("tunerProjectInjectFailures")
                    completeCandidateDelivery(appContext, delivery, false)
                }
            }
            if (!posted) {
                PerformanceMetrics.increment("tunerProjectBootstrapPostRejected")
                completeCandidateDelivery(appContext, delivery, false)
            }
        }, "EpicDash-TunerProjectBootstrap").apply { isDaemon = true }.start()
    }

    private fun installStillCurrent(view: WebView, token: Long): Boolean =
        synchronized(installTokens) { installTokens[view] == token } && view.isAttachedToWindow

    private fun buildCombinedScript(view: WebView): String? = runCatching {
        val base = view.context.assets.open(BASE_ASSET).bufferedReader().use { it.readText() }
        val extension = runCatching {
            view.context.assets.open(EXTENSION_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val cleanup = runCatching {
            view.context.assets.open(CLEANUP_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val layout = runCatching {
            view.context.assets.open(LAYOUT_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val snapshotArrayValues = runCatching {
            view.context.assets.open(SNAPSHOT_ARRAY_VALUES_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val connectionRefresh = runCatching {
            view.context.assets.open(CONNECTION_REFRESH_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        // Never block Tuner surface installation on INI parsing/TuneSnapshot projection. The real
        // permanent project is delivered asynchronously through PROJECT_READY_EVENT immediately
        // after this lightweight shell is installed.
        val permanentBootstrap =
            "window.__EPIC_TUNER_PERMANENT_WORKSPACE__={status:'deferred',capability:'READ_ONLY',reason:'Saved Tuner project loads on demand when ECU is offline'};"
        val projectState = runCatching {
            view.context.assets.open(PROJECT_STATE_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val offlineSurfaces = runCatching {
            view.context.assets.open(OFFLINE_SURFACES_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val recovery = runCatching {
            view.context.assets.open(RECOVERY_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val telemetry = runCatching {
            view.context.assets.open(TELEMETRY_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val tableControls = runCatching {
            view.context.assets.open(TABLE_CONTROLS_ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val layers = listOf(
            extension,
            cleanup,
            layout,
            snapshotArrayValues,
            connectionRefresh,
            permanentBootstrap,
            projectState,
            offlineSurfaces,
            recovery,
            telemetry,
            tableControls
        ).filter { it.isNotBlank() }
        if (layers.isEmpty()) return@runCatching base

        val closureIndex = base.lastIndexOf(CLOSURE_END)
        if (closureIndex < 0) return@runCatching base

        buildString(base.length + layers.sumOf { it.length } + layers.size * 2) {
            append(base, 0, closureIndex)
            layers.forEach { layer ->
                append('\n')
                append(layer)
            }
            append('\n')
            append(base, closureIndex, base.length)
        }
    }.getOrNull()
}
