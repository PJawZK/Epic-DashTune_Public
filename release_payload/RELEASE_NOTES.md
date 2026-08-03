# Epic DashTune 0.11.12 RC1

This is the first official Epic DashTune release candidate.

## Application identity

- Android package: `com.buttonbox.ble.jz`
- Installed version name: `0.11.12-stale1-jz`
- Version code: `1114`
- Minimum Android version: API 26
- ECU capability: strictly read-only

The legacy package, version string, and installed application label are intentionally retained for update continuity in this first release candidate. Product-facing releases use the **Epic DashTune** name.

## Source and build provenance

- Private authoritative repository: `PJawZK/EpicDash-JZ`
- Private source commit: `6d705d5ae3078a2ade377c51e8fe10b680374f1a`
- Accepted application-source baseline: `13797491070fc32fd18c2bc52e137130a129ce5a`
- Exact CI assembly head: `15aac93bf8d8cb385bd2cd3eaf6dd0fd43b63af5`
- Private Android-quality run: `30794624036` (#431)
- Public curated-source tag target: `24ace519ab249178f00972828b7bf20ac2df5580`

The release APK was reconstructed byte-for-byte from the previously prepared continuity-signed candidate and then verified in GitHub Actions using the official Android build-tools `apksigner` command before publication.

## Safety boundary

The application reads ECU runtime/output-channel data. It does **not** write tune pages, burn changes, control outputs, issue virtual-input commands, update firmware, or expose a generic raw ECU-command path.

## Candidate status

This is a GitHub **pre-release**, not a production/field-accepted release. The APK has not been installed or physically accepted on the SM-T500/Mega144H7 as part of this publication gate. The previously accepted PR #34 field APK remains the accepted installed baseline until a separate, explicitly authorized RC acceptance test succeeds.

## Installation continuity

The APK is signed with the established EpicDash JZ continuity certificate and is intended to support update-in-place installation over an existing continuity-signed installation. Do not uninstall the existing application or clear its data to work around an installation problem.

## Public-source distinction

The public repository is a curated export. Its initial source tree omits the binary Gradle wrapper JAR and inherited PNG logo artwork pending provenance review, while this APK represents the current private application build. See `SOURCE_PROVENANCE.md` and `LICENSE_STATUS.md` in the repository.

## Included assets

- `Epic-DashTune-0.11.12-rc.1.apk`
- `Epic-DashTune-0.11.12-rc.1.apk.sha256`
- `Epic-DashTune-0.11.12-rc.1-verification.txt`
