# Building Epic DashTune

## Required toolchain

- JDK 17
- Gradle 8.2 installed locally (CI provisions the exact version)
- Android SDK platform 34
- Android build tools 34.0.0
- Android Gradle Plugin 8.2.0
- Kotlin 1.9.0

The project supports Android API 26 and newer.

## Local setup

Create an untracked `local.properties` pointing to your Android SDK when Android Studio has not already created it:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Then run:

```bash
python3 tools/validate-dashboard.py
python3 tools/test-dashboard-regressions.py

for test in app/src/test/js/*.js; do
  node "$test"
done

gradle testDebugUnitTest
gradle lintDebug
gradle assembleDebug
```

The resulting debug APK is normally:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Signing distinction

No signing key or password is stored in this repository.

Without an external signing properties file, Android uses an ordinary debug key. Such builds are suitable for development but cannot update a maintainer continuity-signed installation in place.

The Gradle project can read an external properties file through either:

```bash
gradle -PepicdashSigningProperties=/absolute/path/to/properties assembleDebug
```

or:

```bash
export EPICDASH_SIGNING_PROPERTIES=/absolute/path/to/properties
gradle assembleDebug
```

Do not commit that properties file or its referenced keystore.

## Official distributed RC1

The canonical maintainer-distributed release candidate is available from [GitHub Release `v0.11.12-rc.1`](https://github.com/PJawZK/Epic-DashTune_Public/releases/tag/v0.11.12-rc.1).

- APK: `Epic-DashTune-0.11.12-rc.1.apk`
- SHA-256: `8747d162b426ce94f517750fa37907512bdfaeba81baf12098850dc1e3a3c5c2`
- Package: `com.buttonbox.ble.jz`
- Version: `0.11.12-stale1-jz` (`1114`)
- Continuity signer certificate SHA-256: `d92dae5e61910fae171af41f615c00685e5e3e6909512f2a0429df0805db8f76`

The GitHub Release also contains a checksum file and the complete official Android build-tools verification report.

A locally assembled debug APK is not the official RC1, even when built from identical source. Do not re-sign, realign, recompress, or otherwise modify the released APK; any byte change invalidates its published SHA-256 and may invalidate update continuity.

To verify a downloaded copy:

```bash
sha256sum -c Epic-DashTune-0.11.12-rc.1.apk.sha256
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose --print-certs Epic-DashTune-0.11.12-rc.1.apk
$ANDROID_HOME/build-tools/34.0.0/zipalign -c -p -v 4 Epic-DashTune-0.11.12-rc.1.apk
unzip -t Epic-DashTune-0.11.12-rc.1.apk
```

Expected signature summary:

- APK Signature Scheme v2: true
- Number of signers: 1
- Signer certificate SHA-256: `d92dae5e61910fae171af41f615c00685e5e3e6909512f2a0429df0805db8f76`

RC1 remains a pre-release until the separately controlled physical acceptance procedure has passed.

## Validation reporting

Report each command as passed, failed, unavailable, or not run. A missing SDK, unavailable dependency cache, or blocked network is an environment limitation—not a passed build.

## Initial binary-export note

The first public import excludes the Gradle wrapper JAR and inherited logo PNGs while their binary and artwork provenance is reviewed. CI installs Gradle 8.2 directly, and a neutral vector placeholder keeps the Android resource tree buildable.
