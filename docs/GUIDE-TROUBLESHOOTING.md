# Troubleshooting

## The APK says it cannot be installed

Use the debug APK for testing. If Android reports a signature conflict, uninstall only the old **MBM - Mobile Balatro Manager** build, then install again. Do not uninstall Balatro. A release APK/AAB must be signed with the same stable key as future updates.

## Mods are not visible

Open **Mods**, choose **Connect folder**, and approve `ASET/Mods` or the `Mods` directory. If access expired, connect it again. Imported packages are inspected first, then installed directly and enabled.

## The game stopped opening

Disable the last installed mod or restore a save/configuration backup. Mod deletion is permanent; framework folders such as Lovely and Steamodded remain protected.

## IMM crashes with `Illegal version 1.0.1o-FULL (STM)`

IMM 2.5.1 uses a strict parser that rejects the mobile build suffix. In **Mods**, open the options for `imm` and press **Fix IMM mobile version**, then restart Balatro. MBM changes only IMM's end-anchored version pattern and keeps the untouched source in app-private compatibility storage.

## Native says it cannot be patched

That is an intentional safety result. Use the Steam/local route; MBM does not bypass Play Store signatures, DRM or sandboxing.

## Desktop pairing fails

Confirm the helper is running, both devices share the same LAN, the address includes the correct port, and the six-digit code has not expired. The helper does not work through the public internet or a VPN relay.
