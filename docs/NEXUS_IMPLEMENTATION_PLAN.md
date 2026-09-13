# Nexus Implementation Contract

This repository follows the supplied Nexus implementation blueprint.

## Non-negotiable rules

1. Work on `main` only.
2. Inspect the current repository before changing code.
3. Implement one coherent task at a time.
4. Never assume a documented feature is implemented.
5. Preserve working functionality unless the task explicitly replaces it.
6. Destructive operations require explicit confirmation.
7. Significant workspace mutations require snapshots/rollback.
8. AI emits typed actions; unrestricted arbitrary execution is not permitted.
9. Heavy Android builds/tests run through GitHub Actions.
10. CI is a completion gate; validation must never be weakened to make CI green.
11. Stop on ambiguous requirements or security-sensitive architectural decisions.
12. Keep Android/mobile constraints in scope for every feature.

## Definition of done

A task is complete only when applicable requirements, implementation, tests, static checks, Android build, CI, acceptance behavior, security/privacy review, documentation, and a coherent commit have been addressed.

## Workstream order

- P0 Repository foundation
- P1 Workspace engine
- P2 Structured action system
- P3 AI provider layer
- P4 Agent engine
- P5 Patch engine
- P6 Git
- P7 GitHub
- P8 Cloud build system
- P9 Controlled terminal
- P10 Code editor
- P11 Nexus UI/UX
- P12 Permissions and safety
- P13 Offline/reliability
- P14 Security/privacy
- P15 Testing
- P16 Release hardening

## Initial execution rule

The actual repository state is authoritative for what is implemented. Source specifications define intended behavior. When state and requirements conflict, record the conflict instead of silently inventing behavior.

The first milestone is repository/architecture verification and a minimal durable vertical slice. Feature breadth follows only after the foundation passes CI.
