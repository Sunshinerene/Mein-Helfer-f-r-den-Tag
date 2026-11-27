# POC-S1 – Sunny Standalone & Connect
Status: contract prepared; execution requires separate Chat 00 GO. Priority: early installable isolated development APK. This is not a production extraction.

## Baseline and ownership
Start from approved integration fd6f4ac plus roadmap documentation commits only; verify exact HEAD and clean worktree before branching. Branch codex/sunny-standalone-poc, no overwrite. Read AGENTS.md, PROJECT_RULES, PROJECT_STATUS, SUNNY_ARCHITECTURE, CODEX_WORKPACKAGES, FILE_OWNERSHIP and ROADMAP-2026-11-27.
Allowed new paths: sunny-standalone-poc/** only, including its own Gradle settings/build, source, resources, tests and report. Existing app, core, runtime, schemas, root build, workflows and security files read-only. Reuse accepted sunny-core via standalone composite build if feasible; do not copy/fork core. No reliance on unmerged 6B.2 or security branch. If dependency wiring needs other files, stop and report. No merge, release, publishing or main changes.

## Deliver in stages
S1.1 Inventory actual existing Sunny functions, file references, dependencies, assets, data owners and permissions. Classify core/general, care-specific, host adapter, UI/asset, security-sensitive. Mark confirmed vs proposed; map every capability to retain/reuse/defer. Do not remove anything from MeinHelfer.
S1.2 Standalone Android development shell: proposed package de.meinhelfer.sunny.poc (check uniqueness), label Sunny AI DevTools, own sandbox, clearly marked experimental UI; local text input, response/status, reset synthetic session, help and capability list. Integrate accepted core and deterministic fallback. No real model required; explicit simulated runtime label, no misleading intelligence claims. Use existing avatar asset only if provenance/rights/path verified; keep appearance unchanged; otherwise neutral placeholder clearly labeled. No new asset generation. Offline launch and function. No Internet, overlay, accessibility service, contacts, health, storage-wide, launcher role or other sensitive permissions.
S1.3 Define minimal versioned capability descriptor (stable ID/version, owner, input/output schema, permission, sensitivity, offline flag, help, dependencies) and in-process mock Connect transport. Deny by default, unknown capabilities/versions/invalid inputs rejected, model suggestions never execute actions. Synthetic MeinHelfer adapter for read-only today tasks/appointments, no connection to real app, no direct DB access. Use typed bounded synthetic responses; no personal records. Demonstrate selection, denial and deterministic fallback.
S1.4 Build APK and document results, inventory, architecture diagram, next migration candidates, limitations and rollback. Real app-to-app IPC, package identity/signature validation, permission grants, model import/inference, persistent memory, TTS/avatar animation, real care data and production extraction deferred to separately approved S2+ or 6B.3/7/8. No claim they work.

## Acceptance
P01 Clean standalone build with Java17, Gradle8.11.1, Kotlin2.2.21, AGP8.10.1, compile35/min26, actual SDK verified; existing standalone core 131 tests pass.
P02 Existing app source, schema and build configuration unchanged; diff only sunny-standalone-poc/**.
P03 APK distinct package, debug signing only, no forbidden permissions or production components; inspect actual merged manifest with aapt.
P04 Core integration and local deterministic fallback tested; no actual network or sensitive data; simulated vs real behavior labeled.
P05 Capability registry rejects duplicate IDs, incompatible versions, malformed input, unknown capability and unauthorized request. Denied actions never invoke adapter. Tests include negative cases.
P06 Synthetic adapter can return bounded read-only fixtures only after explicit test grant; revoke blocks subsequent requests; no real actions.
P07 No sensitive logs, no credential reuse, no production data access, no arbitrary code execution or new permissions.
P08 All mandatory tests passed, zero failures/errors/skips; report exact commands/counts/toolchain, APK path, SHA256, manifest check, changed files, limitations and rollback. No device claim without actual device evidence.
P09 Draft PR only; installability and side-by-side separation verified later on device by owner, not inferred from build.
P10 Architecture findings distinguish reusable core vs MeinHelfer care functions; propose promotion criteria, no automatic promotion.
If toolchain unavailable or conflict/scope insufficient, stop and report. Deliver APK build evidence where environment permits; do not publish artifacts to releases. First milestone is shell + synthetic contract, not full assistant. High reasoning effort recommended, strongest available coding model; do not assume account options.
