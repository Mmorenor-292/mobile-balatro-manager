# Steam route

1. On Windows, download and extract `BMM-Helper-0.4.0-win-x64.zip`, then run `BMM.Helper.exe`. The bundle includes the upstream Maker beside it.
2. Keep the phone and PC on the same Wi‑Fi. Copy the address and six-digit code shown by the helper.
3. In MBM, open **Steam copy**, enter the address and code, and tap **Pair desktop**.
4. MBM shows the detected Balatro installation. If it is not found, tap **Choose .love or .zip** or **Choose folder**. Only that selected source is sent over the local network.
5. The Steam manifest should show `builderAvailable: true`. It also reports whether `%APPDATA%/Balatro` contains saves and whether `%APPDATA%/Balatro/Mods` contains mod folders. These are read-only summaries; the Maker does not silently package mods into the game source.
6. Review mods, choose `Safe`, `Mobile` or `Custom`, and answer **Do you want to import your progress?** before building. MBM starts the maker locally, polls its status, verifies that `balatro.apk` exists, and offers **Install on this phone**, **Save APK** and **Share APK** only after completion.
7. After pairing, open **Saves** to select the destination profile on the phone. When desktop saves are detected, **Import desktop saves** downloads one bounded ZIP over the same LAN connection, creates a backup first, excludes the `Mods` directory, and deletes the temporary archive after import.
8. To bring the desktop mod folders across separately, connect the phone's writable `Mods` folder once, open **Mods**, and tap the hard-drive import button. MBM requests a bounded LAN ZIP from `%APPDATA%/Balatro/Mods`, rejects desktop-only binaries, replaces same-name folders directly after staging, enables the imported mods, and records the operation in **Installation History**.

The helper never asks for Steam credentials and does not upload game files or saves to a cloud service. The save archive is opt-in, LAN-only, bounded to 100 MB/500 files, and sent only after the paired phone requests it. It exposes only the selected, allow-listed manifest and expires its pairing token. Stop it after use.

If a custom bundle has no maker executable, the build deliberately stays in pairing/manifest mode and shows a setup error instead of claiming an APK is ready. MBM never redistributes Balatro, bypasses DRM, or uploads the game to cloud storage.
