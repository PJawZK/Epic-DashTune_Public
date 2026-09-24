#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/buttonbox/ble"
ASSETS = ROOT / "app/src/main/assets"

manager = (JAVA / "UsbEcuManager.kt").read_text()
activity = (JAVA / "DashboardLabActivity.kt").read_text()
ui = (ASSETS / "t4_tuning_workspace.js").read_text()
tuner_write = (JAVA / "TuningWriteEngine.kt").read_text()
snapshot_reader = (JAVA / "AuthoritativeTuneSnapshotReader.kt").read_text()
burn = (JAVA / "TuneBurnPrimitives.kt").read_text()

errors = []

# Step 0E: historical proof workflows must not be executable from the product surface.
for marker in [
    "t3BenchLabFlow",
    "w4VehicleRamFlow",
    "t6PersistentBurnFlow",
    "T3BenchLabFlow.installLauncher",
    "openT3BenchFlow",
    "openW4VehicleRamWrite",
    "getW4VehicleRamStatusJson",
    "openT6PersistentBurnFlow",
    "getT6PersistentBurnStatusJson",
]:
    if marker in activity:
        errors.append("Dashboard activity still exposes legacy proof surface: " + marker)

for marker in [
    "openT3BenchFlow",
    "openW4VehicleRamWrite",
    "getW4VehicleRamStatusJson",
    "openT6PersistentBurnFlow",
    "getT6PersistentBurnStatusJson",
    "t4twT6",
    "T6 legacy proof",
    "VEHICLE_RAM_ONLY",
]:
    if marker in ui:
        errors.append("Tuner UI still exposes legacy proof surface: " + marker)

# Diagnostics/freshness must describe production ownership, not historical proof owners.
for marker in [
    '.put("t3Bench"',
    '.put("w4VehicleRam"',
    '.put("t6PersistentBurn"',
]:
    if marker in manager:
        errors.append("Manager diagnostics still expose proof status: " + marker)

for marker in [
    "legacyProofRecovery",
    '"executableProofFlows", false',
    "t3RecoveryStore.load()",
    "t6RecoveryStore.load()",
    "exclusiveOperationOwner",
    "UsbOperationOwner.TUNER",
    'operationOwner?.diagnosticName ?: ""',
]:
    if marker not in manager:
        errors.append("Missing Step 0E production/recovery marker: " + marker)

freshness_start = manager.find("fun transportFreshnessJson()")
freshness_end = manager.find("private fun setState(", freshness_start)
freshness = manager[freshness_start:freshness_end] if freshness_start >= 0 and freshness_end > freshness_start else ""
for forbidden in ['"T3"', '"W4"', '"T6"', "t3ProofRunning", "w4WriteRunning", "t6BurnRunning"]:
    if forbidden in freshness:
        errors.append("Transport freshness still depends on proof owner/state: " + forbidden)

# Firmware-driven profileless recognition may query identity without an INI, but it must stop before
# framed output/TuneSnapshot/tuning authority. This is deliberately a source contract around the
# safety-sensitive manager ordering rather than a second USB implementation.
discovery_start = manager.find("private fun discoverAndConnect(")
discovery_end = manager.find("private fun selectionDescriptor(", discovery_start)
discovery = manager[discovery_start:discovery_end] if discovery_start >= 0 and discovery_end > discovery_start else ""
if 'if (selectedProfile == null)' in discovery or 'setState(State.NO_PROFILE, "Import mainController.ini"' in discovery:
    errors.append("USB discovery still blocks the read-only firmware signature probe when no INI is loaded")
if 'Connect supported Mega144H7 with USB OTG' in discovery:
    errors.append("USB discovery still labels the shared firmware transport as Mega144H7-only")

connect_start = manager.find("private fun connect(")
connect_end = manager.find("private fun buildProbeSequence(", connect_start)
connect = manager[connect_start:connect_end] if connect_start >= 0 and connect_end > connect_start else ""
for marker in [
    "val selectedProfile = profile",
    "EcuRecognitionResult.evaluate(",
    "selectedProfile?.signature.orEmpty()",
    'handshakeStage = "identity_detected_no_profile"',
    "ECU identity detected without INI authority",
    'setState(State.NO_PROFILE, "Detected $detected • import matching mainController.ini"',
]:
    if marker not in connect:
        errors.append("Missing profileless ECU recognition marker: " + marker)

profileless_start = connect.find("if (selectedProfile == null)")
preflight_start = connect.find("runFramedProtocolPreflight(")
if profileless_start < 0 or preflight_start < 0 or profileless_start >= preflight_start:
    errors.append("Profileless recognition must branch before framed protocol/output authority")
else:
    profileless_block = connect[profileless_start:preflight_start]
    for marker in ['closeConnection("")', "return"]:
        if marker not in profileless_block:
            errors.append("Profileless recognition does not fail closed before profile-authorized reads: " + marker)

signature_start = manager.find("private fun isLikelySignature(")
signature_end = manager.find("private fun envelope(", signature_start)
signature_block = manager[signature_start:signature_end] if signature_start >= 0 and signature_end > signature_start else ""
if "EcuFirmwareIdentity.parse(value) != null" not in signature_block:
    errors.append("Signature plausibility is not delegated to firmware identity parsing")
if "MEGA144" in signature_block:
    errors.append("Legacy MEGA144-specific signature plausibility fallback is still active")

# Accepted production tuning invariants must remain.
for marker in [
    "queueTuningRead",
    "queueTuningWriteBatchJson",
    "queueTuningBurn",
    "tuningWriteStatusJson",
    "ram_applied_verified",
    "write_uncertain",
]:
    if marker not in manager:
        errors.append("Missing normal tuner marker: " + marker)

for marker, source in [
    ("TuningWritePlanner", tuner_write),
    ("EcuCommandResponseCodec", tuner_write),
    ("AuthoritativeTuneSnapshotReader", snapshot_reader),
    ("TuneBurnEvidencePolicy", burn),
]:
    if marker not in source:
        errors.append("Missing neutral production tuner primitive: " + marker)

if errors:
    print("VALIDATION FAILED")
    for error in errors:
        print(" - " + error)
    sys.exit(1)

print("VALIDATION PASSED: profileless ECU identity remains read-only/fail-closed; legacy proof surfaces retired; normal semantic tuning preserved")
