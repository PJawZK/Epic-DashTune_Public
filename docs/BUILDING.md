# Building Epic DashTune

## Required toolchain

- JDK 17
- Gradle 8.2 installed locally (public CI provisions the exact version)
- Android SDK platform 34
- Android build tools 34.0.0 or compatible SDK tooling
- Android Gradle Plugin 8.2.0

The project supports Android API 26 and newer.

The curated public tree intentionally omits `gradle-wrapper.jar`, so use an installed/provisioned Gradle 8.2 rather than relying on `./gradlew` in this repository.

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

gradle --no-daemon testDebugUnitTest
gradle --no-daemon lintDebug
gradle --no-daemon assembleDebug
```

The debug APK is normally:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Signing distinction

No signing key, private key, password, or signing properties file is stored in this repository.

Without an external signing-properties file, Android uses the ordinary debug signing configuration. Such a build can be useful for compatibility testing but **must not be assumed to update a continuity-signed Epic DashTune installation in place**.

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

## Current 0.12.5 RC1 compatibility line

The source identity is:

- package: `com.buttonbox.ble.jz`
- version: `0.12.5-tuner-live-lazy-jz` (`1207`)
- private application-source baseline: `9de5f4129cddc75692b39b84069f658c6a92ce61`

The final GitHub release record and checksum asset are authoritative for the exact distributed `v0.12.5-rc.1` APK. Verify the release APK against its published SHA-256 before testing.

Because this release is primarily for wider hardware/INI compatibility evidence, the release notes must state whether its signer matches the historical continuity signer. Do not infer signer continuity from the unchanged package ID.

## Historical 0.11.12 RC1

`v0.11.12-rc.1` remains available in release history. Its documented continuity signer certificate SHA-256 is:

`d92dae5e61910fae171af41f615c00685e5e3e6909512f2a0429df0805db8f76`

That historical signer fact does not automatically apply to later compatibility-test artifacts.

## Safety note

1207 is not ECU read-only. A compatibility tester who does not intend to tune should avoid editing values and avoid Burn. Production tune mutation remains guarded by exact firmware/profile identity, complete TuneSnapshot authority, native semantic target resolution, bounded write, exact acknowledgement/read-back and full-snapshot verification.

## Validation reporting

Report each command as passed, failed, unavailable, or not run. A missing SDK, unavailable dependency cache, or blocked network is an environment limitation—not a passed build.

## Public binary/artwork exclusions

The public source excludes the binary Gradle wrapper JAR and inherited private PNG logo artwork pending provenance review. CI installs Gradle 8.2 directly, and the public neutral vector placeholder keeps the Android resource tree buildable.
