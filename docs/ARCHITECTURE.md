# Public architecture overview

## Application surfaces

`DashboardLabActivity` is the primary application surface. It owns the LAB WebView and coordinates USB transport, BLE, diagnostics, log playback, location integration, native-to-WebView publication, profile/TuneSnapshot state, and the native Tuner bridge.

`MainActivity` remains the legacy stock dashboard/button-box surface. Lifecycle rules prevent hidden surfaces from retaining conflicting transport ownership.

## Native Android responsibilities

The Kotlin layer owns:

- Android permissions/lifecycle;
- BLE scanning/connection and legacy queues;
- USB device selection, CDC transport and protocol ownership;
- firmware/TunerStudio signature recognition;
- generated-INI parsing/profile identity;
- complete TuneSnapshot acquisition/persistence;
- live output-channel decoding and canonical state;
- semantic Tuner target resolution and guarded native mutation;
- acknowledgement/read-back/full-snapshot verification;
- explicit Burn sequencing and post-Burn verification;
- GPS, playback, settings, diagnostics, crash breadcrumbs and performance metrics;
- structured publication into the WebView.

## WebView responsibilities

`app/src/main/assets/dashboard_lab.html` and the Tuner workspace scripts own presentation, responsive layout, editing interactions, navigation and semantic edit requests.

The WebView does **not** own USB/BLE transport and does not receive generic raw production ECU-command authority.

## ECU recognition and profile authority

The shared `0483:5740` STM32 USB identity is only a transport candidate.

```text
known transport candidate
→ read-only TunerStudio signature probe
→ firmware identity
→ exact generated-INI full-signature match
→ framed protocol preflight
→ complete TuneSnapshot
→ live output streaming / guarded Tuner authority
```

Unknown/ambiguous hardware fails closed. A recognized transport without a matching generated profile may identify firmware but stops before tune/output/stream/write/Burn authority.

`mainController.ini` is structural/schema/menu authority. The complete verified native TuneSnapshot is tune-value authority. Compiled profiles/workspace projections are derived acceleration only.

## Live mutation authority

```text
semantic edit request
→ native target resolution
→ bounded write
→ exact acknowledgement
→ exact read-back
→ complete expected TuneSnapshot verification
→ verified RAM state
→ separate explicit Burn
→ flash-status evidence
→ complete post-Burn TuneSnapshot verification
```

Uncertain outcomes fail closed. Offline/detached editing cannot write, queue ECU writes, or Burn.

## 1207 live/offline project split

1207 removes the previous eager multi-megabyte permanent-project work from normal live interaction:

- live bulk workspace omits decoded array-cell payloads;
- Table/Curve detail uses semantic single-array native detail;
- permanent saved/offline project projection is deferred until the live path is absent and it is actually requested;
- render-time full-workspace cloning is removed;
- saved/offline values still derive from the persisted complete native TuneSnapshot;
- Curves use measured plot geometry for responsive sizing.

This split is responsible for the major live-path performance improvement observed in the current phone evidence.

## State and freshness authority

USB attempts and invalidations use generation/session authority. Dashboard snapshots carry source/session/revision identity, and stale or non-increasing updates are rejected. Data freshness follows accepted native transport/snapshot state rather than WebView timing alone.

## Compatibility-test direction

Epic DashTune is now primarily a public compatibility-test vehicle. New hardware/profile support should be driven by real firmware signatures and generated INIs. Evidence from this line feeds the successor **EpicEFI – EpicHub** architecture; new product-level architecture should be implemented there rather than duplicated here.
