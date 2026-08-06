package cl.mauricio.balatromods;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.webkit.WebViewAssetLoader;
import androidx.documentfile.provider.DocumentFile;
import androidx.core.content.FileProvider;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int PICK_MODS_TREE = 1607;
    private static final int CREATE_REPORT = 1608;
    private static final int PICK_IMPORT_FILE = 1609;
    private static final int PICK_IMPORT_FOLDER = 1610;
    private static final int PICK_NATIVE_APK = 1611;
    private static final int PICK_SAVE_TREE = 1612;
    private static final int CREATE_SAVE_EXPORT = 1613;
    private static final int CREATE_APK_EXPORT = 1614;
    private static final int PICK_STEAM_SOURCE_FILE = 1615;
    private static final int PICK_STEAM_SOURCE_FOLDER = 1616;
    private static final int PICK_SAVE_TARGET_TREE = 1617;
    private static final int CREATE_DIAGNOSTIC_ZIP = 1618;
    private static final String PREFS = "balatro_mod_deck";
    private static final String PREF_TREE_URI = "mods_tree_uri";
    private static final String PREF_INSTALL_HISTORY = "install_history";
    private static final String PREF_SAVE_TREE_URI = "save_tree_uri";
    private static final String PREF_SAVE_TARGET_URI = "save_target_uri";
    private static final String PREF_SAVE_BACKUPS = "save_backups";
    private static final String PREF_HISTORY_RETENTION = "history_retention";
    private static final String KNOWN_BALATRO_PACKAGE = "cl.mauricio.balatro.modded";
    private static final String[] NATIVE_BALATRO_PACKAGES = {
            "com.playstack.balatro.android",
            "com.playstack.balatro",
            KNOWN_BALATRO_PACKAGE
    };
    private static final String KNOWN_PROVIDER = "cl.mauricio.balatro.modded.saves";
    private static final String WEB_HOST = "appassets.androidplatform.net";
    private static final String WEB_URL =
            "https://" + WEB_HOST + "/assets/web/index.html";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<CatalogItem> catalog = new ArrayList<>();

    private SharedPreferences preferences;
    private SnapshotStore snapshots;
    private CatalogClient catalogClient;
    private WebView webView;
    private Uri selectedTreeUri;
    private Uri selectedSaveTreeUri;
    private Uri selectedSaveTargetUri;
    private ModRepository.ScanResult scan;
    private RecoverySession recovery = RecoverySession.empty();
    private boolean pageReady;
    private boolean loading;
    private final Object operationLock = new Object();
    private final List<OperationStatus> operations = new ArrayList<>();
    private volatile OperationStatus runningOperation;
    private String message = "";
    private String pendingReport = "";
    private File pendingDiagnosticArchive;
    private File pendingSaveArchive;
    private String nativeCompatibility = "unknown";
    private String nativePreflight = "Select an APK or installed copy to begin.";
    private boolean desktopPaired;
    private String desktopManifest = "";
    private String desktopBaseUrl = "";
    private String desktopToken = "";
    private String desktopBuild = "";
    private String nativeInstalledPackage = "";
    private File pendingArtifact;
    private Uri selectedSteamSourceUri;
    private String selectedSteamSourceName = "";
    private boolean steamSourceUploaded;

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(4, 25, 29));
        getWindow().setNavigationBarColor(Color.rgb(4, 25, 29));

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        snapshots = new SnapshotStore(this);
        snapshots.setRetentionLimit(Math.max(0, Math.min(preferences.getInt(PREF_HISTORY_RETENTION, 20), 1000)));
        catalogClient = new CatalogClient(this);
        catalog.addAll(catalogClient.cached());

        String storedUri = preferences.getString(PREF_TREE_URI, "");
        if (!storedUri.isBlank()) {
            selectedTreeUri = Uri.parse(storedUri);
        }
        String storedSaveUri = preferences.getString(PREF_SAVE_TREE_URI, "");
        if (!storedSaveUri.isBlank()) selectedSaveTreeUri = Uri.parse(storedSaveUri);
        String storedSaveTargetUri = preferences.getString(PREF_SAVE_TARGET_URI, "");
        if (!storedSaveTargetUri.isBlank()) selectedSaveTargetUri = Uri.parse(storedSaveTargetUri);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        // The bundled UI persists only non-sensitive preferences (wallpaper
        // selector). Keep DOM storage enabled; all game/mod data remains in
        // the native bridge and never enters browser storage.
        settings.setDomStorageEnabled(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setBackgroundColor(Color.rgb(4, 25, 29));
        webView.addJavascriptInterface(new NativeBridge(), "AndroidBridge");
        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler(
                        "/assets/",
                        new WebViewAssetLoader.AssetsPathHandler(this)
                )
                .build();
        webView.setWebViewClient(new LocalOnlyClient(assetLoader));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                android.util.Log.e("BMM_WEB", consoleMessage.message()
                        + " @" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                return true;
            }
        });
        setContentView(webView);
        webView.loadUrl(WEB_URL);
    }

    @Override
    protected void onDestroy() {
        pageReady = false;
        io.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
        }
        super.onDestroy();
    }

    private void refresh(boolean withCatalog) {
        if (loading) {
            return;
        }
        loading = true;
        pushState();
        io.execute(() -> {
            String nextMessage = "";
            try {
                if (selectedTreeUri != null) {
                    scan = ModRepository.scan(this, selectedTreeUri);
                    int compatibilityFixes = applyAutomaticCompatibilityFixes();
                    if (compatibilityFixes > 0) {
                        scan = ModRepository.scan(this, selectedTreeUri);
                        nextMessage = compatibilityFixes == 1
                                ? "IMM mobile compatibility was repaired automatically."
                                : compatibilityFixes + " mobile compatibility fixes were applied.";
                    }
                }
                if (withCatalog) {
                    List<CatalogItem> refreshedCatalog = catalogClient.fetch();
                    catalog.clear();
                    catalog.addAll(refreshedCatalog);
                    nextMessage = nextMessage.isBlank()
                            ? "Catalog updated" : nextMessage + " Catalog updated.";
                }
            } catch (SecurityException error) {
                selectedTreeUri = null;
                scan = null;
                preferences.edit().remove(PREF_TREE_URI).apply();
                nextMessage = "Folder access expired. Connect it again.";
            } catch (Exception error) {
                nextMessage = readable(error);
            }
            final String finalMessage = nextMessage;
            main.post(() -> {
                loading = false;
                message = finalMessage;
                pushState();
            });
        });
    }

    private void chooseFolder(boolean automatic) {
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            );
            if (automatic && isProviderAvailable(KNOWN_PROVIDER)) {
                try {
                    Uri initial = DocumentsContract.buildDocumentUri(
                            KNOWN_PROVIDER,
                            "root:ASET/Mods"
                    );
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
                } catch (Exception ignored) {
                    // The system picker remains a complete fallback.
                }
            }
            try {
                startActivityForResult(intent, PICK_MODS_TREE);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible folder picker.";
                pushState();
            }
        });
    }

    private void importMod() {
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/zip",
                    "application/octet-stream",
                    "application/x-7z-compressed"
            });
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try {
                startActivityForResult(intent, PICK_IMPORT_FILE);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible file picker.";
                pushState();
            }
        });
    }

    private void importModFolder() {
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try {
                startActivityForResult(intent, PICK_IMPORT_FOLDER);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible folder picker.";
                pushState();
            }
        });
    }

    private void selectSteamGame() {
        if (!desktopPaired || desktopBaseUrl.isBlank() || desktopToken.isBlank()) {
            message = "Pair the desktop helper before selecting a manual game source.";
            pushState();
            return;
        }
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/octet-stream",
                    "application/zip",
                    "application/x-zip-compressed"
            });
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(intent, PICK_STEAM_SOURCE_FILE);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible file picker.";
                pushState();
            }
        });
    }

    private void selectSteamFolder() {
        if (!desktopPaired || desktopBaseUrl.isBlank() || desktopToken.isBlank()) {
            message = "Pair the desktop helper before selecting a manual game source.";
            pushState();
            return;
        }
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try {
                startActivityForResult(intent, PICK_STEAM_SOURCE_FOLDER);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible folder picker.";
                pushState();
            }
        });
    }

    private void handleSteamSourceResult(int resultCode, Intent data, boolean folder) {
        if (resultCode != RESULT_OK || data == null
                || (data.getData() == null && data.getClipData() == null)) return;
        Uri uri = data.getData();
        if (folder) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // Immediate upload still works when the provider does not persist grants.
            }
        }
        uploadSteamSource(uri, folder);
    }

    private void uploadSteamSource(Uri uri, boolean folder) {
        io.execute(() -> {
            File temporary = null;
            try {
                temporary = prepareSteamSource(uri, folder);
                String filename = folder ? "Balatro.zip" : safeSourceName(displayName(uri));
                long length = temporary.length();
                if (length <= 0 || length > 500L * 1024L * 1024L) {
                    throw new IllegalArgumentException("The selected source must be between 1 byte and 500 MB.");
                }
                URL endpoint = new URL(desktopBaseUrl + "/upload?token="
                        + Uri.encode(desktopToken) + "&name=" + Uri.encode(filename));
                HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(120000);
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                connection.setFixedLengthStreamingMode(length);
                try (OutputStream output = connection.getOutputStream();
                     InputStream input = new FileInputStream(temporary)) {
                    byte[] buffer = new byte[32_768];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                int status = connection.getResponseCode();
                String body = readConnectionBody(connection, status);
                connection.disconnect();
                if (status >= 400) throw new IllegalStateException(body.isBlank() ? "The helper rejected the source." : body);
                JSONObject manifest = getJson(desktopBaseUrl + "/manifest?token=" + Uri.encode(desktopToken));
                steamSourceUploaded = true;
                selectedSteamSourceName = filename;
                desktopManifest = manifest.toString();
                main.post(() -> {
                    message = "Manual source uploaded to the paired helper. It will be used for the next build.";
                    pushState();
                });
            } catch (Exception error) {
                main.post(() -> {
                    message = "Could not upload the selected source: " + readable(error);
                    pushState();
                });
            } finally {
                if (temporary != null) {
                    //noinspection ResultOfMethodCallIgnored
                    temporary.delete();
                }
            }
        });
    }

    private File prepareSteamSource(Uri uri, boolean folder) throws Exception {
        String extension = folder ? ".zip" : ".love";
        if (!folder) {
            String name = safeSourceName(displayName(uri));
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".love") && !lower.endsWith(".zip")) {
                throw new IllegalArgumentException("Choose a .love file or a ZIP containing the game source.");
            }
            extension = lower.endsWith(".zip") ? ".zip" : ".love";
        }
        File target = new File(getCacheDir(), "bmm-steam-source-" + System.nanoTime() + extension);
        if (folder) {
            DocumentFile root = DocumentFile.fromTreeUri(this, uri);
            if (root == null || !root.isDirectory()) throw new IllegalArgumentException("The selected source folder is not readable.");
            try (OutputStream output = new FileOutputStream(target);
                 ZipOutputStream zip = new ZipOutputStream(output)) {
                int[] entries = {0};
                long[] bytes = {0L};
                zipTree(root, "", zip, entries, bytes);
            }
        } else {
            try (InputStream input = getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IllegalStateException("The selected source could not be opened.");
                copyBounded(input, output, 500L * 1024L * 1024L);
            }
        }
        return target;
    }

    private void zipTree(DocumentFile directory, String prefix, ZipOutputStream zip, int[] entries, long[] bytes) throws Exception {
        for (DocumentFile child : directory.listFiles()) {
            String childName = child.getName() == null ? "item" : child.getName().replaceAll("[\\\\/]+", "_");
            String path = prefix.isBlank() ? childName : prefix + "/" + childName;
            if (child.isDirectory()) {
                zipTree(child, path, zip, entries, bytes);
                continue;
            }
            if (!child.isFile()) continue;
            if (++entries[0] > 50_000) throw new IllegalArgumentException("The selected folder contains too many files.");
            ZipEntry entry = new ZipEntry(path);
            zip.putNextEntry(entry);
            try (InputStream input = getContentResolver().openInputStream(child.getUri())) {
                if (input == null) throw new IllegalStateException("Could not read " + path);
                byte[] buffer = new byte[16_384];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    bytes[0] += read;
                    if (bytes[0] > 500L * 1024L * 1024L) throw new IllegalArgumentException("The selected folder exceeds the 500 MB safety limit.");
                    zip.write(buffer, 0, read);
                }
            }
            zip.closeEntry();
        }
    }

    private static void copyBounded(InputStream input, OutputStream output, long limit) throws Exception {
        byte[] buffer = new byte[32_768];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("The selected source exceeds the 500 MB safety limit.");
            output.write(buffer, 0, read);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "Balatro.love" : last;
    }

    private static String safeSourceName(String raw) {
        String name = raw == null ? "Balatro.love" : raw.replaceAll("[\\\\/\"<>|:*?]+", "_");
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".love") ? name : name + ".love";
    }

    private static String readConnectionBody(HttpURLConnection connection, int status) throws Exception {
        InputStream raw = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (raw == null) return "";
        try (InputStream input = raw) {
            StringBuilder body = new StringBuilder();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) body.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            return body.toString();
        }
    }

    private void pairDesktop(String address, String code) {
        if (address == null || address.isBlank() || code == null || !code.matches("\\d{6}")) {
            message = "Enter the helper address and its six-digit pairing code.";
            pushState();
            return;
        }
        io.execute(() -> {
            String nextMessage;
            try {
                URL base = new URL(address.startsWith("http://") || address.startsWith("https://")
                        ? address : "http://" + address);
                InetAddress resolved = InetAddress.getByName(base.getHost());
                if (!resolved.isLoopbackAddress() && !resolved.isSiteLocalAddress()) {
                    throw new IllegalArgumentException("Pairing is limited to the local network.");
                }
                String root = base.toString().replaceAll("/+$", "");
                JSONObject manifest = getJson(root + "/manifest?code=" + code);
                desktopPaired = true;
                desktopManifest = manifest.toString();
                desktopBaseUrl = root;
                desktopToken = manifest.optString("token", "");
                steamSourceUploaded = false;
                selectedSteamSourceName = "";
                nextMessage = "Desktop paired. Choose the detected Balatro copy in the next step.";
            } catch (Exception error) {
                desktopPaired = false;
                desktopManifest = "";
                desktopBaseUrl = "";
                desktopToken = "";
                nextMessage = "Could not pair with the helper: " + readable(error);
            }
            final String result = nextMessage;
            main.post(() -> { message = result; pushState(); });
        });
    }

    private JSONObject getJson(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            if (input == null) throw new IllegalStateException("The helper returned no response.");
            StringBuilder bodyBuilder = new StringBuilder();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) bodyBuilder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            String body = bodyBuilder.toString();
            if (status >= 400) throw new IllegalStateException(body.isBlank() ? "HTTP " + status : body);
            return new JSONObject(body);
        } finally {
            connection.disconnect();
        }
    }

    private void startDesktopBuild(boolean importSaves) {
        if (!desktopPaired || desktopBaseUrl.isBlank() || desktopToken.isBlank()) {
            message = "Pair the desktop helper before starting a build.";
            pushState();
            return;
        }
        io.execute(() -> {
            try {
                String selectedGame = steamSourceUploaded ? "-1" : "0";
                JSONObject started = getJson(desktopBaseUrl + "/build?token=" + Uri.encode(desktopToken) + "&game=" + selectedGame);
                String jobId = started.optString("jobId", "");
                if (jobId.isBlank()) throw new IllegalStateException("The helper did not return a build job.");
                desktopBuild = new JSONObject().put("jobId", jobId).put("status", "queued").toString();
                main.post(() -> { message = "Desktop build started. Keep the helper running."; pushState(); });
                long deadline = System.currentTimeMillis() + 45L * 60L * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(1800L);
                    JSONObject status = getJson(desktopBaseUrl + "/build-status?token=" + Uri.encode(desktopToken) + "&id=" + Uri.encode(jobId));
                    desktopBuild = status.toString();
                    main.post(this::pushState);
                    String state = status.optString("status", "");
                    if ("completed".equals(state)) { message = importSaves
                            ? "APK built and verified. Open Saves to review and import desktop progress."
                            : "APK built and verified on the paired desktop. Choose Save APK to export it."; main.post(this::pushState); return; }
                    if ("failed".equals(state)) { message = status.optString("error", "The desktop build failed."); main.post(this::pushState); return; }
                }
                message = "The desktop build timed out. Review the helper log before retrying.";
                main.post(this::pushState);
            } catch (Exception error) { message = "Desktop build failed: " + readable(error); main.post(this::pushState); }
        });
    }

    /**
     * Build a separate, personal mod-capable APK from the user's installed
     * Play Store base package. The original package is only read; it is never
     * modified, replaced, or signed with the manager's certificate.
     */
    private void buildNativePersonal() {
        if (!desktopPaired || desktopBaseUrl.isBlank() || desktopToken.isBlank()) {
            message = "Pair the desktop helper before creating the personal Native copy.";
            pushState();
            return;
        }
        io.execute(() -> {
            try {
                String packageName = nativeInstalledPackage;
                if (packageName.isBlank()) {
                    for (String candidate : NATIVE_BALATRO_PACKAGES) {
                        if (candidate.equals(KNOWN_BALATRO_PACKAGE)) continue;
                        try {
                            getPackageManager().getApplicationInfo(candidate, 0);
                            packageName = candidate;
                            break;
                        } catch (PackageManager.NameNotFoundException ignored) {
                            // Try the next official package id.
                        }
                    }
                }
                if (packageName.isBlank()) {
                    throw new IllegalStateException("Install the official Play Store Balatro and launch it once before building.");
                }
                boolean official = packageName.equals("com.playstack.balatro.android")
                        || packageName.equals("com.playstack.balatro");
                if (!official) {
                    throw new IllegalStateException("The detected package is not an official Play Store Balatro copy.");
                }
                ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
                File source = new File(info.sourceDir == null ? "" : info.sourceDir);
                if (!source.isFile() || !source.canRead()) {
                    throw new IllegalStateException("Android did not expose the installed base APK. Use Select APK and choose a user-owned copy.");
                }
                long length = source.length();
                if (length <= 0 || length > 500L * 1024L * 1024L) {
                    throw new IllegalStateException("The installed base APK exceeds the 500 MB safety limit.");
                }
                URL uploadUrl = new URL(desktopBaseUrl + "/upload?token="
                        + Uri.encode(desktopToken) + "&name=Balatro-PlayStore.apk");
                HttpURLConnection upload = (HttpURLConnection) uploadUrl.openConnection();
                upload.setConnectTimeout(5000);
                upload.setReadTimeout(120000);
                upload.setDoOutput(true);
                upload.setRequestMethod("POST");
                upload.setRequestProperty("Content-Type", "application/vnd.android.package-archive");
                upload.setFixedLengthStreamingMode(length);
                try (OutputStream output = upload.getOutputStream(); InputStream input = new FileInputStream(source)) {
                    copyBounded(input, output, 500L * 1024L * 1024L);
                }
                int uploadStatus = upload.getResponseCode();
                String uploadBody = readConnectionBody(upload, uploadStatus);
                upload.disconnect();
                if (uploadStatus >= 400) {
                    throw new IllegalStateException(uploadBody.isBlank() ? "The helper rejected the installed Play Store APK." : uploadBody);
                }
                JSONObject started = getJson(desktopBaseUrl + "/build?token=" + Uri.encode(desktopToken)
                        + "&native=1&game=-2");
                String jobId = started.optString("jobId", "");
                if (jobId.isBlank()) throw new IllegalStateException("The helper did not return a native build job.");
                desktopBuild = new JSONObject().put("jobId", jobId).put("status", "queued").put("kind", "playstore-personal").toString();
                main.post(() -> { message = "Personal Play Store build started. Keep the helper running."; pushState(); });
                long deadline = System.currentTimeMillis() + 60L * 60L * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(2000L);
                    JSONObject status = getJson(desktopBaseUrl + "/build-status?token=" + Uri.encode(desktopToken)
                            + "&id=" + Uri.encode(jobId));
                    desktopBuild = status.toString();
                    main.post(this::pushState);
                    String state = status.optString("status", "");
                    if ("completed".equals(state)) {
                        main.post(() -> { message = "Personal mod-capable APK built. The original Play Store app remains untouched."; pushState(); });
                        return;
                    }
                    if ("failed".equals(state)) {
                        String error = status.optString("error", "The personal Play Store build failed.");
                        main.post(() -> { message = error; pushState(); });
                        return;
                    }
                }
                main.post(() -> { message = "The personal Play Store build timed out. Review the helper log before retrying."; pushState(); });
            } catch (Exception error) {
                String failure = "Could not create the personal Play Store copy: " + readable(error);
                main.post(() -> { message = failure; pushState(); });
            }
        });
    }

    private File downloadDesktopArtifactFile() throws Exception {
        if (desktopBaseUrl.isBlank() || desktopToken.isBlank() || desktopBuild.isBlank()) {
            throw new IllegalStateException("Build an APK on the paired desktop first.");
        }
        JSONObject status = new JSONObject(desktopBuild);
        String jobId = status.optString("jobId", "");
        if (!"completed".equals(status.optString("status")) || jobId.isBlank()) {
            throw new IllegalStateException("The APK is not ready.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(
                desktopBaseUrl + "/build-artifact?token=" + Uri.encode(desktopToken)
                        + "&id=" + Uri.encode(jobId)
        ).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(120000);
        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IllegalStateException("The helper rejected the APK download (HTTP " + code + ").");
        }
        File artifact = new File(getCacheDir(), "balatro-bmm.apk");
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(artifact)) {
            byte[] buffer = new byte[32_768];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 500L * 1024L * 1024L) {
                    throw new IllegalStateException("APK exceeds the 500 MB safety limit.");
                }
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        if (!artifact.isFile() || artifact.length() == 0) {
            throw new IllegalStateException("The helper returned an empty APK.");
        }
        pendingArtifact = artifact;
        return artifact;
    }

    private void downloadDesktopArtifact() {
        io.execute(() -> {
            try {
                downloadDesktopArtifactFile();
                main.post(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.setType("application/vnd.android.package-archive");
                    intent.putExtra(Intent.EXTRA_TITLE, "balatro-bmm.apk");
                    try {
                        startActivityForResult(intent, CREATE_APK_EXPORT);
                    } catch (ActivityNotFoundException error) {
                        message = "No compatible APK exporter is installed.";
                        pushState();
                    }
                });
            } catch (Exception error) {
                message = readable(error);
                main.post(this::pushState);
            }
        });
    }

    private void shareDesktopArtifact() {
        io.execute(() -> {
            try {
                File artifact = downloadDesktopArtifactFile();
                Uri uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        artifact
                );
                main.post(() -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/vnd.android.package-archive");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    share.setClipData(ClipData.newRawUri("Balatro APK", uri));
                    try {
                        startActivity(Intent.createChooser(share, "Share verified APK"));
                        message = "Verified APK ready in the share sheet.";
                        pushState();
                    } catch (ActivityNotFoundException error) {
                        message = "No compatible sharing app is installed.";
                        pushState();
                    }
                });
            } catch (Exception error) {
                message = readable(error);
                main.post(this::pushState);
            }
        });
    }

    private void installDesktopArtifact() {
        io.execute(() -> {
            try {
                File artifact = downloadDesktopArtifactFile();
                Uri uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        artifact
                );
                main.post(() -> {
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    install.setClipData(ClipData.newRawUri("Balatro APK", uri));
                    try {
                        startActivity(install);
                        message = "Android opened the installer. Review the package before confirming.";
                        pushState();
                    } catch (ActivityNotFoundException error) {
                        message = "Android could not open an APK installer. Use Save APK instead.";
                        pushState();
                    }
                });
            } catch (Exception error) {
                message = readable(error);
                main.post(this::pushState);
            }
        });
    }

    private void selectNativeApk() {
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.android.package-archive");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(intent, PICK_NATIVE_APK);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible APK picker.";
                pushState();
            }
        });
    }

    private void chooseSaveFolder() {
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try {
                startActivityForResult(intent, PICK_SAVE_TREE);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible save-folder picker.";
                pushState();
            }
        });
    }

    private void chooseSaveTarget() {
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try {
                startActivityForResult(intent, PICK_SAVE_TARGET_TREE);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible save-target picker.";
                pushState();
            }
        });
    }

    private void handlePickedSaveTree(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }
        } catch (SecurityException ignored) { }
        selectedSaveTreeUri = uri;
        preferences.edit().putString(PREF_SAVE_TREE_URI, uri.toString()).apply();
        runFileOperation(() -> {
            int count = countSaveFiles();
            return count + " save file" + (count == 1 ? "" : "s") + " found. Preview before importing.";
        });
    }

    @SuppressLint("WrongConstant")
    private void handlePickedSaveTargetTree(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException ignored) { }
        selectedSaveTargetUri = uri;
        preferences.edit().putString(PREF_SAVE_TARGET_URI, uri.toString()).apply();
        message = "Save target connected. Preview conflicts before importing.";
        pushState();
    }

    private DocumentFile requireSaveRoot(Uri uri, boolean writable) {
        if (uri == null) throw new IllegalStateException("Choose a save folder first.");
        DocumentFile root = DocumentFile.fromTreeUri(this, uri);
        if (root == null || !root.isDirectory() || (writable && !root.canWrite())) {
            throw new IllegalStateException("The selected save folder is unavailable or not writable.");
        }
        return root;
    }

    private int countSaveConflicts(DocumentFile source, DocumentFile target, int depth) throws Exception {
        if (depth > 5) throw new IllegalArgumentException("Save folder nesting is too deep.");
        int conflicts = 0;
        for (DocumentFile file : source.listFiles()) {
            DocumentFile existing = target.findFile(file.getName());
            if (file.isDirectory()) {
                if (existing != null && existing.isDirectory()) {
                    conflicts += countSaveConflicts(file, existing, depth + 1);
                }
            } else if (file.isFile() && existing != null) {
                conflicts++;
            }
        }
        return conflicts;
    }

    private DocumentFile saveSourceForProfile(String option, String profile) throws Exception {
        DocumentFile source = requireSaveRoot(selectedSaveTreeUri, false);
        if ("selected".equals(option) && profile != null && !profile.isBlank() && !"Root folder".equals(profile)) {
            DocumentFile chosen = source.findFile(profile);
            if (chosen == null || !chosen.isDirectory()) throw new IllegalStateException("The selected save profile is no longer available.");
            return chosen;
        }
        return source;
    }

    private void importSave(String option, String profile) {
        if (selectedSaveTreeUri == null) {
            chooseSaveFolder();
            return;
        }
        if (selectedSaveTargetUri == null) {
            chooseSaveTarget();
            return;
        }
        runFileOperation(() -> {
            if (selectedSaveTreeUri.toString().equals(selectedSaveTargetUri.toString())) {
                throw new IllegalStateException("Source and target save folders must be different.");
            }
            DocumentFile source = saveSourceForProfile(option, profile);
            DocumentFile target = requireSaveRoot(selectedSaveTargetUri, true);
            int files = countSaveFiles(source, 0);
            int conflicts = countSaveConflicts(source, target, 0);
            if (files == 0) throw new IllegalStateException("No save files were found in the source folder.");
            File backup = createSaveBackup(target, "Before import");
            copySaveTree(source, target, new int[]{0}, new long[]{0});
            String selected = "all".equals(option) ? "all compatible files" : (profile == null || profile.isBlank() ? "this profile folder" : profile);
            return files + " save file(s) imported for " + selected + ". "
                    + conflicts + " conflict(s) were replaced. Reversible backup saved.";
        });
    }

    private void importDesktopSave(String profile) {
        if (!desktopPaired || desktopBaseUrl.isBlank() || desktopToken.isBlank()) {
            message = "Pair the desktop helper before importing desktop progress.";
            pushState();
            return;
        }
        if (selectedSaveTargetUri == null) {
            chooseSaveTarget();
            return;
        }
        io.execute(() -> {
            File archive = new File(getCacheDir(), "desktop-balatro-saves.zip");
            try {
                downloadDesktopSaveArchive(profile, archive);
                DocumentFile target = requireSaveRoot(selectedSaveTargetUri, true);
                File backup = createSaveBackup(target, "Before desktop import");
                extractSaveArchive(archive, target);
                main.post(() -> {
                    message = "Desktop progress imported with a reversible backup. Review the target profile before launching Balatro.";
                    pushState();
                });
            } catch (Exception error) {
                main.post(() -> { message = "Desktop save import failed: " + readable(error); pushState(); });
            } finally {
                //noinspection ResultOfMethodCallIgnored
                archive.delete();
            }
        });
    }

    private void downloadDesktopSaveArchive(String profile, File target) throws Exception {
        String endpoint = desktopBaseUrl + "/save-archive?token=" + Uri.encode(desktopToken);
        if (profile != null && !profile.isBlank()) endpoint += "&profile=" + Uri.encode(profile);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(120000);
        int status = connection.getResponseCode();
        if (status >= 400) throw new IllegalStateException("The helper could not provide the desktop saves (HTTP " + status + ").");
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16_384];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 100L * 1024L * 1024L) throw new IllegalArgumentException("Desktop save archive exceeds the 100 MB safety limit.");
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void extractSaveArchive(File archive, DocumentFile target) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[16_384];
            int files = 0;
            long bytes = 0;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..") || name.startsWith("Mods/")) {
                    throw new IllegalArgumentException("Unsafe or unsupported save archive entry.");
                }
                String[] parts = name.split("/");
                DocumentFile parent = target;
                int last = entry.isDirectory() ? parts.length : parts.length - 1;
                for (int i = 0; i < last; i++) {
                    if (parts[i].isBlank()) continue;
                    DocumentFile next = parent.findFile(parts[i]);
                    if (next == null) next = parent.createDirectory(parts[i]);
                    if (next == null || !next.isDirectory()) throw new IllegalStateException("Could not create save subfolder.");
                    parent = next;
                }
                if (!entry.isDirectory()) {
                    if (++files > 500) throw new IllegalArgumentException("Desktop save archive contains too many files.");
                    String fileName = parts[parts.length - 1];
                    DocumentFile old = parent.findFile(fileName);
                    if (old != null) old.delete();
                    DocumentFile destination = parent.createFile("application/octet-stream", fileName);
                    if (destination == null) throw new IllegalStateException("Could not create restored save file.");
                    OutputStream output = getContentResolver().openOutputStream(destination.getUri());
                    if (output == null) throw new IllegalStateException("Could not write restored save file.");
                    try (OutputStream out = output) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            bytes += read;
                            if (bytes > 100L * 1024L * 1024L) throw new IllegalArgumentException("Desktop save archive exceeds the 100 MB safety limit.");
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void importDesktopMods() {
        if (!desktopPaired || desktopBaseUrl.isBlank() || desktopToken.isBlank()) {
            message = "Pair the desktop helper before importing desktop mods.";
            pushState();
            return;
        }
        if (selectedTreeUri == null || scan == null) {
            message = "Connect the phone's Mods folder before importing desktop mods.";
            pushState();
            return;
        }
        runFileOperation("import", "desktop", "desktop", "Importing desktop mods…", () -> {
            File archive = new File(getCacheDir(), "desktop-balatro-mods.zip");
            File staging = new File(getCacheDir(), "desktop-mods-import-" + System.nanoTime());
            try {
                downloadDesktopModsArchive(archive);
                if (!staging.mkdirs()) throw new IllegalStateException("Could not create secure mod staging storage.");
                extractDesktopModsArchive(archive, staging);
                ModRepository.ScanResult current = ModRepository.scan(this, selectedTreeUri);
                snapshots.create("Before desktop mod import", current.mods());
                int imported = installDesktopModFolders(staging, current.folder(), current.mods());
                scan = ModRepository.scan(this, selectedTreeUri);
                return imported + (imported == 1 ? " desktop mod imported" : " desktop mods imported")
                        + " and enabled.";
            } finally {
                //noinspection ResultOfMethodCallIgnored
                archive.delete();
                deleteLocalTree(staging);
            }
        });
    }

    private void downloadDesktopModsArchive(File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                desktopBaseUrl + "/mods-archive?token=" + Uri.encode(desktopToken)
        ).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(180000);
        int status = connection.getResponseCode();
        if (status >= 400) throw new IllegalStateException("The helper could not provide desktop mods (HTTP " + status + ").");
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16_384];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 250L * 1024L * 1024L) throw new IllegalArgumentException("Desktop mod archive exceeds the 250 MB safety limit.");
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void extractDesktopModsArchive(File archive, File staging) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[16_384];
            int entries = 0;
            long bytes = 0;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..") || name.startsWith("Mods/")) {
                    throw new IllegalArgumentException("Unsafe desktop mod archive entry.");
                }
                String[] parts = name.split("/");
                if (parts.length == 0 || parts[0].isBlank()) throw new IllegalArgumentException("Desktop mod archive has an invalid folder name.");
                File destination = new File(staging, name);
                String canonicalRoot = staging.getCanonicalPath() + File.separator;
                if (!destination.getCanonicalPath().startsWith(canonicalRoot)) throw new IllegalArgumentException("Desktop mod archive escaped its staging folder.");
                if (entry.isDirectory()) {
                    if (!destination.mkdirs() && !destination.isDirectory()) throw new IllegalStateException("Could not stage desktop mod folder.");
                } else {
                    if (++entries > 20_000) throw new IllegalArgumentException("Desktop mod archive contains too many files.");
                    String lower = name.toLowerCase(Locale.ROOT);
                    for (String blocked : new String[]{".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".ps1", ".apk"}) {
                        if (lower.endsWith(blocked)) throw new IllegalArgumentException("Blocked desktop-only mod file: " + name);
                    }
                    File parent = destination.getParentFile();
                    if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IllegalStateException("Could not stage desktop mod files.");
                    try (OutputStream output = new FileOutputStream(destination)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            bytes += read;
                            if (bytes > 250L * 1024L * 1024L) throw new IllegalArgumentException("Desktop mod archive exceeds the 250 MB safety limit.");
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
            if (entries == 0) throw new IllegalArgumentException("The desktop mod archive is empty.");
        }
    }

    private int installDesktopModFolders(File staging, DocumentFile modsFolder, List<ModEntry> existingMods) throws Exception {
        Map<String, ModEntry> existing = new HashMap<>();
        for (ModEntry mod : existingMods) existing.put(mod.folderName.toLowerCase(Locale.ROOT), mod);
        File[] folders = staging.listFiles(File::isDirectory);
        if (folders == null) throw new IllegalStateException("Could not read staged desktop mods.");
        int imported = 0;
        for (File folder : folders) {
            String name = sanitizeDesktopModName(folder.getName());
            if (name.isBlank()) continue;
            ModEntry current = existing.get(name.toLowerCase(Locale.ROOT));
            if (current != null && ModRepository.isEssential(current)) {
                throw new IllegalStateException("Framework mod " + name + " is protected; review it manually before replacement.");
            }
            DocumentFile old = modsFolder.findFile(name);
            if (old != null && old.exists()) {
                ModRepository.deleteDocumentTree(old);
            }
            DocumentFile target = modsFolder.createDirectory(name);
            if (target == null) throw new IllegalStateException("Could not create desktop mod " + name + ".");
            try {
                copyLocalTreeToDocument(folder, target, new int[]{0}, new long[]{0});
                DocumentFile marker = target.findFile(".lovelyignore");
                if (marker != null && marker.exists() && !marker.delete()) {
                    throw new IllegalStateException("Could not enable imported mod " + name + ".");
                }
                recordInstallHistory("desktop-import:" + name + ":" + System.currentTimeMillis(), name + " imported from desktop", "", "desktop-import", "", name);
                imported++;
            } catch (Exception error) {
                ModRepository.deleteDocumentTree(target);
                throw error;
            }
        }
        return imported;
    }

    private void copyLocalTreeToDocument(File source, DocumentFile target, int[] entries, long[] bytes) throws Exception {
        File[] children = source.listFiles();
        if (children == null) throw new IllegalStateException("Could not read staged mod files.");
        for (File child : children) {
            String name = child.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (name.isBlank() || name.equals(".") || name.equals("..")) continue;
            if (child.isDirectory()) {
                DocumentFile directory = target.createDirectory(name);
                if (directory == null) throw new IllegalStateException("Could not create mod subfolder " + name + ".");
                copyLocalTreeToDocument(child, directory, entries, bytes);
            } else if (child.isFile()) {
                if (++entries[0] > 20_000) throw new IllegalArgumentException("Desktop mod contains too many files.");
                DocumentFile destination = target.createFile("application/octet-stream", name);
                if (destination == null) throw new IllegalStateException("Could not create mod file " + name + ".");
                try (InputStream input = new FileInputStream(child); OutputStream output = getContentResolver().openOutputStream(destination.getUri())) {
                    if (output == null) throw new IllegalStateException("The Mods provider denied write access.");
                    byte[] buffer = new byte[16_384];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        bytes[0] += read;
                        if (bytes[0] > 250L * 1024L * 1024L) throw new IllegalArgumentException("Desktop mod exceeds the 250 MB safety limit.");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static String sanitizeDesktopModName(String name) {
        return name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static void deleteLocalTree(File directory) {
        if (directory == null || !directory.exists()) return;
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) {
            if (child.isDirectory()) deleteLocalTree(child);
            //noinspection ResultOfMethodCallIgnored
            child.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        directory.delete();
    }

    private void copySaveTree(DocumentFile source, DocumentFile target, int[] count, long[] bytes) throws Exception {
        for (DocumentFile file : source.listFiles()) {
            String name = file.getName() == null ? "save" : file.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (file.isDirectory()) {
                DocumentFile child = target.findFile(name);
                if (child == null) child = target.createDirectory(name);
                if (child == null || !child.isDirectory()) throw new IllegalStateException("Could not create save subfolder " + name);
                copySaveTree(file, child, count, bytes);
            } else if (file.isFile()) {
                if (++count[0] > 500) throw new IllegalArgumentException("Save folder contains too many files.");
                DocumentFile existing = target.findFile(name);
                if (existing != null && existing.isFile()) existing.delete();
                DocumentFile destination = target.createFile("application/octet-stream", name);
                if (destination == null) throw new IllegalStateException("Could not create save file " + name);
                InputStream raw = getContentResolver().openInputStream(file.getUri());
                OutputStream output = getContentResolver().openOutputStream(destination.getUri());
                if (raw == null || output == null) throw new IllegalStateException("Could not copy save file " + name);
                try (InputStream input = raw; OutputStream out = output) {
                    byte[] buffer = new byte[16_384]; int read;
                    while ((read = input.read(buffer)) != -1) {
                        bytes[0] += read;
                        if (bytes[0] > 100L * 1024L * 1024L) throw new IllegalArgumentException("Save import exceeds the 100 MB safety limit.");
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private int countSaveFiles() throws Exception {
        if (selectedSaveTreeUri == null) return 0;
        DocumentFile root = DocumentFile.fromTreeUri(this, selectedSaveTreeUri);
        if (root == null || !root.isDirectory()) throw new IllegalStateException("The save folder is unavailable.");
        return countSaveFiles(root, 0);
    }

    private int countSaveFiles(DocumentFile directory, int depth) throws Exception {
        if (depth > 5) throw new IllegalArgumentException("Save folder nesting is too deep.");
        int count = 0;
        for (DocumentFile file : directory.listFiles()) {
            if (file.isDirectory()) count += countSaveFiles(file, depth + 1);
            else if (file.isFile()) {
                count++;
                if (count > 500) throw new IllegalArgumentException("Save folder contains too many files.");
            }
        }
        return count;
    }

    private JSONArray saveProfiles(DocumentFile root) {
        JSONArray profiles = new JSONArray();
        if (root == null || !root.isDirectory()) return profiles;
        try {
            for (DocumentFile child : root.listFiles()) {
                if (child.isDirectory() && child.getName() != null && !child.getName().isBlank()) profiles.put(child.getName());
            }
        } catch (Exception ignored) { }
        return profiles;
    }

    private JSONArray safeSaveProfiles() {
        try { return saveProfiles(requireSaveRoot(selectedSaveTreeUri, false)); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private void exportSave() {
        if (selectedSaveTreeUri == null) {
            chooseSaveFolder();
            return;
        }
        runFileOperation(() -> {
            pendingSaveArchive = createSaveArchive();
            main.post(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, "balatro-save-backup.zip");
                try { startActivityForResult(intent, CREATE_SAVE_EXPORT); }
                catch (ActivityNotFoundException error) { message = "No compatible save exporter is installed."; pushState(); }
            });
            return "Save backup ready. Choose where to export it.";
        });
    }

    private File createSaveArchive() throws Exception {
        DocumentFile root = DocumentFile.fromTreeUri(this, selectedSaveTreeUri);
        if (root == null || !root.isDirectory()) throw new IllegalStateException("The save folder is unavailable.");
        File archive = new File(getCacheDir(), "save-backup-" + System.currentTimeMillis() + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(archive)))) {
            int[] count = {0};
            long[] bytes = {0};
            addSaveFiles(root, "", zip, count, bytes);
        }
        return archive;
    }

    private File createSaveBackup(DocumentFile root, String label) throws Exception {
        File directory = new File(getFilesDir(), "save-backups");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create the local save-backup folder.");
        String id = "save:" + UUID.randomUUID();
        File archive = new File(directory, id.substring("save:".length()) + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(archive)))) {
            int[] count = {0};
            long[] bytes = {0};
            addSaveFiles(root, "", zip, count, bytes);
        }
        JSONArray existing = saveBackupHistoryJson();
        JSONArray next = new JSONArray();
        JSONObject item = new JSONObject();
        long now = System.currentTimeMillis();
        item.put("id", id);
        item.put("label", label);
        item.put("file", archive.getName());
        item.put("source", "local device");
        item.put("profile", "selected target folder");
        item.put("size", archive.length());
        item.put("createdAtEpoch", now);
        item.put("createdAt", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(new Date(now)));
        item.put("entries", countSaveFiles(root, 0));
        next.put(item);
        int limit = historyRetentionLimit();
        for (int i = 0; i < existing.length() && (limit == 0 || i < limit - 1); i++) next.put(existing.getJSONObject(i));
        preferences.edit().putString(PREF_SAVE_BACKUPS, next.toString()).apply();
        return archive;
    }

    private JSONArray saveBackupHistoryJson() {
        try { return new JSONArray(preferences.getString(PREF_SAVE_BACKUPS, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private int historyRetentionLimit() {
        return Math.max(0, Math.min(preferences.getInt(PREF_HISTORY_RETENTION, 20), 1000));
    }

    private void setHistoryRetention(int limit) {
        int normalized = Math.max(0, Math.min(limit, 1000));
        preferences.edit().putInt(PREF_HISTORY_RETENTION, normalized).apply();
        snapshots.setRetentionLimit(normalized);
        if (normalized > 0) {
            trimHistoryPreference(PREF_INSTALL_HISTORY, normalized);
            trimHistoryPreference(PREF_SAVE_BACKUPS, normalized);
        }
        message = normalized == 0 ? "History retention set to unlimited" : "History retention set to " + normalized + " records";
        pushState();
    }

    private void trimHistoryPreference(String key, int limit) {
        try {
            JSONArray existing = new JSONArray(preferences.getString(key, "[]"));
            if (existing.length() <= limit) return;
            JSONArray trimmed = new JSONArray();
            for (int i = 0; i < limit; i++) trimmed.put(existing.get(i));
            preferences.edit().putString(key, trimmed.toString()).apply();
        } catch (Exception ignored) {
            // A corrupt history is handled by its normal reader; settings must not crash the app.
        }
    }

    private JSONObject findSaveBackup(String id) {
        JSONArray items = saveBackupHistoryJson();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }

    private void restoreSaveBackup(String id) {
        runFileOperation(() -> {
            JSONObject item = findSaveBackup(id);
            if (item == null) throw new IllegalStateException("Save backup is no longer available.");
            DocumentFile target = requireSaveRoot(selectedSaveTargetUri, true);
            File archive = new File(new File(getFilesDir(), "save-backups"), item.optString("file"));
            if (!archive.isFile()) throw new IllegalStateException("The local save backup file is missing.");
            clearSaveTree(target);
            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
                ZipEntry entry;
                byte[] buffer = new byte[16_384];
                long bytes = 0;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');
                    if (name.startsWith("/") || name.contains("../") || name.equals("..")) throw new IllegalArgumentException("Unsafe save-backup path.");
                    DocumentFile parent = target;
                    String[] parts = name.split("/");
                    for (int i = 0; i < parts.length - (entry.isDirectory() ? 0 : 1); i++) {
                        if (parts[i].isBlank()) continue;
                        DocumentFile next = parent.findFile(parts[i]);
                        if (next == null) next = parent.createDirectory(parts[i]);
                        if (next == null || !next.isDirectory()) throw new IllegalStateException("Could not restore save subfolder.");
                        parent = next;
                    }
                    if (!entry.isDirectory()) {
                        String fileName = parts[parts.length - 1];
                        DocumentFile destination = parent.createFile("application/octet-stream", fileName);
                        if (destination == null) throw new IllegalStateException("Could not restore save file.");
                        OutputStream output = getContentResolver().openOutputStream(destination.getUri());
                        if (output == null) throw new IllegalStateException("Could not write restored save file.");
                        try (OutputStream out = output) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                bytes += read;
                                if (bytes > 100L * 1024L * 1024L) throw new IllegalArgumentException("Save backup exceeds the 100 MB safety limit.");
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
            return "Save backup restored";
        });
    }

    private void clearSaveTree(DocumentFile directory) {
        for (DocumentFile child : directory.listFiles()) {
            if (child.isDirectory()) clearSaveTree(child);
            child.delete();
        }
    }

    private void addSaveFiles(DocumentFile directory, String prefix, ZipOutputStream zip, int[] count, long[] bytes) throws Exception {
        for (DocumentFile file : directory.listFiles()) {
            String name = file.getName() == null ? "file" : file.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            String entryName = prefix + name;
            if (file.isDirectory()) addSaveFiles(file, entryName + "/", zip, count, bytes);
            else if (file.isFile()) {
                if (++count[0] > 500) throw new IllegalArgumentException("Save folder contains too many files.");
                ZipEntry entry = new ZipEntry(entryName);
                zip.putNextEntry(entry);
                InputStream raw = getContentResolver().openInputStream(file.getUri());
                if (raw == null) throw new IllegalStateException("Could not read save file " + name);
                try (InputStream input = raw) {
                    byte[] buffer = new byte[16_384]; int read;
                    while ((read = input.read(buffer)) != -1) {
                        bytes[0] += read;
                        if (bytes[0] > 100L * 1024L * 1024L) throw new IllegalArgumentException("Save backup exceeds the 100 MB safety limit.");
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MODS_TREE) {
            handlePickedTree(resultCode, data);
        } else if (requestCode == CREATE_REPORT) {
            handleReportDestination(resultCode, data);
        } else if (requestCode == PICK_IMPORT_FILE) {
            handleImportedMod(resultCode, data);
        } else if (requestCode == PICK_IMPORT_FOLDER) {
            handleImportedModFolder(resultCode, data);
        } else if (requestCode == PICK_NATIVE_APK) {
            handleNativeApk(resultCode, data);
        } else if (requestCode == PICK_SAVE_TREE) {
            handlePickedSaveTree(resultCode, data);
        } else if (requestCode == PICK_SAVE_TARGET_TREE) {
            handlePickedSaveTargetTree(resultCode, data);
        } else if (requestCode == CREATE_SAVE_EXPORT) {
            handleSaveExportDestination(resultCode, data);
        } else if (requestCode == CREATE_APK_EXPORT) {
            handleArtifactDestination(resultCode, data);
        } else if (requestCode == PICK_STEAM_SOURCE_FILE) {
            handleSteamSourceResult(resultCode, data, false);
        } else if (requestCode == PICK_STEAM_SOURCE_FOLDER) {
            handleSteamSourceResult(resultCode, data, true);
        } else if (requestCode == CREATE_DIAGNOSTIC_ZIP) {
            handleDiagnosticDestination(resultCode, data);
        }
    }

    private void handleDiagnosticDestination(int resultCode, Intent data) {
        File archive = pendingDiagnosticArchive;
        pendingDiagnosticArchive = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || archive == null) {
            if (archive != null) {
                //noinspection ResultOfMethodCallIgnored
                archive.delete();
            }
            return;
        }
        try (InputStream input = new FileInputStream(archive);
             OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new IllegalStateException("The ZIP destination is not writable.");
            byte[] buffer = new byte[32_768];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            message = "Diagnostic ZIP saved";
        } catch (Exception error) {
            message = readable(error);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
            pushState();
        }
    }

    private void handleArtifactDestination(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null || pendingArtifact == null) { pendingArtifact = null; return; }
        try (InputStream input = new java.io.FileInputStream(pendingArtifact); OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new IllegalStateException("The APK destination is not writable.");
            byte[] buffer = new byte[32_768]; int read; while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            message = "Verified APK exported";
        } catch (Exception error) { message = readable(error); }
        finally { //noinspection ResultOfMethodCallIgnored
            pendingArtifact.delete(); pendingArtifact = null; pushState(); }
    }

    private void handleSaveExportDestination(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null || pendingSaveArchive == null) {
            pendingSaveArchive = null;
            return;
        }
        try (InputStream input = new java.io.FileInputStream(pendingSaveArchive);
             OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) throw new IllegalStateException("The save destination is not writable.");
            byte[] buffer = new byte[32_768]; int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            message = "Save backup exported";
        } catch (Exception error) {
            message = readable(error);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            pendingSaveArchive.delete();
            pendingSaveArchive = null;
            pushState();
        }
    }

    private void handleNativeApk(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null
                || (data.getData() == null && data.getClipData() == null)) return;
        List<Uri> selected = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                selected.add(data.getClipData().getItemAt(i).getUri());
            }
        } else {
            selected.add(data.getData());
        }
        io.execute(() -> {
            String nextMessage;
            List<File> stagedFiles = new ArrayList<>();
            try {
                for (int i = 0; i < selected.size(); i++) {
                    File staged = new File(getCacheDir(), "native-preflight-"
                            + System.currentTimeMillis() + "-" + i + ".apk");
                    copyBoundedUri(selected.get(i), staged, 250L * 1024L * 1024L,
                            "APK exceeds the 250 MB safety limit.");
                    stagedFiles.add(staged);
                }
                if (stagedFiles.isEmpty()) throw new IllegalArgumentException("Select at least one APK.");
                File primary = stagedFiles.get(0);
                android.content.pm.PackageInfo info = getPackageManager().getPackageArchiveInfo(
                        primary.getAbsolutePath(), archivePackageFlags()
                );
                if (info == null || info.packageName == null) {
                    throw new IllegalArgumentException("Android could not read this APK manifest.");
                }
                String abi = inspectApkAbis(primary);
                String signature = signingDigest(info);
                String splitNote = stagedFiles.size() > 1
                        ? " Split APK set selected (" + stagedFiles.size() + " files); no split patch is attempted."
                        : "";
                nativeCompatibility = "unsupported";
                nativePreflight = "Package " + info.packageName + " (version "
                        + info.versionName + ") · ABI " + abi + " · signing SHA-256 " + signature
                        + " was inspected, but this Play Store copy cannot be patched safely."
                        + splitNote
                        + " Use the Steam/local route instead.";
                nextMessage = nativePreflight;
            } catch (Exception error) {
                nativeCompatibility = "unsupported";
                nativePreflight = readable(error);
                nextMessage = nativePreflight;
            } finally {
                // Staged APKs are only for preflight; they are never modified or retained.
                for (File staged : stagedFiles) {
                    //noinspection ResultOfMethodCallIgnored
                    staged.delete();
                }
            }
            final String finalMessage = nextMessage;
            main.post(() -> { message = finalMessage; pushState(); });
        });
    }

    private int archivePackageFlags() {
        int flags = PackageManager.GET_PERMISSIONS;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? flags | PackageManager.GET_SIGNING_CERTIFICATES
                : flags | PackageManager.GET_SIGNATURES;
    }

    private void copyBoundedUri(Uri uri, File destination, long limit, String limitMessage) throws Exception {
        InputStream raw = getContentResolver().openInputStream(uri);
        if (raw == null) throw new IllegalStateException("The selected APK could not be read.");
        try (InputStream input = raw; OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32_768];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IllegalArgumentException(limitMessage);
                output.write(buffer, 0, read);
            }
        }
    }

    private String inspectApkAbis(File apk) throws Exception {
        Set<String> abis = new HashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("lib/")) {
                    String[] parts = name.split("/");
                    if (parts.length > 1 && !parts[1].isBlank()) abis.add(parts[1]);
                }
            }
        }
        return abis.isEmpty() ? "managed/unknown" : String.join(", ", abis);
    }

    private String signingDigest(PackageInfo info) throws Exception {
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else if (info.signatures != null) {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) return "unavailable";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(signatures[0].toByteArray());
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02X", value));
        return result.toString();
    }

    private void handleImportedMod(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri archive = data.getData();
        try {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(
                        archive,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }
        } catch (SecurityException ignored) {
            // A one-shot URI is still usable for the immediate inspection.
        }
        if (selectedTreeUri == null || scan == null) {
            message = "Mod archive selected. Connect the Mods folder before installing.";
            pushState();
            return;
        }
        String name = "ImportedMod.zip";
        DocumentFile selected = DocumentFile.fromSingleUri(this, archive);
        if (selected != null && selected.getName() != null) name = selected.getName();
        final String finalName = name;
        runFileOperation("import", finalName, "local", "Installing imported mod…", () -> {
            CatalogInstaller.InstallResult result = CatalogInstaller.installArchive(
                    this, scan.folder(), archive, finalName
            );
            recordInstallHistory(
                    "import:" + result.folderName() + ":" + System.currentTimeMillis(),
                    result.folderName() + " imported",
                    "",
                    "install",
                    "",
                    result.folderName()
            );
            return result.folderName() + " imported and enabled.";
        });
    }

    private void handleImportedModFolder(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri folder = data.getData();
        try {
            int granted = data.getFlags();
            if ((granted & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if ((granted & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(folder, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        } catch (SecurityException ignored) {
            // One-shot access is sufficient for this import.
        }
        if (selectedTreeUri == null || scan == null) {
            message = "Mod folder selected. Connect the Mods folder before installing.";
            pushState();
            return;
        }
        DocumentFile source = DocumentFile.fromTreeUri(this, folder);
        if (source == null) {
            message = "The selected folder could not be read.";
            pushState();
            return;
        }
        String name = source.getName() == null ? "ImportedMod" : source.getName();
        final String finalName = name;
        runFileOperation("import", finalName, "local", "Installing imported mod…", () -> {
            CatalogInstaller.InstallResult result = CatalogInstaller.installDirectory(
                    this, scan.folder(), source, finalName
            );
            recordInstallHistory(
                    "import:" + result.folderName() + ":" + System.currentTimeMillis(),
                    result.folderName() + " imported",
                    "",
                    "install",
                    "",
                    result.folderName()
            );
            return result.folderName() + " imported and enabled.";
        });
    }

    private void handlePickedTree(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        int permissionFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            boolean canRead =
                    (permissionFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
            boolean canWrite =
                    (permissionFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
            if (canRead && canWrite) {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            } else if (canRead) {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } else if (canWrite) {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            }
        } catch (SecurityException error) {
            Toast.makeText(
                    this,
                    "Android could not persist folder access.",
                    Toast.LENGTH_LONG
            ).show();
        }
        selectedTreeUri = uri;
        preferences.edit().putString(PREF_TREE_URI, uri.toString()).apply();
        message = "Folder connected";
        refresh(false);
    }

    private void handleReportDestination(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            pendingReport = "";
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (output == null) {
                throw new IllegalStateException("The report destination is not writable.");
            }
            output.write(pendingReport.getBytes(StandardCharsets.UTF_8));
            message = "Diagnostic report exported";
        } catch (Exception error) {
            message = readable(error);
        } finally {
            pendingReport = "";
            pushState();
        }
    }

    private void toggleMod(String folder, boolean hidden) {
        runFileOperation(hidden ? "disable" : "enable", folder, "local",
                hidden ? "Disabling mod…" : "Enabling mod…", () -> {
            ModEntry mod = findMod(folder);
            ModRepository.setHidden(this, mod, hidden);
            return hidden ? mod.name + " disabled" : mod.name + " enabled";
        });
    }

    private void toggleMods(JSONArray folders, boolean hidden) {
        runFileOperation(hidden ? "disable" : "enable", "multiple", "local",
                hidden ? "Disabling selected mods…" : "Enabling selected mods…", () -> {
            requireScan();
            if (folders == null || folders.length() == 0) throw new IllegalArgumentException("Select at least one mod.");
            snapshots.create("Before multi-mod change", scan.mods());
            int changed = 0;
            for (int i = 0; i < folders.length(); i++) {
                String folder = folders.optString(i, "");
                if (folder.isBlank()) continue;
                ModRepository.setHidden(this, findMod(folder), hidden);
                changed++;
            }
            return changed + (changed == 1 ? " mod " : " mods ") + (hidden ? "disabled" : "enabled") + ". Backup saved.";
        });
    }

    private void deleteMod(String folder) {
        runFileOperation("delete", folder, "", "Deleting mod…", () -> {
            requireScan();
            ModEntry mod = findMod(folder);
            ModRepository.deletePermanently(mod);
            return mod.name + " was permanently deleted.";
        });
    }

    private void deleteMods(JSONArray folders) {
        runFileOperation("delete", "multiple", "", "Deleting selected mods…", () -> {
            requireScan();
            if (folders == null || folders.length() == 0) throw new IllegalArgumentException("Select at least one mod.");
            List<ModEntry> selected = new ArrayList<>();
            for (int i = 0; i < folders.length(); i++) {
                String folder = folders.optString(i, "");
                if (!folder.isBlank()) selected.add(findMod(folder));
            }
            if (selected.isEmpty()) throw new IllegalArgumentException("Select at least one mod.");
            for (ModEntry mod : selected) {
                if (ModRepository.isEssential(mod)) {
                    throw new IllegalStateException(mod.name + " is a protected framework. Remove it individually only after explicit review.");
                }
            }
            int deleted = 0;
            for (ModEntry mod : selected) {
                ModRepository.deletePermanently(mod);
                deleted++;
            }
            return deleted + (deleted == 1 ? " mod was" : " mods were") + " permanently deleted.";
        });
    }

    private void cleanAllJunk() {
        runFileOperation("cleanup", "all", "local", "Cleaning known junk…", () -> {
            requireScan();
            ModRepository.CleanupReport report = ModRepository.cleanKnownJunk(scan.folder());
            int privateItems = clearPrivateTemporaryItems();
            int total = report.removed() + privateItems;
            if (total == 0) {
                return "No known junk was found. Mods and backups were left untouched.";
            }
            return total + (total == 1 ? " junk item was" : " junk items were")
                    + " permanently removed. Mods and backups were left untouched.";
        });
    }

    private int clearPrivateTemporaryItems() {
        File[] children = getCacheDir().listFiles();
        if (children == null) return 0;
        int removed = 0;
        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.ROOT);
            boolean known = name.startsWith("install-")
                    || name.startsWith("import-")
                    || name.startsWith("import-folder-")
                    || name.startsWith("desktop-mods-import-")
                    || name.startsWith("desktop-balatro-mods")
                    || name.startsWith("desktop-balatro-saves")
                    || name.startsWith("bmm-steam-source-")
                    || name.startsWith("native-preflight-");
            if (!known) continue;
            if (child.isDirectory()) deleteLocalTree(child);
            else child.delete();
            if (!child.exists()) removed++;
        }
        return removed;
    }

    private void recordInstallHistory(
            String id,
            String label,
            String version,
            String kind,
            String quarantineName,
            String originalName
    ) {
        try {
            JSONArray existing = new JSONArray(
                    preferences.getString(PREF_INSTALL_HISTORY, "[]")
            );
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("label", label);
            item.put("version", version == null ? "" : version);
            item.put("kind", kind);
            item.put("source", kind.equals("install") || kind.equals("update") ? "Catalog" : "Local");
            item.put("result", "success");
            item.put("profile", "Mods folder");
            item.put("artifact", "");
            item.put("checksum", "");
            item.put("error", "");
            item.put("quarantineName", quarantineName == null ? "" : quarantineName);
            item.put("originalName", originalName == null ? "" : originalName);
            long now = System.currentTimeMillis();
            item.put("createdAtEpoch", now);
            item.put("createdAt", DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()
            ).format(new Date(now)));
            item.put("entries", kind.equals("restore") ? "restored" : kind);
            next.put(item);
            int limit = historyRetentionLimit();
            for (int i = 0; i < existing.length() && (limit == 0 || i < limit - 1); i++) {
                next.put(existing.getJSONObject(i));
            }
            preferences.edit().putString(PREF_INSTALL_HISTORY, next.toString()).apply();
        } catch (Exception ignored) {
            // History must never make an install or recovery action fail.
        }
    }

    private void attachCatalogMetadata(String historyId, CatalogItem item, String selectedVersion, String artifactUrl) {
        if (item == null || historyId == null || historyId.isBlank()) return;
        try {
            JSONArray existing = installHistoryJson();
            for (int i = 0; i < existing.length(); i++) {
                JSONObject entry = existing.optJSONObject(i);
                if (entry == null || !historyId.equals(entry.optString("id"))) continue;
                entry.put("catalogId", item.id());
                entry.put("catalogSource", item.source());
                entry.put("catalogName", item.name());
                entry.put("catalogFolder", item.folderName());
                entry.put("catalogHomepage", item.homepage());
                entry.put("catalogDownloadUrl", artifactUrl == null || artifactUrl.isBlank() ? item.downloadUrl() : artifactUrl);
                entry.put("catalogVersion", selectedVersion == null ? item.version() : selectedVersion);
                entry.put("profile", "Mods folder");
                entry.put("artifact", artifactUrl == null || artifactUrl.isBlank() ? item.downloadUrl() : artifactUrl);
                preferences.edit().putString(PREF_INSTALL_HISTORY, existing.toString()).apply();
                return;
            }
        } catch (Exception ignored) {
            // Metadata is helpful for reinstall, but must never break a successful install.
        }
    }

    private void reinstallInstall(String id) {
        JSONObject record = findInstallHistory(id);
        if (record == null) {
            message = "This installation entry is no longer available.";
            pushState();
            return;
        }
        String catalogId = record.optString("catalogId");
        String catalogSource = record.optString("catalogSource", "");
        if (catalogId.isBlank()) {
            message = "This entry predates catalog metadata. Reinstall it from Discover.";
            pushState();
            return;
        }
        try {
            CatalogItem item = findCatalogItem(catalogId, catalogSource);
            boolean replaceExisting = findInstalledCatalogMod(item) != null;
            installCatalogItem(
                    catalogId,
                    catalogSource,
                    replaceExisting,
                    record.optString("catalogVersion", ""),
                    record.optString("catalogDownloadUrl", "")
            );
        } catch (Exception error) {
            message = readable(error);
            pushState();
        }
    }

    private JSONObject findInstallHistory(String id) {
        try {
            JSONArray items = new JSONArray(preferences.getString(PREF_INSTALL_HISTORY, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (item.optString("id").equals(id)) return item;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (("update".equals(item.optString("kind")) || "quarantine".equals(item.optString("kind")))
                        && item.optString("originalName").equals(id)) return item;
            }
        } catch (Exception ignored) {
            // Corrupt history is ignored and can be recreated by future actions.
        }
        return null;
    }

    private JSONArray installHistoryJson() {
        try {
            return new JSONArray(preferences.getString(PREF_INSTALL_HISTORY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void deleteHistoryEntry(String id, String kind) {
        runFileOperation(() -> {
            boolean removed;
            if ("backups".equals(kind)) {
                removed = snapshots.remove(id);
                if (!removed) {
                    JSONObject saveBackup = findSaveBackup(id);
                    if (saveBackup != null) {
                        File archive = new File(new File(getFilesDir(), "save-backups"), saveBackup.optString("file"));
                        if (archive.isFile()) archive.delete();
                        JSONArray existing = saveBackupHistoryJson();
                        JSONArray next = new JSONArray();
                        for (int i = 0; i < existing.length(); i++) {
                            JSONObject item = existing.optJSONObject(i);
                            if (item != null && !id.equals(item.optString("id"))) next.put(item);
                        }
                        preferences.edit().putString(PREF_SAVE_BACKUPS, next.toString()).apply();
                        removed = true;
                    }
                }
            } else {
                JSONArray existing = installHistoryJson();
                JSONArray next = new JSONArray();
                removed = false;
                for (int i = 0; i < existing.length(); i++) {
                    JSONObject item = existing.optJSONObject(i);
                    if (item != null && id.equals(item.optString("id"))) {
                        removed = true;
                    } else if (item != null) {
                        next.put(item);
                    }
                }
                if (removed) {
                    preferences.edit().putString(PREF_INSTALL_HISTORY, next.toString()).apply();
                }
            }
            if (!removed) throw new IllegalStateException("History entry was already removed.");
            return "History entry removed";
        });
    }

    private int safeSaveFileCount() {
        try { return countSaveFiles(); }
        catch (Exception ignored) { return 0; }
    }

    private void quickRescue() {
        runFileOperation(() -> {
            requireScan();
            snapshots.create("Before Safe Mode", scan.mods());
            Map<String, Boolean> states = new HashMap<>();
            for (ModEntry mod : scan.mods()) {
                states.put(mod.folderName, !ModRepository.isEssential(mod));
            }
            ModRepository.applyStates(this, scan.mods(), states);
            recovery = RecoverySession.empty();
            return "Safe Mode applied. You can test Balatro now.";
        });
    }

    private void undoLatest() {
        restoreSnapshot("");
    }

    private void restoreSnapshot(String id) {
        if (id != null && id.startsWith("save:")) {
            restoreSaveBackup(id);
            return;
        }
        runFileOperation(() -> {
            requireScan();
            SnapshotStore.Snapshot snapshot =
                    id == null || id.isBlank() ? snapshots.latest() : snapshots.find(id);
            if (snapshot == null) {
                throw new IllegalStateException("No snapshot is available.");
            }
            ModRepository.applyStates(this, scan.mods(), snapshot.states());
            recovery = RecoverySession.empty();
            return "Snapshot restored";
        });
    }

    private void beginIsolation() {
        runFileOperation(() -> {
            requireScan();
            snapshots.create("Before guided isolation", scan.mods());
            List<String> suspects = new ArrayList<>();
            for (ModEntry mod : scan.mods()) {
                if (!mod.hidden && !ModRepository.isEssential(mod)) {
                    suspects.add(mod.folderName);
                }
            }
            if (suspects.isEmpty()) {
                throw new IllegalStateException("There are no active non-essential mods to test.");
            }
            List<String> testing = RecoveryPlanner.nextTest(suspects);
            recovery = new RecoverySession(
                    true,
                    1,
                    RecoveryPlanner.estimatedSteps(suspects.size()),
                    List.copyOf(suspects),
                    testing,
                    suspects.size() == 1,
                    suspects.size() == 1 ? suspects.get(0) : ""
            );
            applyRecoveryTest();
            return suspects.size() == 1
                    ? "Only one suspect remained."
                    : "First test group is ready.";
        });
    }

    private void isolationResult(boolean opened) {
        runFileOperation(() -> {
            requireScan();
            if (!recovery.active() || recovery.complete()) {
                throw new IllegalStateException("No guided isolation is running.");
            }
            List<String> suspects = RecoveryPlanner.afterResult(
                    recovery.suspects(),
                    recovery.testing(),
                    opened
            );
            if (suspects.isEmpty()) {
                throw new IllegalStateException(
                        "The result was inconsistent. Restore the snapshot and restart isolation."
                );
            }
            boolean complete = suspects.size() == 1;
            List<String> testing = complete ? List.of() : RecoveryPlanner.nextTest(suspects);
            recovery = new RecoverySession(
                    true,
                    recovery.step() + 1,
                    recovery.totalSteps(),
                    suspects,
                    testing,
                    complete,
                    complete ? suspects.get(0) : ""
            );
            if (complete) {
                SnapshotStore.Snapshot original = snapshots.latest();
                if (original != null) {
                    ModRepository.applyStates(this, scan.mods(), original.states());
                    scan = ModRepository.scan(this, selectedTreeUri);
                }
                ModEntry culprit = findMod(recovery.culprit());
                ModRepository.setHidden(this, culprit, true);
                return "Suspect isolated: " + culprit.name;
            }
            applyRecoveryTest();
            return "Next test group is ready.";
        });
    }

    private void applyRecoveryTest() throws Exception {
        Set<String> testing = new HashSet<>(recovery.testing());
        Map<String, Boolean> states = new HashMap<>();
        for (ModEntry mod : scan.mods()) {
            if (ModRepository.isEssential(mod)) {
                states.put(mod.folderName, false);
            } else {
                states.put(mod.folderName, !testing.contains(mod.folderName));
            }
        }
        ModRepository.applyStates(this, scan.mods(), states);
    }

    private void finishIsolation() {
        recovery = RecoverySession.empty();
        message = "Guided isolation finished";
        pushState();
    }

    private void loadCatalog() {
        if (loading) {
            return;
        }
        loading = true;
        message = "";
        pushState();
        io.execute(() -> {
            String nextMessage;
            try {
                List<CatalogItem> result = catalogClient.fetch();
                catalog.clear();
                catalog.addAll(result);
                nextMessage = "Catalog updated";
            } catch (Exception error) {
                nextMessage = catalog.isEmpty()
                        ? "Catalog unavailable: " + readable(error)
                        : "Could not refresh catalog. Showing the last catalog copy.";
            }
            final String finalMessage = nextMessage;
            main.post(() -> {
                loading = false;
                message = finalMessage;
                pushState();
            });
        });
    }

    private void installCatalogItem(String id, String source) {
        installCatalogItem(id, source, false, "", "");
    }

    private void updateCatalogItem(String id, String source) {
        installCatalogItem(id, source, true, "", "");
    }

    private void installCatalogItem(String id, String source, boolean replaceExisting) {
        installCatalogItem(id, source, replaceExisting, "", "");
    }

    private void installCatalogItem(String id, String source, String version, String downloadUrl) {
        installCatalogItem(id, source, false, version, downloadUrl);
    }

    private void updateCatalogItem(String id, String source, String version, String downloadUrl) {
        installCatalogItem(id, source, true, version, downloadUrl);
    }

    private void updateAllCatalogMods() {
        runFileOperation("update-all", "multiple", "catalog", "Preparing all updates…", () -> {
            requireScan();
            String catalogWarning = "";
            updateRunningOperation("multiple", "catalog", "Refreshing catalog before checking updates…");
            try {
                List<CatalogItem> refreshed = catalogClient.fetch();
                catalog.clear();
                catalog.addAll(refreshed);
            } catch (Exception error) {
                catalogWarning = " Catalog refresh failed, so cached metadata was used.";
            }
            resolveInstalledReleaseMetadata();
            List<CatalogItem> updates = availableCatalogUpdates();
            if (updates.isEmpty()) {
                int fixed = applyAutomaticCompatibilityFixes();
                return fixed > 0
                        ? "All mods were current; IMM mobile compatibility was repaired automatically." + catalogWarning
                        : "All catalog-matched mods are already up to date." + catalogWarning;
            }
            updates.sort(Comparator
                    .comparingInt(MainActivity::catalogUpdatePriority)
                    .thenComparing(CatalogItem::name, String.CASE_INSENSITIVE_ORDER));
            updates = orderCatalogUpdatesByDependency(updates);
            int updated = 0;
            List<String> failures = new ArrayList<>();
            for (int index = 0; index < updates.size(); index++) {
                CatalogItem item = updates.get(index);
                updateRunningOperation(
                        item.id(),
                        item.source(),
                        "Updating " + (index + 1) + " of " + updates.size() + ": " + item.name() + "…"
                );
                try {
                    ModEntry existing = findInstalledCatalogMod(item);
                    if (existing == null) throw new IllegalStateException("installed copy no longer matches");
                    List<String> missing = missingFrameworks(item);
                    if (!missing.isEmpty()) throw new IllegalStateException("missing " + String.join(", ", missing));
                    String selectedVersion = item.version();
                    String url = catalogClient.resolveDownloadUrl(item, selectedVersion, "");
                    CatalogInstaller.InstallResult result = CatalogInstaller.install(
                            this, scan.folder(), item, url, true, existing
                    );
                    scan = ModRepository.scan(this, selectedTreeUri);
                    ModEntry installed = findMod(result.folderName());
                    boolean immFixed = applyImmCompatibility(installed);
                    if (immFixed) scan = ModRepository.scan(this, selectedTreeUri);
                    String actualVersion = installed.version == null || installed.version.isBlank()
                            ? selectedVersion : installed.version;
                    String historyId = "install:" + result.folderName() + ":" + System.currentTimeMillis();
                    recordInstallHistory(historyId, item.name() + " updated", actualVersion,
                            "update", "", result.folderName());
                    attachCatalogMetadata(historyId, item, selectedVersion, url);
                    updated++;
                } catch (Exception error) {
                    failures.add(item.name() + ": " + readable(error));
                    if (selectedTreeUri != null) scan = ModRepository.scan(this, selectedTreeUri);
                }
            }
            if (failures.isEmpty()) {
                return updated + (updated == 1 ? " mod was" : " mods were")
                        + " updated successfully." + catalogWarning;
            }
            return updated + " updated; " + failures.size() + " failed. "
                    + String.join(" | ", failures) + catalogWarning;
        });
    }

    private List<CatalogItem> availableCatalogUpdates() {
        List<CatalogItem> updates = new ArrayList<>();
        if (scan == null) return updates;
        for (CatalogItem item : catalog) {
            ModEntry installed = findInstalledCatalogMod(item);
            if (installed == null || installed.version == null || installed.version.isBlank()
                    || item.version() == null || item.version().isBlank()) continue;
            if (catalogUpdateAvailable(item, installed)) updates.add(item);
        }
        return updates;
    }

    private boolean catalogUpdateAvailable(CatalogItem item, ModEntry installed) {
        return catalogUpdateStatus(item, installed).updateAvailable();
    }

    private CatalogUpdatePolicy.Result catalogUpdateStatus(CatalogItem item, ModEntry installed) {
        if (item == null || installed == null || item.version() == null || item.version().isBlank()) {
            return new CatalogUpdatePolicy.Result(
                    CatalogUpdatePolicy.Status.UNKNOWN,
                    "The installed mod or catalog version could not be identified."
            );
        }
        return CatalogUpdatePolicy.evaluate(
                item.version(),
                installed.version,
                installedCatalogRevision(item, installed.folderName)
        );
    }

    private void loadCatalogVersions(String id, String source) {
        runFileOperation("versions", id, source, "Loading published versions…", () -> {
            CatalogItem item = findCatalogItem(id, source);
            CatalogItem enriched = catalogClient.enrichVersions(item);
            replaceCatalogItem(item, enriched);
            catalogClient.persist(new ArrayList<>(catalog));
            int releases = enriched.versions() == null ? 0 : enriched.versions().size();
            if (releases > 1 || !VersionOrder.isSourceRevision(enriched.version())) {
                return releases + (releases == 1 ? " published version loaded." : " published versions loaded.");
            }
            return "No published releases were found; latest source remains available.";
        });
    }

    private void resolveInstalledReleaseMetadata() {
        List<CatalogItem> snapshot = new ArrayList<>(catalog);
        int total = 0;
        for (CatalogItem item : snapshot) {
            if ("BMI".equals(item.source()) && findInstalledCatalogMod(item) != null) total++;
        }
        int current = 0;
        for (CatalogItem item : snapshot) {
            if (!"BMI".equals(item.source()) || findInstalledCatalogMod(item) == null) continue;
            current++;
            updateRunningOperation(
                    item.id(),
                    item.source(),
                    "Resolving release " + current + " of " + total + ": " + item.name() + "…"
            );
            try {
                replaceCatalogItem(item, catalogClient.enrichVersions(item));
            } catch (Exception ignored) {
                // Unknown is safer than a false update; the item remains usable as source-only.
            }
        }
        catalogClient.persist(new ArrayList<>(catalog));
    }

    private void replaceCatalogItem(CatalogItem original, CatalogItem replacement) {
        if (original == null || replacement == null) return;
        for (int index = 0; index < catalog.size(); index++) {
            CatalogItem existing = catalog.get(index);
            if (existing.id().equals(original.id()) && existing.source().equals(original.source())) {
                catalog.set(index, replacement);
                return;
            }
        }
    }

    private String installedCatalogRevision(CatalogItem item, String folderName) {
        JSONArray history = installHistoryJson();
        for (int index = 0; index < history.length(); index++) {
            JSONObject entry = history.optJSONObject(index);
            if (entry == null
                    || !item.id().equals(entry.optString("catalogId"))
                    || !item.source().equals(entry.optString("catalogSource"))) continue;
            String recordedFolder = entry.optString("originalName");
            if (!recordedFolder.isBlank() && !recordedFolder.equals(folderName)) continue;
            return entry.optString("catalogVersion", "");
        }
        return "";
    }

    private static int catalogUpdatePriority(CatalogItem item) {
        String identity = ModRepository.normalizeId(item.id() + " " + item.name() + " " + item.folderName());
        if (identity.contains("steamodded") || identity.contains("lovely")) return 0;
        if (identity.contains("talisman")) return 1;
        return 2;
    }

    private List<CatalogItem> orderCatalogUpdatesByDependency(List<CatalogItem> updates) {
        Map<String, CatalogItem> byId = new HashMap<>();
        for (CatalogItem item : updates) {
            for (String identity : new String[]{item.id(), item.name(), item.folderName()}) {
                String canonical = DependencySpec.canonicalId(identity);
                if (!canonical.isBlank()) byId.putIfAbsent(canonical, item);
            }
        }
        List<CatalogItem> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> completed = new HashSet<>();
        for (CatalogItem item : updates) {
            visitCatalogUpdate(item, byId, visiting, completed, ordered);
        }
        return ordered;
    }

    private void visitCatalogUpdate(
            CatalogItem item,
            Map<String, CatalogItem> byId,
            Set<String> visiting,
            Set<String> completed,
            List<CatalogItem> ordered
    ) {
        String key = item.source() + ":" + item.id();
        if (completed.contains(key)) return;
        if (!visiting.add(key)) return;
        ModEntry installed = findInstalledCatalogMod(item);
        if (installed != null) {
            for (String raw : installed.dependencies) {
                CatalogItem dependency = byId.get(DependencySpec.parse(raw).id);
                if (dependency != null) {
                    visitCatalogUpdate(dependency, byId, visiting, completed, ordered);
                }
            }
        }
        visiting.remove(key);
        if (completed.add(key)) ordered.add(item);
    }

    private void installCatalogItem(String id, String source, boolean replaceExisting, String requestedVersion, String requestedDownloadUrl) {
        runFileOperation(replaceExisting ? "update" : "install", id, source,
                replaceExisting ? "Updating mod…" : "Installing mod…", () -> {
            requireScan();
            CatalogItem item = findCatalogItem(id, source);
            String originalVersion = item.version();
            if ("BMI".equals(item.source())) {
                try {
                    CatalogItem enriched = catalogClient.enrichVersions(item);
                    replaceCatalogItem(item, enriched);
                    item = enriched;
                    catalogClient.persist(new ArrayList<>(catalog));
                } catch (Exception ignored) {
                    // Source-only installation remains available when release metadata is offline.
                }
            }
            List<String> missingFrameworks = missingFrameworks(item);
            if (!missingFrameworks.isEmpty()) {
                throw new IllegalStateException(
                        "Install required framework first: "
                                + String.join(", ", missingFrameworks)
                );
            }
            ModEntry existing = null;
            if (replaceExisting) {
                existing = findInstalledCatalogMod(item);
                if (existing == null) {
                    throw new IllegalStateException("The installed copy could not be matched to this catalog entry. Refresh Library and try again.");
                }
            }
            String selectedVersion = requestedVersion == null || requestedVersion.isBlank()
                    ? item.version()
                    : requestedVersion;
            String selectedDownloadUrl = requestedDownloadUrl;
            if (VersionOrder.isSourceRevision(originalVersion)
                    && selectedVersion.equalsIgnoreCase(originalVersion)
                    && !VersionOrder.isSourceRevision(item.version())) {
                selectedVersion = item.version();
                selectedDownloadUrl = "";
            }
            String url = catalogClient.resolveDownloadUrl(item, selectedVersion, selectedDownloadUrl);
            CatalogInstaller.InstallResult result = CatalogInstaller.install(
                    this, scan.folder(), item, url, replaceExisting, existing
            );
            scan = ModRepository.scan(this, selectedTreeUri);
            ModEntry installed = findMod(result.folderName());
            boolean immFixed = applyImmCompatibility(installed);
            if (immFixed) scan = ModRepository.scan(this, selectedTreeUri);
            String actualVersion = installed.version == null || installed.version.isBlank()
                    ? selectedVersion : installed.version;
            String historyId = "install:" + result.folderName() + ":" + System.currentTimeMillis();
            recordInstallHistory(
                    historyId,
                    item.name() + (replaceExisting ? " updated" : " installed"),
                    actualVersion,
                    replaceExisting ? "update" : "install",
                    "",
                    result.folderName()
            );
            attachCatalogMetadata(historyId, item, selectedVersion, url);
            String warning = result.warnings().isEmpty()
                    ? ""
                    : " " + String.join(" ", result.warnings());
            return item.name()
                    + (replaceExisting ? " updated to " : " installed at ")
                    + actualVersion + " and enabled."
                    + (VersionOrder.isSourceRevision(selectedVersion)
                    ? " Source revision " + selectedVersion + "." : "")
                    + (immFixed ? " IMM mobile compatibility was repaired automatically." : "")
                    + warning;
        });
    }

    private List<String> missingFrameworks(CatalogItem item) {
        Set<String> installed = new HashSet<>();
        for (ModEntry mod : scan.mods()) {
            installed.add(ModRepository.normalizeId(mod.id));
            installed.add(ModRepository.normalizeId(mod.name));
            installed.add(ModRepository.normalizeId(mod.folderName));
        }
        List<String> missing = new ArrayList<>();
        String itemIdentity = ModRepository.normalizeId(
                item.id() + " " + item.name() + " " + item.folderName()
        );
        if (item.requiresSteamodded()
                && !itemIdentity.contains("steamodded")
                && installed.stream().noneMatch(value -> value.contains("steamodded"))) {
            missing.add("Steamodded");
        }
        if (item.requiresTalisman()
                && !itemIdentity.contains("talisman")
                && installed.stream().noneMatch(value -> value.contains("talisman"))) {
            missing.add("Talisman");
        }
        return missing;
    }

    private void repairImmVersionParser(String folder) {
        runFileOperation("repair", folder, "IMM", "Applying IMM mobile compatibility fix…", () -> {
            ModEntry mod = findMod(folder);
            if (!isImm(mod)) {
                throw new IllegalArgumentException("This compatibility fix only applies to IMM.");
            }
            return applyImmCompatibility(mod)
                    ? "IMM fixed for Balatro mobile version strings. Restart Balatro before opening IMM."
                    : "IMM mobile version compatibility is already fixed.";
        });
    }

    private int applyAutomaticCompatibilityFixes() throws Exception {
        if (scan == null) return 0;
        int fixed = 0;
        for (ModEntry mod : scan.mods()) {
            if (isImm(mod) && applyImmCompatibility(mod)) fixed++;
        }
        return fixed;
    }

    private boolean applyImmCompatibility(ModEntry mod) throws Exception {
        if (!isImm(mod)) return false;
        DocumentFile versionFile = findImmVersionFile(mod.directory, 0);
        if (versionFile == null) {
            throw new IllegalStateException("IMM's imm/lib/version.lua file was not found.");
        }
        String source = readBoundedText(versionFile, 256 * 1024);
        ImmCompatibility.PatchResult patch = ImmCompatibility.patchVersionParser(source);
        if (!patch.changed()) return false;

        File backupDirectory = new File(getFilesDir(), "compat-backups");
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create private compatibility backup storage.");
        }
        String backupName = (mod.folderName + "-" + mod.version + "-imm-version.lua")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        File backup = new File(backupDirectory, backupName);
        if (!backup.exists()) {
            try (OutputStream output = new FileOutputStream(backup)) {
                output.write(source.getBytes(StandardCharsets.UTF_8));
            }
        }
        try (OutputStream output = getContentResolver().openOutputStream(versionFile.getUri(), "wt")) {
            if (output == null) throw new IllegalStateException("Android denied write access to IMM.");
            output.write(patch.content().getBytes(StandardCharsets.UTF_8));
        }
        return true;
    }

    private static boolean isImm(ModEntry mod) {
        if (mod == null) return false;
        String id = ModRepository.normalizeId(mod.id);
        String name = ModRepository.normalizeId(mod.name);
        return "balatroimm".equals(id)
                || "imm".equals(id)
                || "balatroingamemodmanager".equals(id)
                || "imm".equals(name)
                || "balatroingamemodmanager".equals(name);
    }

    private DocumentFile findImmVersionFile(DocumentFile root, int depth) {
        if (root == null || depth > 4) return null;
        DocumentFile directLib = root.findFile("lib");
        if (directLib != null && directLib.isDirectory()) {
            DocumentFile directVersion = directLib.findFile("version.lua");
            if (directVersion != null && directVersion.isFile()) return directVersion;
        }
        for (DocumentFile child : root.listFiles()) {
            if (child.isDirectory()) {
                DocumentFile found = findImmVersionFile(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String readBoundedText(DocumentFile file, int maxBytes) throws Exception {
        InputStream raw = getContentResolver().openInputStream(file.getUri());
        if (raw == null) throw new IllegalStateException("The selected file could not be read.");
        try (InputStream input = raw; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("The compatibility file is unexpectedly large.");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void launchBalatro() {
        main.post(() -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(KNOWN_BALATRO_PACKAGE);
            if (launch == null) {
                message = "Balatro Modded is not installed.";
                pushState();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        });
    }

    private void detectNativeInstalled() {
        detectNativeInstalled(true);
    }

    /**
     * Read-only installed-package detection. The silent variant is used when
     * Native is opened so the result is ready without showing a launch toast.
     */
    private void detectNativeInstalled(boolean notify) {
        io.execute(() -> {
            String foundPackage = "";
            String foundLabel = "";
            for (String candidate : NATIVE_BALATRO_PACKAGES) {
                try {
                    ApplicationInfo info = getPackageManager().getApplicationInfo(candidate, 0);
                    foundPackage = info.packageName;
                    foundLabel = getPackageManager().getApplicationLabel(info).toString();
                    break;
                } catch (PackageManager.NameNotFoundException ignored) {
                    // Try the next known package or the label-based fallback below.
                }
            }
            if (foundPackage.isBlank()) {
                try {
                    for (ApplicationInfo info : getPackageManager().getInstalledApplications(0)) {
                        if (getPackageName().equals(info.packageName)) {
                            continue;
                        }
                        String label = getPackageManager().getApplicationLabel(info).toString();
                        if (label.toLowerCase(Locale.ROOT).contains("balatro")) {
                            foundPackage = info.packageName;
                            foundLabel = label;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                    // Package visibility can be restricted; the picker route remains available.
                }
            }
            final String result;
            if (foundPackage.isBlank()) {
                nativeInstalledPackage = "";
                result = "No installed Balatro package is visible to Android. Select an APK or split APK for preflight.";
            } else {
                nativeInstalledPackage = foundPackage;
                boolean officialStoreCopy = foundPackage.equals("com.playstack.balatro.android")
                        || foundPackage.equals("com.playstack.balatro");
                if (officialStoreCopy) {
                    result = "Official Play Store copy " + foundLabel + " (" + foundPackage + ") detected. "
                            + "MBM can use it as a read-only source for a separate personal mod-capable copy. "
                            + "The original app is left installed and untouched.";
                } else {
                    result = "Installed package " + foundLabel + " (" + foundPackage + ") detected. "
                            + "This copy cannot be patched safely. Use the Steam/local route instead.";
                }
            }
            nativeCompatibility = foundPackage.isBlank()
                    ? "unsupported"
                    : (foundPackage.equals("com.playstack.balatro.android") || foundPackage.equals("com.playstack.balatro")
                    ? "playstore-source" : "unsupported");
            nativePreflight = result;
            main.post(() -> {
                if (notify) message = result;
                pushState();
            });
        });
    }

    private void exportReport() {
        main.post(() -> {
            pendingReport = buildReport();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "balatro-mobile-mod-manager-diagnostic.json");
            try {
                startActivityForResult(intent, CREATE_REPORT);
            } catch (ActivityNotFoundException error) {
                message = "This Android build has no compatible document exporter.";
                pushState();
            }
        });
    }

    private void exportDiagnosticZip(boolean share) {
        runFileOperation("diagnostic", "diagnostic", "local", "Building diagnostic ZIP…", () -> {
            requireScan();
            File archive = buildDiagnosticArchive();
            main.post(() -> {
                if (share) {
                    shareDiagnosticArchive(archive);
                } else {
                    pendingDiagnosticArchive = archive;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_TITLE, archive.getName());
                    try {
                        startActivityForResult(intent, CREATE_DIAGNOSTIC_ZIP);
                    } catch (ActivityNotFoundException error) {
                        pendingDiagnosticArchive = null;
                        //noinspection ResultOfMethodCallIgnored
                        archive.delete();
                        message = "This Android build has no compatible ZIP exporter.";
                        pushState();
                    }
                }
            });
            return share
                    ? "Diagnostic ZIP ready to share."
                    : "Diagnostic ZIP ready. Choose where to save it.";
        });
    }

    private File buildDiagnosticArchive() throws Exception {
        for (File old : getCacheDir().listFiles() == null ? new File[0] : getCacheDir().listFiles()) {
            if (old.getName().startsWith("MBM-diagnostic-") && old.getName().endsWith(".zip")) {
                //noinspection ResultOfMethodCallIgnored
                old.delete();
            }
        }
        File archive = new File(getCacheDir(), "MBM-diagnostic-" + System.currentTimeMillis() + ".zip");
        Map<String, byte[]> entries = DiagnosticBundle.entries();
        entries.put("README.txt", DiagnosticBundle.utf8(
                "MBM diagnostic bundle\n\n"
                        + "Send this ZIP when asking for help with mod versions, dependencies or crashes.\n"
                        + "It contains the mod inventory, parsed metadata, catalog matches, install receipts, scan errors "
                        + "and bounded text files useful for debugging.\n\n"
                        + "Excluded by design: Balatro game files/APKs, save data, images, audio, credentials, pairing tokens "
                        + "and binary mod assets. Lines that look like passwords, tokens, cookies or API keys are redacted.\n"
        ));
        entries.put("environment.json", DiagnosticBundle.utf8(buildReport()));
        entries.put("catalog-status.json", DiagnosticBundle.utf8(buildCatalogDiagnostic().toString(2)));
        entries.put("install-history.json", DiagnosticBundle.utf8(installHistoryJson().toString(2)));

        JSONArray inventory = new JSONArray();
        int[] fileCount = {0};
        long[] includedBytes = {0};
        if (scan != null) {
            for (ModEntry mod : scan.mods()) {
                collectDiagnosticFiles(
                        mod.directory,
                        "mods/" + safeDiagnosticSegment(mod.folderName),
                        0,
                        inventory,
                        entries,
                        fileCount,
                        includedBytes
                );
            }
        }
        entries.put("inventory.json", DiagnosticBundle.utf8(inventory.toString(2)));
        return DiagnosticBundle.write(archive, entries);
    }

    private JSONObject buildCatalogDiagnostic() {
        JSONObject result = new JSONObject();
        JSONArray matches = new JSONArray();
        try {
            result.put("generatedAt", System.currentTimeMillis());
            result.put("catalogItems", catalog.size());
            if (scan != null) {
                for (ModEntry installed : scan.mods()) {
                    JSONObject row = new JSONObject();
                    row.put("folder", installed.folderName);
                    row.put("id", installed.id);
                    row.put("installedVersion", installed.version);
                    CatalogItem match = null;
                    for (CatalogItem item : catalog) {
                        if (findInstalledCatalogMod(item) == installed) {
                            match = item;
                            break;
                        }
                    }
                    if (match == null) {
                        row.put("match", JSONObject.NULL);
                        row.put("updateState", "unmatched");
                    } else {
                        CatalogUpdatePolicy.Result status = catalogUpdateStatus(match, installed);
                        row.put("match", match.toJson(true, installed.version, status));
                        row.put("updateState", status.wireValue());
                        row.put("updateReason", status.reason());
                        row.put("installedCatalogRevision", installedCatalogRevision(match, installed.folderName));
                    }
                    matches.put(row);
                }
            }
            result.put("mods", matches);
        } catch (Exception error) {
            try {
                result.put("error", readable(error));
            } catch (Exception ignored) {
                // Keep a valid partial diagnostic object.
            }
        }
        return result;
    }

    private void collectDiagnosticFiles(
            DocumentFile directory,
            String prefix,
            int depth,
            JSONArray inventory,
            Map<String, byte[]> entries,
            int[] fileCount,
            long[] includedBytes
    ) throws Exception {
        if (directory == null || depth > 8 || fileCount[0] >= 5_000) return;
        for (DocumentFile child : directory.listFiles()) {
            if (fileCount[0] >= 5_000) return;
            String name = child.getName() == null ? "unnamed" : child.getName();
            String path = prefix + "/" + safeDiagnosticSegment(name);
            if (child.isDirectory()) {
                collectDiagnosticFiles(child, path, depth + 1, inventory, entries, fileCount, includedBytes);
                continue;
            }
            if (!child.isFile()) continue;
            fileCount[0]++;
            JSONObject item = new JSONObject();
            item.put("path", path);
            item.put("size", child.length());
            item.put("lastModified", child.lastModified());
            item.put("type", child.getType() == null ? "" : child.getType());
            item.put("includedText", false);

            if (isDiagnosticText(name) && includedBytes[0] < 12L * 1024L * 1024L) {
                byte[] raw = readBoundedDocument(child, 256 * 1024);
                if (raw != null && includedBytes[0] + raw.length <= 12L * 1024L * 1024L) {
                    String redacted = redactDiagnosticText(new String(raw, StandardCharsets.UTF_8));
                    byte[] safe = redacted.getBytes(StandardCharsets.UTF_8);
                    entries.put("diagnostic-files/" + path, safe);
                    includedBytes[0] += safe.length;
                    item.put("includedText", true);
                    item.put("sha256", sha256(raw));
                }
            }
            inventory.put(item);
        }
    }

    private byte[] readBoundedDocument(DocumentFile file, int limit) throws Exception {
        InputStream raw = getContentResolver().openInputStream(file.getUri());
        if (raw == null) return null;
        try (InputStream input = raw; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16_384];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) return null;
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static boolean isDiagnosticText(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".json") || lower.endsWith(".toml") || lower.endsWith(".lua")
                || lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md")
                || lower.endsWith(".cfg") || lower.endsWith(".ini");
    }

    private static String safeDiagnosticSegment(String value) {
        String safe = value == null ? "unnamed" : value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) return "unnamed";
        return safe;
    }

    private static String redactDiagnosticText(String value) {
        return DiagnosticBundle.redact(value);
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder output = new StringBuilder(digest.length * 2);
        for (byte item : digest) output.append(String.format(Locale.ROOT, "%02x", item));
        return output.toString();
    }

    private void shareDiagnosticArchive(File archive) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    archive
            );
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, "MBM diagnostic bundle");
            send.putExtra(Intent.EXTRA_TEXT, "MBM diagnostic bundle for mod troubleshooting.");
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("MBM diagnostic ZIP", uri));
            startActivity(Intent.createChooser(send, "Share diagnostic ZIP"));
        } catch (Exception error) {
            message = readable(error);
            pushState();
        }
    }

    private void resetSettings() {
        main.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript(
                        "(function(){['bmm-wallpaper','bmm-advanced','bmm-crash-opt-in','bmm-history-retention'].forEach(function(key){localStorage.removeItem(key);});location.reload();})()",
                        null
                );
            }
            message = "App preferences reset";
            pushState();
        });
    }

    private void openAwesomeBalatro() {
        main.post(() -> {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/jie65535/awesome-balatro")
            );
            startActivity(intent);
        });
    }

    private void openModWebsite(String address) {
        main.post(() -> {
            try {
                Uri uri = Uri.parse(address);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                    throw new IllegalArgumentException("This source link is not a secure HTTPS URL.");
                }
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception error) {
                message = readable(error);
                pushState();
            }
        });
    }

    private void runFileOperation(CheckedOperation operation) {
        runFileOperation("operation", "global", "local", "Working…", operation);
    }

    private void runFileOperation(
            String kind,
            String itemId,
            String source,
            String startLabel,
            CheckedOperation operation
    ) {
        String safeKind = kind == null || kind.isBlank() ? "operation" : kind;
        String safeItemId = itemId == null || itemId.isBlank() ? "global" : itemId;
        String safeSource = source == null ? "" : source;
        String safeLabel = startLabel == null || startLabel.isBlank() ? "Working…" : startLabel;
        OperationStatus status = new OperationStatus(
                UUID.randomUUID().toString(),
                safeKind,
                safeItemId,
                safeSource,
                safeLabel,
                "queued",
                isExclusiveOperation(safeKind, safeItemId)
        );
        synchronized (operationLock) {
            boolean duplicate = operations.stream().anyMatch(existing ->
                    existing.itemId.equals(status.itemId)
                            && ("queued".equals(existing.status) || "running".equals(existing.status))
            );
            if (duplicate) {
                message = safeLabel.replace("…", "") + " is already queued.";
                pushState();
                return;
            }
            operations.add(status);
        }
        message = runningOperation == null ? safeLabel : "Queued: " + safeLabel;
        pushState();
        io.execute(() -> {
            status.status = "running";
            runningOperation = status;
            main.post(this::pushState);
            String resultMessage;
            try {
                resultMessage = operation.run();
                if (selectedTreeUri != null) {
                    scan = ModRepository.scan(this, selectedTreeUri);
                }
            } catch (Exception error) {
                resultMessage = readable(error);
            }
            final String finalMessage = resultMessage;
            main.post(() -> {
                synchronized (operationLock) {
                    operations.remove(status);
                }
                if (runningOperation == status) runningOperation = null;
                message = finalMessage;
                pushState();
            });
        });
    }

    private void updateRunningOperation(String itemId, String source, String label) {
        OperationStatus active = runningOperation;
        if (active == null) return;
        active.itemId = itemId == null ? "" : itemId;
        active.source = source == null ? "" : source;
        active.label = label == null ? "" : label;
        main.post(this::pushState);
    }

    private static boolean isExclusiveOperation(String kind, String itemId) {
        return "global".equals(itemId)
                || "multiple".equals(itemId)
                || "all".equals(itemId)
                || "desktop".equals(itemId)
                || "update-all".equals(kind)
                || "cleanup".equals(kind)
                || "restore".equals(kind);
    }

    private void pushState() {
        if (!pageReady || webView == null) {
            return;
        }
        JSONObject state = stateJson();
        String script = "window.__nativeReceive && window.__nativeReceive("
                + JSONObject.quote(state.toString())
                + ");";
        main.post(() -> {
            if (pageReady && webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private JSONObject stateJson() {
        JSONObject state = new JSONObject();
        try {
            boolean connected = selectedTreeUri != null && scan != null;
            state.put("connected", connected);
            state.put("providerDetected", isProviderAvailable(KNOWN_PROVIDER));
            state.put("loading", loading);
            JSONArray operationItems = new JSONArray();
            OperationStatus primary = runningOperation;
            synchronized (operationLock) {
                for (OperationStatus operation : operations) operationItems.put(operation.toJson());
                if (primary == null && !operations.isEmpty()) primary = operations.get(0);
            }
            state.put("operations", operationItems);
            state.put("operation", primary == null
                    ? new JSONObject().put("active", false)
                    : primary.toJson().put("active", true));
            state.put("folder", connected ? scan.folderName() : "");
            state.put("gameFile", steamSourceUploaded ? selectedSteamSourceName : (connected ? "Balatro local copy detected" : ""));
            state.put("steamSourceName", selectedSteamSourceName);
            state.put("steamSourceUploaded", steamSourceUploaded);
            state.put("nativeCompatibility", nativeCompatibility);
            state.put("nativePreflight", nativePreflight);
            state.put("saveFolder", selectedSaveTreeUri == null ? "" : "Connected save folder");
            state.put("saveTargetFolder", selectedSaveTargetUri == null ? "" : "Connected target folder");
            state.put("saveFileCount", safeSaveFileCount());
            state.put("saveProfiles", safeSaveProfiles());
            state.put("desktopPaired", desktopPaired);
            state.put("desktopManifest", desktopManifest);
            state.put("desktopBuild", desktopBuild);
            try {
                JSONObject desktop = desktopManifest.isBlank() ? new JSONObject() : new JSONObject(desktopManifest);
                state.put("desktopSaveSummary", desktop.optJSONObject("saves") == null ? new JSONObject() : desktop.optJSONObject("saves"));
                state.put("desktopModSummary", desktop.optJSONObject("mods") == null ? new JSONObject() : desktop.optJSONObject("mods"));
            } catch (Exception ignored) {
                state.put("desktopSaveSummary", new JSONObject());
                state.put("desktopModSummary", new JSONObject());
            }
            state.put("canUndo", snapshots.latest() != null);
            state.put("message", message);
            state.put("version", BuildConfig.VERSION_NAME);
            state.put("channel", BuildConfig.BMM_CHANNEL);

            JSONArray mods = new JSONArray();
            int active = 0;
            int hidden = 0;
            int problems = 0;
            if (scan != null) {
                for (ModEntry mod : scan.mods()) {
                    mods.put(mod.toJson());
                    if (mod.hidden) hidden++;
                    else active++;
                    if ("error".equals(mod.severity) || "warning".equals(mod.severity)) {
                        problems++;
                    }
                }
            }
            state.put("mods", mods);
            state.put("counts", new JSONObject()
                    .put("active", active)
                    .put("hidden", hidden)
                    .put("problems", problems));
            state.put("junkCount", scan == null ? 0 : scan.junkNames().size());
            state.put("updatesAvailable", availableCatalogUpdates().size());
            state.put("recovery", recovery.toJson());

            JSONArray history = new JSONArray();
            for (SnapshotStore.Snapshot snapshot : snapshots.list()) {
                history.put(snapshot.toJson());
            }
            state.put("history", history);
            JSONArray saveBackups = saveBackupHistoryJson();
            JSONArray allBackups = new JSONArray();
            for (int i = 0; i < history.length(); i++) allBackups.put(history.get(i));
            for (int i = 0; i < saveBackups.length(); i++) allBackups.put(saveBackups.get(i));
            state.put("backupHistory", allBackups);
            state.put("installHistory", installHistoryJson());

            JSONArray available = new JSONArray();
            for (CatalogItem item : catalog) {
                ModEntry installedMod = findInstalledCatalogMod(item);
                available.put(item.toJson(
                        installedMod != null,
                        installedMod == null ? "" : installedMod.version,
                        installedMod == null
                                ? new CatalogUpdatePolicy.Result(
                                CatalogUpdatePolicy.Status.UNKNOWN,
                                "This mod is not installed."
                        )
                                : catalogUpdateStatus(item, installedMod)
                ));
            }
            state.put("catalog", available);
            state.put("catalogSources", new JSONArray(List.of(
                    "Balatro Mod Index",
                    "Thunderstore",
                    "Awesome Balatro"
            )));
        } catch (Exception error) {
            try {
                state.put("message", "Could not serialize application state.");
            } catch (Exception ignored) {
                // Empty JSON is still safer than crashing the app.
            }
        }
        return state;
    }

    private String buildReport() {
        JSONObject report = new JSONObject();
        try {
            report.put("app", "MBM - Mobile Balatro Manager");
            report.put("version", BuildConfig.VERSION_NAME);
            report.put("createdAt", System.currentTimeMillis());
            report.put("balatroPackageDetected", isPackageInstalled(KNOWN_BALATRO_PACKAGE));
            report.put("providerDetected", isProviderAvailable(KNOWN_PROVIDER));
            report.put("modsFolder", scan == null || scan.folder() == null
                    ? "" : String.valueOf(scan.folder().getName()));
            JSONArray mods = new JSONArray();
            if (scan != null) {
                for (ModEntry mod : scan.mods()) {
                    mods.put(mod.toJson());
                }
                report.put("scanErrors", new JSONArray(scan.scanErrors()));
            }
            report.put("mods", mods);
            report.put("junk", scan == null ? new JSONArray() : new JSONArray(scan.junkNames()));
            report.put("recovery", recovery.toJson());
            report.put("privacy", "No game APK, save data, credentials or pairing tokens included.");
            return report.toString(2);
        } catch (Exception error) {
            return "{\"error\":\"Could not build diagnostic report\"}";
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private boolean isProviderAvailable(String authority) {
        ProviderInfo provider = getPackageManager().resolveContentProvider(
                authority,
                PackageManager.MATCH_ALL
        );
        return provider != null;
    }

    private ModEntry findMod(String folder) {
        requireScan();
        for (ModEntry mod : scan.mods()) {
            if (mod.folderName.equals(folder)) {
                return mod;
            }
        }
        throw new IllegalArgumentException("Mod not found: " + folder);
    }

    private CatalogItem findCatalogItem(String id, String source) {
        for (CatalogItem item : catalog) {
            if (item.id().equals(id) && item.source().equals(source)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Catalog item is no longer available.");
    }

    private ModEntry findInstalledCatalogMod(CatalogItem item) {
        if (scan == null || item == null) return null;
        String itemId = ModRepository.normalizeId(item.id());
        String itemName = ModRepository.normalizeId(item.name());
        String itemFolder = ModRepository.normalizeId(item.folderName());
        for (ModEntry mod : scan.mods()) {
            String modId = ModRepository.normalizeId(mod.id);
            String modName = ModRepository.normalizeId(mod.name);
            String modFolder = ModRepository.normalizeId(mod.folderName);
            if (itemId.equals(modId) || itemId.equals(modName) || itemId.equals(modFolder)
                    || itemName.equals(modId) || itemName.equals(modName) || itemName.equals(modFolder)
                    || itemFolder.equals(modId) || itemFolder.equals(modName) || itemFolder.equals(modFolder)) {
                return mod;
            }
        }
        return null;
    }

    private void requireScan() {
        if (scan == null || selectedTreeUri == null) {
            throw new IllegalStateException("Connect the Mods folder first.");
        }
    }

    private static String readable(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static boolean bool(JSONObject payload, String key) {
        return payload != null && payload.optBoolean(key);
    }

    private static String string(JSONObject payload, String key) {
        return payload == null ? "" : payload.optString(key);
    }

    private final class NativeBridge {
        @JavascriptInterface
        public void invoke(String method, String rawPayload) {
            JSONObject payload;
            try {
                payload = rawPayload == null || rawPayload.isBlank()
                        ? new JSONObject()
                        : new JSONObject(rawPayload);
            } catch (Exception ignored) {
                payload = new JSONObject();
            }
            switch (method) {
                case "getState" -> {
                    pushState();
                    if (scan == null && selectedTreeUri != null) {
                        refresh(false);
                    }
                }
                case "refresh" -> refresh(false);
                case "chooseFolder" -> chooseFolder(bool(payload, "automatic"));
                case "chooseSaveFolder" -> chooseSaveFolder();
                case "chooseSaveTarget" -> chooseSaveTarget();
                case "toggleMod" ->
                        toggleMod(string(payload, "folder"), bool(payload, "hidden"));
                case "toggleMods" -> toggleMods(payload.optJSONArray("folders"), bool(payload, "hidden"));
                case "deleteMod" -> deleteMod(string(payload, "folder"));
                case "deleteMods" -> deleteMods(payload.optJSONArray("folders"));
                case "cleanAllJunk" -> cleanAllJunk();
                case "updateAllMods" -> updateAllCatalogMods();
                case "saveSnapshot" -> runFileOperation(() -> {
                    requireScan();
                    snapshots.create("Manual backup", scan.mods());
                    return "Backup saved";
                });
                case "quickRescue" -> quickRescue();
                case "undo" -> undoLatest();
                case "restoreSnapshot" -> restoreSnapshot(string(payload, "id"));
                case "reinstallInstall" -> reinstallInstall(string(payload, "id"));
                case "beginIsolation" -> beginIsolation();
                case "isolationResult" -> isolationResult(bool(payload, "opened"));
                case "finishIsolation" -> finishIsolation();
                case "launchBalatro" -> launchBalatro();
                case "loadCatalog" -> loadCatalog();
                case "loadCatalogVersions" -> loadCatalogVersions(
                        string(payload, "id"),
                        string(payload, "source")
                );
                case "installCatalogMod" -> installCatalogItem(
                        string(payload, "id"),
                        string(payload, "source"),
                        string(payload, "version"),
                        string(payload, "downloadUrl")
                );
                case "updateCatalogMod" -> updateCatalogItem(
                        string(payload, "id"),
                        string(payload, "source"),
                        string(payload, "version"),
                        string(payload, "downloadUrl")
                );
                case "importMod" -> importMod();
                case "importModFolder" -> importModFolder();
                case "repairImmVersion" -> repairImmVersionParser(string(payload, "folder"));
                case "importDesktopMods" -> importDesktopMods();
                case "exportHistory" -> exportReport();
                case "deleteHistoryEntry" -> deleteHistoryEntry(
                        string(payload, "id"),
                        string(payload, "kind")
                );
                case "setHistoryRetention" -> setHistoryRetention(payload.optInt("limit", 20));
                case "previewSave" -> {
                    String option = string(payload, "option");
                    String profile = string(payload, "profile");
                    if (selectedSaveTreeUri == null) chooseSaveFolder();
                    else if (("selected".equals(option) || "all".equals(option)) && selectedSaveTargetUri == null) chooseSaveTarget();
                    else runFileOperation(() -> {
                        int count = safeSaveFileCount();
                        return switch (option) {
                            case "selected", "all" -> {
                                DocumentFile source = saveSourceForProfile(option, profile);
                                DocumentFile target = requireSaveRoot(selectedSaveTargetUri, true);
                                int conflicts = countSaveConflicts(source, target, 0);
                                yield countSaveFiles(source, 0) + " save file(s) reviewed. " + conflicts + " conflict(s) found. Press Import to apply.";
                            }
                            case "none" -> "Clean build selected. Existing progress will not be copied.";
                            default -> count + " save file(s) ready for review.";
                        };
                    });
                }
                case "importSave" -> importSave(string(payload, "option"), string(payload, "profile"));
                case "importDesktopSave" -> importDesktopSave(string(payload, "profile"));
                case "exportSave" -> exportSave();
                case "buildSteam" -> startDesktopBuild(bool(payload, "importSaves"));
                case "buildNativePersonal" -> buildNativePersonal();
                case "buildNative" -> buildNativePersonal();
                case "downloadDesktopArtifact" -> downloadDesktopArtifact();
                case "shareArtifact" -> shareDesktopArtifact();
                case "installArtifact" -> installDesktopArtifact();
                case "detectNative" -> detectNativeInstalled();
                case "selectNativeApk" -> selectNativeApk();
                case "detectNativeLegacy" -> {
                    nativeCompatibility = "unsupported";
                    nativePreflight = "This copy cannot be patched safely. Use the Steam/local route instead.";
                    message = nativePreflight;
                    pushState();
                }
                case "pairDesktop" -> pairDesktop(string(payload, "address"), string(payload, "code"));
                case "selectSteamGame" -> selectSteamGame();
                case "selectSteamFolder" -> selectSteamFolder();
                case "chooseCatalogVersion" -> {
                    message = "Choose a version from the catalog card before installing.";
                    pushState();
                }
                case "viewInstall" -> {
                    message = "Open History > Installation History to review this mod's installation record.";
                    pushState();
                }
                case "resetSettings" -> resetSettings();
                case "exportReport" -> exportReport();
                case "saveDiagnosticZip" -> exportDiagnosticZip(false);
                case "shareDiagnosticZip" -> exportDiagnosticZip(true);
                case "openAwesomeBalatro" -> openAwesomeBalatro();
                case "openModWebsite" -> openModWebsite(string(payload, "url"));
                default -> {
                    message = "Unknown command: " + method;
                    pushState();
                }
            }
        }
    }

    private final class LocalOnlyClient extends WebViewClient {
        private final WebViewAssetLoader assetLoader;

        private LocalOnlyClient(WebViewAssetLoader assetLoader) {
            this.assetLoader = assetLoader;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(
                WebView view,
                WebResourceRequest request
        ) {
            WebResourceResponse response =
                    assetLoader.shouldInterceptRequest(request.getUrl());
            return response != null ? response : super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (WEB_URL.equals(url)) {
                pageReady = true;
                pushState();
                if (selectedTreeUri != null && scan == null) {
                    refresh(false);
                }
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("https".equals(uri.getScheme())
                    && WEB_HOST.equals(uri.getHost())) {
                return false;
            }
            if ("https".equals(uri.getScheme())) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
            return true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(
                WebView view,
                int errorCode,
                String description,
                String failingUrl
        ) {
            if (WEB_URL.equals(failingUrl)) {
                TextView fallback = new TextView(MainActivity.this);
                fallback.setText(getString(R.string.webview_error));
                fallback.setTextColor(Color.WHITE);
                fallback.setTextSize(18);
                fallback.setPadding(48, 80, 48, 48);
                fallback.setBackgroundColor(Color.rgb(4, 25, 29));
                setContentView(fallback);
            }
        }
    }

    private static final class OperationStatus {
        final String token;
        final String kind;
        volatile String itemId;
        volatile String source;
        volatile String label;
        volatile String status;
        final boolean exclusive;

        private OperationStatus(
                String token,
                String kind,
                String itemId,
                String source,
                String label,
                String status,
                boolean exclusive
        ) {
            this.token = token;
            this.kind = kind;
            this.itemId = itemId;
            this.source = source;
            this.label = label;
            this.status = status;
            this.exclusive = exclusive;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("token", token);
                json.put("kind", kind);
                json.put("itemId", itemId);
                json.put("source", source);
                json.put("label", label);
                json.put("status", status);
                json.put("exclusive", exclusive);
                json.put("active", true);
                return json;
            } catch (Exception error) {
                throw new IllegalStateException("Could not serialize operation state.", error);
            }
        }
    }

    @FunctionalInterface
    private interface CheckedOperation {
        String run() throws Exception;
    }

    private record RecoverySession(
            boolean active,
            int step,
            int totalSteps,
            List<String> suspects,
            List<String> testing,
            boolean complete,
            String culprit
    ) {
        static RecoverySession empty() {
            return new RecoverySession(false, 0, 0, List.of(), List.of(), false, "");
        }

        JSONObject toJson() {
            JSONObject result = new JSONObject();
            try {
                result.put("active", active);
                result.put("step", step);
                result.put("totalSteps", totalSteps);
                result.put("suspects", new JSONArray(suspects));
                result.put("testing", new JSONArray(testing));
                result.put("complete", complete);
                result.put("culprit", culprit);
            } catch (Exception error) {
                throw new IllegalStateException("Could not serialize recovery state.", error);
            }
            return result;
        }
    }
}
