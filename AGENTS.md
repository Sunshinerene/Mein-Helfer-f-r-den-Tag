# Sunny development rules
Status: preparation; no implementation authorized until Chat 00 approves a specific work package.
Read docs/PROJECT_RULES.md, PROJECT_STATUS.md, SUNNY_ARCHITECTURE.md, CODEX_WORKPACKAGES.md and FILE_OWNERSHIP.md before editing. Paths in this sentence refer to docs/.
Sunny extends MeinHelfer, never replaces it. Local first; preserve existing data, settings and functionality.
Work only on assigned branch and approved paths. Never push to main, merge, release, reset data, change permissions or bypass security without explicit approval. Inspect current HEAD and concurrent changes first. Stop on conflicts or failed prerequisite tests.
Use synthetic fixtures only; never upload care data, secrets or private conversations. Treat model/OCR/web output as untrusted. No silent plaintext fallback, network fallback or generated-code execution. Authorization must be enforced outside the model.
Report actual commands, results, skipped tests, limitations, changed paths, requirement IDs and rollback steps in each PR. No claim of acceptance without evidence and owner approval. Update project memory through Chat 00.
