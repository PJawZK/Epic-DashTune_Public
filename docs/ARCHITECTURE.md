# Public architecture overview

## Application surfaces

`DashboardLabActivity` is the launcher and owns the primary LAB WebView, read-only USB transport, LAB-scoped BLE connection, log playback, diagnostics, location integration, and native-to-WebView snapshot publication.

`MainActivity` remains the legacy stock dashboard/button-box surface. It owns a separate Activity-scoped BLE manager while visible. Activity lifecycle rules prevent the hidden stock surface from retaining transport ownership behind LAB.

## Native Android responsibilities

The Kotlin layer owns:

- Android permissions and lifecycle;
- BLE scanning, connection and the existing three legacy write queues;
- USB device selection, permission ownership, CDC transport and read-only TunerStudio output polling;
- ECU channel decoding and canonical native state;
- GPS collection and legacy GPS payload mapping;
- MSL/log playback;
- persistent settings and bounded diagnostic history;
- generation/session authority and performance measurements;
- safe bridge publication into the dashboard WebView.

## Dashboard responsibilities

`app/src/main/assets/dashboard_lab.html` provides the current dashboard/LAB renderer and editor. It owns presentation, layout editing, math-expression evaluation, warning presentation, history graphs, demo scenarios, and user-facing diagnostics.

The WebView receives structured state from native Android. It does not own USB/BLE transport and cannot construct raw ECU commands.

## Read-only ECU flow

The supported USB path is:

```text
Android USB host
  -> supported-device selection
  -> CDC control/data interface ownership
  -> plain signature request
  -> CRC-framed signature synchronization
  -> CRC-framed output-channel reads
  -> CRC validation
  -> INI-profile decoding
  -> canonical native snapshot
  -> WebView presentation
```

`UsbEcuManager` implements discovery and output reads only. No tune-page writes, burns, controller reset, engine-stop command, output control, or generic raw command interface is implemented.

## State and freshness authority

USB attempts and invalidations use a process-local generation authority. Dashboard snapshots carry source/session/revision identity, and stale or non-increasing updates are rejected. Data freshness is based on the accepted ECU packet/snapshot timeline rather than WebView bridge age alone.

## BLE queue status

Button, variable-request and GPS/ADC traffic currently use three independent legacy FIFO queues with one in-flight flag per queue and no global GATT arbiter. Current production policy is characterized and instrumented but deliberately not corrected in this baseline. Queue depth, loss, completion, duration and overlap counters exist to support bounded physical evidence before policy changes.

## Future tuning boundary

Future ECU-write capability must be implemented beside—not inside—the dashboard renderer. The required design is a separate native tuning core with a typed allowlist, exact ECU/INI/tune identity, explicit safety-state machine, no blind retries after uncertain outcomes, read-back verification, deliberate RAM-write/burn separation, backup/recovery procedures, and staged bench/vehicle acceptance.
