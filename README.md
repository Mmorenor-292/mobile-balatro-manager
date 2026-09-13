# MBM — Mobile Balatro Manager

## Pixel interface preview

The September 2026 redesign follows the approved Home / Mods / Appearance mockup.
The manager now has three destinations: **Home**, **Mods** and **Descubrir**.
Home summarizes the actual collection. Mods provides search, active/inactive/update
filters, toggles, published-version selection and individual update/delete actions.
Discover installs entries from the existing catalogs or opens source-only links.
Appearance offers a classic background, a plain dark background, a local image,
and a persistent CRT switch and intensity control.

Steam import, saves, repair, bulk cleanup and backup/history screens are intentionally
outside this simplified UI. Their existing backend and separate assistant are retained.
Updates and installation use the upstream 2.0.4 motor, including release detection,
installation receipts and serialized operations. The UI uses the native update status;
unknown revisions are not presented as definite updates. Historical versions are
available only when exposed by the catalog; BMI can load published versions on demand.
Mod writes are temporarily locked while an operation runs.

### Run the preview alongside an existing manager

Build the UI first, then build the isolated debug package:

```powershell
npm --prefix ui run build
gradle :app:assembleDebug '-PqaApplicationIdSuffix=.preview'
```

This produces `cl.mauricio.balatromods.preview`; it can coexist with a manager signed
with another key. Omitting the property preserves the normal package ID. Debug WebView
inspection is enabled only for debug builds. Do not uninstall an existing manager merely
to compare the preview. See [the focused QA report](docs/UI-REDESIGN-QA.md).

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
