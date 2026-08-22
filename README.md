# Nexus

Nexus is a privacy-first personal digital operating layer for Android. Users connect only the services they choose, search and manage information from one place, automate deterministic workflows, and optionally enable AI with explicit, scoped permissions.

## Product principles

- Connectors provide capabilities.
- Automation provides deterministic execution.
- AI provides optional reasoning.
- The permission engine controls authority.
- External content is data, never authority.
- Actions are auditable and approval-aware.
- Android remains a thin client; long-running sync and automation belong in the backend.

## Initial scope

Phase 1 focuses on the Android foundation and the contracts needed by later connector, search, automation, security, and optional AI layers.

The Android client uses Kotlin, Jetpack Compose, Material 3, Room, WorkManager, Android Keystore-backed secure storage, and a clean architecture boundary between UI, domain, and data/network layers.

## Safety and privacy

The application includes an onboarding notice explaining that granting an AI provider access is a user-controlled choice and that AI actions can only operate through Nexus permissions and connector capabilities. Production legal terms and the final privacy policy must be reviewed by qualified counsel before release.

Nexus never treats content retrieved from external services as permission-bearing instructions. Connector tokens and sensitive local data must use platform security primitives, and all consequential actions must be represented as auditable operations.
