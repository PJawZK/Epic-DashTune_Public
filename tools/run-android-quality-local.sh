#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: required command '$1' is not available" >&2
    exit 2
  }
}

need python3
need node
need java

JAVA_VERSION="$(java -version 2>&1 | head -n1)"
if [[ "$JAVA_VERSION" != *'"17.'* && "$JAVA_VERSION" != *'"17"'* ]]; then
  echo "ERROR: Android quality is pinned to Java 17; current runtime is: $JAVA_VERSION" >&2
  echo "Set JAVA_HOME/PATH to a JDK 17 installation and rerun." >&2
  exit 2
fi

chmod +x ./gradlew

echo "== EpicDash JZ local Android quality =="
echo "HEAD: $(git rev-parse HEAD 2>/dev/null || echo unknown)"
echo "Java: $JAVA_VERSION"
echo "Node: $(node --version)"
echo "Python: $(python3 --version)"
echo

run() {
  echo
  echo ">>> $*"
  "$@"
}

run python3 tools/validate-dashboard.py
run python3 tools/validate-t3-manager.py
run node app/src/test/js/dashboard_runtime_snapshot_test.js
run node app/src/test/js/dashboard_tps_trace_characterization_test.js
run node app/src/test/js/dashboard_diagnostics_active_view_characterization_test.js
run node app/src/test/js/dashboard_analysis_history_scaling_characterization_test.js
run node app/src/test/js/dashboard_storage_capacity_recovery_characterization_test.js
run node app/src/test/js/dashboard_storage_save_correctness_test.js
run node app/src/test/js/dashboard_custom_widget_scaling_characterization_test.js
run node app/src/test/js/dashboard_activity_ownership_characterization_test.js
run node app/src/test/js/dashboard_gps_data_truth_characterization_test.js
run node app/src/test/js/t4_scalar_simulation_characterization_test.js
run node app/src/test/js/w4_vehicle_ram_characterization_test.js
run node app/src/test/js/t5_tuning_array_characterization_test.js
run node app/src/test/js/t6_persistent_burn_characterization_test.js
run node app/src/test/js/tuning_write_characterization_test.js
run node app/src/test/js/production_tuner_contract_test.js
run node app/src/test/js/dashboard_ble_queue_progress_characterization_test.js
run node app/src/test/js/dashboard_lifecycle_instrumentation_test.js
run ./gradlew --no-daemon testDebugUnitTest
run ./gradlew --no-daemon lintDebug
run ./gradlew --no-daemon assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo
  echo "APK: $APK"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$APK"
  fi
fi

echo
echo "LOCAL ANDROID QUALITY PASSED"
