# Epic DashTune

Epic DashTune is the legacy Android dashboard, diagnostics, logging, playback, and native Tuner application that preceded **EpicEFI – EpicHub**.

Active long-term product development has moved to EpicHub. This repository remains public as a useful compatibility-test vehicle for EpicEFI/rusEFI Android connectivity, generated `mainController.ini` profiles, TunerStudio signatures, live telemetry, and the existing guarded native tuning path.

> **Safety warning:** this build is **not ECU read-only**. With an exact matching generated INI, current ECU generation/signature, and complete TuneSnapshot, Epic DashTune can perform guarded semantic RAM tune writes and an explicit separate Burn. Do not change tuning values or Burn unless you understand the consequences and have appropriate recovery procedures.

## Current compatibility-test release

Current source/application state:

- Application: `0.12.5-tuner-live-lazy-jz / 1207`
- Android package retained for continuity: `com.buttonbox.ble.jz`
- Private integrated application-source baseline: `9de5f4129cddc75692b39b84069f658c6a92ce61`
- Private merged-main Android quality: `#1988 / 36041788337` — PASS
- Public release target: `v0.12.5-rc.1`

The release is intentionally a **pre-release compatibility test build**, not a claim of broad hardware qualification.

## What we want testers to try

The most useful public testing now is with **different real generated INIs and different EpicEFI/rusEFI STM32 hardware/firmware combinations**.

Please report:

- Android device/model and Android version;
- ECU board/hardware;
- exact firmware/TunerStudio signature shown by the app;
- whether your genuine generated `mainController.ini` imports successfully;
- whether exact signature/profile matching reaches preflight, complete TuneSnapshot, and stable streaming;
- whether live channels decode correctly;
- any fail-closed state, unsupported INI construct, crash, or major performance issue;
- an exported diagnostic report when possible.

This evidence is particularly useful for strengthening **EpicHub's** future connection/profile compatibility matrix.

## Current authority and safety model

Transport identity is not ECU identity. The known `0483:5740` USB identity is treated only as a shared STM32/rusEFI transport candidate.

The current connection/tuning authority is:

```text
known transport candidate
→ read-only TunerStudio signature probe
→ firmware identity
→ exact generated-INI full-signature match
→ protocol preflight
→ complete native TuneSnapshot
→ live telemetry / guarded native tuning authority
```

Unknown/ambiguous hardware and profile mismatches fail closed.

`mainController.ini` is structure/schema/menu authority. A complete verified native TuneSnapshot is tune-value authority. The WebView does not own raw ECU transport or production write authority.

Normal tune mutation remains native and guarded:

```text
semantic edit
→ native target resolution
→ bounded write
→ exact acknowledgement/read-back
→ complete expected TuneSnapshot verification
→ verified RAM state
→ separate explicit Burn
→ post-Burn verification
```

Offline editing does not gain ECU write/Burn authority.

## 1207 status

1207 was physically exercised on a Samsung SM-A137F / Android 14 with the current MEGA144H7 generated profile. The captured session showed stable ~19.5 Hz streaming, zero CRC/protocol errors, multiple verified semantic RAM writes, and a major reduction in the previous live WebView project-payload overhead.

That evidence is valid for the tested combination only. It is **not** broad qualification across other phones/tablets, ECUs, firmware builds, or generated INIs.

Known remaining performance work is concentrated in profile restore, post-write workspace rebuilding, on-demand offline project construction, and initial TuneSnapshot acquisition.

## Building and testing

The project uses JDK 17, Gradle 8.2, Android Gradle Plugin 8.2.0, and Android SDK 34.

Public CI provisions Gradle 8.2 directly because the binary Gradle wrapper JAR remains excluded from the curated public source pending provenance review.

```bash
python3 tools/validate-dashboard.py
python3 tools/test-dashboard-regressions.py
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

See [docs/BUILDING.md](docs/BUILDING.md) for the complete setup.

## Public-export provenance

This repository is a curated export of the private `PJawZK/EpicDash-JZ` repository, not a history mirror. Private signing material, operational handoffs, internal project history, generated APKs, raw vehicle logs, personal paths, and sensitive diagnostic evidence are not exported to normal public source history.

See [SOURCE_PROVENANCE.md](SOURCE_PROVENANCE.md) and [docs/PUBLIC_EXPORT_POLICY.md](docs/PUBLIC_EXPORT_POLICY.md).

## EpicHub successor

EpicHub is the active successor application. Findings from Epic DashTune compatibility testing should be treated as evidence for EpicHub, not as a reason to keep two competing application architectures alive.

## Licence status

No repository-wide open-source licence is granted yet. Inherited source and asset ownership/attribution are still being audited. See [LICENSE_STATUS.md](LICENSE_STATUS.md) before copying, modifying, or redistributing this code.

Third-party dependencies remain subject to their own licences; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
