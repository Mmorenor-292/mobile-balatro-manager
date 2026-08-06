# Public vNext changelog

## 2.0.4 · 2026-08-06

- Separated semantic releases from moving Balatro Mod Index source hashes, so an
  uncomparable hash is never presented as a confirmed update.
- Added on-demand GitHub release history with a quota-free official Atom-feed fallback;
  uploaded mod packages are preferred over bundled Steamodded archives.
- Added **Save diagnostic ZIP** and **Share via Telegram** with bounded inventory,
  catalog/receipt state, useful text files, secret redaction, and explicit APK/save exclusion.
- Added **Switch version** for intentional upgrades or downgrades that are not newer than
  the installed build, instead of misleadingly labelling every different version as Update.

## 2.0.1 · 2026-08-05

- Added per-card `Installing…` / `Updating…` feedback without a blocking overlay.
- Made version selection available in Discover and Library, including current/latest labels, upgrades, downgrades and reinstalls.
- Fixed installed-mod matching so catalog updates no longer require delete/reinstall.
- Replaced quarantine-based install/delete with inspected direct install, transactional update rollback in private cache, and permanent deletion.
- Added a bounded, idempotent IMM mobile-version parser fix for `1.0.1o-FULL (STM)`.
- Expanded Awesome Balatro release history from GitHub and consumes BMI version arrays when provided.

## 2.0.0 · 2026-08-02

- Added a four-step Steam port wizard with local helper pairing, manual `.love`/
  ZIP/folder fallback, real build polling, and explicit install/save/share actions.
- Added Native APK/split preflight for package, version, ABI, signature and safe
  fallback behavior; Play Store copies are never patched when safety cannot be proven.
- Added bounded desktop save summaries and explicit LAN save import with backup;
  desktop `Mods` content is excluded from the save archive.
- Added reversible mod import/delete/restore, thumbnails with puzzle fallback, two
  exact History tabs, catalog filters/sorting, wallpaper persistence and public docs.
- Added privacy notice, wallpaper generation record, helper provenance, checksums,
  Android matrix and Maestro flows.

Known limits: Maker/mod compatibility varies, official Play Store sandboxes remain
opaque, and ARM64 physical-device validation is still pending.
