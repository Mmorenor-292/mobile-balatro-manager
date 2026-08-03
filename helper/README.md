# MBM Helper 0.4.0

Portable Windows helper for the public Steam route.

## What it does

- Reads only common Steam locations and `steamapps/libraryfolders.vdf`.
- Detects Balatro candidates under `steamapps/common/Balatro`.
- Serves a small LAN-only pairing endpoint over a random or requested local port.
- Uses a one-time six-digit code that expires after ten minutes.
- Exposes a JSON health check, an allowlisted game manifest, and an asynchronous
  local build job plus APK download after pairing when the bundled upstream
  Balatro Mobile Maker executable is present.
- Accepts an explicitly selected `.love` or `.zip` source over the paired LAN
  connection (`/upload`) with a 500 MB cap; manual sources are kept in a temporary
  folder and used only when the build request selects `game=-1`.
- Accepts the user's installed Play Store base APK (`.apk`) and exposes a
  bounded **personal native build** route. The helper copies the upstream
  `balatro-portrait-mobile` source into an isolated temporary workspace,
  invokes its rootless builder, validates the resulting APK, and serves it only
  to the paired phone. It produces a separate `com.unofficial.balatro`
  package; it never modifies the official Play Store package.
- Detects the user's `%APPDATA%/Balatro` save profiles and `%APPDATA%/Balatro/Mods`
  folder without executing Lua or mod code. The app can request one bounded save
  ZIP over the same paired LAN connection; the archive excludes `Mods` and is
  deleted after the response.
- The app can also request a bounded desktop-mod ZIP over the same paired LAN
  connection. Desktop-only binaries are rejected; the phone performs a second
  inspection before importing the folders into its selected Mods tree.
- Does not upload game files, mods, saves, Steam credentials or Google credentials.
- Does not scan the whole disk and does not execute Lua or mod code.

## Use

```powershell
.\BMM.Helper.exe
.\BMM.Helper.exe --json --no-server
.\BMM.Helper.exe --steam-root "D:\SteamLibrary" --port 19077
.\BMM.Helper.exe --maker "C:\path\to\balatro-mobile-maker.exe"
```

Open MBM on the phone, choose **Steam copy**, enter the helper address and the six-digit code shown by the helper.
The phone and PC must be on the same local network. Stop the helper when finished.

For the Play Store route, open **Native Android** after pairing. MBM detects the
official package, uploads its base APK over the same local connection, and calls
`/build?native=1&game=-2`. The helper returns a signed personal APK through
`/build-artifact`; it never stores the source in Drive or includes it in the
public bundle.

## Verification

The local QA run covers one detected Balatro installation, `/health` (200), `/pair`
(six-digit code), `/manifest` after pairing, and a 401 response without a valid code.
Generated smoke logs stay outside the public source snapshot.

The helper can be published as a self-contained `win-x64` .NET single-file build.
The public source repository does not include the upstream **Balatro Mobile Maker**
executable or commercial game files. Place a user-downloaded Maker beside
`BMM.Helper.exe`, or pass `--maker PATH` for an explicitly supplied copy.
MBM starts the maker in an isolated temporary workspace, polls `/build-status`,
validates the resulting APK structure, and downloads it only over the paired LAN
connection. If the maker is missing, the manifest reports `builderAvailable: false`
and the helper returns a setup error rather than claiming an APK exists.

The bundled maker is sourced from
`https://github.com/blake502/balatro-mobile-maker`. Its upstream README and
dependency notices are included in the public documentation; the bundle manifest
records its source commit and SHA-256. It downloads its own upstream build tools
on first use. No Balatro game files, saves, credentials, or private signing keys
are included.

The native personal builder is sourced from
`https://github.com/ShaggyLorean/balatro-portrait-mobile`. The public source
repository does not redistribute that builder or its game assets. If installed
locally, it downloads only its documented Android packaging dependencies on first
use and receives the user's APK through the paired LAN request. No commercial APK
is shipped with MBM.
