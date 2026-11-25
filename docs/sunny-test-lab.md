# Sunny Test Lab

Status: approved concept, not implemented or tested.
Owner: 09 – Sunny Update & Testing; coordination 00; design 08.

## Isolation
Separate application ID and APK, separate private storage and encryption keys. Synthetic fixtures only. No access to production backups, real care data or production notifications. Fault injection compiled only into dedicated lab variant, never normal debug/release APKs. Distinct app label and persistent test banner.

## Restore interruption tests
Inject process termination after verification (before approval), after approval, after rollback metadata persistence, after APPLYING marker, after each file move, after key/prefs changes, after integrity check, after COMMITTED/ROLLED_BACK and during cleanup. Reopen and verify original or committed fixture, key consistency, no unapproved restore, idempotent repeated startup, and no database access during failed recovery. Process termination is not equivalent to power loss.

## Design contract
Reuse HelferTheme and existing style settings for setup, recovery and lab screens. Preserve light/dark, font scale, shapes, colors and effects; safe fallback when settings unavailable. No new independent design system. Existing Sunny artwork remains unchanged; archived assets require explicit inventory and format/alpha/resource checks before integration. Test portrait/landscape and large fonts. No production asset replacement in this milestone.

## Release gate
Verify separate package IDs and absence of fault injection in normal APK. Build both variants and execute tests with synthetic data before any restore test on a real device. Report actual results and limitations. Future extensions: model, voice, storage, performance and database diagnostics.
