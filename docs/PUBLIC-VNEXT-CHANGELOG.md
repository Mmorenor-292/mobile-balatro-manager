# Public vNext changelog

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
