# Release channels

MBM - Mobile Balatro Manager has explicit Android channels so a beta build cannot silently replace a production install.

| Channel | Application id | Version label | Artifact | Purpose |
| --- | --- | --- | --- | --- |
| Beta | `cl.mauricio.balatromods.beta` | `2.0.0-beta` | `Mobile-Mod-Manager-Public-vNext-beta.apk` / `.aab` | Side-by-side acceptance testing |
| Production candidate | `cl.mauricio.balatromods` | `2.0.0` | `Mobile-Mod-Manager-Public-vNext-release.apk` / `.aab` | Stable release candidate |
| Debug | `cl.mauricio.balatromods` | `2.0.0` | `Mobile-Mod-Manager-Public-vNext-debug.apk` | Local development only |

The beta and production variants use the same private local signing configuration in this package, while the keystore itself remains outside Drive. A future public store release must use the publisher-owned key and Play testing tracks. The beta channel does not alter Balatro, bypass Play Store licensing, or include commercial game files.
