# POC-S1 device test record – 2026-11-27
Tested source b2f567933d58ea569eb691d1e675beab535072fd; CI run 45981591325; artifact 15190116172. CI APK SHA256 bc45ca726c4113346b53adfb265a9793ff272252ad929bb5f961b62ee4deea1d. Installed APK checksum and device/OS version not independently confirmed.
Independent CI: 131 core + 16 POC tests, zero failures/errors/skips; APK, manifest, debug signature, isolation checks passed.
Owner screenshots show successful app launch, simulated core ACK, granted synthetic tasks and appointments. Owner reported all six remaining checks passed:
- Flight mode simulation
- Missing runtime fallback
- Revocation followed by denied request
- Session reset clears input, results and grants
- Tasks-only grant leaves appointments blocked
- MeinHelfer existing functions and data unchanged
Evidence: Chat 00 screenshots and explicit owner report. This is functional prototype evidence, not a production security certification. Actual IPC, local model inference, real care data, persistent memory, TTS and original avatar are not implemented. Device metadata and installed checksum remain unverified. Draft PR #10 remains open; merge/release require separate explicit approval.
