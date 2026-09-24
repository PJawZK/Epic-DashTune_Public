'use strict';

const assert = require('assert');
const fs = require('fs');

const read = path => fs.readFileSync(path, 'utf8');
const store = read('app/src/main/java/com/buttonbox/ble/TunerRecoveryBundleStore.kt');
const permanent = read('app/src/main/java/com/buttonbox/ble/TunerPermanentProjectStore.kt');
const bridge = read('app/src/main/java/com/buttonbox/ble/TunerRecoveryBridge.kt');
const activity = read('app/src/main/java/com/buttonbox/ble/TunerRecoveryActivity.kt');
const labActivity = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const application = read('app/src/main/java/com/buttonbox/ble/EpicDashApplication.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const surface = read('app/src/main/java/com/buttonbox/ble/T4TuningWorkspaceSurface.kt');
const recovery = read('app/src/main/assets/t4_tuning_workspace_recovery.js');

assert.doesNotThrow(() => new Function(recovery), 'Tuner recovery presentation layer does not parse');
assert.ok(!fs.existsSync('app/src/main/java/com/buttonbox/ble/TunerPersistentIniStore.kt'), 'duplicate standalone persistent-INI subsystem returned');
assert.ok(!fs.existsSync('app/src/test/js/tuner_persistent_ini_source_characterization_test.js'), 'obsolete standalone INI characterization returned');

for (const marker of [
  'internal object TunerRecoveryBundleStore',
  'BUNDLE_SCHEMA_VERSION = 2',
  'EpicDash-JZ-Tuner-Recovery.json',
  '"iniSource"',
  '"tuneSnapshot"',
  'TuneSnapshot.fromBackupJson',
  'profile.tuneProfileFingerprint()',
  'MediaStore.Downloads.EXTERNAL_CONTENT_URI',
  'takePersistableUriPermission',
  'fun importBundle',
  'commitRecoveryGeneration', 'RecoveryFileState', 'rollback()',
  'AtomicFile(target)',
  'reinstallRestoreRequiresUserSelection',
  'FileObserver.MOVED_TO',
  'last-tune.snapshot.json',
  'MIRROR_DEBOUNCE_MS = 250L',
  'newSingleThreadScheduledExecutor',
  'pendingMirror?.cancel(false)'
]) assert.ok(store.includes(marker), 'missing native-snapshot recovery bundle marker: ' + marker);
for(const obsolete of ['"valuesState"','VALUES_SCHEMA_VERSION','EpicDash-JZ-Tuner-Values.json']){
  assert.ok(!store.includes(obsolete),'recovery bundle retained obsolete semantic-value payload: '+obsolete);
}

for (const marker of [
  'class TunerRecoveryBridge',
  '@JavascriptInterface',
  'fun openRecoveryBundle()',
  'TunerRecoveryActivity::class.java',
  'fun diagnostics()'
]) assert.ok(bridge.includes(marker), 'missing recovery bridge marker: ' + marker);

for (const marker of [
  'class TunerRecoveryActivity : AppCompatActivity()',
  'ActivityResultContracts.OpenDocument()',
  'if (savedInstanceState == null)',
  'TunerRecoveryBundleStore.importBundle(this, uri)',
  'DashboardLabActivity::class.java',
  'Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK'
]) assert.ok(activity.includes(marker), 'missing SAF recovery activity marker: ' + marker);

for (const marker of [
  'TUNER_UI_RECOVERY_V3',
  't4twRestoreProject',
  'RESTORE SAVED TUNER PROJECT',
  'EpicDash-JZ-Tuner-Recovery.json',
  'last complete native TuneSnapshot',
  "workspace?.status!=='ready'",
  "for(const label of ['Parameters','Tables','Curves'])",
  "button.disabled=true",
  "host.textContent=''",
  'host.appendChild(t4RecoveryStartup)',
  'openRecoveryBundle?.()===true'
]) assert.ok(recovery.includes(marker), 'missing recovery UI marker: ' + marker);
assert.ok(!recovery.includes('page.prepend(t4RecoveryPanel)'), 'recovery panel must not float above the normal Tuner chrome');

assert.ok(surface.includes('t4_tuning_workspace_recovery.js'), 'recovery UI layer is not injected');
assert.ok(labActivity.includes('addJavascriptInterface(TunerRecoveryBridge(applicationContext), "EpicDashTunerRecovery")'), 'recovery bridge must be installed before WebView page load');
assert.ok(!surface.includes('hasLocalProject(context)'), 'recovery bridge availability must not be gated by file existence');
assert.ok(!surface.includes('view.reload()'), 'Tuner surface must not reload the WebView to expose recovery');
assert.ok(store.includes('localProjectState'), 'recovery diagnostics must expose validated project classification');
assert.ok(surface.indexOf('PROJECT_STATE_ASSET') < surface.indexOf('RECOVERY_ASSET'), 'recovery UI must run after project-state activation');
assert.ok(surface.indexOf('RECOVERY_ASSET') < surface.indexOf('TELEMETRY_ASSET'), 'recovery UI must run before telemetry completion layer');
assert.ok(application.includes('TunerPermanentProjectStore.initialize(this)'), 'permanent native project store is not initialized at application startup');
assert.ok(application.includes('TunerRecoveryBundleStore.startMirroring(this)'), 'recovery bundle mirror is not started at application startup');
assert.ok(manifest.includes('android:name="com.buttonbox.ble.TunerRecoveryActivity"'), 'recovery activity is not registered');

for (const marker of [
  'persistVerifiedSnapshot', 'last-tune.snapshot.json', 'TunerRecoveryBundleStore.mirrorAsync(context)',
  'TuneSnapshot.fromBackupJson', 'AtomicFile(target)'
]) assert.ok(permanent.includes(marker), 'permanent project store is missing recovery handoff marker: ' + marker);
for(const obsolete of ['JavascriptInterface','saveProject','loadProject','values.json']){
  if(obsolete==='values.json') continue; // legacy cleanup filename is intentionally present.
  assert.ok(!permanent.includes(obsolete),'permanent project store reintroduced WebView persistence: '+obsolete);
}

for (const permission of ['MANAGE_EXTERNAL_STORAGE','READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE']) {
  assert.ok(!manifest.includes(permission), 'broad storage permission must not be introduced: ' + permission);
}

for (const source of [bridge, activity, recovery]) {
  for (const forbidden of [
    'UsbEcuManager', 'bulkTransfer(', 'controlTransfer(', 'queueTuningRead', 'queueTuningWrite',
    'queueTuningBurn', 'writeTuningChangesJson', 'burnTuningChangesJson', 'saveTuningToEcu('
  ]) assert.ok(!source.includes(forbidden), 'recovery UI/action path gained forbidden ECU authority: ' + forbidden);
}
for (const forbidden of ['UsbEcuManager','bulkTransfer(','controlTransfer(','queueTuningRead','queueTuningWrite','queueTuningBurn']) {
  assert.ok(!store.includes(forbidden),'recovery file store gained transport/write authority: '+forbidden);
}

console.log('Tuner native TuneSnapshot SAF recovery bundle characterization passed');

assert.ok(store.includes('Recovery project commit failed; the previous project was restored.'), 'recovery import must fail transactionally');
assert.ok(!store.includes('The INI was restored, but the saved TuneSnapshot could not be restored.'), 'partial INI-only failure wording returned');
