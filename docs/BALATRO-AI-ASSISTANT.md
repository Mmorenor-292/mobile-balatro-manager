# Balatro AI Assistant

Balatro AI Assistant is a separate Android companion for Mobile Balatro Manager.
It can analyze crash logs, review a Mods archive, prepare a narrow compatibility
patch, or scaffold a new Steamodded mod. It does not contain an API key or a
ChatGPT password.

## How authentication works

1. Install and sign in to Codex CLI on the desktop using the user's own ChatGPT
   account.
2. Start BMM Helper 0.5.0. It shows a six-digit code that expires after ten minutes.
3. Pair the Android assistant to the helper over the same local network.
4. The helper invokes `codex exec` locally with `gpt-5.6-terra` and high reasoning.

The desktop keeps the OAuth session. The phone stores only the helper address and
a short-lived pairing token in app-private storage. No Google, Steam, ChatGPT or
Codex credential is bundled into or requested by the APK.

## Safety boundary

- Attachments are copied into a per-session staging directory.
- ZIP path traversal and executable/native payloads are rejected.
- Lua, installers and downloaded code are never executed by the helper.
- Codex runs with no interactive approvals and a workspace-write sandbox limited
  to the staging workspace.
- An analysis can be viewed directly. A repair or new mod becomes a ZIP only after
  review and explicit export.
- Importing that ZIP is a separate user action in Mobile Balatro Manager.

This design deliberately avoids autonomous changes to the installed game, saves or
original Mods folder. It is an assistant and repair workshop, not an unattended
remote-control agent.

## Supported tasks

- **Analyze crash:** identify evidence and precise next steps without changing files.
- **Repair incompatibility:** copy only affected text files and prepare the smallest
  reversible patch plus `REPAIR.md`.
- **Create mod:** generate a Steamodded-compatible source scaffold with metadata,
  `main.lua`, documentation and placeholder asset guidance.
- **Review Mods folder:** inspect a ZIP for dependency, metadata and mobile risks.

## Build

Build the web surface first, then the Android module:

```powershell
cd assistant-ui
npm ci
npm test
npm run lint
npm run build
cd ..
gradle :assistant:lintDebug :assistant:assembleDebug
```

Build the helper with .NET 10:

```powershell
dotnet publish helper/BmmHelper.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```
