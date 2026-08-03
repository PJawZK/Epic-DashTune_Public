# Epic DashTune

Epic DashTune is an Android dashboard, diagnostics, logging, playback, and tuning-analysis platform for EpicEFI systems.

> **Current safety boundary:** the application is strictly read-only with respect to the ECU. It does not write tune data, burn changes, control outputs, or send virtual-input commands.

This public repository is being prepared from the separately maintained private development repository through a curated export. Source, tests, build tooling, public architecture documentation, and reproducible release information will be added through reviewed pull requests.

The experimental UX project remains separate so interface prototypes can evolve without being mistaken for supported production behavior.

## Current public-import status

- Repository initialized.
- Curated source import in preparation.
- Signing keys, credentials, private operational handoffs, personal paths, raw vehicle evidence, and internal-only diagnostics are excluded.
- Licence and inherited-source attribution are being verified before a licence grant is attached to the imported code.

## Project identity

- Product name: **Epic DashTune**
- Android package retained for update continuity: `com.buttonbox.ble.jz`
- Minimum Android: API 26
- Current ECU capability: read-only

No APK is published from this repository yet. Build and release instructions will be included with the first reviewed source import.
