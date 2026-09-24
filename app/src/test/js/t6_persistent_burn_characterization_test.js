const fs = require('fs');
const assert = require('assert');

const activity = fs.readFileSync('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt', 'utf8');
const manager = fs.readFileSync('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt', 'utf8');
const ui = fs.readFileSync('app/src/main/assets/t4_tuning_workspace.js', 'utf8');

for (const marker of [
  'openT6PersistentBurnFlow',
  'getT6PersistentBurnStatusJson',
  't6PersistentBurnFlow',
  't4twT6',
  'T6 legacy proof'
]) {
  assert.ok(!activity.includes(marker), 'Dashboard activity retained T6 proof surface: ' + marker);
  assert.ok(!ui.includes(marker), 'Tuner UI retained T6 proof surface: ' + marker);
}


for (const marker of [
  'updateT6Control',
  'openT6Flow',
  't6Button',
  'w4Status',
  't6Status'
]) {
  assert.ok(!ui.includes(marker), 'Tuner UI retained dangling legacy proof runtime symbol: ' + marker);
}

assert.ok(!manager.includes('.put("t6PersistentBurn"'), 'Diagnostics retained T6 proof status');
assert.ok(manager.includes('legacyProofRecovery'), 'Legacy proof recovery state is not surfaced');
assert.ok(manager.includes('t6RecoveryStore.load()'), 'Legacy T6 recovery marker is not checked');
assert.ok(manager.includes('queueTuningBurn'), 'Normal explicit Save/Burn path missing');
assert.ok(activity.includes('fun burnTuningChangesJson(): String'), 'Normal Save/Burn WebView bridge missing');

console.log('T6 retirement characterization passed');
