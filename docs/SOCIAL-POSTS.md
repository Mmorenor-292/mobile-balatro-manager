# Social launch copy

These drafts describe the public source release without claiming that the project ships Balatro or modifies a Play Store install in place.

## Reddit

**Title:** I built MBM, a local-first mod manager for Balatro on Android

I’m working on MBM — Mobile Balatro Manager, an Android companion for managing user-owned Balatro mod folders and preparing local mobile builds.

It can:

- connect to an approved Mods folder through Android’s file picker;
- import a ZIP or folder, inspect metadata without running Lua, and quarantine changes;
- enable or disable mods, restore backups, and keep separate backup and installation history;
- browse Balatro Mod Index, Thunderstore, and Awesome Balatro entries inside the app;
- pair with a Windows helper for a local Steam-to-Android build flow; and
- preflight a native APK and explain when it cannot be patched safely.

The project does not include Balatro, commercial APKs, saves, credentials, signing keys, DRM workarounds, or a cloud upload service. Steam builds use the player’s own game copy and the optional Balatro Mobile Maker toolchain. Native Play Store support is deliberately conservative: if the package, ABI, signature, or sandbox cannot be handled safely, MBM stops and explains why.

Source: https://github.com/Mmorenor-292/mobile-balatro-manager

I’m looking for reports from Android devices with real mod folders. If a build fails, include the Android version, Balatro version, Steamodded/Lovely versions, and the exact error.

## X

MBM — Mobile Balatro Manager is now public: a local-first Android companion for managing Balatro mods, backups, install history, and user-owned Steam/mobile build sources.

It never ships Balatro or bypasses Play Store signatures, DRM, or licensing. The source is here:

https://github.com/Mmorenor-292/mobile-balatro-manager

Android bug reports with the exact version and log are welcome.
