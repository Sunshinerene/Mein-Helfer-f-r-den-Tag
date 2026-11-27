# POC-S2 – Sunny Emotion & Voice
Status: proposed contract, separate execution GO required. Baseline: accepted POC-S1 merge 6b0dedc; verify latest integration HEAD and documentation-only changes before creating codex/sunny-standalone-s2. No merge/release without separate approval.

## Scope
Only sunny-standalone-poc/**. Preserve all POC-S1 capabilities and tests, package de.meinhelfer.sunny.poc, sandbox, no backup, no Internet/overlay/microphone/accessibility permissions, no real care data or IPC. Existing MeinHelfer app and core read-only. Workflows/root build locked. Do not copy unfinished runtime/security branches. Use existing Java17/Gradle8.11.1/Kotlin2.2.21/AGP8.10.1/SDK35. Keep new dependencies out unless separately approved.

## S2.1 – Emotion contract
Read existing SunnyEmotionCatalog.kt and SunnyEmotionMedia.kt as reference. Preserve stable su01–su35 and eight existing semantic groups (Friendly, Explaining, Warm, Thinking, MotivatedReading, Calm, Active, Talking), exact mappings from source, extensible to su36+. Separate semantic selection, media lookup and UI. Deterministic fallback for absent/invalid assets and missing video; no fabricated emotion images. Preserve original identity. Test all IDs, mappings, missing and malformed catalog entries, fallback and repeated selection.

## S2.2 – Original media gate
Authoritative user-selected archives Sunny_Emotion_Medien_png.zip and Sunny_Emotion_Medien_optimiert_Webp.zip have not been retrieved by Chat 00. Existing repository has seven older named WebP images and two Sprechvideos, not proven equivalent to current archive. Do not silently substitute them. Verify source availability, owner authorization for reuse, inventory, filename mapping, checksums and sizes before copying approved media into isolated app. Prefer approved WebP for APK; PNG originals remain unchanged. No new generated images or public distribution. If unavailable, keep clearly labeled neutral placeholder and report asset blocker; proceed with technical work only. Video playback only for explicitly approved local assets, no network sources, no autoplay audio.

## S2.3 – Local voice
Use Android TTS with explicit user-triggered playback of fixed synthetic texts; select installed German offline voice (isNetworkConnectionRequired false), no download, no online fallback. If no offline voice or engine initialization fails, display text and status. Android engine is external trust boundary; no personal/health input to it in this POC. Do not claim engine behavior beyond observed device tests. No microphone. TTS engine discovery may require narrow manifest queries for TTS_SERVICE on Android 11+, but POC-S1 isolation CI currently rejects queries. Do not add queries or change CI without separate coordinated approval; report blocker if necessary. Use listener callbacks to drive Talking while utterance active, restore appropriate idle emotion on completion/error/stop; guard stale callbacks, release on lifecycle exit, no background speech. Inject fake voice for unit tests. No claim of lip sync.

## S2.4 – UI
Add emotion preview, image/video/auto selector (video only if approved), safe fallback, speech start/stop/status and modest configurable shape/color. Preserve POC-S1 core/connect/help/reset. Reset cancels speech and clears session/grants. Keep UI readable and accessible. No real LLM, persistent memory, host connection, or production extraction.

## Acceptance
S201: standalone build and baseline 131 core +16 POC tests unchanged and passing.
S202: test emotion mappings and fallback, future IDs, invalid catalog.
S203: test offline voice selection, unavailable/error fallback, start/done/error/stop lifecycle, stale callbacks and reset with fakes.
S204: actual APK same unique package; no new permissions, components, network or backup. Manifest queries remain absent unless separately approved.
S205: approved media provenance and exact asset hashes documented; otherwise explicit placeholder and media acceptance deferred.
S206: all mandatory tests pass, zero failures/errors/skips; report exact commands/counts/toolchain, APK SHA256, manifest, diff, known limitations and rollback.
S207: device checks for offline speech, visible emotion transitions, stop/reset, old POC-S1 regression, unchanged MeinHelfer. No device claim before actual owner test.
S208: Debug signing continuity must be resolved separately before updating installed POC-S1; CI currently uses ephemeral debug keys. Do not uninstall or overwrite existing app to bypass mismatch. Report signing fingerprint and coordinate test update strategy.
S209: Draft PR only. No release, merge, actual app-to-app communication or model runtime migration. Stop on scope conflict.
