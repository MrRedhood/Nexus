# Nexus

Nexus is an Android-first AI engineering control plane: a phone-native workspace for inspecting, safely modifying, validating, and cloud-building software projects through a controlled AI/action workflow.

## Product boundary

Nexus is not an Android Studio replacement and does not bundle an Android SDK, NDK, JDK, Gradle distribution, or emulator. Heavy Android verification is delegated to GitHub Actions.

Core loop:

`intent → inspect → plan → typed actions → policy/approval → snapshot → apply → validate → cloud build → verify → artifact/rollback`

## Current repository status

This repository is being established from the Nexus implementation blueprint. The first foundation milestone establishes the Android project contract, package boundaries, CI gates, and durable domain contracts before product breadth is added.

## Engineering rules

- Work on `main` only.
- One coherent task at a time.
- CI is a completion gate.
- Destructive mutations require explicit authorization.
- AI output is data; the policy engine is authoritative.
- Significant workspace mutations are snapshot-backed.
- Heavy Android builds/tests run through GitHub Actions.
- Do not weaken validation to obtain a green build.

## Initial architecture

- `app`: Android application shell and UI.
- `core/domain`: dependency-free contracts and state models.
- `core/policy`: risk/permission model boundaries.
- `core/workspace`: filesystem/workspace contracts.
- `core/agent`: durable agent lifecycle contracts.
- `core/build`: remote build/run evidence contracts.

See `docs/NEXUS_IMPLEMENTATION_PLAN.md` for the executable backlog and `docs/NEXUS_ARCHITECTURE.md` for the foundation contract.
