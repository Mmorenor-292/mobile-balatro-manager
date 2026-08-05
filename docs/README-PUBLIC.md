# MBM - Mobile Balatro Manager

MBM - Mobile Balatro Manager is an independent Android companion for managing a user's Balatro mods, catalog releases, and recovery files. It does not include Balatro, commercial game files, credentials or signing keys.

## Start here

- **Steam copy**: use the portable Windows helper and a local-network pairing code. See [GUIDE-STEAM.md](GUIDE-STEAM.md).
- **Native Android**: select an installed APK or APK set for a safety preflight. Unsupported Play Store copies are sent to the Steam fallback. See [GUIDE-NATIVE.md](GUIDE-NATIVE.md).
- **Mods**: connect the Mods folder once, then import, enable, disable or quarantine mods.
- **Saves**: preview, back up and export local progress. See [GUIDE-SAVES.md](GUIDE-SAVES.md).
- **History**: use `Backup History` for restore points and `Installation History` for mod operations.

## Privacy

Files remain on the device by default. Desktop pairing is LAN-only, consent-based and limited to an allow-list. Catalog downloads are initiated by the user and inspected before staging. See the privacy notice in the release folder.

After a verified Steam build, the Android app can install the APK on the current phone, save it through the system document picker, or share it through the Android share sheet. These are explicit user actions; the helper never uploads the artifact to a cloud service.

When the paired helper reports `%APPDATA%/Balatro` saves, the **Saves** screen can import a selected profile or all compatible profiles into a user-selected writable destination. The transfer is a bounded, LAN-only ZIP requested by the phone, creates a reversible backup first, excludes the desktop `Mods` folder, and is deleted after use. Mods remain separately managed through **Mods** because the upstream Maker does not package them into the game source automatically.

When the paired helper reports desktop mod folders, the **Mods** screen shows a hard-drive import action after the phone's writable `Mods` folder is connected. The helper creates a separate bounded ZIP, rejects desktop-only binaries, and the app quarantines conflicts before copying. The import is reversible and recorded in Installation History; it does not claim that every desktop mod is Android-compatible.

## Testing build

Use `Mobile-Mod-Manager-Public-vNext-debug.apk` for local testing. It is signed with a development key and may require uninstalling an older manager build with a different key. Do not uninstall Balatro.

The public folder contains both reproducible unsigned inputs and a locally signed APK/AAB
for testing. A store release should use a stable publisher-owned key; the private local
keystore is never included. The Steam wizard builds only when the user supplies the
upstream Balatro Mobile Maker executable to the helper; otherwise it remains honest
manifest-only preflight.

## Release channels

- **Beta** (`Mobile-Mod-Manager-Public-vNext-beta.apk` / `.aab`) uses the `.beta` application id suffix and `-beta` version suffix, so it can be installed beside production for testing.
- **Production candidate** (`Mobile-Mod-Manager-Public-vNext-release.apk` / `.aab`) keeps the stable application id and production channel label. Publish it only after the physical ARM64 acceptance pass and publisher-key review.
- The debug APK is a development-only build and is not a production update path.
