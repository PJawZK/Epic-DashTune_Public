# Epic DashTune

Epic DashTune is an Android dashboard, diagnostics, logging, playback, and tuning-analysis platform for EpicEFI systems.

> **Current safety boundary:** the application is strictly read-only with respect to the ECU. It does not write tune data, burn changes, control outputs, or virtual-input commands.

This repository contains the curated public Android source, dashboard assets, validation tools, and automated tests. Experimental UX concepts remain in a separate project so prototypes are not mistaken for supported production behavior.

## Current source identity

- Public product name: **Epic DashTune**
- Legacy Android app label in the initial import: **EpicDash JZ**
- Android package retained for update continuity: `com.buttonbox.ble.jz`
- Version: `0.11.12-stale1-jz` (`1114`)
- Minimum Android: API 26
- Compile/target SDK: API 34
- ECU capability: strictly read-only

The legacy package and app label are intentionally retained in this first public import. Renaming the installed application is a separate compatibility and release task.

## Implemented capabilities

- Android dashboard and LAB environment
- read-only EpicEFI Mega144H7 USB output-channel streaming
- BLE transport for the existing ESP32 dashboard/button-box integration
- MSL log playback and imported tabular log playback
- configurable dashboard layouts, math channels, warnings, and diagnostics
- GPS integration and VSS fallback
- bounded diagnostic history and performance instrumentation
- dashboard, JVM, source-contract, and characterization tests

## Safety model

The USB ECU transport implements signature discovery and output-channel reads only. No tune-page writes, burns, output commands, reset/stop commands, or generic raw ECU-command interface are implemented.

Future tuning capability is not part of the current application. Any such work requires a separate native tuning core, typed allowlist, explicit safety-state machine, fresh ECU/tune identity gates, read-back verification, deliberate RAM-write/burn separation, backup and recovery procedures, and staged physical acceptance.

See [SECURITY.md](SECURITY.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Building and testing

The project uses JDK 17, Gradle 8.2, Android Gradle Plugin 8.2.0, and Android SDK 34.

```bash
python3 tools/validate-dashboard.py
python3 tools/test-dashboard-regressions.py
./gradlew testDebugUnitTest lintDebug assembleDebug
```

See [docs/BUILDING.md](docs/BUILDING.md) for the complete setup and signing distinction.

## Public-export provenance

The initial source import was produced by an explicit allowlist from private development commit `6d705d5ae3078a2ade377c51e8fe10b680374f1a`. Private signing material, operational handoffs, assistant instructions, internal audit history, generated APKs, personal paths, raw vehicle logs, and sensitive diagnostics are not included.

See [SOURCE_PROVENANCE.md](SOURCE_PROVENANCE.md) and [docs/PUBLIC_EXPORT_POLICY.md](docs/PUBLIC_EXPORT_POLICY.md).

## Licence status

No repository-wide open-source licence is granted yet. Inherited source and asset ownership/attribution are still being audited. See [LICENSE_STATUS.md](LICENSE_STATUS.md) before copying, modifying, or redistributing this code.

Third-party dependencies remain subject to their own licences; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
