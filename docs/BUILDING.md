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

## Validation reporting

Report each command as passed, failed, unavailable, or not run. A missing SDK, unavailable dependency cache, or blocked network is an environment limitation—not a passed build.

## Initial binary-export note

The first public import excludes the Gradle wrapper JAR and inherited logo PNGs while their binary and artwork provenance is reviewed. CI installs Gradle 8.2 directly, and a neutral vector placeholder keeps the Android resource tree buildable.
