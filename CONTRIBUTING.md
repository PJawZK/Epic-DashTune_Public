# Contributing to Epic DashTune

Thank you for helping improve Epic DashTune.

## Current project role

Epic DashTune is now the legacy/public compatibility-test vehicle for the successor **EpicEFI – EpicHub** project. The most valuable contributions are bounded fixes and evidence that improve real ECU/INI/device compatibility without creating a competing second architecture.

Useful reports include:

- Android device/version;
- ECU hardware and exact firmware/TunerStudio signature;
- genuine generated `mainController.ini` behavior;
- connection/preflight/TuneSnapshot/stream results;
- unsupported INI constructs;
- performance/crash evidence and exported diagnostics.

## Safety boundary

1207 contains guarded native tuning capability. Contributions must preserve:

- stable package identity unless a deliberate migration is planned;
- transport ID is not ECU identity;
- exact live firmware signature + exact generated-INI match before tune authority;
- complete verified native TuneSnapshot authority;
- semantic UI mutation requests only;
- bounded native write, exact acknowledgement/read-back and full-snapshot verification;
- separate explicit Burn;
- fail-closed uncertainty;
- no offline/detached ECU write or Burn;
- no raw production ECU-write authority in WebView/layout/math/plugin paths.

Do not add speculative board-specific shortcuts merely to make an unsupported ECU appear connected.

## Development workflow

1. Open/reference an issue describing a bounded problem or compatibility finding.
2. Work on a focused branch.
3. Add/update tests for changed behavior.
4. Run public validators, Tuner authority/runtime tests, JVM tests, Android lint, and APK assembly.
5. Describe changed files, tests executed, build result, safety impact, and remaining uncertainty in the pull request.
6. For hardware/profile support, prefer real signature/INI/diagnostic evidence over board-name assumptions.

## Repository hygiene

Do not commit keys, credentials, signing properties, personal paths, generated APK/AAB files, raw vehicle recordings, private handoffs, or sensitive diagnostic evidence.

## UX experiments and EpicHub

Large new product-level UX/architecture work belongs in EpicHub. Epic DashTune changes should primarily maintain the compatibility-test surface or produce evidence transferable to EpicHub.

## Licence and third-party material

This repository currently has no project-wide licence grant. Contributions cannot be accepted for redistribution until contributor terms and inherited-source licensing are resolved. Do not add third-party code, artwork, fonts, or assets unless their licence and attribution are documented and compatible with the eventual repository licence.
