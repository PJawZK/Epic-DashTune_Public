'use strict';
const assert = require('assert');
const fs = require('fs');
const read = p => fs.readFileSync(p, 'utf8');
const activity = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const store = read('app/src/main/java/com/buttonbox/ble/TunerPermanentProjectStore.kt');
const surface = read('app/src/main/java/com/buttonbox/ble/T4TuningWorkspaceSurface.kt');
const hub = read('app/src/main/java/com/buttonbox/ble/DashboardDataHub.kt');

const restoreStart = activity.indexOf('private fun restoreUsbProfileAsync()');
const restoreEnd = activity.indexOf('private val openUsbIniLauncher', restoreStart);
assert.ok(restoreStart >= 0 && restoreEnd > restoreStart, 'restore boundaries missing');
const restore = activity.slice(restoreStart, restoreEnd);
assert.ok(restore.includes('TunerPermanentProjectStore.loadProfileForCurrentIni'));
assert.ok(!restore.includes('UsbTunerStudioProfileParser.parseMeasured(persisted.second'));
assert.ok(restore.includes('catch (cancelled: CancellationException)'));
assert.ok(restore.includes('throw cancelled'));
assert.ok(store.includes('fun loadProfileForCurrentIni(context: Context)'));
assert.ok(store.includes('profileForSource(context, source, importedName, sourceSha)'));
assert.ok(hub.includes('usbChannelCatalogJson: String'));
assert.ok(!hub.includes('usbChannelCatalog: JSONArray'));
assert.ok(activity.includes('getHistoricalProcessExitReasons(packageName, 0, 6)'));
assert.ok(activity.includes('"previousProcessExitReasons"'));
assert.ok(activity.includes('webView.destroy()'), 'LAB Activity must still explicitly destroy its WebView');

for (const marker of [
  'tunerProfileIniRead',
  'tunerProfileIniHash',
  'tunerProfileResolve',
  'tunerProfileRestoreTotal',
  'tunerCompiledProfileRead',
  'tunerCompiledProfileDecode',
  'tunerCompiledProfileEncode',
  'tunerCompiledProfileSerialize',
  'tunerCompiledProfileWrite',
  'tunerCompiledProfilePersistTotal',
  'tunerCompiledProfileAuthorityCheck',
  'tunerCompiledProfileDeferred',
  'tunerCompiledProfileDeferredWhileBusy',
  'tunerCompiledProfileInjectionGateRejected',
  'tunerCompiledProfileAsyncQueued',
  'tunerCompiledProfileAsyncSuccess',
  'tunerCompiledProfileAsyncFailure',
  'tunerCompiledProfileAsyncStale',
  'tunerProfileProcessHits',
  'tunerProfileCompiledHits',
  'tunerProfileIniParse',
  'tunerProfileIniParses',
  'tunerProfileStateLockWait',
  'tunerSnapshotRead',
  'tunerSnapshotDecode',
  'tunerSnapshotVerify',
  'tunerSnapshotLoadTotal',
  'tunerBootstrapIniRead',
  'tunerBootstrapIniHash',
  'tunerBootstrapProfileResolve',
  'tunerBootstrapSnapshotResolve',
  'tunerBootstrapProcessHits',
  'tunerBootstrapBuilds',
  'tunerBootstrapSingleFlightWait',
  'tunerBootstrapSingleFlightJoins',
  'tunerWorkspaceProjectionBuild',
  'tunerBootstrapCacheSerialize',
  'tunerBootstrapCompatibilityDecode',
  'tunerPermanentBootstrapTotal'
]) assert.ok(store.includes(marker), 'missing permanent-project profiling marker: ' + marker);

for (const marker of [
  'tunerSurfaceScriptBuild',
  'tunerSurfaceScriptEvaluate',
  'tunerProjectBootstrapSerialize',
  'tunerProjectPayloadChars',
  'tunerProjectBootstrapStarts',
  'tunerProjectBootstrapDiscardedAfterPayload',
  'tunerProjectBootstrapDiscardedBeforeInject',
  'tunerProjectBootstrapRetriedAfterProfilePersist',
  'tunerProjectBootstrapPostRejected',
  'tunerProjectInjectStarts',
  'tunerProjectInjectCallbacks',
  'tunerProjectInjectFailures',
  'tunerProjectInject',
  'tunerCompiledProfileDeliveryRegistered',
  'tunerCompiledProfileDeliveryCompleted',
  'tunerCompiledProfileDeliveryGateWaits',
  'tunerCompiledProfileDeliveryBarrierReleased',
  'tunerCompiledProfileDeliveryGateReopened',
  'tunerCompiledProfileInstallTokenReplaced',
  'tunerCompiledProfileInstallTokenDetached',
  'tunerCompiledProfileDetachHookInstalled',
  'tunerCompiledProfileRendererHookInstalled',
  'tunerCompiledProfileDeliveryTokenCancellations',
  'tunerCompiledProfileDeliveryMonitorInterrupted',
  'persistDeferredCompiledProfileAsync'
]) assert.ok(surface.includes(marker), 'missing safe WebView profiling marker: ' + marker);

for (const marker of ['tunerProjectBootstrapCall', 'tunerProjectJsonSerialize']) {
  assert.ok(!surface.includes(marker), 'unsafe split bootstrap profiling returned: ' + marker);
}

// Permanent-store authority and single-serialization invariants remain unchanged.
assert.ok(
  surface.includes('TunerPermanentProjectStore.bootstrapWorkspacePayload(appContext)'),
  'Tuner surface must consume the store-owned immutable serialized payload directly'
);
assert.ok(surface.includes('val payload = bootstrap.json'), 'surface must retain only the immutable serialized workspace String');
assert.ok(!surface.includes('.bootstrapWorkspaceJson(view.context.applicationContext)'), 'surface must not reconstruct a giant JSONObject');
assert.ok(!surface.includes('val workspace = TunerPermanentProjectStore.bootstrapWorkspaceJson'), 'surface must not retain a giant workspace JSONObject');
assert.ok(
  store.includes('data class BootstrapWorkspacePayload(') &&
    store.includes('val json: String') &&
    store.includes('val compiledProfileCandidateId: Long?'),
  'bootstrap result must pair the immutable String with only a nullable candidate token'
);
assert.ok(store.includes('private val compiledProfileCandidateSequence = AtomicLong(0L)'), 'deferred profile candidates need monotonic identity');
assert.ok(store.includes('candidateId = compiledProfileCandidateSequence.incrementAndGet()'), 'new parsed profile must receive a fresh candidate ID');
assert.ok(store.includes('private var cachedBootstrapCandidateId'), 'cached immutable payload must retain its lightweight candidate identity');
assert.ok(store.includes('private fun cachedBootstrapPayloadLocked(currentCandidateId: Long?): BootstrapWorkspacePayload'), 'process hits must return exact String/current-candidate pair');
assert.ok(store.includes('cachedBootstrapCandidateId != currentCandidateId'), 'stale cached candidate identity must self-clear without worker lock nesting');
assert.ok(store.includes('return@synchronized cachedBootstrapPayloadLocked(candidateId)'), 'process bootstrap hit must return cached pair using the currently captured candidate');
assert.ok(!store.includes('val cachedJson = JSONObject(cached)'), 'cached project must not decode just to reserialize');
assert.ok(store.includes('private fun deferredCandidateIdForProfileLocked(profile: UsbTunerStudioProfile): Long?'), 'workspace must bind exact deferred candidate');
assert.ok(!store.includes('persistCompiledProfileAsync(context, sourceSha256, importedName, it)'), 'compiled profile persistence must remain off blocking parse path');
assert.ok(store.includes('private var deferredCompiledProfileEligible: DeferredCompiledProfile? = null'), 'retry eligibility must remain exact-candidate scoped');
assert.ok(store.includes('private val profileStateLock = Any()'), 'profile/deferred state needs a dedicated short-held lock');
assert.ok(store.includes('private val bootstrapCacheLock = Any()'), 'workspace cache/single-flight needs a distinct lock');
assert.ok(store.includes('private val heavyWorkLock = Any()'), 'large native serialization phases must remain mutually excluded');
assert.ok(!store.includes('private val cacheLock = Any()'), '1195 shared profile/workspace cache lock must remain retired');

const importStart = store.indexOf('fun persistAuthoritativeIni(');
const importEnd = store.indexOf('/**\n     * Returns the current authoritative INI profile', importStart);
const importCommit = store.slice(importStart, importEnd);
assert.ok(importCommit.includes('synchronized(profileStateLock)'), 'INI commit must clear profile state under profile lock');
assert.ok(importCommit.includes('synchronized(bootstrapCacheLock)'), 'INI commit must independently clear bootstrap cache state');
assert.ok(importCommit.indexOf('synchronized(profileStateLock)') < importCommit.indexOf('synchronized(bootstrapCacheLock)'), 'INI invalidation lock sections must be separate and ordered');
const importProfileBlockEnd = importCommit.indexOf('synchronized(bootstrapCacheLock)');
assert.ok(!importCommit.slice(importCommit.indexOf('synchronized(profileStateLock)'), importProfileBlockEnd).includes('bootstrapCacheLock'), 'INI invalidation must not nest bootstrap lock inside profile lock');

const deferredGateStart = store.indexOf('fun persistDeferredCompiledProfileAsync(context: Context, candidateId: Long?)');
const deferredGateEnd = store.indexOf('private fun profileForSource(', deferredGateStart);
assert.ok(deferredGateStart >= 0 && deferredGateEnd > deferredGateStart, 'deferred persistence gate boundaries missing');
const deferredGate = store.slice(deferredGateStart, deferredGateEnd);
assert.ok(deferredGate.includes('if (candidateId == null)') && deferredGate.includes('tunerCompiledProfileInjectionGateRejected'), 'missing candidate ID must fail closed');
assert.ok(deferredGate.includes('next == null || next.candidateId != candidateId'), 'stale candidate must fail closed');
assert.ok(deferredGate.indexOf('next.candidateId != candidateId') < deferredGate.indexOf('deferredCompiledProfileEligible = next'), 'identity must be checked before eligibility');
assert.ok(deferredGate.includes('next != null && deferredCompiledProfileEligible === next'), 'retry must require exact eligible candidate');
assert.ok(deferredGate.includes('launchDeferredCompiledProfilePersistence(context.applicationContext, retry)'), 'retry must stay behind gated helper');
assert.ok(deferredGate.includes('synchronized(profileStateLock)'), 'deferred candidate bookkeeping must use profile-state lock');
assert.ok(deferredGate.includes('synchronized(heavyWorkLock)'), 'compiled-profile heavy phase must share guard');
assert.ok(!deferredGate.includes('bootstrapCacheLock'), 'compiled-profile worker must never enter bootstrap lock');
assert.ok(deferredGate.indexOf('val persistenceResult = synchronized(heavyWorkLock)') < deferredGate.indexOf('val retry = synchronized(profileStateLock)'), 'heavy guard must be released before completion profile lock');
assert.ok(!deferredGate.includes('if (retry) persistDeferredCompiledProfileAsync'), 'retry must not bypass injection eligibility');
assert.ok(store.includes('deferredCompiledProfileEligible = null\n                    PerformanceMetrics.increment("tunerCompiledProfileDeferred")'), 'new candidate must start ineligible');

const profileResolveStart = store.indexOf('private fun profileForSource(');
const profileResolveEnd = store.indexOf('private fun readSnapshotForProfile(', profileResolveStart);
const profileResolve = store.slice(profileResolveStart, profileResolveEnd);
assert.ok(profileResolve.includes('synchronized(profileStateLock)'), 'profile resolution must use dedicated profile-state single-flight');
assert.ok(profileResolve.includes('tunerProfileStateLockWait'), 'profile lock acquisition wait must be measured');
assert.ok(!profileResolve.includes('bootstrapCacheLock'), 'fast profile resolution must not wait on workspace single-flight');
assert.ok(!profileResolve.includes('heavyWorkLock'), 'fast profile resolution must stay outside heavy guard');

assert.ok(!store.includes('private fun primeBootstrapCacheAsync('), 'live snapshot commit must not eagerly project the giant saved workspace');

const snapshotPersistStart = store.indexOf('fun persistVerifiedSnapshot(');
const snapshotPersistEnd = store.indexOf('fun loadSnapshotForProfile(', snapshotPersistStart);
const snapshotPersist = store.slice(snapshotPersistStart, snapshotPersistEnd);
assert.ok(snapshotPersist.includes('synchronized(bootstrapCacheLock)'), 'snapshot commit must invalidate only the bootstrap cache lock domain');
assert.ok(snapshotPersist.includes('PerformanceMetrics.increment("tunerOfflineProjectionDeferred")'), 'snapshot commit must record deferred offline projection');
assert.ok(!snapshotPersist.includes('profileStateLock'), 'snapshot commit must not couple profile state to bootstrap invalidation');
assert.ok(!snapshotPersist.includes('heavyWorkLock'), 'snapshot commit must not perform giant workspace projection/serialization');
assert.ok(!snapshotPersist.includes('bootstrapWorkspacePayload('), 'snapshot commit must leave saved-project projection for explicit offline demand');

const coldBuild = store.slice(store.indexOf('fun bootstrapWorkspacePayload'), store.indexOf('/** Compatibility wrapper'));
assert.ok(coldBuild.includes('synchronized(profileStateLock)'), 'cold workspace must capture candidate through short profile lock');
assert.ok(coldBuild.includes('synchronized(bootstrapCacheLock)'), 'cold workspace miss must stay process single-flight');
assert.ok(coldBuild.includes('synchronized(heavyWorkLock)'), 'cold workspace build must share heavy guard');
assert.ok(coldBuild.includes('tunerProfileStateLockWait'), 'cold workspace candidate capture must measure profile lock wait');
assert.ok(coldBuild.includes('tunerBootstrapSingleFlightWait'), 'cold workspace must measure actual bootstrap lock wait');
assert.ok(coldBuild.includes('tunerBootstrapSingleFlightJoins'), 'cold workspace must expose joined generations');
assert.ok(coldBuild.includes('val candidateId = synchronized(profileStateLock)'), 'cold workspace must capture exact candidate before bootstrap single-flight');
assert.ok(coldBuild.includes('cacheBootstrapLocked(key, json, candidateId)'), 'cold build must commit one immutable payload/candidate pair');
assert.ok(coldBuild.indexOf('val candidateId = synchronized(profileStateLock)') < coldBuild.indexOf('synchronized(bootstrapCacheLock)'), 'candidate capture must complete before bootstrap lock');
const coldBootstrapSection = coldBuild.slice(coldBuild.indexOf('synchronized(bootstrapCacheLock)'));
assert.ok(!coldBootstrapSection.includes('profileStateLock'), 'workspace bootstrap/heavy section must never acquire profile lock');
assert.ok(store.includes('JSONObject(bootstrapWorkspacePayload(context).json)'), 'compatibility callers may decode only the serialized String member');

// T4 owns the WebView-side large-payload lifetime. Candidate identity alone is insufficient because
// different WebView/install generations may consume the same cached candidate.
assert.ok(surface.includes('private data class CandidateDelivery('), 'surface must identify each large-payload delivery');
assert.ok(surface.includes('val installToken: Long'), 'each delivery must be owned by an exact install token');
assert.ok(surface.includes('private val installSequence = AtomicLong(0L)'), 'install tokens must be globally monotonic in-process');
assert.ok(surface.includes('private val candidateDeliverySequence = AtomicLong(0L)'), 'delivery IDs must be monotonic');
assert.ok(surface.includes('private val candidateDeliveryLock = ReentrantLock()'), 'delivery coordination must use explicit lock');
assert.ok(surface.includes('private val candidateDeliveryChanged = candidateDeliveryLock.newCondition()'), 'blocked generations need condition');
assert.ok(surface.includes('private val candidateDeliveries = LinkedHashMap<Long, CandidateDelivery>()'), 'tracked leases must retain candidate, delivery and owner token');
assert.ok(surface.includes('private var candidatePersistencePending: Long? = null'), 'successful injection must close gate');
assert.ok(surface.includes('private var candidatePersistenceInFlight: Long? = null'), 'gate must stay closed through native persistence');

const installStart = surface.indexOf('fun install(view: WebView?)');
const installEnd = surface.indexOf('fun detach(view: WebView?)', installStart);
const install = surface.slice(installStart, installEnd);
assert.ok(install.includes('installTokens.put(view, token)'), 'install must atomically replace exact WebView token');
assert.ok(install.includes('cancelCandidateDeliveries(view.context.applicationContext, previousToken)'), 'replacing an install token must cancel old-token leases');
assert.ok(install.includes('ensureLifecycleHooks(view)'), 'every installed WebView must receive teardown/renderer hooks');

const detachStart = surface.indexOf('fun detach(view: WebView?)');
const detachEnd = surface.indexOf('private fun ensureLifecycleHooks(', detachStart);
const detach = surface.slice(detachStart, detachEnd);
assert.ok(detach.includes('installTokens.remove(view)'), 'detach must invalidate the exact active install token');
assert.ok(detach.includes('cancelCandidateDeliveries(view.context.applicationContext, token)'), 'detach must cancel all leases owned by the removed token');

const hooksStart = surface.indexOf('private fun ensureLifecycleHooks(');
const hooksEnd = surface.indexOf('private fun installCombinedScript(', hooksStart);
const hooks = surface.slice(hooksStart, hooksEnd);
assert.ok(hooks.includes('View.OnAttachStateChangeListener'), 'WebView window teardown must have an explicit detach hook');
assert.ok(hooks.includes('onViewDetachedFromWindow'), 'detached WebView must cancel outstanding leases');
assert.ok(hooks.includes('T4TuningWorkspaceSurface.detach(v as? WebView)'), 'detach callback must route through exact token cancellation');
assert.ok(surface.includes('override fun onRenderProcessGone'), 'renderer loss must have an explicit cancellation hook');
assert.ok(surface.includes('T4TuningWorkspaceSurface.detach(view)'), 'renderer loss must cancel exact WebView leases');
assert.ok(surface.includes('return delegate.onRenderProcessGone(view, detail)'), 'renderer failure behavior must remain delegated');
assert.ok(surface.includes('delegate.onPageFinished(view, url)'), 'renderer wrapper must preserve Activity page-finished behavior');

const registerStart = surface.indexOf('private fun tryRegisterCandidateDelivery(');
const registerEnd = surface.indexOf('private fun awaitCandidateDeliveryGate()', registerStart);
const register = surface.slice(registerStart, registerEnd);
assert.ok(register.includes('candidateDeliveryLock.withLock'), 'candidate registration must be atomic');
assert.ok(register.includes('candidatePersistencePending != null || candidatePersistenceInFlight != null'), 'new payload must be rejected while persistence is pending/active');
assert.ok(register.includes('CandidateDelivery(candidateId, deliveryId, installToken)'), 'registered lease must bind candidate, unique delivery and install token');
assert.ok(register.includes('candidateDeliveries[deliveryId] = delivery'), 'lease must be registered before WebView-side large-payload work');

const waitStart = surface.indexOf('private fun awaitCandidateDeliveryGate()');
const waitEnd = surface.indexOf('private fun reservePersistenceIfReadyLocked()', waitStart);
const waitGate = surface.slice(waitStart, waitEnd);
assert.ok(waitGate.includes('candidateDeliveryChanged.await()'), 'blocked generation must release lock while waiting');
assert.ok(waitGate.includes('candidateDeliveryLock.unlock()'), 'delivery wait must always release lock');

const reserveStart = surface.indexOf('private fun reservePersistenceIfReadyLocked()');
const reserveEnd = surface.indexOf('private fun launchCandidatePersistence(', reserveStart);
const reserve = surface.slice(reserveStart, reserveEnd);
assert.ok(reserve.includes('candidateDeliveries.isNotEmpty()'), 'persistence must wait for every existing lease');
assert.ok(reserve.includes('candidatePersistenceInFlight != null'), 'barrier release must prevent duplicate native submissions');
assert.ok(reserve.includes('candidatePersistencePending == null'), 'barrier release requires a successful candidate');
assert.ok(reserve.includes('candidatePersistenceInFlight = candidateId'), 'worker slot must be reserved before leaving lock');

const completeStart = surface.indexOf('private fun completeCandidateDelivery(');
const completeEnd = surface.indexOf('private fun cancelCandidateDeliveries(', completeStart);
const complete = surface.slice(completeStart, completeEnd);
assert.ok(complete.includes('candidateDeliveries.remove(delivery.deliveryId)'), 'completion must remove exact lease');
assert.ok(complete.includes('registeredDelivery != delivery'), 'mismatched/duplicate completion must fail closed');
assert.ok(complete.includes('if (injected)'), 'only completed JS evaluation may nominate persistence');
assert.ok(complete.includes('maxOf('), 'out-of-order callbacks must preserve newest monotonic candidate');
assert.ok(complete.includes('candidatePersistencePending ?: delivery.candidateId'), 'newest-candidate selection must retain earlier newer success');
assert.ok(complete.includes('reservePersistenceIfReadyLocked()'), 'normal completion must use shared barrier release logic');

const cancelStart = surface.indexOf('private fun cancelCandidateDeliveries(');
const cancelEnd = surface.indexOf('private fun monitorCompiledProfilePersistence(', cancelStart);
const cancel = surface.slice(cancelStart, cancelEnd);
assert.ok(cancel.includes('entry.value.installToken == installToken'), 'teardown must remove only leases owned by exact install token');
assert.ok(cancel.includes('iterator.remove()'), 'teardown must actually remove owned leases');
assert.ok(cancel.includes('reservePersistenceIfReadyLocked()'), 'teardown cancellation must share normal barrier release logic');
assert.ok(cancel.includes('launchCandidatePersistence(context, it)'), 'teardown may release an already-successful sibling candidate exactly once');

const monitorStart = surface.indexOf('private fun monitorCompiledProfilePersistence(');
const monitorEnd = surface.indexOf('private fun profilePersistenceWorkerAlive()', monitorStart);
const monitor = surface.slice(monitorStart, monitorEnd);
assert.ok(monitor.includes('var interrupted = false'), 'monitor must remember interruption without abandoning cleanup');
assert.ok(monitor.includes('while (profilePersistenceWorkerAlive())'), 'gate must remain closed until native worker family is gone');
assert.ok(monitor.includes('catch (_: InterruptedException)'), 'monitor interruption must be handled');
assert.ok(!monitor.includes('return@Thread'), 'monitor interruption must never bypass gate cleanup');
assert.ok(monitor.includes('tunerCompiledProfileDeliveryMonitorInterrupted'), 'monitor interruption must be diagnosable');
assert.ok(monitor.includes('candidatePersistenceInFlight = null'), 'worker-family completion must reopen gate');
assert.ok(monitor.includes('candidateDeliveryChanged.signalAll()'), 'waiters must be released after native worker family exits');
assert.ok(monitor.indexOf('candidateDeliveryChanged.signalAll()') < monitor.indexOf('Thread.currentThread().interrupt()'), 'interrupt status may be restored only after gate cleanup/signaling');
assert.ok(surface.includes('PROFILE_PERSIST_THREAD_NAME = "EpicDash-TunerProfilePersist"'), 'surface must track actual derived-profile worker family');

const loadStart = surface.indexOf('private fun loadPermanentProjectAsync(');
const loadEnd = surface.indexOf('private fun installStillCurrent(', loadStart);
const load = surface.slice(loadStart, loadEnd);
const bootstrapPos = load.indexOf('bootstrap = TunerPermanentProjectStore.bootstrapWorkspacePayload(appContext)');
const registerPos = load.indexOf('delivery = tryRegisterCandidateDelivery(candidateId, token)');
const scriptPos = load.indexOf('val script = "window.__EPIC_TUNER_PERMANENT_WORKSPACE__=$payload;"');
assert.ok(bootstrapPos >= 0 && registerPos > bootstrapPos && scriptPos > registerPos, 'exact token-owned lease must register before giant WebView script construction');
assert.ok(load.includes('if (!awaitCandidateDeliveryGate()) return@Thread'), 'blocked generation must wait rather than inject concurrently');
assert.ok(load.includes('tunerProjectBootstrapRetriedAfterProfilePersist'), 'blocked generation must re-read bootstrap after persistence');
assert.ok(load.includes('completeCandidateDelivery(appContext, delivery, false)'), 'all discard/failure paths must cancel exact lease');
assert.ok(load.includes('completeCandidateDelivery(appContext, delivery, true)'), 'JS callback must complete exact successful lease');
assert.ok(load.indexOf('completeCandidateDelivery(appContext, delivery, true)') > load.indexOf('tunerProjectInjectCallbacks'), 'success may be recorded only inside JS evaluation callback');
assert.ok(load.includes('if (!posted)') && load.includes('tunerProjectBootstrapPostRejected'), 'failed View.post must terminate lease');
assert.ok(load.includes('runCatching {\n                    view.evaluateJavascript(script)'), 'synchronous JS injection failure must be contained and cancel lease');

for (const forbidden of ['JavascriptInterface', 'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'localStorage']) {
  assert.ok(!store.includes(forbidden), 'profiling changed permanent-project authority: ' + forbidden);
}
for (const forbidden of ['queueTuningWrite', 'queueTuningBurn', 'bulkTransfer(', 'controlTransfer(']) {
  assert.ok(!surface.includes(forbidden), 'lifecycle hardening changed Tuner surface authority: ' + forbidden);
}

console.log('Tuner lifecycle/profile restore lock-decoupled token-owned concurrent-delivery characterization passed');
