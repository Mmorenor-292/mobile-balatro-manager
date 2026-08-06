# MBM - Mobile Balatro Manager — Privacy Notice

**Last updated:** 2026-08-02

MBM - Mobile Balatro Manager is a privacy-conscious companion for user-owned Balatro files. It does not include Balatro, commercial game files, saves, Steam credentials, Google credentials, or signing keys.

## What stays local

- Mod folders, private update rollback cache, save previews, save archives, installation history, and backup history stay on the device or the user-selected desktop.
- Steam pairing is a one-time, LAN-only connection to the helper running on the user's PC. The helper reads only the approved Steam library roots and the selected game source. The app permits cleartext HTTP only for this explicitly paired local helper; it does not use a cloud endpoint.
- A selected `.love`, `.zip`, or folder is sent only to the paired helper for the requested local build. Temporary build workspaces are deleted after the helper job ends.

## Optional network actions

- Catalog refresh and mod downloads happen only after the user requests them and use the configured HTTPS catalog sources.
- The app does not upload game files, saves, credentials, crash reports, or mod folders to a cloud service by default.
- APK sharing, installation, save export, report export, and catalog downloads are explicit user actions handled by Android/system pickers.

## Diagnostics and permissions

The app may request access to a user-selected Mods folder, save folder, APK/source file, or desktop helper address. File inspection is bounded and does not execute Lua or mod code. Local crash reports are off by default; if the user opts in under Settings, diagnostics remain on-device and are exported only on demand through the Android share/picker flow.

## Retention and deletion

Mod deletion is permanent and removes the selected folder from `Mods`; it does not create a quarantine folder. Updates use a temporary app-private rollback copy only while replacing the installed version, then delete it after success. Temporary APK/source files are stored in the app cache and are eligible for Android cache cleanup.

## Third parties and changes

Catalog providers, Android, the optional upstream Balatro Mobile Maker, and installed mods have their own policies and licenses. Review each mod's source and license before redistribution. This notice may be updated with a new release; the release folder contains the applicable copy.
