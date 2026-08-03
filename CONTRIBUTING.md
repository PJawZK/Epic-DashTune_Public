# Contributing to Epic DashTune

Thank you for helping improve Epic DashTune.

## Current project boundary

The public application is currently read-only with respect to the ECU. Contributions must not add tune writes, burns, output control, virtual-input writes, or generic raw-command paths.

Preserve these rules:

- keep package identity stable unless a migration is explicitly planned;
- preserve raw ECU-reported values;
- keep communication, logging, state ownership, and safety validation independent from dashboard rendering;
- do not commit keys, credentials, signing properties, personal paths, APKs, raw vehicle recordings, or sensitive diagnostics;
- avoid unrelated refactors in bug-fix pull requests;
- document unavailable checks as unavailable, never as passed.

## Development workflow

1. Open or reference an issue that describes the problem or bounded improvement.
2. Work on a focused branch.
3. Add or update tests for changed behavior.
4. Run the repository validators, JVM tests, Android lint, and APK assembly when available.
5. Describe the changed files, tests executed, build result, safety impact, and remaining uncertainty in the pull request.

## UX experiments

Large visual experiments, speculative interactions, and prototypes belong in the separate UX project until they are selected for production integration. Production pull requests should include clear acceptance criteria and preserve quick-glance usability.

## Future tuning capability

Proposals involving ECU writes require a separate architecture and safety review before implementation. No WebView, custom math, layout, script, or plugin path may construct raw ECU writes.

## Licence

Do not add third-party code, artwork, fonts, or assets unless their licence and attribution are compatible with the eventual repository licence and recorded in the pull request.
