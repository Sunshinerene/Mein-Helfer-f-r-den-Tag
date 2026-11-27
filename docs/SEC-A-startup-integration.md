# SEC-A startup integration
Status: proposed integration contract; not production-enabled.
Verified baseline: 116 unit tests, run 45947436389. Combined gate tests pending.

## Lifecycle
- Missing status alone never authorizes creation. Confirm first-run evidence and absence of existing key/data/artifacts.
- Store lifecycle outside backup payload and reset deletion scope (candidate: noBackupFilesDir); audit reset and restore before use.
- Serialize startup, reset, restore and key creation process-wide.
- Persist RESET_PENDING before any authorized destructive reset operation. Verify every required deletion before persisting RESET_COMPLETED. Preserve pending evidence on failure.
- Consume creation authorization before opening a newly created database; interrupted creation must fail closed rather than silently recreate. Verify encrypted Room open before publishing INITIALIZED.
- Existing legacy databases without lifecycle metadata must be validated before marking initialized.
- Unknown or unreadable status blocks normal startup. Recovery must not automatically clear status or artifacts.
- Restore must reconcile lifecycle only after verified commit or rollback; preserve original status on failure.

## Integration constraints
Do not enable the new startup path until reset, first-run, restore and migration paths are reconciled. No plaintext Room fallback, no replacement key for encrypted candidates, no deletion of migration originals on uncertain failure. Existing startupReady must not become true before required validation. Preserve database-free recovery screen and reminder warnings.
No schema change, merge, release or production install.

## Acceptance
ST-01 restart persistence; ST-02 write failure blocks; ST-03 malformed status blocks; ST-04 one-use creation authorization; ST-05 interrupted reset blocks; ST-06 no personal data; ST-07 legacy compatibility; ST-08 restore regression. Device tests must exercise actual AtomicFile, Keystore, SQLCipher, process interruption and recovery. Pure policy tests are not evidence of device behavior.
