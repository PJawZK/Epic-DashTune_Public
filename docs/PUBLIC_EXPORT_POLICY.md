# Public export policy

The public repository is populated from the private `PJawZK/EpicDash-JZ` development repository through a curated export rather than a direct history mirror.

## Included

A reviewed export may include:

- Android application source and resources;
- dashboard/Tuner assets and validation tools;
- unit, browser, characterization, authority-contract, and source-contract tests;
- text Gradle/build configuration;
- public architecture, build, safety, contribution, provenance, and release documentation;
- reproducible release metadata that does not expose private signing material.

## Excluded

The public export must exclude:

- keystores, passwords, aliases, tokens, API keys, signing properties, and encoded private-key material;
- `local.properties`, machine-specific paths, caches, build outputs, APKs/AABs in normal source history, and untracked artifacts;
- private chat continuations, operational handoffs, internal audit/history, and assistant-specific instructions;
- raw vehicle logs/recordings, personal data, and sensitive diagnostic evidence;
- private release-management material that would weaken signing or operational security;
- inherited private PNG logo artwork and the binary Gradle wrapper JAR until their provenance/licensing review permits publication.

## Required checks before each import

1. Bind the candidate export to an exact private application-source commit.
2. Scan exported names and contents for secrets, credentials, signing material, personal paths, raw logs, generated binaries, and sensitive evidence.
3. Preserve documented public-only substitutions such as the neutral vector logo.
4. Confirm package/version identity and the actual ECU capability boundary represented by the source.
5. Run the public validators, Tuner authority/runtime tests, JVM tests, Android lint, and assembly.
6. Review the complete public diff before merging.
7. Record private source identity, export identity, transformations/exclusions, public CI evidence, and release artifact identity.

## ECU/tuning safety documentation

Public documentation must describe the actual capability of the exported source. Current 1207 source is **not ECU read-only**.

The public source may contain guarded native tuning capability only while preserving these invariants:

- USB transport ID is not ECU identity;
- live complete TunerStudio signature drives firmware identity;
- exact genuine generated-INI full-signature match is required before tune authority;
- `mainController.ini` remains structural/schema/menu authority;
- complete verified native TuneSnapshot remains tune-value authority;
- WebView/UI requests are semantic and never raw production transport writes;
- native writes are bounded and require exact acknowledgement/read-back plus full expected-snapshot verification;
- Burn is separate and explicit;
- offline/detached state cannot write or Burn;
- unknown/ambiguous/profileless hardware fails closed.

A future export that changes these rules requires explicit safety review and new qualification evidence.

## Provenance

Each public source import states the exact private application-source commit from which it was derived, records excluded paths/substitutions, and records checks actually executed. Public `main` is authoritative for public users; private operational history is preserved privately and is not required to build or understand the public source.
