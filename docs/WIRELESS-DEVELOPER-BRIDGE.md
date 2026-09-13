# Wireless Developer Bridge

## Goal

Let a developer inspect crash evidence and edit a phone's selected Balatro Mods
tree from a notebook without USB, ADB, root, or exposing Android storage broadly.

## Recommended architecture

MBM already owns a persisted Android Storage Access Framework permission for the
user-selected Mods tree, and the existing desktop helper already provides an explicitly paired
LAN channel. Extend that channel into a short-lived, bidirectional workspace:

1. The user starts **Wireless debug session** in MBM and pairs the existing
   six-digit helper code.
2. MBM sends a bounded manifest first: relative path, size, modified time and
   SHA-256. File contents are not transferred until the desktop asks for them.
3. The helper creates a local mirror under
   `%LOCALAPPDATA%\BMM.Helper\workspaces\<session-id>`. Codex or an editor can
   read and modify this mirror normally.
4. The helper returns a change set, not an unrestricted ZIP: added, modified and
   deleted paths plus the original hashes it observed.
5. MBM previews the changes. On approval it creates a full affected-mod backup,
   stages every new file, checks hashes again, swaps one mod at a time and rolls
   back that mod on failure.
6. The session expires automatically and the phone stops polling. The helper
   deletes tokens and can retain or delete the mirror at the user's choice.

## Transport and security

- Phone-initiated HTTPS or authenticated WebSocket over local Wi-Fi/Tailscale.
- Six-digit pairing plus a random 256-bit session token; token expires and is
  scoped to one selected Mods tree.
- No permanent Android listening socket and no cloud relay.
- Relative paths only; block traversal, symlinks, APKs and native executables.
- Limits: 20,000 files, 250 MB per session and 4 MB per editable text file.
- Read-only is the default. Write, delete and whole-folder replacement require
  an explicit mobile preview.
- Conflict if the phone's current SHA-256 differs from the mirror's base hash.
- Lua is treated as text and is never executed by MBM or the helper.

## Crash logs

Android does not let MBM read another app's private files. The bridge can expose
logs only when they are inside the selected shared tree, explicitly shared into
MBM, or exported by a cooperative Mobile Tools+ endpoint. Mobile Tools+ should
therefore write bounded rotating logs to a user-selected `MBM-Debug/Logs`
directory and never include saves or credentials.

## Delivery phases

1. **Mirror read-only:** manifest, selective download and helper workspace.
2. **Safe text sync:** `.lua`, `.json`, `.toml`, `.md` with preview and hashes.
3. **Atomic mod replacement:** assets, whole-folder backup and rollback.
4. **Live debug:** rotating Mobile Tools+ metrics/logs and optional game restart.

Phase 1 is enough for Codex to diagnose directly from the notebook. Phase 2 is
the first phase that permits remote repair. A general-purpose file server on the
phone is intentionally rejected because it widens the storage and network attack
surface without improving the common mod-debug workflow.
