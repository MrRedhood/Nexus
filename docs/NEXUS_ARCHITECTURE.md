# Nexus Architecture Foundation

## Product boundary

Nexus is an Android-first engineering control plane. Android is the primary control surface; heavyweight Android builds and validation are delegated to GitHub Actions.

Nexus does not bundle Android SDK/NDK/JDK/Gradle/emulator toolchains in the APK.

## Trust boundaries

```text
Android UI
   |
   v
Application orchestration
   |
   +--> Policy broker ---> typed action executor ---> Workspace boundary
   |                                  |                    |
   |                                  |                    +--> local files
   |                                  |
   |                                  +--> Git boundary
   |                                  +--> terminal registry
   |
   +--> AI provider boundary
   |
   +--> GitHub boundary ---> Actions ---> evidence/artifacts
```

Model output is untrusted input. The model cannot select credentials, change policy, escape the workspace root, authorize a destructive action, or directly execute an arbitrary shell command.

## Domain contracts

### Workspace

A workspace has an opaque ID, display name, canonical storage mode, root capability, schema version, and lifecycle state.

Every file operation must use normalized relative paths and enforce the workspace root at the execution boundary. Significant mutations associate an operation with a snapshot.

### Action

Every AI-requested mutation is represented as a typed action with:

- action ID and idempotency key
- schema version
- action type
- target/resource
- expected preconditions and hashes
- risk classification
- required permission
- optional snapshot ID
- lifecycle state
- result/error/evidence

Unknown fields or action types are rejected at the broker boundary.

Initial action families:

- ReadFile
- WriteFile
- CreateFile
- DeleteFile
- MoveFile
- PatchFile
- SearchFiles
- ListDirectory
- Git
- Build
- ControlledTerminal

### Agent lifecycle

```text
Idle
 -> Thinking
 -> Inspecting
 -> Planning
 -> AwaitingApproval
 -> Executing
 -> Validating
 -> Building
 -> Diagnosing
 -> Completed
```

Any active state can transition to `Cancelled` or `Failed` under the relevant cancellation/error policy. Transitions and payloads must be durably persisted so startup reconciliation can recover non-terminal work.

### Build/evidence

A remote run is correlated by workflow, ref, source commit SHA, dispatch key, and exact run ID when available. Uncertain dispatch responses are reconciled instead of blindly retried.

Artifacts are evidence objects, not merely download URLs. They carry run/source identity, digest where available, expiry metadata, and provenance details.

## Foundation package boundaries

```text
com.mrredhood.nexus
  app/
  core/domain/
  core/policy/
  core/workspace/
  core/agent/
  core/build/
```

Domain contracts remain free of Android UI concerns. Android implementations belong at platform boundaries. The policy engine owns risk/authorization decisions; executors do not infer permission from UI state.

## Recovery and reliability

The durable source of truth must survive process death. Non-terminal operations are reconciled on startup. Long-running network work uses Android-appropriate background scheduling rather than assuming an Activity remains alive.

File mutations are atomic where practical and protected by optimistic hash/precondition checks. A stale-read mutation fails closed and exposes conflict information rather than overwriting newer user changes.

## Implementation order

1. Repository/toolchain/CI verification.
2. Android project and module boundaries.
3. Durable domain contracts and serialization.
4. Workspace safety boundary and journal/snapshot primitives.
5. Policy broker and typed actions.
6. Agent state persistence.
7. Android UI shell.
8. Provider/Git/GitHub adapters.
9. Cloud build evidence.
10. Feature expansion only after the vertical slice passes CI and acceptance tests.
