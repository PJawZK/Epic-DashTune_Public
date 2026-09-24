#!/usr/bin/env python3
from pathlib import Path
from html.parser import HTMLParser
import re, subprocess, sys, tempfile
ROOT=Path(__file__).resolve().parents[1]
HTML=ROOT/'app/src/main/assets/dashboard_lab.html'
MANIFEST=ROOT/'app/src/main/AndroidManifest.xml'
GRADLE=ROOT/'app/build.gradle.kts'
JAVA=ROOT/'app/src/main/java/com/buttonbox/ble'
errors=[]
class IdParser(HTMLParser):
    def __init__(self): super().__init__(); self.ids=[]
    def handle_starttag(self,tag,attrs):
        values=dict(attrs)
        if 'id' in values: self.ids.append(values['id'])
parser=IdParser(); parser.feed(HTML.read_text())
duplicates=sorted({item for item in parser.ids if parser.ids.count(item)>1})
if duplicates: errors.append('Duplicate HTML IDs: '+', '.join(duplicates))
text=HTML.read_text(); match=re.search(r'<script>(.*?)</script>',text,re.S)
if not match: errors.append('dashboard_lab.html has no script block')
else:
    with tempfile.NamedTemporaryFile('w',suffix='.js',delete=False) as temp: temp.write(match.group(1)); name=temp.name
    try:
        result=subprocess.run(['node','--check',name],capture_output=True,text=True)
        if result.returncode: errors.append('JavaScript syntax: '+result.stderr.strip())
    except FileNotFoundError: print('WARN: node unavailable; JavaScript syntax check skipped')
manifest=MANIFEST.read_text()
for activity in ['DashboardLabActivity','MainActivity','SettingsActivity','AddGaugeActivity']:
    if f'android:name="com.buttonbox.ble.{activity}"' not in manifest: errors.append(f'Manifest activity is not absolute: {activity}')
if 'SplashActivity' in manifest or (JAVA/'SplashActivity.kt').exists():
    errors.append('Step 0H legacy SplashActivity returned')
if (ROOT/'app/src/main/res/layout/activity_splash.xml').exists():
    errors.append('Step 0H legacy splash layout returned')
if (ROOT/'app/src/main/assets/whatsnew.txt').exists():
    errors.append('Step 0H packaged legacy whatsnew asset returned')
if 'android.intent.category.HOME' in manifest: errors.append('Normal build still declares HOME category')
launcher=re.search(r'<activity[^>]+android:name="com\.buttonbox\.ble\.DashboardLabActivity".*?</activity>',manifest,re.S)
if not launcher or 'android.intent.action.MAIN' not in launcher.group(0) or 'android.intent.category.LAUNCHER' not in launcher.group(0): errors.append('DashboardLabActivity is not the direct launcher')
gradle=GRADLE.read_text()
version_code_match=re.search(r'\bversionCode\s*=\s*(\d+)',gradle)
version_name_match=re.search(r'\bversionName\s*=\s*"([^"]+)"',gradle)
if not version_code_match or not version_name_match:
    errors.append('Gradle application version metadata is missing or malformed')
else:
    version_code=int(version_code_match.group(1)); version_name=version_name_match.group(1)
    if version_code <= 0 or not version_name.strip(): errors.append('Gradle application version metadata is invalid')
if 'syncWhatsNew' in gradle or 'src/main/assets/whatsnew.txt' in gradle:
    errors.append('Step 0H legacy Whats New packaging returned')
activity_source=(JAVA/'DashboardLabActivity.kt').read_text()
combined='\n'.join([text,activity_source,manifest])
for marker in ['id="shellTabs"','data-shell="dash"','data-shell="tuner"','data-shell="logging"','data-shell="diagnostics"','id="tabs" class="shellContextNav"','id="diagnosticsTabs"','function activateShellDestination','function shellDestinationForPage','openLogPlaybackBtn']:
    if marker not in text: errors.append('Shell 1 navigation marker missing: '+marker)
if 'data-shell="settings"' in text:
    errors.append('Redundant Settings top-level shell destination returned')
dash_nav=re.search(r'<nav id="tabs"[^>]*>(.*?)</nav>',text,re.S)
if not dash_nav:
    errors.append('Shell 1 Dash context navigation missing')
else:
    for page_id in ['page-analysis','page-diagnostics','page-rules','page-tuning']:
        if f'data-page="{page_id}"' in dash_nav.group(1):
            errors.append('Shell 1 utility/tuner page leaked into Dash tabs: '+page_id)

for marker in ['openStockEpicDash','MainActivity::class.java','openMainSettings','SettingsActivity::class.java']:
    if marker not in activity_source: errors.append('Step 0H fallback shell marker missing: '+marker)
for marker in ['iniStatePill','iniStateLabel','applyIniCompatibility','refreshIniCompatibility','getIniCompatibilityJson']:
    if marker not in text+activity_source: errors.append('Missing INI compatibility UI/bridge marker: '+marker)
for required_source in ['MainActivity.kt','SettingsActivity.kt','AddGaugeActivity.kt']:
    if not (JAVA/required_source).exists(): errors.append('Step 0H required fallback source missing: '+required_source)
markers=[
'layoutManagerModal','widgetEditorModal','editorToolbar','LAYOUT_SCHEMA_VERSION=5','repairPageGeometry','geometryFits','gridX','landscapeGridX',
'makeEditableLayoutCopy','holdHalo','haloGeometryFromPoints','Landscape halo viewport containment','settingHoldHaloSize','controlSurface','controlMode','openStockEpicDash','Theme.EpicDashJZ.Starting','@mipmap/ic_launcher',
'DashboardLabActivity : AppCompatActivity(), MslLogPlayer.Listener, BleManager.BleCallback','DashboardDataHub.registerBleControl',
'AFR Gas Scale','EpicDashHandleBack','ResizeObserver','editorDragHandle','editorResizeHandle','editorUndoBtn','editorRedoBtn',
'settingLandscapeDensity','RADIAL_SWEEP_DEG=240','Collision-free occupancy repair','Copied factory gauges unlocked','Guarded control preview model',
'customGraphWidget','widgetGraphChannel4','graphScaleMode','widgetVisualState','widgetAccentSelect','widgetAttentionHighInput','openingPageId','duplicatePageBtn','exportAllLayouts','Configurable graph widget','Per-widget visual ranges','installSelectionGuards','warningInterruptsEditor','closeLockStateModals','Edit-mode warning suppression','layout-removed','syncCoreWidgetCard','Copied built-in widget deletion','compileExpression','mathChannelModal','warningRuleModal','controlSettingsSection','Control confirmation state model','Expanded conditional rule model',
'USB ECU — TunerStudio transport','importUsbIni','usbReconnect','setLiveTransport','setUsbPollHz','USB transport controls','USB dynamic channel registry','android.hardware.usb.host'
]
for marker in markers:
    if marker not in combined: errors.append('Missing v0.10.1 marker: '+marker)
for name in ['DiagnosticStore.kt','DashboardDataHub.kt','DashboardLabActivity.kt','MslLogPlayer.kt','LocationDataHub.kt','BleManager.kt','UsbEcuManager.kt','UsbTunerStudioProfile.kt','TuneSnapshot.kt','TuneSnapshotReadPlan.kt','TuningCore.kt','TuningScalarCodec.kt','TuningWorkspace.kt','TuningSemanticPreview.kt','IniConditionEvaluator.kt','T4TuningWorkspaceSurface.kt','T3PilotRamProtocol.kt','T3RamScalarTransaction.kt','T3RamRecoveryFileStore.kt','T3RamProofRunner.kt','T3NativeSafetyInputsResolver.kt','T3BoundTransportPort.kt','T3SynchronousTuneSnapshotReader.kt','AuthoritativeTuneSnapshotReader.kt','EcuCommandResponseCodec.kt','TuneBurnPrimitives.kt','T6PersistentBurn.kt','T6PersistentBurnRecoveryFileStore.kt','TuningWriteEngine.kt']:
    if not (JAVA/name).exists(): errors.append('Missing source: '+name)
for resource in ['values/themes.xml','values-v31/themes.xml','drawable/epicdash_jz_starting_window.xml','drawable/epicdash_jz_splash_icon.xml','mipmap-anydpi-v26/ic_launcher.xml']:
    if not (ROOT/'app/src/main/res'/resource).exists(): errors.append('Missing startup resource: '+resource)
usb_source=(JAVA/'UsbEcuManager.kt').read_text()
activity_source=(JAVA/'DashboardLabActivity.kt').read_text()
ble_source=(JAVA/'BleManager.kt').read_text()
for marker in ['MAX_CONSECUTIVE_FAILURES','handshakeStage','findBulkPipe','tap Reconnect','selectedEndpointIn',
               'parseCdcUnions','CDC_UNION_SUBTYPE','PORT_OPEN_SETTLE_MS',
               'strictWriteFrame','runFramedProtocolPreflight','framedProtocolAttempts',
               'Optimized USB RX envelope confirmed','USB_RX_BUFFER_BYTES','ENVELOPE_READ_TIMEOUT_MS',
               'CONTROL_DTR_RTS','SET_LINE_CODING','SET_CONTROL_LINE_STATE',
               'probeAttempts','successfulProbeMode']:
    if marker not in usb_source: errors.append('Missing proven USB stream marker: '+marker)
for marker in ['lastActiveMeasuredHz','lastDisconnectCause','channelAuditJson','rawNumeric','disconnectQueued','currentMeasuredHz','streamReadMode','FULL_BLOCK_REQUEST_LIMIT','scheduleNextPoll']:
    if marker not in usb_source: errors.append('Missing v0.11.6 stream/mapping marker: '+marker)
hub_source=(JAVA/'DashboardDataHub.kt').read_text()
for marker in ['targetAFR','VBattAvg','rawBattery','usbPollTargetHz']:
    if marker not in (hub_source+usb_source): errors.append('Missing canonical/rate marker: '+marker)
for marker in ['readEnvelopeOptimized','optimizedReceiveBuffer','bridgeFramesCoalesced','dataToPaint','decodePlanChannels','usbSessionId','lastAcceptedNativeRevision','TPS direct','runtimeMode']:
    if marker not in combined+usb_source+hub_source+activity_source+(JAVA/'PerformanceMetrics.kt').read_text(): errors.append('Missing fixed optimized runtime marker: '+marker)
for forbidden in ['PerformanceProfile','setPerformanceProfile','currentPerformanceProfile','performanceProfile','perfTestStrip','data-perf-profile','requestedPerformanceProfile','activeProfile','INSTRUMENTED_LEGACY','NATIVE_OPTIMIZED','NATIVE_BRIDGE']:
    if forbidden in text+usb_source+hub_source+activity_source+(JAVA/'PerformanceMetrics.kt').read_text():
        errors.append('Step 0G retained selectable performance-profile surface: '+forbidden)
if (JAVA/'PerformanceProfile.kt').exists():
    errors.append('Step 0G obsolete PerformanceProfile source still exists')
for marker in ['UsbGenerationAuthority','generationAuthority','onUsbStateChanged(state: State, message: String, generation: Long)','onUsbValues(values: Map<String, Float>, elapsedMs: Long, generation: Long)']:
    if marker not in usb_source+(JAVA/'UsbGenerationAuthority.kt').read_text(): errors.append('Missing reconnect generation marker: '+marker)
for marker in ['nativeOrderingDecision','staleMonitoringArmed','disarmLiveStaleMonitoring','Reconnect generation regression','Stale self-test isolation','selfTestRunning&&rule.id===\'stale\'']:
    if marker not in text: errors.append('Missing reconnect/stale dashboard regression marker: '+marker)
for marker in ['staleCondition','runIsolatedStaleDiagnostic','liveStaleIsolationSnapshot']:
    if marker not in text: errors.append('Missing shared stale diagnostic marker: '+marker)
for marker in ['staleRearmBarrier','beginPostSelfTestStaleRearm','postSelfTestFreshnessDecision','acceptPostSelfTestFreshness']:
    if marker not in text: errors.append('Missing post-self-test stale rearm marker: '+marker)
for marker in ['UsbPermissionRequestTracker','UsbPermissionRequestCoordinator','EXTRA_PERMISSION_GENERATION','pendingPermissionRequest.consume','publishIfCurrent','advanceGeneration']:
    if marker not in usb_source+(JAVA/'UsbPermissionRequestTracker.kt').read_text(): errors.append('Missing USB permission ownership marker: '+marker)
for marker in ['runTuneSnapshotRead','TuneSnapshotReadPlanner','tuneSnapshotBackup','latestTuneSnapshot']:
    if marker not in usb_source: errors.append('Missing T1 TuneSnapshot marker: '+marker)
scalar_codec_source=(JAVA/'TuningScalarCodec.kt').read_text()
semantic_preview_source=(JAVA/'TuningSemanticPreview.kt').read_text()
write_engine_source=(JAVA/'TuningWriteEngine.kt').read_text()
ini_condition_source=(JAVA/'IniConditionEvaluator.kt').read_text() if (JAVA/'IniConditionEvaluator.kt').exists() else ''
if 'TuningScalarCodec' not in scalar_codec_source:
    errors.append('Missing extracted production TuningScalarCodec')
for marker in ['TuningSemanticPreviewBuilder','SEMANTIC_PREVIEW','TuningWritePlanner.resolveForPreview','writeEligible','noOp']:
    if marker not in semantic_preview_source: errors.append('Missing Step 0F semantic-preview marker: '+marker)
for marker in ['IniConditionTruth','IniUiConditionState','IniCompatibilityReport','IniConditionAuthority','Condition identifier']:
    if marker not in ini_condition_source: errors.append('Missing native INI condition evaluator marker: '+marker)
for marker in ['currentMenuConditions','currentGroupConditions','combineUiConditions']:
    if marker not in (JAVA/'UsbTunerStudioProfile.kt').read_text(): errors.append('INI parser lost inherited menu/group condition support: '+marker)
for marker in ['IniConditionAuthority.build(profile, snapshot)','conditionAuthority.requireWriteAllowed(request)']:
    if marker not in write_engine_source: errors.append('Semantic write planner lost INI condition authority: '+marker)
for marker in ['fun resolveForPreview(','private fun resolveRequest(','resolveRequest(profile, snapshot, request, conditionAuthority)']:
    if marker not in write_engine_source: errors.append('Preview/execution no longer share the production semantic resolver: '+marker)
for forbidden in ['UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'writeTune(', 'burnTune(', 'requestBurn', 'burnCommand', '.put("offset"', '.put("pageNumber"', '.put("pageIdentifier"', '.put("rawHex"']:
    if forbidden in semantic_preview_source:
        errors.append('Semantic preview gained forbidden transport/storage surface: '+forbidden)
for obsolete in ['TuningSimulationJournal.kt','TuningSimulationPreview.kt','TuningArraySimulation.kt']:
    if (JAVA/obsolete).exists():
        errors.append('Step 0F obsolete simulation source still exists: '+obsolete)
t3_protocol=(JAVA/'T3PilotRamProtocol.kt').read_text()
t3_transaction=(JAVA/'T3RamScalarTransaction.kt').read_text()
t3_store=(JAVA/'T3RamRecoveryFileStore.kt').read_text()
t3_runner=(JAVA/'T3RamProofRunner.kt').read_text()
t3_native_safety=(JAVA/'T3NativeSafetyInputsResolver.kt').read_text()
t3_bound_transport=(JAVA/'T3BoundTransportPort.kt').read_text()
t3_sync_snapshot=(JAVA/'T3SynchronousTuneSnapshotReader.kt').read_text()
for marker in ['engineSnifferRpmThreshold','requirePlan','requireAgainstProfile','tuningScalarDefinitionFingerprint','candidateWriteBody','restoreWriteBody','readBackBody','T3TuneSnapshotVerifier','u16Le(target.pageIdentifier)','u16Le(target.offset)','u16Le(target.byteSize)']:
    if marker not in t3_protocol: errors.append('Missing T3 profile-derived pilot protocol marker: '+marker)
for marker in ['T3RamScalarTransaction','RECOVERY_REQUIRED','INVALID_VOLTAGE','VOLTAGE_TOO_LOW','armCandidateWrite','beginCandidateWrite','verifyCandidateReadBack','verifyCandidateSnapshot','armRestore','beginRestoreWrite','verifyBaselineRestored','replayAllowed']:
    if marker not in t3_transaction: errors.append('Missing T3 transaction safety marker: '+marker)
for marker in ['AtomicFile','stream.fd.sync()','T3RamRecoveryMarker.fromJson','T3RecoveryStoreState.CORRUPT']:
    if marker not in t3_store: errors.append('Missing T3 durable recovery marker: '+marker)
for marker in ['T3RamTransportPort','T3RamProofRunner','executeApproved','readCompleteTuneSnapshot','readSafetyInputs','BASELINE_RESTORED','RECOVERY_REQUIRED']:
    if marker not in t3_runner: errors.append('Missing T3 proof-runner marker: '+marker)
for marker in ['T3NativeOutputSample','T3NativeSafetyInputsResolver','T3SafetyChannelPolicy','maximumSampleAgeMs','RPMValue','VBatt','flashWritePending','flashWriteErrors','isCranking','idleisCranking','tuneProfileFingerprint','profile.outputBlockSize','safetyDefinitionFingerprint','GENERATION_MISMATCH','AMBIGUOUS_CHANNEL','CHANNEL_DEFINITION_MISMATCH']:
    if marker not in t3_native_safety: errors.append('Missing T3 profile-derived native safety marker: '+marker)
for marker in ['T3BoundTransportPort','isNativeOwnerThread','currentGeneration','requireAllowedExchange','LABEL_CANDIDATE','LABEL_CANDIDATE_READBACK','LABEL_RESTORE','LABEL_RESTORE_READBACK','nativeOutputSampleReader','T3NativeSafetyInputsResolver','candidateWriteBody','restoreWriteBody','readBackBody']:
    if marker not in t3_bound_transport: errors.append('Missing T3 bound-transport marker: '+marker)
for marker in ['T3SynchronousTuneSnapshotReader','TuneSnapshotReadPlanner.build','UsbTuneReadCodec.buildRangePayload','UsbTuneReadCodec.extractData','chunk.count + 1','TunePageSnapshot','TuneSnapshot.create']:
    if marker not in t3_sync_snapshot: errors.append('Missing T3 synchronous snapshot marker: '+marker)
for forbidden in ['UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'Executors.', 'ScheduledExecutor', 'evaluateJavascript(', 'requestBurn', 'burnCommand']:
    if forbidden in t3_native_safety or forbidden in t3_bound_transport or forbidden in t3_sync_snapshot:
        errors.append('T3 native adapter/helper layer gained forbidden direct-USB/UI/second-executor/burn surface: '+forbidden)
for forbidden in ['candidateWriteBody(', 'restoreWriteBody(']:
    if forbidden in t3_native_safety or forbidden in t3_sync_snapshot:
        errors.append('T3 safety/snapshot helper gained forbidden write-construction surface: '+forbidden)
for forbidden in ["'B'.code", '"B%2i"', 'burnCommand', 'requestBurn', 'bulkTransfer(', 'controlTransfer(', 'UsbEcuManager(', 'fun retry', 'retry(']:
    if forbidden in t3_protocol or forbidden in t3_transaction or forbidden in t3_store or forbidden in t3_runner or forbidden in t3_bound_transport or forbidden in t3_sync_snapshot:
        errors.append('T3 pilot domain layer gained forbidden burn/direct-USB/retry surface: '+forbidden)
for forbidden in ['2273317132','ef05b36cc848a3d20a8f8e1f2a38e745d9ae888b4820aa6356183a4f5d5f8e3b','0687ada62dec3291f3d18bdd8a4ecfc2e9a7c4b79881c212352bcfc8fbeca895','const val ECU_SIGNATURE','const val PROFILE_FINGERPRINT','const val DEFINITION_FINGERPRINT','const val PAGE_NUMBER','const val PAGE_IDENTIFIER','const val PAGE_SIZE','const val OFFSET','const val WIDTH']:
    if forbidden in t3_protocol or forbidden in t3_native_safety:
        errors.append('T3 runtime duplicated historical INI storage authority: '+forbidden)
if 'safetyDefinitionFingerprint' not in t3_transaction or 'SAFETY_SCHEMA_CHANGED' not in t3_transaction:
    errors.append('T3 transaction no longer freezes the profile-derived safety schema for one attempt')

if 'minimumVoltage: Double? = null' not in t3_transaction or 'if (!inputs.voltage.isFinite() || inputs.voltage <= 0.0) return T3RamBlockReason.INVALID_VOLTAGE' not in t3_transaction or 'if (minimumVoltage != null && inputs.voltage < minimumVoltage) return T3RamBlockReason.VOLTAGE_TOO_LOW' not in t3_transaction:
    errors.append('T3 voltage policy no longer requires valid native VBatt while keeping an optional evidence-backed numeric floor')
if 'VOLTAGE_POLICY_UNSET' in t3_transaction:
    errors.append('T3 transaction still treats absence of a numeric W3 voltage floor as an error')
if 'suspendForAlternateTransport' not in ble_source or 'reconcileTransportPolicy' not in activity_source:
    errors.append('USB/BLE transport arbitration hotfix is incomplete')
t4_workspace=(JAVA/'TuningWorkspace.kt').read_text() if (JAVA/'TuningWorkspace.kt').exists() else ''
t4_surface=(JAVA/'T4TuningWorkspaceSurface.kt').read_text() if (JAVA/'T4TuningWorkspaceSurface.kt').exists() else ''
t4_preview=(JAVA/'TuningSemanticPreview.kt').read_text() if (JAVA/'TuningSemanticPreview.kt').exists() else ''
t4_js_path=ROOT/'app/src/main/assets/t4_tuning_workspace.js'
t4_js=t4_js_path.read_text() if t4_js_path.exists() else ''
t4_telemetry_path=ROOT/'app/src/main/assets/t4_tuning_workspace_telemetry_controls.js'
t4_telemetry=t4_telemetry_path.read_text() if t4_telemetry_path.exists() else ''
t4_connection_path=ROOT/'app/src/main/assets/t4_tuning_workspace_connection_refresh.js'
t4_connection=t4_connection_path.read_text() if t4_connection_path.exists() else ''
for marker in ['EPIC_SHARED_SHELL_STATIC_V1','<header class="t4tw-topchrome t4tw-shared-topchrome">','id="sharedTopPageActions"','id="sharedTopEcuState"','id="iniStatePill"','id="headerLockBtn"','id="layoutEditBtn"','id="labSettingsBtn"']:
    if marker not in text: errors.append('Missing static shared-shell authority marker: '+marker)
for forbidden in ['<div class="logo">','<div class="statusbar">','id="clock"','id="rateText"','id="ageText"']:
    if forbidden in text: errors.append('Legacy first-paint dashboard shell returned: '+forbidden)
for forbidden in ['t4tw-compat','<div class="t4tw-title">EpicDashTune</div>','Profile-derived tuning workspace','<span class="t4tw-badge">LIVE ECU</span>','<span class="t4tw-badge sim">EDIT / WRITE</span>']:
    if forbidden in t4_js: errors.append('Legacy hidden Tuner UI returned: '+forbidden)
for forbidden in ['header.replaceChildren(appbar)',"srcDot.closest('.pill')",'appbar.append(brand,nav,status)','t4tw-shared-dash-tools']:
    if forbidden in t4_telemetry: errors.append('Legacy runtime shell transformer returned: '+forbidden)
if "else if(live&&page.classList.contains('active')&&!t4ConnectionWorkspaceIsLive())" in t4_connection:
    errors.append('Connected-state chrome polling regained full-workspace reload authority')
for marker in ['iniCompatibility','conditionState','writeAllowed','writeBlockReason']:
    if marker not in t4_workspace: errors.append('Tuning workspace lost INI compatibility projection: '+marker)
for marker in ['TuningWorkspaceBuilder','LIVE_TUNE_SNAPSHOT','totalProfileScalars','ambiguousScalarNames','profile.tuneProfileFingerprint()','snapshot.generation == currentGeneration']:
    if marker not in t4_workspace: errors.append('Missing T4 profile-derived tuning workspace marker: '+marker)
for marker in ['T4TuningWorkspaceSurface','t4_tuning_workspace.js','evaluateJavascript']:
    if marker not in t4_surface: errors.append('Missing T4 tuning surface installer marker: '+marker)
for marker in ['getTuningWorkspaceJson','T4TuningWorkspaceSurface.install(view)','previewTuningScalarJson(name: String, requestedValue: Double)','previewTuningArrayCellJson(name: String, cellIndex: Int, requestedValue: Double)','previewTuningBitFieldJson(name: String, requestedValue: Double)']:
    if marker not in activity_source: errors.append('Missing Step 0F semantic preview bridge marker: '+marker)
if 'internal fun tuningWorkspaceJson()' not in usb_source or 'TuningWorkspaceBuilder.build(selectedProfile, snapshot, generation)' not in usb_source:
    errors.append('USB manager does not own the current-profile/current-TuneSnapshot workspace projection')
for marker in ['internal fun previewTuningScalarJson','internal fun previewTuningArrayCellJson','internal fun previewTuningBitFieldJson','TuningSemanticPreviewBuilder.preview(']:
    if marker not in usb_source: errors.append('USB manager does not own semantic preview projection: '+marker)
for marker in ['TuningSemanticPreviewBuilder','SEMANTIC_PREVIEW','TuningWritePlanner.resolveForPreview','writeEligible','noOp']:
    if marker not in t4_preview: errors.append('Missing semantic preview safety marker: '+marker)
for marker in ['page-tuning','t4tw-internal-state','t4twTopEcu','t4twTopBurn','getTuningWorkspaceJson','previewTuningScalarJson','previewTuningArrayCellJson','previewTuningBitFieldJson','epicdash.t4.quickTuning.v1','epicdash.t4.scalarDraft.v1','Search hierarchy / feature','Changes','Apply edit','Pending changes','WRITE ALL PENDING EDITS TO ECU RAM','writeEligible','noOp']:
    if marker not in t4_js: errors.append('Missing tuner semantic-preview/pending-edit UI marker: '+marker)
for marker in ['conditionStateOf','conditionVisible','conditionUsable','writeAllowed','EpicDashApplyIniCompatibility','unsupportedConditionExpressions']:
    if marker not in t4_js: errors.append('Tuner lost evaluated INI condition UI marker: '+marker)
for marker in ['settingsHierarchy(q)','ensureHierarchySelection(systems)','hierarchySelect','t4tw-hierarchy-selectors',"selectors.dataset.uiContract = 'responsive-hierarchy-v1'",'HIERARCHY_GENERAL_CATEGORY','HIERARCHY_OTHER_SYSTEM','Search hierarchy / feature']:
    if marker not in t4_js: errors.append('Tuner lost responsive System → Category → Feature hierarchy marker: '+marker)
if 'function hierarchyColumn(' in t4_js:
    errors.append('Legacy three-column Tuner hierarchy renderer returned')
if 'conditions shown, not yet interpreted' in t4_js:
    errors.append('Obsolete non-evaluated INI condition wording returned')
if "tab.dataset.page = 'page-tuning'" in t4_js or 'tabs.insertBefore(tab' in t4_js:
    errors.append('Shell 1 tuner returned to Dash tab injection')
if "tab.addEventListener('click'" in t4_js:
    errors.append('Shell 1 stale injected-tab listener returned')
if "window.addEventListener('epicdash:page-activated'" not in t4_js:
    errors.append('Shell 1 tuner is not bound to authoritative shell page activation')
if "window.dispatchEvent(new CustomEvent('epicdash:tuner-ready'))" not in t4_js:
    errors.append('Shell 1 tuner readiness event missing')
for forbidden in ['simulateTuningScalarJson','simulateTuningArrayCellJson','LOCAL_SIMULATION_ONLY','LOCAL_ARRAY_SIMULATION_ONLY','NOT_TRANSMITTED',"result.status !== 'simulated'"]:
    if forbidden in t4_js or forbidden in activity_source or forbidden in usb_source:
        errors.append('Production tuner retained proof-era simulation marker: '+forbidden)
for forbidden in ['.put("offset"', '.put("pageNumber"', '.put("pageIdentifier"', '.put("rawHex"', '.put("proposedRaw"', '.put("originalRawHex"', '.put("encodedBytes"']:
    if forbidden in t4_workspace or forbidden in t4_preview:
        errors.append('WebView-facing tuning JSON leaks native storage/raw-byte metadata: '+forbidden)
for forbidden in ['openT3BenchFlow','openW4VehicleRamWrite','getW4VehicleRamStatusJson','openT6PersistentBurnFlow','getT6PersistentBurnStatusJson','t4twT6','updateT6Control','openT6Flow','t6Button','w4Status','t6Status','prepareT3BenchProposal','executePreparedT3BenchProof','candidateWriteBody','restoreWriteBody','bulkTransfer','controlTransfer','requestBurn','burnCommand']:
    if forbidden in t4_js:
        errors.append('Tuner UI retained/gained forbidden proof or raw transport surface: '+forbidden)
for forbidden in ['openT3BenchFlow','openW4VehicleRamWrite','getW4VehicleRamStatusJson','openT6PersistentBurnFlow','getT6PersistentBurnStatusJson','t3BenchLabFlow','w4VehicleRamFlow','t6PersistentBurnFlow']:
    if forbidden in activity_source:
        errors.append('Dashboard activity retained legacy proof bridge/flow: '+forbidden)
for forbidden in ['.put("t3Bench"', '.put("w4VehicleRam"', '.put("t6PersistentBurn"']:
    if forbidden in usb_source:
        errors.append('USB diagnostics retained legacy proof status: '+forbidden)
for marker in ['legacyProofRecovery','exclusiveOperationOwner','UsbOperationOwner.TUNER']:
    if marker not in usb_source:
        errors.append('Missing Step 0E proof-retirement/operation-owner marker: '+marker)
for forbidden in ['UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'candidateWriteBody(', 'restoreWriteBody(', 'requestBurn', 'burnCommand', "'B'.code", '"B%2i"']:
    if forbidden in t4_preview:
        errors.append('Semantic preview layer gained forbidden transport/write/burn surface: '+forbidden)
tuner_write=(JAVA/'TuningWriteEngine.kt').read_text() if (JAVA/'TuningWriteEngine.kt').exists() else ''
authoritative_snapshot=(JAVA/'AuthoritativeTuneSnapshotReader.kt').read_text() if (JAVA/'AuthoritativeTuneSnapshotReader.kt').exists() else ''
ecu_response_codec=(JAVA/'EcuCommandResponseCodec.kt').read_text() if (JAVA/'EcuCommandResponseCodec.kt').exists() else ''
tune_burn=(JAVA/'TuneBurnPrimitives.kt').read_text() if (JAVA/'TuneBurnPrimitives.kt').exists() else ''
for marker in ['SemanticTuningWriteEnvelope','TuningWritePlanner','TuningWriteProtocol','expectedSnapshot',"rangeBody('C'.code.toByte()","rangeBody('R'.code.toByte()"]:
    if marker not in tuner_write: errors.append('Missing normal tuner write-engine marker: '+marker)
for marker, source in [
    ('AuthoritativeTuneSnapshotReader', authoritative_snapshot),
    ('EcuCommandResponseCodec', ecu_response_codec),
    ('TuneBurnProtocol', tune_burn),
    ('TuneFlashStatusResolver', tune_burn),
    ('TuneBurnEvidencePolicy', tune_burn),
]:
    if marker not in source: errors.append('Missing neutral production tuner primitive: '+marker)
if 'T3PilotRamProtocol.decodeResponseBody' in tuner_write:
    errors.append('Normal tuning write protocol still depends on T3 pilot response decoding')
for marker in ['readTuningFromEcuJson','writeTuningChangesJson','burnTuningChangesJson','getTuningWriteStatusJson']:
    if marker not in activity_source: errors.append('Missing normal tuner semantic bridge marker: '+marker)
for marker in ['queueTuningRead','queueTuningWriteBatchJson','queueTuningBurn','tuningWriteStatusJson','ram_applied_verified','write_uncertain']:
    if marker not in usb_source: errors.append('Missing normal tuner manager marker: '+marker)
for marker in ['Read ECU','Write this edit to ECU RAM','Write this cell to ECU RAM','WRITE ALL PENDING EDITS TO ECU RAM','Save / Burn ECU','writeTuningChangesJson','write pending edits first','Select row','Select column','Rectangle / range to next cell','Tap target cell…','Select all','Apply to selection','tableRectIndices','tableRowIndices','tableColumnIndices','selectionOperationValue','stageArraySelectionEdit']:
    if marker not in t4_js: errors.append('Missing normal tuner/table-edit UI marker: '+marker)
for marker in ['button.dataset.mode = spec.mode',"{mode:'settings',label:'Parameters'}","{mode:'tables',label:'Tables'}","{mode:'curves',label:'Curves'}",'Search hierarchy / feature','renderSettingsMode','TUNER_UI_SIDEQUEST_RESPONSIVE_HIERARCHY_V1','TUNER_UI_SIDEQUEST_PARAMETERS_V1','TUNER_UI_SIDEQUEST_TABLES_V1','TUNER_UI_SIDEQUEST_CURVES_V1','TUNER_UI_VISUAL_PARITY_V1','t4tw-topchrome','t4tw-content-modes','t4tw-telemetry','responsive-tables-v1','responsive-curves-v1','renderTablesMode','renderCurvesMode','renderCurveGraph','curveActiveId','renderArrayGridInto','fitInlineTable','currentTableDraftChanges','t4tw-hierarchy-selectors','t4tw-system-rail','t4tw-inline-parameters','responsive-parameters-v1','hierarchySelect','renderSettingsDialogInto','ENUM / BIT • editable','stageBitFieldEdit','currentBitWrite',"kind:'bitField'",'ECU command not exposed']:
    if marker not in t4_js: errors.append('Missing INI-driven settings UI marker: '+marker)
for marker in ['t4twSettingsPending','t4twSettingsWriteRam','Write pending to ECU RAM','settingsWriteRam.onclick = writeAllDrafts']:
    if marker not in t4_js: errors.append('1158 Settings RAM affordance missing: '+marker)
for marker in ['window.EpicDashHaptic=haptic',"document.addEventListener('click',event=>{",'settingHaptic']:
    if marker not in text: errors.append('1158 expanded haptic UI missing: '+marker)
if (JAVA/'UsbProfileCacheCodec.kt').exists():
    errors.append('1158 must not contain parsed-profile cache experiment')

for marker in ['UsbTuneBitField','UsbTuneMenuItem','UsbTuneDialog','tuneBitsRegex','groupChildMenu','commandButton']:
    if marker not in (JAVA/'UsbTunerStudioProfile.kt').read_text(): errors.append('Missing INI settings parser/model marker: '+marker)
for marker in ['TuningWorkspaceBitField','totalProfileBitFields','menuItems','dialogs']:
    if marker not in t4_workspace: errors.append('Missing INI settings workspace marker: '+marker)

if t4_js_path.exists():
    try:
        result=subprocess.run(['node','--check',str(t4_js_path)],capture_output=True,text=True)
        if result.returncode: errors.append('T4 tuning JavaScript syntax: '+result.stderr.strip())
    except FileNotFoundError:
        print('WARN: node unavailable; T4 tuning JavaScript syntax check skipped')
if errors:
    print('VALIDATION FAILED')
    for error in errors: print(' - '+error)
    sys.exit(1)
print(f'VALIDATION PASSED: {len(parser.ids)} unique IDs; INI-driven tuner settings plus accepted semantic Read/Write/Burn, Step 0E proof retirement, authoritative TuneSnapshot and existing telemetry controls present')
