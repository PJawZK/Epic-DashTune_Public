# Security policy

## Current safety scope

Epic DashTune is currently strictly read-only with respect to the ECU. A security or safety report is especially important if behavior could:

- transmit ECU writes, burns, output commands, or virtual-input commands;
- bypass package or signing continuity;
- expose credentials, signing material, personal paths, or private vehicle evidence;
- accept stale ECU/session identity as current;
- misrepresent raw ECU values in a safety-relevant display;
- permit untrusted dashboard content to construct transport commands.

## Reporting

Please do not publish exploitable details, credentials, private keys, or sensitive vehicle data in a public issue. Contact the repository owner privately through GitHub first and provide a minimal reproduction, affected version or commit, and expected impact.

## Supported state

Until the first public source release is tagged, only the current default branch is considered for security review. Public APK releases will identify their exact source commit, package, version, checksum, and signing expectations.

## ECU-write boundary

Future write capability is not part of the current supported application. It must remain behind a separate native tuning core with a typed allowlist, explicit safety state machine, fresh ECU and tune identity gates, read-back verification, explicit RAM-write and burn separation, backup/recovery procedures, and staged physical acceptance.
