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
for activity in ['SplashActivity','DashboardLabActivity','MainActivity','SettingsActivity','AddGaugeActivity']:
    if f'android:name="com.buttonbox.ble.{activity}"' not in manifest: errors.append(f'Manifest activity is not absolute: {activity}')
if 'android.intent.category.HOME' in manifest: errors.append('Normal build still declares HOME category')
launcher=re.search(r'<activity[^>]+android:name="com\.buttonbox\.ble\.DashboardLabActivity".*?</activity>',manifest,re.S)
if not launcher or 'android.intent.action.MAIN' not in launcher.group(0) or 'android.intent.category.LAUNCHER' not in launcher.group(0): errors.append('DashboardLabActivity is not the direct launcher')
gradle=GRADLE.read_text()
if 'versionCode = 1114' not in gradle or 'versionName = "0.11.12-stale1-jz"' not in gradle: errors.append('Version metadata is not v0.11.12-stale1-jz / 1114')
combined='\n'.join([text,(JAVA/'DashboardLabActivity.kt').read_text(),manifest])
markers=[
'layoutManagerModal','widgetEditorModal','editorToolbar','LAYOUT_SCHEMA_VERSION=5','repairPageGeometry','geometryFits','gridX','landscapeGridX',
'makeEditableLayoutCopy','holdHalo','haloGeometryFromPoints','Landscape halo viewport containment','settingHoldHaloSize','controlSurface','controlMode','openStockEpicDash','Theme.EpicDashJZ.Starting','@mipmap/ic_launcher',
'DashboardLabActivity : AppCompatActivity(), MslLogPlayer.Listener, BleManager.BleCallback','DashboardDataHub.registerBleControl',
'AFR Gas Scale','EpicDashHandleBack','ResizeObserver','editorDragHandle','editorResizeHandle','editorUndoBtn','editorRedoBtn',
'settingLandscapeDensity','RADIAL_SWEEP_DEG=240','Collision-free occupancy repair','Copied factory gauges unlocked','Guarded control preview model',
'customGraphWidget','widgetGraphChannel4','graphScaleMode','widgetVisualState','widgetAccentSelect','widgetAttentionHighInput','openingPageId','duplicatePageBtn','exportAllLayouts','Configurable graph widget','Per-widget visual ranges','installSelectionGuards','warningInterruptsEditor','closeLockStateModals','Edit-mode warning suppression','layout-removed','syncCoreWidgetCard','Copied built-in widget deletion','compileExpression','mathChannelModal','warningRuleModal','controlSettingsSection','Control confirmation state model','Expanded conditional rule model',
'USB ECU — read-only beta','importUsbIni','usbReconnect','setLiveTransport','setUsbPollHz','USB transport controls','USB dynamic channel registry','android.hardware.usb.host'
]
for marker in markers:
    if marker not in combined: errors.append('Missing v0.10.1 marker: '+marker)
for name in ['DiagnosticStore.kt','DashboardDataHub.kt','DashboardLabActivity.kt','MslLogPlayer.kt','LocationDataHub.kt','BleManager.kt','UsbEcuManager.kt','UsbTunerStudioProfile.kt']:
    if not (JAVA/name).exists(): errors.append('Missing source: '+name)
for resource in ['values/themes.xml','values-v31/themes.xml','drawable/epicdash_jz_starting_window.xml','drawable/epicdash_jz_splash_icon.xml','mipmap-anydpi-v26/ic_launcher.xml']:
    if not (ROOT/'app/src/main/res'/resource).exists(): errors.append('Missing startup resource: '+resource)
usb_source=(JAVA/'UsbEcuManager.kt').read_text()
activity_source=(JAVA/'DashboardLabActivity.kt').read_text()
ble_source=(JAVA/'BleManager.kt').read_text()
for marker in ['MAX_CONSECUTIVE_FAILURES','handshakeStage','findBulkPipe','tap Reconnect','selectedEndpointIn',
               'parseCdcUnions','CDC_UNION_SUBTYPE','PORT_OPEN_SETTLE_MS',
               'strictWriteFrame','runFramedProtocolPreflight','framedProtocolAttempts',
               'Buffered USB RX envelope confirmed','USB_RX_BUFFER_BYTES','ENVELOPE_READ_TIMEOUT_MS',
               'CONTROL_DTR_RTS','SET_LINE_CODING','SET_CONTROL_LINE_STATE',
               'probeAttempts','successfulProbeMode']:
    if marker not in usb_source: errors.append('Missing proven USB stream marker: '+marker)
for marker in ['lastActiveMeasuredHz','lastDisconnectCause','channelAuditJson','rawNumeric','disconnectQueued','currentMeasuredHz','streamReadMode','FULL_BLOCK_REQUEST_LIMIT','scheduleNextPoll']:
    if marker not in usb_source: errors.append('Missing v0.11.6 stream/mapping marker: '+marker)
hub_source=(JAVA/'DashboardDataHub.kt').read_text()
for marker in ['targetAFR','VBattAvg','rawBattery','usbPollTargetHz']:
    if marker not in (hub_source+usb_source): errors.append('Missing canonical/rate marker: '+marker)
for marker in ['PerformanceProfile','setPerformanceProfile','selectiveDecode','readEnvelopeOptimized','bridgeFramesCoalesced','dataToPaint','perfTestStrip','data-perf-profile','decodePlanChannels','usbSessionId','lastAcceptedNativeRevision','tpsTrace','TPS development fixtures']:
    if marker not in combined+usb_source+hub_source+activity_source: errors.append('Missing v0.11.8 perf2 marker: '+marker)
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
if 'suspendForAlternateTransport' not in ble_source or 'reconcileTransportPolicy' not in activity_source:
    errors.append('USB/BLE transport arbitration hotfix is incomplete')
if errors:
    print('VALIDATION FAILED')
    for error in errors: print(' - '+error)
    sys.exit(1)
print(f'VALIDATION PASSED: {len(parser.ids)} unique IDs; v0.11.11 explicit demo, reconnect authority, raw frame rate, TPS truth and Performance Lab controls present')
