# Nexus — Implementation Plan

> Single source of truth for Nexus rebuild progress. Work only on `main`. CI is the verification gate for every implementation batch.

## Status legend
- ✅ Completed
- 🔄 In progress
- ⬜ Remaining

## Current implementation status

### 01 Foundation
- ⬜ Android project / Compose foundation
- ⬜ Navigation architecture
- ⬜ Theme/design system
- ⬜ Dependency injection
- ⬜ Networking
- ⬜ Local database
- ⬜ Secure storage
- ⬜ Logging/error handling
- ⬜ Account/backend authentication

### 02 Project / Workspace
- ⬜ Project management
- ⬜ Workspace abstraction
- ⬜ Local/remote workspace support
- ⬜ File tree and metadata
- ⬜ Recent/favorite files
- ⬜ Unified FileSystem abstraction

### 03 File System
- ⬜ list/read/write/create/delete/rename/move/exists/search/metadata
- ⬜ GitHub-backed file operations
- ⬜ Workspace-wide AI file access
- ⬜ AI create/modify/delete files and folders

### 04 Editor
- ⬜ Full mobile code editor
- ✅ Syntax highlighting for supported languages
- ⬜ Tabs/file tree
- ⬜ Search/replace
- ⬜ Undo/redo
- ⬜ Selection/editor actions
- ⬜ Diagnostics/error markers
- ⬜ Code folding/minimap where practical
- ⬜ LSP/autocomplete/definitions/references later

### 05 Search / Indexing
- ⬜ Filename/text/regex search
- ⬜ Symbol search
- ⬜ Workspace index
- ⬜ Project graph
- ⬜ Dependency/symbol extraction
- ⬜ Semantic search later

### 06 Git
- ⬜ Status
- ⬜ Diff
- ⬜ Stage/unstage
- ⬜ Commit
- ⬜ Branch/checkout
- ⬜ Log
- ⬜ Stash
- ⬜ Merge/conflict resolution
- ⬜ Reset/cherry-pick
- ⬜ Change summary with files and +/- line counts

### 07 GitHub Connector
- 🔄 GitHub repository synchronization
- 🔄 GitHub token/credential storage
- 🔄 Fetch/push/commit from Nexus
- ⬜ GitHub App architecture with granular permissions
- ⬜ Repository list/search/connect/create/fork
- ⬜ Branch operations
- ⬜ Pull requests
- ⬜ Issues
- ⬜ Actions
- ⬜ Checks
- ⬜ Webhooks
- ⬜ Secure short-lived installation tokens

### 08 GitHub Actions / Cloud Build
- ⬜ Workflow detection
- ⬜ Workflow generator
- ⬜ Android Debug APK workflow
- ⬜ Release APK workflow
- ⬜ AAB workflow
- ⬜ Build profiles
- ⬜ Dispatch/tracking
- ⬜ Live logs
- ⬜ Build history
- ⬜ Artifact download/metadata
- ⬜ Build failure intelligence

### 09 Build / Artifact System
- ⬜ Build records
- ⬜ APK/AAB artifacts
- ⬜ Install/download/share
- ⬜ Commit/branch/variant/size/run/checksum metadata
- ⬜ Artifact retention/management

### 10 Terminal
- ✅ Terminal entry/UI
- ✅ Persistent sessions
- ✅ Command input/output streaming
- ✅ Command history
- ✅ Cancellation
- ✅ Local/remote/CI provider abstraction

### 11 Tests / Diagnostics
- ⬜ Test discovery
- ⬜ CI test execution
- ⬜ Test results/history
- ⬜ Failure extraction
- ⬜ Lint/static analysis
- ⬜ Diagnostics integration

### 12 AI Provider System
- 🔄 Multi-provider abstraction
- 🔄 Model selection
- 🔄 Streaming
- ⬜ Gemini/OpenAI/Claude/OpenRouter/DeepInfra/LiteLLM/custom providers
- ⬜ API key management
- ⬜ Token usage/context windows
- ⬜ Provider errors/fallback

### 13 AI Context Engine
- 🔄 AIContextService pipeline
- 🔄 Context Inspector
- 🔄 Attachments
- 🔄 Workspace file picker
- 🔄 Current file / selection / Git diff / terminal output / workspace summary toggles
- 🔄 Token limits/truncation
- ⬜ Workspace indexing/relevance ranking
- ⬜ Project graph/context integration

### 14 AI Tools
- 🔄 read_file/search_file/search_code
- 🔄 create_file/edit_file/delete_file/rename_file
- 🔄 Git status/diff tools
- 🔄 Terminal/build/test/log tools
- ⬜ Commit/push/create PR tools with permissions
- ⬜ Tool approval/permission engine

### 15 Edit / Patch Engine
- 🔄 AI file change pipeline
- 🔄 Diff/change presentation
- 🔄 File change tracking
- 🔄 Added/removed line counts
- 🔄 Tap changed file → editor
- ⬜ Patch planner
- ⬜ Conflict detection
- ⬜ Snapshot/rollback
- ⬜ Validation/apply/undo

### 16 Task Engine
- ⬜ Structured AI task plans
- ⬜ Planned → Approved → Running → Validating → Completed
- ⬜ Failed → Retry / AI investigation
- ⬜ Task history

### 17 AI Coding Agent
- 🔄 Workspace AI chat
- 🔄 ChatGPT-style chat UI
- 🔄 AI working/streaming animation
- 🔄 Pause/cancel AI response
- 🔄 AI workspace file operations
- 🔄 AI permission modes: Never / Some / Autonomous
- ⬜ Full inspect → plan → approve → edit → test → build → verify loop
- ⬜ Autonomous engineering workflows

### 18 Debugging Agent
- ⬜ Build failure → log extraction → relevant code → Git history → hypothesis
- ⬜ Proposed fix
- ⬜ Test/build verification
- ⬜ Persistent debug sessions
- ⬜ Build → debug → fix loop

### 19 Parallel AI / Agents
- ⬜ Agent registry
- ⬜ Analyst/Implementer/Reviewer/Tester/Security/Build Engineer/Researcher roles
- ⬜ Independent contexts
- ⬜ Parallel execution
- ⬜ Proposal/result merge
- ⬜ Consensus/review
- ⬜ Conflict handling
- ⬜ Agent sandbox/permissions

### 20 Memory
- ⬜ Project memory
- ⬜ Workspace memory
- ⬜ Task memory
- ⬜ Architecture decisions
- ⬜ Known issues
- ⬜ Important files
- ⬜ Global user preferences

### 21 GitHub Engineering
- ⬜ PR creation/review/comments/merge/close
- ⬜ Issue creation/edit/comment/close
- ⬜ Release creation
- ⬜ Changelog generation
- ⬜ CI/artifact linking

### 22 Advanced Builds
- ⬜ Flavors/variants
- ⬜ Signing configuration
- ⬜ Release builds
- ⬜ Environment variables
- ⬜ Secrets configuration
- ⬜ Build matrix
- ⬜ Custom CI scripts
- ⬜ Secret protection from AI context/logs

### 23 Advanced Automation
- ⬜ Global command bar
- ⬜ Natural-language deterministic commands
- ⬜ Automated build-repair workflows
- ⬜ Workflow templates
- ⬜ Notifications

### 24 Connector Marketplace
- ⬜ Connector protocol
- ⬜ Marketplace/discovery/install/uninstall
- ⬜ Versioning/update
- ⬜ Permissions/reviews
- ⬜ Developer SDK
- ⬜ Validation/signing/security scanning

### 25 Ecosystem
- ⬜ Nexus Connector SDK
- ⬜ Nexus Agent SDK
- ⬜ Nexus Tool SDK
- ⬜ Nexus Workflow Template SDK
- ⬜ Third-party developer ecosystem

## V1 Definition of Done

- ⬜ Project management
- ⬜ Workspace
- ⬜ File tree
- ⬜ Editor
- ⬜ Search
- ⬜ Git
- ⬜ Terminal
- ⬜ GitHub authentication/repository selection
- ⬜ Branches/commits/PRs/issues/Actions/artifacts
- ⬜ Android workflow + Debug APK + Release APK + AAB
- ⬜ Build logs/history/artifacts
- ⬜ Multi-provider AI
- ⬜ Project context/code search/tool calling
- ⬜ File editing/diff/approval/rollback
- ⬜ Task plans
- ⬜ Coding/build/debug/reviewer agents
- ⬜ Parallel agents
- ⬜ Permission engine + activity log + secret protection
- ⬜ Project/task/architecture/global memory

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

## Next priority

1. ⬜ Complete Git first-class operations.
2. ⬜ Complete GitHub connector/Actions operations.
3. ⬜ Build cloud APK/AAB pipeline.
4. ⬜ Complete AI tool permissions + patch/snapshot/rollback.
5. ⬜ Complete task/coding/debug agents.

_Last updated: 2026-08-26_
