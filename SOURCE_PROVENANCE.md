# Source provenance

## Current public compatibility baseline

- Private source repository: `PJawZK/EpicDash-JZ`
- Exact private integrated application-source commit: `9de5f4129cddc75692b39b84069f658c6a92ce61`
- Accepted PR #86 source head: `5cf2244c0f49ce0b87c9c8230c92224d2828940a`
- Application: `0.12.5-tuner-live-lazy-jz / 1207`
- Private exact merged-main Android-quality run: `#1988 / 36041788337` — PASS
- Private merged-main APK artifact ID: `10826804972`
- Private merged-main artifact ZIP SHA-256: `52f3b52c6af1e33eac7421c3ead0f92bb39d919d6c77b023ec40e1d45294cbed`
- Private merged-main extracted APK SHA-256: `59f16df82c2e3fb6dec7d9dafc92fd29d375350e45dc29e1ca2de8d9d776211e`
- Private merged-main APK size: `7,571,317` bytes
- Curated export workflow run: `36043195993` — PASS
- Curated export artifact ID: `10827696167`
- Curated export ZIP SHA-256: `a8cdf88f59f704ad76774f753ed262d96cde32c96e44b7cd58a7c30dc2c050db`
- First public source-sync commit for this baseline: `c56fbe30cd0520a64398dbf247b428a51c945594`

The public tree is a curated source export, not a mirror of private Git history. The normal merge of private PR #86 preserves the complete private development history; public history records only the reviewed export/import boundary.

## Current physical evidence boundary

1207 has been physically exercised on a Samsung SM-A137F / Android 14 with the current MEGA144H7 firmware/profile combination. Evidence from that captured session includes:

- exact firmware/profile signature match;
- complete 60,764-byte TuneSnapshot acquisition;
- stable live streaming at about 19.5 Hz against the 20 Hz target;
- zero CRC and protocol errors in the captured session;
- multiple verified semantic RAM tune writes;
- live permanent-project payload reduced from the previous multi-megabyte path to a 249-character deferred marker;
- no new managed uncaught crash captured in that session.

This is evidence for that tested phone/ECU/profile combination only. It is not broad qualification across other Android devices, ECU boards, firmware builds, or generated INIs.

## Public compatibility-test purpose

The `v0.12.5-rc.1` line is intentionally published as a pre-release compatibility test build. The goal is to gather real-world evidence from:

- different EpicEFI/rusEFI STM32 hardware;
- different firmware signatures;
- genuine generated `mainController.ini` files;
- differing runtime block/page geometry;
- unsupported INI constructs and fail-closed states;
- Android phones/tablets outside the current test set.

Those findings are intended to strengthen the successor **EpicEFI – EpicHub** connection/profile architecture.

## Current tuning capability and safety boundary

Unlike the initial public RC1, 1207 is **not ECU read-only**.

A live tuning operation requires the current connection/profile authority chain and remains native/semantic:

```text
matching generated INI
+ current ECU generation/signature
+ complete TuneSnapshot
→ semantic edit request
→ native target resolution
→ bounded write
→ exact acknowledgement/read-back
→ complete expected TuneSnapshot verification
→ verified RAM state
→ separate explicit Burn
→ post-Burn verification
```

The WebView does not receive raw production write authority. Detached/offline editing cannot write to the ECU or Burn. Unknown/ambiguous hardware and profile mismatches fail closed.

## Curated export paths and transformations

The 1207 source export includes the current Android application source/resources, tests, validation tools, Gradle text configuration, and public build inputs needed to reproduce the source state.

The public import deliberately excludes:

- private signing material and signing properties;
- keystores/passwords/private keys/tokens;
- private handoff and operational documents;
- raw vehicle logs and sensitive diagnostics;
- generated APK/AAB/build output;
- machine-local properties and personal paths;
- inherited private PNG logo artwork pending provenance review;
- binary `gradle-wrapper.jar` pending provenance review.

The public repository retains its neutral vector `epicdash_jz_logo.xml` replacement. Public CI provisions Gradle 8.2 directly rather than depending on the omitted binary wrapper JAR.

`CURATED_PRIVATE_MANIFEST.sha256` records hashes produced from the exact curated private-export staging tree. Public-specific files and the neutral vector substitution are documented separately and therefore are not expected to be byte-identical to that private staging manifest.

## Validation performed for 1207 publication

- exact private application source merged with a normal merge commit;
- exact merged-private `main` Android quality passed completely;
- curated export source checked out from exact application commit `9de5f412...`;
- export filename/content safety scans passed;
- curated export archive checksum verified;
- public source import preserved the public-only vector substitution and excluded private signing/log artifacts;
- public PR/main CI is required to pass validators, Tuner authority/runtime tests, JVM unit tests, Android lint, and debug assembly before release publication.

## Historical initial import / RC1

The initial public import and `v0.11.12-rc.1` remain part of repository/release history. Their exact provenance and read-only boundary were correct for those historical artifacts. They do not describe the current 1207 capability boundary.
