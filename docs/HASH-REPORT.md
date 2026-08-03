# MBM - Mobile Balatro Manager — artifact hashes

Generated: 2026-08-02

All hashes are SHA-256. Release APK is verified with Android v2 + v3 signatures; release AAB is JAR-verified.

- Mobile-Mod-Manager-Public-vNext-debug.apk: 994ADB551737982A8E8F569EFFC2C9DA90295CA39988E882012344E27F3CFB73
- Mobile-Mod-Manager-Public-vNext-release.apk: 7C1666961CA04F68CEA3EDE1F640B91F1D42683A4852F62E468869A240FBF417
- Mobile-Mod-Manager-Public-vNext-release.aab: 5EA0E4273A80C185AE63E2A29A1B52A9FC713445D7167185E2716FE60C42B1D6
- Mobile-Mod-Manager-Public-vNext-release-unsigned.apk: 7B8F70604D81F3F5DE26BCC294F1240037D15BC06C79122F49829CDFC368B4D1
- Mobile-Mod-Manager-Public-vNext-release-unsigned.aab: CD36128326D2895F6E5F6AE049EABA67CEBEC259E12403994A7C36F1AAC00A1C
- Mobile-Mod-Manager-Public-vNext-web.zip: 7E085FC6CBB681DE7B12EF49DD296CB68335DF5A3C25F8B458E4AE6B4A5AECF4

Validation:

- Web tests: 8/8, ESLint and Vite build: passed
- Gradle unit tests, lint, debug APK, release APK/AAB: passed
- API31 smoke: manager rendered after structuredClone fallback; Native detected cl.mauricio.balatro.modded; Balatro returned to its modded menu
- No commercial Balatro APK, game source, save or signing key included
