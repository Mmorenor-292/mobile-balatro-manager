# MBM - Mobile Balatro Manager · Public vNext checkpoint

An independent Android manager for Balatro Modded. It remains available even when a
broken mod prevents the game from starting, and now exposes separate Steam/local,
Native preflight, Saves, split History, Discover, Settings and Help surfaces.

## Use

1. Build the Android app from source, then install the APK on a test device.
2. Open **Steam copy** for the local desktop wizard or **Native Android** for preflight.
3. Use **Mods** to import, enable, disable or move a mod to reversible quarantine.
4. Use **Saves** to choose whether progress is imported and **History** to restore backups
   or review installations.

Downloaded mods are installed hidden in quarantine. Inspect them in Library and enable
them manually. The app never executes Lua code.

If an earlier debug build of this manager is installed, Android may require you to
uninstall that manager once because its signing key differs. Do not uninstall Balatro.

## Catalogs

- Balatro Mod Index: primary structured catalog.
- Thunderstore: secondary community catalog.
- Awesome Balatro: broad human-curated directory shown inside Discover. MBM only offers an in-app install action when a linked GitHub repository exposes a verified ZIP release; source-only entries stay explicitly non-installable.

Mobile compatibility is shown as unknown unless there is explicit evidence.

## Build

Requirements: JDK 17, Android SDK 37, Gradle 9.5, and Node.js.

```powershell
cd ui
npm ci
npm test -- --run
npm run lint
npm run build
cd ..
$env:BALATRO_SIGNING_PROPERTIES='C:\private\path\signing.properties'
gradle testDebugUnitTest lintDebug assembleRelease
```

The signing properties file must define `storeFile`, `storePassword`, `keyAlias`, and
`keyPassword`. No secrets are included in the source tree.

## Deliberate limits

- Android requires one initial Storage Access Framework approval; this cannot be
  legitimately bypassed.
- Awesome Balatro is treated as a community directory, not a package registry. Each
  entry is inspected independently and installs are limited to HTTPS release archives.
- A desktop mod is not guaranteed to work with the mobile port.
- The existing Balatro APK is ARM; direct game-launch testing belongs on a physical
  phone or ARM emulator, not the x86_64 emulator used for manager QA.

See [docs/NOTEBOOK-TESTING.md](docs/NOTEBOOK-TESTING.md) for notebook test routes.
See [docs/SOCIAL-POSTS.md](docs/SOCIAL-POSTS.md) for launch copy and [docs/LICENSES-ATTRIBUTIONS.md](docs/LICENSES-ATTRIBUTIONS.md) for third-party notices.

For the real Steam-to-Android build, place the user-provided upstream Balatro Mobile
Maker executable beside `BMM.Helper.exe`, or pass `--maker PATH`, and restart the
helper. The public source release does not redistribute the Maker or Balatro files.
Without a Maker, the pairing flow remains a manifest-only preflight and cannot claim
an APK exists.
