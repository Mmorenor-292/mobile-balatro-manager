# Android validation matrix

## Automated checks

| Layer | Command / flow | Expected result |
| --- | --- | --- |
| Web UI | `npm test -- --run` | 8 Vitest tests pass |
| Web lint | `npm run lint` | ESLint exits 0 |
| Web build | `npm run build` | Vite writes assets to Android `assets/web` |
| Android unit/lint | `gradle testDebugUnitTest lintDebug` | All tests/lint pass |
| Android packaging | `gradle testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease assembleBeta bundleBeta` | Tests/lint and signed beta/production APK/AAB packaging pass |
| Emulator install | `adb install -r -d app-release.apk` and `app-beta.apk` | Production package installs; beta package installs beside it as `.beta` and launches on API 36 x86_64 |
| Smoke UI | `home-and-wizards.yaml` | Home, Native fallback, Steam wizard visible |
| Mods/history UI | `mods-and-history.yaml` | Import actions and two history tabs visible |
| Public surfaces | `public-surfaces.yaml` | Wallpaper, Help, and About/Licenses surfaces pass |
| Steam/save surfaces | `steam-and-saves.yaml` | Four-step manual-source route and save import choices pass on `emulator-5554` |
| Desktop save transfer | Helper `/manifest` + `/save-archive?profile=1` smoke | Authorized bounded ZIP returned; `Mods` excluded; temporary archive deleted |
| Desktop mod transfer | Helper `/manifest` + `/mods-archive` smoke | Authorized bounded ZIP returned; desktop-only binaries rejected; temporary archive deleted |

## Devices and limitations

- Local AVD: `BMM_Public_API36`, Android API 36, x86_64. It validates the WebView, SAF intents, package install and Java bridge, but it does not include Google Play Services.
- Google Play AVD: `BMM_Public_GooglePlay_API31`, Android API 31, x86_64; MBM - Mobile Balatro Manager release install and automatic Native detection passed beside the authorized local `cl.mauricio.balatro.modded` package (version `11.5a`), which opened to the modded main menu. Known official package ids are declared for Android 11+ visibility, and the bridge includes a fallback for the API 31 WebView's missing `structuredClone`.
- A physical ARM64 phone remains required before publication for Play Store/ABI-specific validation and for testing the user's real Balatro copy.
- Native Play Store patching is intentionally fail-closed. The preflight inspects package/version, ABI entries, signing certificate digest, and selected split count, but it must not bypass DRM, signatures, licensing, split APK delivery or sandbox boundaries.
- The Steam route needs the portable Windows helper and a real Steam Balatro installation. The helper only exposes allow-listed Balatro files over a one-time LAN pairing code.

## Manual smoke checklist

1. Launch the debug APK and wait for `MBM - MOBILE BALATRO MANAGER`.
2. Open `Mods`, connect a `Mods`/`ASET` folder through Android's picker, then refresh.
3. Import a ZIP or folder; confirm it is staged in quarantine and appears in Installation History.
4. Delete a non-framework mod; confirm it moves to reversible quarantine and its history entry offers `Restore`.
5. Open Settings and switch each wallpaper; reload the app and confirm the selected wallpaper persists.
6. Open Native, select an APK, and confirm the result is a truthful safe fallback when the copy cannot be patched.
7. Complete a Steam build with the helper, then exercise `Install on this phone`, `Save APK`, and `Share APK`; each action must be explicit and must use Android's system UI.
8. With the helper paired and a writable destination selected in **Saves**, choose a desktop profile and press **Import desktop saves**; confirm a backup is created before the bounded LAN archive is extracted.
