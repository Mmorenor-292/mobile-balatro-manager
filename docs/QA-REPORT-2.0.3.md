# Mobile Balatro Manager 2.0.3 QA report

Date: 2026-08-06

## Scope

This release repairs catalog update detection, exact dependency/version checks,
mobile IMM compatibility, and operation feedback. It also specifies a safe
wireless developer workspace for a future release.

## Automated checks

- Vitest: 12 tests passed.
- ESLint: passed.
- Vite production build: passed and generated the embedded WebView assets.
- Android unit tests: passed.
- Android Lint and release Lint Vital: passed.
- Signed release packaging: passed.

## Android emulator validation

Device: `BMM_Public_API36`, Android API 36, x86_64, serial `emulator-5570`.

- Installed and launched version code `23`, version name `2.0.3`.
- Connected a test Mods tree through Android's Storage Access Framework.
- Confirmed `Pokermon-Maelmc` does not satisfy the exact `Pokermon` dependency.
- Confirmed `Pokermon 3.8.1-0724a` is diagnosed as too old for
  `Pokermon (>=3.8.1-0731b)`.
- Confirmed refresh patches IMM's strict parser automatically. The source
  changed only from the end-anchored mobile-incompatible pattern to the bounded
  suffix-tolerant pattern, with an app-private backup.
- Loaded the live catalog and real thumbnails.
- Confirmed the version selector exposes the latest source revision and the
  installed version separately.
- Updated Pokermon through Discover; the resulting metadata version was
  `3.8.1-0731b`.
- Captured `UPDATING MOD…` on the active card and
  `QUEUED · DISABLING MOD` on a second action. Both actions completed serially
  while navigation remained available.
- No `FATAL EXCEPTION` for `cl.mauricio.balatromods` appeared during the flow.

## Release identity

- APK size: `5,670,247` bytes.
- APK SHA-256: `42cca340d5031e41cc722527f72cf86fa4741a3f3c576b22c5d5c8127c7f23c5`.
- Signing certificate SHA-256:
  `51aff3d6b40e88d52623967a2595ab0dfca7c5a635e3cd107e2fa2ec830c13a9`.
- The certificate matches the distributed 2.0.2 APK, so Android can update it
  in place without clearing MBM's own data.

## Scope boundary

The wireless developer bridge in `WIRELESS-DEVELOPER-BRIDGE.md` is an approved
design, not a feature shipped in 2.0.3. Android cannot expose another app's
private files without cooperation; Mobile Tools+ must export bounded logs to a
shared directory for remote inspection.
