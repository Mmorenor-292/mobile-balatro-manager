# Public vNext QA report

Date: 2026-08-02
Build: MBM - Mobile Balatro Manager 2.0.0 (`cl.mauricio.balatromods`)

## Automated checks

- Web: `npm test -- --run` — 8/8 passed, including multi-select mod actions and local settings/retention controls.
- Legacy WebView compatibility: API 31's Chromium 91 initially exposed a missing `structuredClone`; the bridge now uses a JSON-state fallback and renders correctly.
- Web: `npm run lint` — passed.
- Web: `npm run build` — passed; production assets copied into Android assets.
- Android: `testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease assembleBeta bundleBeta` — passed.
- Android LAN pairing: release manifest permits cleartext only for the explicitly paired local helper endpoint; the rebuilt APK installed/launched without fatal or WebView errors.
- Android release APK: installed and launched on `emulator-5556` (`x86_64`) after the FileProvider/share/install and Native preflight changes.
- Android beta APK: installed beside production as `cl.mauricio.balatromods.beta` (`2.0.0-beta`) and launched on `emulator-5556`.
- Helper: `/health`, six-digit `/pair`, authorized `/manifest`, allowlist isolation, and no-maker `/build` fail-closed response — passed.
- Helper build integration: bundled upstream Maker detected, `/build` job completed from an isolated local `.love`, `/build-status` reached `completed`, APK download succeeded, and the downloaded APK contained `AndroidManifest.xml`. No game file or save was copied to Drive.
- Helper Steam integration: the local Steam installation was detected through the allowlisted `steamapps` root; `game=0` completed a real isolated Maker build and the 64,049,601-byte APK contained `AndroidManifest.xml`. The temporary commercial copy and generated APK were deleted after validation.
- Helper save/mod inspection: published helper `--json` and authorized `/manifest` reported the local `%APPDATA%/Balatro` profile and mod-folder summaries; authorized `/save-archive?profile=1` returned a bounded ZIP (3 entries in the local smoke) with `Mods` excluded. The temporary archive was deleted after validation.
- Helper desktop-mod transfer: the published helper's authorized `/mods-archive` returned a bounded 106 MB ZIP containing the detected 24 mod folders; desktop-only executable extensions are rejected and the temporary archive was deleted after validation. The Android bridge and UI action compile and lint successfully; direct SAF import remains a manual-device scenario.
- Helper game preflight: the published helper reported the detected Steam executable as version `11.5`, architecture `windows-x64`, and frameworks `lovely` plus `smods-1.0.0-beta-1814a`; the wizard exposes that summary before configuration.
- Native/game smoke on `BMM_Public_GooglePlay_API31` / `emulator-5554`: the authorized local Balatro package `cl.mauricio.balatro.modded` (version `11.5a`) was already installed and opened to the modded main menu; MBM - Mobile Balatro Manager installed beside it, automatically detected that exact package when Native opened, and returned the safe Steam/local fallback without modifying the game. The fallback label scan excludes the manager package itself, and the manifest declares the known official package ids for Android 11+ visibility.
- Native personal-build integration: the rebuilt helper reported `nativeBuilderAvailable: true`; a 100,850,922-byte authorized local Balatro APK was uploaded through `/upload` as a bounded `playstore` source, `/build?native=1&game=-2` reached `completed`, and the 72,558,804-byte output reported package `com.unofficial.balatro`. It installed on `emulator-5554`, launched, reached the Balatro menu, and produced no fatal exception. This validates the separate-package builder without downloading or redistributing a commercial Play Store APK.
- Maestro on the renamed final signed APK: `home-and-wizards.yaml`, `mods-and-history.yaml`, `public-surfaces.yaml`, and `steam-and-saves.yaml` all passed on `BMM_Public_GooglePlay_API35` / `emulator-5554`. The home flow now scrolls before the fallback action, and the public flow uses the WebView-accessible `Open settings` label. The Play Store was opened through the official package page; with the authorized test account it reports Balatro as “This app is available only for your other devices”, so no installation/start claim is made for the commercial game.
- Earlier ADB transport drops were isolated to a stale secondary emulator; after restarting ADB, the final signed APK passed all four flows on `emulator-5556`.

## Android smoke

- AVD: `BMM_Public_GooglePlay_API31`, Android API 31, x86_64; manager WebView verified after the compatibility fallback.
- Google Play AVD: `BMM_Public_GooglePlay_API35`, Android API 35, x86_64, Google Play Services present and the authorized Google account visible.
- `adb install -r -d` — passed.
- Cold launch and WebView render — passed after the normal WebView startup wait.
- Maestro `home-and-wizards.yaml`, `mods-and-history.yaml`, `public-surfaces.yaml`, `steam-and-saves.yaml` — passed on the renamed final signed APK on `emulator-5554`.
- No fatal exception or `BMM_WEB` JavaScript error in the final launch log.

## Covered public flows

- Home with Steam and Native routes.
- Native incompatibility preflight and guided Steam fallback.
- Mods import ZIP/folder actions and exactly two History tabs.
- Reversible quarantine delete/restore and persistent installation history.
- Save source/target selection, bounded conflict preview, reversible import path and ZIP export path.
- Settings wallpaper selector with local persistence.
- Settings advanced mode, local-only crash-report opt-in (off by default), and bounded history retention selector.

## Known limits

- The manager was validated on x86_64 AVDs, including the Google Play API 31 image with an authorized local Balatro package. ARM64/physical-device validation remains open. The official package detection branch is implemented for `com.playstack.balatro.android` and `com.playstack.balatro`; the end-to-end personal builder was exercised with an already-authorized local APK, because the public QA bundle does not download or redistribute a commercial Play Store APK.
- The debug APK remains the development artifact; the public folder contains signed beta and production-candidate APK/AAB artifacts using the local BMM public key. The private keystore is not included in the repository or Drive.
- The helper pairing/manifest transport and asynchronous maker orchestration are implemented and allow-listed. The public helper bundle includes the separately documented upstream Maker executable; it still requires the user's own Steam copy and downloads its build tools on first use.
- Native does not patch the Play Store package in place. It creates a separate `com.unofficial.balatro` package from a user-owned base APK; the original app remains untouched. If Android cannot expose a readable base APK, MBM asks the user to select one explicitly or use the Steam route.
- Save import requires two user-selected SAF folders (source and destination); it cannot write into a Play Store private sandbox that Android does not expose.
- Desktop progress transfer is explicit and LAN-only, but it imports into a user-selected writable Android folder; it does not silently inject into an official Play Store private sandbox.
- Desktop mod transfer is explicit and LAN-only, but it imports into a user-selected writable Android `Mods` folder; it does not silently inject into an official Play Store private sandbox. Direct SAF import on a physical ARM64 phone remains open.
- The public QA matrix does not include a commercial Play Store APK. It verifies the package IDs and read-only source handoff in code, plus the full separate-package builder with an authorized local APK. The generated native APK is personal-use output and is not redistributed as part of MBM.
- The upstream Maker does not package Steamodded/Lovely/mod folders into the `.love` source. MBM detects and reports those folders and manages them after installation through the Mods route; compatibility still depends on the mod and the generated mobile environment.

## Release verification — 2026-08-04

- Source revision: `a88cf12` (`main`) plus this verification record.
- Frontend: `npm ci`, `npm test -- --run` (8/8), `npm run lint`, and `npm run build` — passed.
- Android: `testDebugUnitTest`, `lintDebug`, and `assembleRelease` — passed with the canonical Android App Lab SDK.
- Release APK: `cl.mauricio.balatromods`, version `2.0.0` (`versionCode 20`), signed with the existing BMM release certificate (SHA-256 `e4748c44c8fa257d605278446b449dbbb5fa498ba9b51d3e18ab591858d5d671`).
- Embedded web assets include the compact mod-card rules (`min-height: 4.55rem`) and current wallpaper assets.
- Android smoke: `adb install -r -d` and cold launch passed on `emulator-5554`; no fatal exception was found in the collected logcat.
- Release artifact SHA-256: `A65CD100796FBD7FDA0F05FF12081CB30A6DEDB7174DAFF4150E2EE336A25769`.

## Release verification — 2026-08-05

- Frontend: `npm test -- --run` (9/9), `npm run lint`, and `npm run build` — passed.
- Catalog behavior: Discover refreshes trusted indexes on entry; cards show installed/latest versions, expose verified release choices where the source provides them, and route Install/Update through the selected archive URL. Awesome Balatro entries without a verified archive remain source-only.
- Android behavior: mod enable/disable and other file operations are queued on the I/O executor without a blocking full-screen loading state; the catalog refresh still reports a compact non-blocking status chip.
- Android: `testDebugUnitTest`, `lintDebug`, and `assembleRelease` — passed with the canonical Android App Lab toolchain.
- Android smoke: release APK installed incrementally on `emulator-5554`, cold launch passed, screenshot captured, and collected logcat contained no fatal manager exception.
- Release APK: `cl.mauricio.balatromods`, version `2.0.0` (`versionCode 20`), signed with the existing BMM release certificate (SHA-256 `e4748c44c8fa257d605278446b449dbbb5fa498ba9b51d3e18ab591858d5d671`).
