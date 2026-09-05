# Nexus — Implementation Plan

> Single source of truth for Nexus rebuild progress. Work only on `main`. CI is the verification gate for every implementation batch.

## Current implementation status

### 12 AI Provider System
- 🔄 Multi-provider abstraction
- 🔄 Model selection
- 🔄 Streaming
- 🔄 Gemini / OpenRouter / DeepInfra / LiteLLM provider support
- ✅ Per-provider encrypted API key management
- 🔄 Token usage/context windows
- ⬜ Provider errors/fallback

### 15 Edit / Patch Engine
- 🔄 AI file change pipeline
- 🔄 Diff/change presentation
- 🔄 File change tracking
- 🔄 Added/removed line counts
- 🔄 Tap changed file → editor
- ⬜ Patch planner
- ⬜ Conflict detection
- ✅ Snapshot/rollback foundation
- 🔄 Validation/apply/undo

### 17 AI Coding Agent
- 🔄 Workspace AI chat
- 🔄 ChatGPT-style chat UI
- 🔄 AI working/streaming animation
- 🔄 Pause/cancel AI response
- 🔄 AI workspace file operations
- ✅ AI permission enforcement: Never / Some / Autonomous
- 🔄 Full inspect → plan → approve → edit → test → build → verify loop
- 🔄 Autonomous engineering workflows

## Hard constraints

1. Work only on `main`.
2. Do not add local Android SDK/NDK/JDK/Gradle build infrastructure to V1.
3. GitHub Actions is the V1 build machine.
4. AI file changes must flow through permissions, validation/diff, and recoverability mechanisms.
5. Never expose secrets to AI context or normal logs.
6. Every implementation batch must be verified by CI; do not abandon CI runs.

## Recently completed work

- ✅ Workspace AI chat entry point
- ✅ ChatGPT-style AI chat interface direction
- ✅ Single AI permission setting: Never / Some / Autonomous
- ✅ AI can operate across Workspace files (architecture in place)
- ✅ AI change tracking: created/modified/deleted files and line counts
- ✅ Changed-file navigation into editor
- ✅ AI response pause/cancel capability
- ✅ Chat attachments and context toggles
- ✅ Context Inspector
- ✅ Animated AI working state
- ✅ GitHub token storage and GitHub sync foundation
- ✅ Unsupported GitHub file-type protection
- ✅ Terminal entry foundation
- ✅ Terminal sessions, history and cancellation stabilization
- ✅ Nexus app logo integration
- ✅ Settings simplification and model selector consolidation
- ✅ Editor syntax highlighting for supported programming languages
- ✅ Git status/diff/stage/unstage/commit/change-summary operations
- ✅ Git branch listing and safe workspace checkout
- ✅ Git commit history/log
- ✅ GitHub pull request service and management UI
- ✅ GitHub Checks API service and ChatGPT-style Checks screen
- ✅ GitHub issue service and ChatGPT-style Issues UI
- ✅ GitHub Releases REST service and ChatGPT-style Releases screen
- ✅ GitHub Actions run workspace, live tracking, logs, rerun/cancel controls
- ✅ ChatGPT-style GitHub Actions build/details screen
- ✅ GitHub workspace navigation links Releases and Actions
- ✅ Android APK/AAB artifact management
- ✅ Deterministic CI failure intelligence
- ✅ Advanced Git operations hardening
- ✅ Git merge conflict preview/resolution workflow
- ✅ AI patch execution with validation and snapshot-backed rollback
- ✅ Focused AI provider settings: Gemini, OpenRouter, DeepInfra, LiteLLM
- ✅ Lazy pagination for GitHub issues and comments
- ✅ AI action payload parsing hardening and JVM test-runtime compatibility
- ✅ Dedicated AI Providers screen with provider/model/key management
- ✅ Floating AI workspace entry point
- ✅ Dedicated Git credentials screen with HTTPS, GitHub token and SSH credential management
- ✅ GitHub token removed from general Settings
- ✅ Deterministic `NexusAgentWorkflow` state machine
- ✅ Engineering workflow lifecycle tests covering autonomous, approval and failure paths
- ✅ Engineering workflow runner bridging workspace inspection, AI actions, validation, GitHub Actions build and verification stages
- 🔄 GitHub repository discovery and Actions build/artifact foundation

## Next priority

1. ⬜ Connect Workspace AI chat and AI actions to `NexusEngineeringWorkflowRunner` so engineering tasks actually enter the lifecycle from the user-facing AI surface.
2. ⬜ Complete provider error/fallback handling and token/context accounting.
3. ⬜ Finish patch planner and conflict detection for AI workspace edits.
4. ⬜ Complete the end-to-end Git credential path: credential selection → clone/pull/push → GitHub Actions integration.

_Last updated: 2026-09-05 — Recorded successful Nexus Android CI runs 416 and 417; CI 417 verified the required plan update after the engineering workflow runner passed._
