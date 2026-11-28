# File ownership
Steps 4/5: backup, security, DbCrypto, AppReset, BackupCard, network and update security.
Codex 6A: new sunny-core/ only, plus its own work-package report. settings.gradle.kts and root build configuration require explicit one-time coordination before editing.
Shared locked paths: app/build.gradle.kts, manifests, HelferApplication, MainActivity, AppDatabase, AiClient, AiProvider, SunnyMemory, LabActivity, workflows. No changes without Chat 00 approval. No simultaneous edits to same file. No production integration during 6A.
Branches proposed: codex/sunny-brain, codex/sunny-modules, codex/sunny-standalone; integration entwicklung/sunny-2; main untouched. Check current HEAD before branching. Existing release/devtools-v1.0 is protected reference by project rule (technical protection not verified).

6A refinement: standalone Gradle build entirely inside sunny-core/; no root build changes authorized. Report in sunny-core/README.md. Root integration is a later coordinated task.

## 6B.2 scoped authorization
Chat 00 owner GO 2026-11-27. New branch codex/sunny-runtime-android. Allowed: new sunny-runtime-android/**; settings.gradle.kts, root build.gradle.kts and app/build.gradle.kts only for module wiring; app/src/main/java/de/meinhelfer/tag/ai/SunnyModelStore.kt and SunnyLiteRtTest.kt only for shared model access and cleanup safety. Existing core sources/tests read-only. All other app files, schemas, workflows, permissions, backup/security paths and main remain locked. No concurrent changes; stop if scope insufficient. No host routing/UI migration.
