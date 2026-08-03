# Source provenance

## Initial public import

- Private source repository: `PJawZK/EpicDash-JZ`
- Exact private `main` commit exported: `6d705d5ae3078a2ade377c51e8fe10b680374f1a`
- Accepted application-source baseline represented by that tree: `13797491070fc32fd18c2bc52e137130a129ce5a`
- Temporary export-workflow branch head: `6ded39dda336ef2c41ab84dc6225655b7b57ef0d`
- Export workflow run: `30815441574`
- Exact export-head Android-quality run: `#434` (`30815441856`), successful
- Export artifact ID: `8856516144`
- Public pre-provenance application tree: `bd5564ffb171b8566ffa3122210273aed67dc52e`
- Public Android-quality run on that application tree: `#6` (`30822871731`), successful
- Final public exact head after provenance/manifest finalization: `232b405b7d2061996f5c47672f196635a667b8ad`
- Final public Android-quality run: `#8` (`30823533093`), successful

The temporary branch added only an export workflow. Application source was taken from the exact private `main` tree above.

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

The import excludes private signing material and properties, private continuity/handoff documents, assistant-specific instructions, internal audit/history documents, release-management internals, generated APKs/build output, personal paths, raw vehicle logs, tune/configuration artifacts, and sensitive diagnostics.

## Validation performed before upload

- export archive checksum verified;
- path/name and content scans for keys, signing files, credentials, personal paths, APKs, logs, and diagnostic evidence;
- Android package/version and manifest reviewed;
- read-only USB transport boundary reviewed;
- dashboard validator passed;
- dashboard reconnect/stale regression suite passed;
- all JavaScript characterization/source-contract tests passed;
- private Android-quality CI passed JVM tests, Android lint, and APK assembly;
- final public exact-head Android-quality CI passed the same validation, JVM-test, lint, debug-build, and artifact-upload gates.
