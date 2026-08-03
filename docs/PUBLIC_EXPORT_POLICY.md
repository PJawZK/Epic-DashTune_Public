# Public export policy

The public repository is populated from the private EpicDash JZ development repository through a curated export rather than a direct mirror.

## Included

The audited source import may include:

- Android application source and resources;
- Gradle wrapper and build configuration;
- dashboard assets and validation tools;
- unit, browser, characterization, and source-contract tests;
- public architecture, build, safety, and contribution documentation;
- reproducible release metadata that does not expose private signing material.

## Excluded

The public export must exclude:

- keystores, passwords, aliases, tokens, API keys, signing properties, and encoded key material;
- local.properties, machine-specific paths, caches, build outputs, APKs, and untracked artifacts;
- private chat continuations, internal operational handoffs, and assistant-specific instructions;
- raw vehicle logs, recordings, personal data, and sensitive diagnostics;
- private release-management details that would weaken signing or operational security;
- experimental UX material maintained in the separate UX project.

## Required checks before each import

1. Compare the candidate export against the exact private source commit.
2. Scan names and contents for secrets, credentials, personal paths, and sensitive evidence.
3. Confirm inherited source and asset licensing before adding a repository-wide licence grant.
4. Verify the Android package, version identity, read-only boundary, and build configuration.
5. Run public validators, tests, lint, and assembly where available.
6. Review the complete diff before merging.

## Provenance

Each source-import pull request should state the exact private source commit from which it was derived, describe excluded paths or transformations, and record the checks actually executed. The public commit is authoritative for public users; private operational history is not required to build or understand the public application.
