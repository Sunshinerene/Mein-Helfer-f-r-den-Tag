# Sunny Brain v0.4 – Device acceptance

Status: pending. Owner approved documentation-only CI trigger in Chat 00/1. No signing or production release authorized.

## CI gate
Verify current branch head, at least 131 core and 57 standalone tests, zero failures/errors/skips, 36 WebP assets, APK identity and isolation. Build #23 at 4302ccc passed twice per owner screenshots; not evidence for the new head.

## Device gate
Use only permanently signed APK after separate approval and pinned certificate verification. Keep v0.3 installed; no uninstall. Candidate model: litert-community/Gemma3-1B-IT, gemma3-1b-it-int4.litertlm; owner reports download complete. Verify provenance, hash and runtime compatibility; CPU first. No real care data.

- [ ] Android recognizes update; installation without uninstall.
- [ ] Sunny starts; existing avatar and voice functions preserved; MeinHelfer unchanged.
- [ ] Trusted model imports and persists across restart.
- [ ] Airplane mode: native ACK/CLARIFY classification succeeds without fallback.
- [ ] Repeat classification; UI remains responsive.
- [ ] Cancellation safely completes; repeat operation after cleanup.
- [ ] Invalid import preserves existing valid model.
- [ ] Confirmed removal works; Sunny remains usable without model.

Record device OS, APK hash, model hash, timings, errors and actual observations. Synthetic unit tests do not establish native device inference. Free conversation is outside this scope.
