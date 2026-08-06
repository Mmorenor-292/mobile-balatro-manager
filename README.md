# MBM - Mobile Balatro Manager 2.0.4

An independent Android manager for Balatro Modded. It remains available even when a
broken mod prevents the game from starting, and now exposes separate Steam/local,
Native preflight, Saves, split History, Discover, Settings and Help surfaces.

## Use

1. Build the Android app from source, then install the APK on a test device.
2. Open **Steam copy** for the local desktop wizard or **Native Android** for preflight.
3. Use **Mods** to import, enable, disable, update or permanently delete a mod.
   **Update all** upgrades every catalog-matched mod with a newer release, while
   **Clean junk** permanently removes only known manager leftovers and OS metadata.
4. Use **Saves** to choose whether progress is imported and **History** to restore backups
   or review installations.

Downloaded and imported mods are inspected in private cache first, then installed directly
into the collection and enabled. Discover and Library always show a version selector; when
the source exposes release history, the user can update, downgrade or reinstall without
deleting the current copy first. The app never executes Lua code.

For Balatro Mod Index entries, **Load published versions** resolves stable GitHub releases
on demand instead of presenting a moving commit hash as a semantic version. **Update all**
does the same for installed mods before comparing versions. If only source code exists and
MBM has no matching installation receipt, the result is **Version check needed**, not a
false update notification. If GitHub's anonymous API quota is exhausted, MBM uses GitHub's
official release feed and immutable tag archives, then resolves the uploaded ZIP only when
the selected version is installed.

Help includes **Save diagnostic ZIP** and **Share via Telegram**. The privacy-filtered
archive contains inventory, parsed mod metadata, dependency and catalog status, install
receipts, scan errors, and bounded text files useful for debugging. It excludes game/APK
files, saves, credentials, images, audio and binary assets, and redacts secret-like lines.

Mod writes remain serialized for Android storage safety, but individual cards now
show **Queued**, **Installing**, **Updating**, **Enabling**, **Disabling** or
**Deleting** while they run. A busy mod is locked; actions for a different mod can
still be queued instead of being rejected by a global blocker.

Dependency rules retain their required versions and use exact Steamodded IDs. A
fork such as `PokermonMaelmc` no longer satisfies `Pokermon`, and an installed
`3.8.1-0724a` is reported as too old for `Pokermon (>=3.8.1-0731b)`. **Update all**
refreshes catalog metadata first and tracks source-revision hashes separately from
the semantic version declared inside each mod.

Deleting a mod is irreversible and removes its folder instead of renaming it inside `Mods`.
Legacy `.bmm-trash--*` folders are counted as junk and removed only when the user
presses **Clean junk** and confirms. Real mod folders, inactive mods and backups are
never included in this cleanup.

## Balatro AI Assistant

The repository also includes a separate Android APK for guided crash analysis,
compatibility repair proposals and Steamodded mod scaffolding. It pairs over the
local network with **BMM Helper 0.5.0**, which invokes the user's already signed-in
Codex CLI with `gpt-5.6-terra` and high reasoning. ChatGPT/Codex OAuth credentials
never enter the APK and are never copied over the network.

The assistant treats logs and mod archives as untrusted data, rejects executable
payloads, works only on a staging copy, and requires the user to review and export
any proposed ZIP. It never silently edits the original Mods folder or game saves.
See [docs/BALATRO-AI-ASSISTANT.md](docs/BALATRO-AI-ASSISTANT.md).

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
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :assistant:lintDebug :assistant:assembleRelease
```

The signing properties file must define `storeFile`, `storePassword`, `keyAlias`, and
`keyPassword`. No secrets are included in the source tree.

## Deliberate limits

- Android requires one initial Storage Access Framework approval; this cannot be
  legitimately bypassed.
- Awesome Balatro is treated as a community directory, not a package registry. Each
  entry is inspected independently and installs are limited to HTTPS release archives.
- A desktop mod is not guaranteed to work with the mobile port.
- IMM 2.5.1/2.6.0 rejects Balatro's mobile version suffix. MBM now applies the narrow,
  idempotent compatibility patch automatically after connecting the folder and after
  every IMM install/update, with a versioned original in app-private storage. The
  manual **Fix IMM mobile version** action remains available.
- The existing Balatro APK is ARM; direct game-launch testing belongs on a physical
  phone or ARM emulator, not the x86_64 emulator used for manager QA.

See [docs/NOTEBOOK-TESTING.md](docs/NOTEBOOK-TESTING.md) for notebook test routes.
See [docs/WIRELESS-DEVELOPER-BRIDGE.md](docs/WIRELESS-DEVELOPER-BRIDGE.md) for the
planned no-USB, hash-checked notebook workspace used to inspect and repair phone mods.
See [docs/SOCIAL-POSTS.md](docs/SOCIAL-POSTS.md) for launch copy and [docs/LICENSES-ATTRIBUTIONS.md](docs/LICENSES-ATTRIBUTIONS.md) for third-party notices.

For the real Steam-to-Android build, place the user-provided upstream Balatro Mobile
Maker executable beside `BMM.Helper.exe`, or pass `--maker PATH`, and restart the
helper. The public source release does not redistribute the Maker or Balatro files.
Without a Maker, the pairing flow remains a manifest-only preflight and cannot claim
an APK exists.
