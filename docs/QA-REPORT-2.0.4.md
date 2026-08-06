# MBM 2.0.4 QA report

**Date:** 2026-08-06  
**Package:** `cl.mauricio.balatromods`  
**Version:** `2.0.4` (`versionCode 24`)

## Result

The release passed the focused web, Java, Android lint, packaging, signature, upgrade,
catalog-fallback, and diagnostic-export checks described below.

## Automated checks

- Web UI: 15 Vitest tests passed; ESLint and Vite production build passed.
- Android: debug unit tests, debug lint, release lint, and release assembly passed.
- APK signature: Android v2 and v3 signatures verified.
- APK SHA-256: `3C59642B1CAFADCDC2391803E5D49718D47CE975BF5376F1BF4A578EE32EEC47`.
- Signing certificate SHA-256:
  `51aff3d6b40e88d52623967a2595ab0dfca7c5a635e3cd107e2fa2ec830c13a9`.

## Android acceptance checks

- Tested on disposable AVD `BMM_Public_API36`, serial `emulator-5570`.
- Installed MBM 2.0.3 (`versionCode 23`) and upgraded in place to 2.0.4
  (`versionCode 24`) with no signature conflict.
- The connected `MBMTest/Mods` SAF permission and three active test mods survived the
  upgrade; a cold launch rendered the real native state with no fatal or WebView error.
- A live GitHub anonymous API request was rate-limited. The official releases feed fallback
  then loaded 10 Pokermon releases, including `3.8.0` and `3.7.0`.
- Installed Pokermon `3.8.1-0731b` was treated as current/newer than published `3.8.0`;
  no false update badge appeared and the action was labelled **Switch version**.

## Diagnostic ZIP acceptance checks

- A real connected test folder produced a readable 565,346-byte ZIP with 120 entries.
- Required entries were present: `README.txt`, `environment.json`, `catalog-status.json`,
  `install-history.json`, and `inventory.json`.
- Useful bounded Lua/JSON/Markdown files were included; no APK or `.jkr` save was included.
- The Android Create Document picker opened with the generated ZIP filename.
- The Android share intent resolved. Telegram itself was not installed in this disposable
  AVD, so selecting Telegram as the final recipient remains a physical-device check.

## Remaining release limit

A final physical ARM64 device pass is still recommended before a broad public rollout.
