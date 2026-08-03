# Source provenance

## Initial public import

- Private source repository: `PJawZK/EpicDash-JZ`
- Exact private `main` commit exported: `6d705d5ae3078a2ade377c51e8fe10b680374f1a`
- Accepted application-source baseline represented by that tree: `13797491070fc32fd18c2bc52e137130a129ce5a`
- Temporary export-workflow branch head: `6ded39dda336ef2c41ab84dc6225655b7b57ef0d`
- Export workflow run: `30815441574`
- Exact export-head Android-quality run: `#434` (`30815441856`), successful
- Export artifact ID: `8856516144`
- Public exact-head before the provenance-only update: `bd5564ffb171b8566ffa3122210273aed67dc52e`
- Public Android-quality run: `#6` (`30822871731`), successful

The temporary branch added only an export workflow. Application source was taken from the exact private `main` tree above.

## Official RC1 binary publication

The first official release candidate was published on 2026-08-03 as GitHub pre-release [`v0.11.12-rc.1`](https://github.com/PJawZK/Epic-DashTune_Public/releases/tag/v0.11.12-rc.1).

### Source and build identity

- Public release tag target: `24ace519ab249178f00972828b7bf20ac2df5580`
- Private authoritative source commit: `6d705d5ae3078a2ade377c51e8fe10b680374f1a`
- Accepted application-source baseline: `13797491070fc32fd18c2bc52e137130a129ce5a`
- Exact private CI assembly head: `15aac93bf8d8cb385bd2cd3eaf6dd0fd43b63af5`
- Private Android-quality workflow run: `30794624036`, run number `431`
- Private CI APK artifact ID: `8848426854`
- Official RC publication workflow run: `30829230084`, successful

The private CI head and the authoritative private `main` commit differ only in controlled project documentation. The packaged Android application content and controlled assets correspond to the accepted application-source baseline above.

### Distributed artifact identity

- APK: `Epic-DashTune-0.11.12-rc.1.apk`
- APK SHA-256: `8747d162b426ce94f517750fa37907512bdfaeba81baf12098850dc1e3a3c5c2`
- Size: `7088353` bytes
- Package: `com.buttonbox.ble.jz`
- Version name: `0.11.12-stale1-jz`
- Version code: `1114`
- Minimum Android API: `26`
- Compile/target SDK: `34`

### Signing and official verification

- Android build-tools verifier: `34.0.0`
- APK Signature Scheme v2: passed
- Number of signers: `1`
- Continuity signer certificate SHA-256: `d92dae5e61910fae171af41f615c00685e5e3e6909512f2a0429df0805db8f76`
- Signer key: RSA 2048-bit
- Package/version identity: passed
- ZIP integrity: passed
- Supported page-aware alignment check: passed
- Controlled packaged asset equality: passed

The release contains the APK, its checksum file, and the complete official verification report. The signing keystore, signing properties, passwords, private key, temporary binary delta, and operational publication payload were not committed to public `main`.

### Acceptance boundary

RC1 is an official pre-release artifact, but it has not yet replaced the previously accepted field APK. Installation, update-in-place continuity, Samsung SM-T500 behavior, EpicEFI Mega144H7 USB behavior, and physical BLE queue behavior require separately authorized physical acceptance.

The ECU interface remains strictly read-only.

## Allowlisted paths

The initial export includes:

- `app/`
- `gradle/`
- `tools/`
- `.github/workflows/android-quality.yml`
- root Gradle configuration files
- `build-jz.sh`

Public-facing README, architecture, build, security, contribution, export-policy, provenance, and licensing-status documents were prepared separately for this public repository. The initial public Git import excludes the binary Gradle wrapper JAR and inherited logo PNG artwork pending separate provenance review; CI provisions Gradle 8.2 directly and a neutral vector placeholder supplies the referenced splash/icon resource.

## Excluded material

The public source import excludes private signing material and properties, private continuity/handoff documents, assistant-specific instructions, internal audit/history documents, release-management internals, generated build output, personal paths, raw vehicle logs, tune/configuration artifacts, and sensitive diagnostics.

Official downloadable binaries are distributed as GitHub Release assets rather than committed to normal source history.

## Validation performed before upload

- export archive checksum verified;
- path/name and content scans for keys, signing files, credentials, personal paths, APKs, logs, and diagnostic evidence;
- Android package/version and manifest reviewed;
- read-only USB transport boundary reviewed;
- dashboard validator passed;
- dashboard reconnect/stale regression suite passed;
- all JavaScript characterization/source-contract tests passed;
- private Android-quality CI passed JVM tests, Android lint, and APK assembly;
- public exact-head Android-quality CI passed the same validation, JVM-test, lint, and debug-build gates;
- official RC1 APK hash, package/version, continuity signer, ZIP integrity, alignment, and controlled packaged assets passed publication verification.
