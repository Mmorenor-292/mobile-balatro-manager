# Testing MBM - Mobile Balatro Manager from a notebook

## Recommended: test on your Android phone

This is the only route that validates the complete workflow with your real Balatro
installation and mods.

1. On the notebook, wait for Google Drive Desktop to sync the release folder, or open
   Google Drive in the browser and download `Balatro-Mobile-Mod-Manager-v2.0.1-release.apk`.
2. Transfer the APK to the phone with Drive, USB, Nearby Share, or Telegram.
3. On Android, open the APK from Drive or Files.
4. If prompted, allow that app to **Install unknown apps**, then install.
5. Open **MBM - Mobile Balatro Manager**, tap **Connect now**, and approve the `ASET/Mods` folder.
6. Confirm that Library lists your mods, toggle a harmless mod, undo the change, and
   open Discover and Rescue.

If Android reports that the package cannot be updated, uninstall only the old
**MBM - Mobile Balatro Manager** debug build and install again. Do not uninstall Balatro. Your game
and mod files are separate, but this removes the manager's own saved snapshots.

## Android Studio emulator: test the manager UI and file operations

Use an API 35 or newer x86_64 virtual device. From PowerShell:

```powershell
adb install -r "C:\path\to\Balatro-Mobile-Mod-Manager-v2.0.1-release.apk"
adb shell am start -n cl.mauricio.balatromods/.MainActivity
```

To test folder access, create a fixture folder inside the emulator's Downloads folder,
add a few dummy mod directories, then choose that folder from **Connect now**.

The existing Balatro Modded APK is ARM and normally will not install on this x86_64
emulator (`INSTALL_FAILED_NO_MATCHING_ABIS`). Use the real phone or an ARM emulator to
test direct launch into Balatro.

## Browser-only preview: inspect the interface

This route is fast and requires no Android tooling, but uses mock data and cannot
manage the phone's actual files.

```powershell
cd "C:\path\to\balatro-mod-deck-v2\ui"
npm ci
npm run dev -- --host 127.0.0.1 --port 4173
```

Open `http://127.0.0.1:4173` in the notebook browser. Stop the server with `Ctrl+C`.

## Minimum release smoke test

- Library renders at 360 × 800 without horizontal scrolling.
- Search and all four Library filters work.
- Discover switches between All, Mod Index, and Thunderstore.
- Quick Rescue asks for confirmation before changing files.
- Undo and History restore the previous hidden/active state.
- Guided Isolation accepts both **Balatro opened** and **Balatro failed** results.
- The app reopens after a force stop while a deliberately malformed dummy mod exists.
