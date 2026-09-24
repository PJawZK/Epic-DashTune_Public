const fs = require('fs');
const assert = require('assert');

const activity = fs.readFileSync('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt', 'utf8');
const manager = fs.readFileSync('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt', 'utf8');
const ui = fs.readFileSync('app/src/main/assets/t4_tuning_workspace.js', 'utf8');

for (const marker of [
  'openW4VehicleRamWrite',
  'getW4VehicleRamStatusJson',
  'w4VehicleRamFlow',
  'VEHICLE_RAM_ONLY',
  'Restore ECU RAM baseline • W4',
  'Native W4'
]) {
  assert.ok(!activity.includes(marker), 'Dashboard activity retained W4 proof surface: ' + marker);
  assert.ok(!ui.includes(marker), 'Tuner UI retained W4 proof surface: ' + marker);
}

assert.ok(!manager.includes('.put("w4VehicleRam"'), 'Diagnostics retained W4 proof status');
assert.ok(manager.includes('legacyProofRecovery'), 'Legacy proof recovery state is not surfaced');
assert.ok(manager.includes('t3RecoveryStore.load()'), 'Legacy T3/W4 recovery marker is not checked');
assert.ok(manager.includes('queueTuningWriteBatchJson'), 'Normal semantic write path missing');
assert.ok(activity.includes('fun writeTuningChangesJson(json: String): String'), 'Normal semantic WebView write bridge missing');

console.log('W4 retirement characterization passed');
