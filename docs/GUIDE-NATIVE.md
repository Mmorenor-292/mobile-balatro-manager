# Native Android route

Native now supports a **personal, rootless clone** workflow. It does not patch,
replace, or re-sign the Play Store package. The original app remains installed
and the output is a separate `com.unofficial.balatro` package that the user can
remove independently.

## Simplest flow (Play Store copy)

1. Install Balatro from Google Play and open it once.
2. On the PC, run `BMM.Helper.exe`; keep the phone and PC on the same Wi-Fi.
3. In MBM open **Native Android**, pair the helper, and let MBM detect the
   official package (`com.playstack.balatro.android` or
   `com.playstack.balatro`). Detection is read-only.
4. Press **Create personal APK**. MBM streams only the installed base APK to
   the paired helper over the local network. The helper runs the bundled
   `balatro-portrait-mobile` builder, adds Lovely and Steamodded, signs the
   separate output, and returns it to MBM.
5. Press **Install on this phone**. Android will show the normal package
   installer; review the new package before accepting it. Then connect or
   import the Mods folder in MBM as usual.

If Android hides the package, use **Select APK or split APK** and choose a
user-owned base APK. The helper has the same 500 MB bounded upload limit. If the
helper does not report the native builder as ready, update/re-extract the public
helper bundle and restart it.

The upstream project explicitly documents this rootless Play Store-source path,
including a Termux build from an installed official app:
<https://github.com/ShaggyLorean/balatro-portrait-mobile>.

This route is a personal-use build: MBM does not download a commercial APK,
send the source to Drive/WhatsApp, or include Balatro files in its public
bundle. The user must own the Play Store copy and must keep the generated APK
private. The older Steamodded documentation still says the official Play Store
package itself is not directly moddable; the separate-package builder is the
community workaround, not an in-place patch:
<https://github.com/Steamodded/smods/wiki>.
