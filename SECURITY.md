# Security policy

## Current safety scope

Epic DashTune 1207 contains guarded native ECU tuning capability. It is **not** an ECU read-only application.

A security or safety report is especially important if behavior could:

- transmit a tune write or Burn without the documented live identity/TuneSnapshot gates;
- allow WebView/UI code to construct arbitrary raw ECU writes;
- bypass exact generated-INI full-signature matching;
- accept stale ECU/session identity as current;
- treat unknown or ambiguous hardware as tune-authorized;
- write or Burn while detached/offline;
- bypass package/signing continuity expectations;
- expose credentials, signing material, personal paths, or private vehicle evidence;
- misrepresent raw ECU values in a safety-relevant display.

## ECU mutation boundary

Normal tune mutation must remain:

```text
matching generated INI
+ current ECU generation/signature
+ complete verified TuneSnapshot
→ semantic edit request
→ native target resolution
→ bounded write
→ exact acknowledgement/read-back
→ complete expected TuneSnapshot verification
→ verified RAM state
→ separate explicit Burn
→ post-Burn verification
```

The WebView does not own production transport or raw write authority. Unknown/ambiguous/profileless hardware fails closed. Offline editing cannot write or Burn.

## Reporting

Do not publish exploitable details, credentials, private keys, or sensitive vehicle data in a public issue. Contact the repository owner privately through GitHub first and provide a minimal reproduction, affected version/commit, and expected impact.

For compatibility bugs that do not expose sensitive data, include Android device/version, ECU hardware, exact TunerStudio signature, generated INI identity, and an exported diagnostic report when appropriate.

## Supported state

Public pre-releases identify their exact public source commit, private source provenance, package/version, checksum, and known physical-evidence boundary. A CI pass is software qualification only; broader ECU/device compatibility requires field evidence.
