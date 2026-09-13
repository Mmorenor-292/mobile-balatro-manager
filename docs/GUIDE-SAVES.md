# Saves and progress

When a build route supports progress import, MBM asks:

- Yes, import this profile folder;
- Yes, import all compatible files;
- No, start clean;
- Review files first.

On Android, choose the folder containing the local save files from **Saves**, then choose the destination profile folder separately. MBM counts files with bounded limits, previews filename conflicts, creates a local ZIP restore point before replacement, and can export a ZIP backup through the Android document picker. Android private app sandboxes remain inaccessible unless the system picker explicitly exposes a user-authorized provider; MBM never bypasses that boundary.

When the Steam helper is paired, MBM can also show desktop profiles discovered under `%APPDATA%/Balatro`. Choose an Android destination folder, optionally select one profile, then press **Import desktop saves**. The helper sends a bounded archive over the local network only after that explicit action; the app creates a reversible backup before replacing conflicts. The archive excludes `%APPDATA%/Balatro/Mods`, which must be installed separately through the Mods screen.

Every backup belongs in `Backup History`; restore it there. Installs, updates, and permanent deletions are recorded separately in `Installation History`.
