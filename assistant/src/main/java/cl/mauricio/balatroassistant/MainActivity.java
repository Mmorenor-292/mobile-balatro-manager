package cl.mauricio.balatroassistant;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_CRASH = 3201;
    private static final int PICK_MODS = 3202;
    private static final int EXPORT_ARTIFACT = 3203;
    private static final String HOST = "appassets.androidplatform.net";
    private static final String WEB_URL = "https://" + HOST + "/assets/web/index.html";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView webView;
    private boolean pageReady;
    private boolean paired;
    private boolean assistantAvailable;
    private String baseUrl = "";
    private String token = "";
    private String crashAttachment = "";
    private String modsAttachment = "";
    private String operationLabel = "";
    private String message = "Pair with BMM Helper on your desktop to begin.";
    private JSONObject job;
    private String pendingArtifactJob = "";

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(2, 18, 22));
        getWindow().setNavigationBarColor(Color.rgb(2, 18, 22));
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.setBackgroundColor(Color.rgb(2, 18, 22));
        webView.addJavascriptInterface(new NativeBridge(), "AssistantBridge");
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public void onPageFinished(WebView view, String url) { pageReady = true; pushState(); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !HOST.equalsIgnoreCase(request.getUrl().getHost());
            }
        });
        setContentView(webView);
        webView.loadUrl(WEB_URL);
    }

    @Override protected void onDestroy() {
        pageReady = false;
        io.shutdownNow();
        if (webView != null) { webView.removeJavascriptInterface("AssistantBridge"); webView.destroy(); }
        super.onDestroy();
    }

    private void pair(String address, String code) {
        if (address == null || address.isBlank() || code == null || !code.matches("\\d{6}")) {
            message = "Enter the helper address and six-digit pairing code."; pushState(); return;
        }
        startOperation("Pairing with desktop…");
        io.execute(() -> {
            try {
                URL candidate = new URL(address.startsWith("http://") || address.startsWith("https://") ? address : "http://" + address);
                InetAddress resolved = InetAddress.getByName(candidate.getHost());
                if (!resolved.isLoopbackAddress() && !resolved.isSiteLocalAddress()) throw new IllegalArgumentException("Pairing is limited to the local network.");
                String root = candidate.toString().replaceAll("/+$", "");
                JSONObject manifest = getJson(root + "/manifest?code=" + Uri.encode(code));
                String nextToken = manifest.optString("token", "");
                if (nextToken.isBlank()) throw new IllegalStateException("Helper did not return a session token.");
                baseUrl = root;
                token = nextToken;
                paired = true;
                assistantAvailable = manifest.optBoolean("assistantAvailable", false);
                finishOperation(assistantAvailable
                        ? "Desktop paired. Codex OAuth stays on the desktop."
                        : "Desktop paired, but Codex CLI is not available there.");
            } catch (Exception error) {
                paired = false; assistantAvailable = false; baseUrl = ""; token = "";
                finishOperation("Could not pair: " + readable(error));
            }
        });
    }

    private void pickFile(boolean crash) {
        main.post(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (crash) {
                intent.setType("text/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/markdown", "application/octet-stream"});
            } else {
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivityForResult(intent, crash ? PICK_CRASH : PICK_MODS); }
            catch (ActivityNotFoundException error) { message = "No compatible file picker is installed."; pushState(); }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_CRASH || requestCode == PICK_MODS) uploadAttachment(data.getData(), requestCode == PICK_CRASH);
        else if (requestCode == EXPORT_ARTIFACT) downloadArtifact(data.getData());
    }

    private void uploadAttachment(Uri uri, boolean crash) {
        if (!paired) { message = "Pair the desktop helper first."; pushState(); return; }
        startOperation(crash ? "Uploading crash log…" : "Inspecting Mods ZIP…");
        io.execute(() -> {
            File temporary = null;
            try {
                String name = displayName(uri, crash ? "crash.log" : "Mods.zip");
                temporary = new File(getCacheDir(), (crash ? "assistant-crash-" : "assistant-mods-") + System.nanoTime() + (crash ? ".log" : ".zip"));
                long maximum = crash ? 2L * 1024L * 1024L : 50L * 1024L * 1024L;
                try (InputStream input = getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new IllegalStateException("Selected file could not be opened.");
                    copyBounded(input, output, maximum);
                }
                postFile(baseUrl + "/assistant-upload?token=" + Uri.encode(token) + "&kind=" + (crash ? "crash" : "mods") + "&name=" + Uri.encode(name), temporary);
                if (crash) crashAttachment = name; else modsAttachment = name;
                finishOperation(crash ? "Crash log attached." : "Mods ZIP attached and safely extracted to staging.");
            } catch (Exception error) { finishOperation("Attachment failed: " + readable(error)); }
            finally { if (temporary != null) temporary.delete(); }
        });
    }

    private void runAssistant(String task, String prompt) {
        if (!paired || !assistantAvailable) { message = "Pair a desktop with Codex available first."; pushState(); return; }
        if (prompt != null && prompt.length() > 8000) { message = "Prompt exceeds 8,000 characters."; pushState(); return; }
        startOperation("Terra is inspecting the staging copy…");
        io.execute(() -> {
            try {
                JSONObject payload = new JSONObject().put("task", task).put("prompt", prompt == null ? "" : prompt);
                JSONObject started = postJson(baseUrl + "/assistant-run?token=" + Uri.encode(token), payload);
                String id = started.optString("jobId", "");
                if (id.isBlank()) throw new IllegalStateException("Helper did not return a job ID.");
                long deadline = System.currentTimeMillis() + 20L * 60L * 1000L;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(1600L);
                    JSONObject status = getJson(baseUrl + "/assistant-status?token=" + Uri.encode(token) + "&id=" + Uri.encode(id));
                    job = status;
                    main.post(this::pushState);
                    String value = status.optString("status", "");
                    if ("completed".equals(value)) { finishOperation("Assistant result is ready for explicit review."); return; }
                    if ("failed".equals(value)) throw new IllegalStateException(status.optString("error", "Assistant job failed."));
                }
                throw new IllegalStateException("Assistant timed out after 20 minutes.");
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); finishOperation("Assistant run was interrupted."); }
            catch (Exception error) { finishOperation("Assistant failed: " + readable(error)); }
        });
    }

    private void exportArtifact(String id) {
        if (id == null || id.isBlank() || job == null || !job.optBoolean("artifactReady")) {
            message = "This result has no repair ZIP to export."; pushState(); return;
        }
        pendingArtifactJob = id;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "balatro-assistant-" + id.substring(0, Math.min(8, id.length())) + ".zip");
        try { startActivityForResult(intent, EXPORT_ARTIFACT); }
        catch (ActivityNotFoundException error) { message = "No compatible file exporter is installed."; pushState(); }
    }

    private void downloadArtifact(Uri destination) {
        String id = pendingArtifactJob; pendingArtifactJob = "";
        if (id.isBlank()) return;
        startOperation("Exporting reviewed ZIP…");
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + "/assistant-artifact?token=" + Uri.encode(token) + "&id=" + Uri.encode(id)).openConnection();
                connection.setConnectTimeout(5000); connection.setReadTimeout(120000); connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                if (status >= 400) throw new IllegalStateException(readBody(connection, status));
                try (InputStream input = connection.getInputStream(); OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IllegalStateException("Selected destination is not writable.");
                    copyBounded(input, output, 250L * 1024L * 1024L);
                }
                finishOperation("Proposed ZIP exported. Import it only after reviewing the included REPAIR.md.");
            } catch (Exception error) { finishOperation("Export failed: " + readable(error)); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void startOperation(String label) { operationLabel = label; message = label; pushState(); }
    private void finishOperation(String result) { main.post(() -> { operationLabel = ""; message = result; pushState(); }); }

    private JSONObject getJson(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(120000); connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        try { String body = readBody(connection, status); if (status >= 400) throw new IllegalStateException(body); return new JSONObject(body); }
        finally { connection.disconnect(); }
    }

    private JSONObject postJson(String endpoint, JSONObject payload) throws Exception {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(120000); connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json"); connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        int status = connection.getResponseCode();
        try { String body = readBody(connection, status); if (status >= 400) throw new IllegalStateException(body); return new JSONObject(body); }
        finally { connection.disconnect(); }
    }

    private void postFile(String endpoint, File file) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(120000); connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/octet-stream"); connection.setFixedLengthStreamingMode(file.length());
        try (InputStream input = new FileInputStream(file); OutputStream output = connection.getOutputStream()) { copyBounded(input, output, 50L * 1024L * 1024L); }
        int status = connection.getResponseCode();
        try { String body = readBody(connection, status); if (status >= 400) throw new IllegalStateException(body); }
        finally { connection.disconnect(); }
    }

    private static String readBody(HttpURLConnection connection, int status) throws Exception {
        InputStream raw = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (raw == null) return "HTTP " + status;
        try (InputStream input = raw; ByteArrayOutputStream output = new ByteArrayOutputStream()) { copyBounded(input, output, 2L * 1024L * 1024L); return new String(output.toByteArray(), StandardCharsets.UTF_8); }
    }

    private String displayName(Uri uri, String fallback) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { String value = cursor.getString(0); if (value != null && !value.isBlank()) return value.replaceAll("[\\\\/]+", "_"); }
        } catch (Exception ignored) { }
        return fallback;
    }

    private static void copyBounded(InputStream input, OutputStream output, long maximum) throws Exception {
        byte[] buffer = new byte[32_768]; long total = 0; int read;
        while ((read = input.read(buffer)) != -1) { total += read; if (total > maximum) throw new IllegalArgumentException("File exceeds the safety limit."); output.write(buffer, 0, read); }
    }

    private void pushState() {
        if (!pageReady || webView == null) return;
        JSONObject state = new JSONObject();
        try {
            state.put("paired", paired);
            state.put("address", baseUrl);
            state.put("assistantAvailable", assistantAvailable);
            state.put("model", "gpt-5.6-terra");
            state.put("reasoning", "high");
            state.put("crashAttachment", crashAttachment);
            state.put("modsAttachment", modsAttachment);
            state.put("operation", new JSONObject().put("active", !operationLabel.isBlank()).put("label", operationLabel));
            state.put("job", job == null ? JSONObject.NULL : job);
            state.put("message", message);
        } catch (Exception ignored) { }
        String script = "window.__assistantReceive && window.__assistantReceive(" + JSONObject.quote(state.toString()) + ");";
        main.post(() -> { if (pageReady && webView != null) webView.evaluateJavascript(script, null); });
    }

    private static String readable(Exception error) { String value = error.getMessage(); return value == null || value.isBlank() ? error.getClass().getSimpleName() : value; }

    private final class NativeBridge {
        @JavascriptInterface public void invoke(String method, String rawPayload) {
            JSONObject payload;
            try { payload = rawPayload == null || rawPayload.isBlank() ? new JSONObject() : new JSONObject(rawPayload); }
            catch (Exception ignored) { payload = new JSONObject(); }
            switch (method) {
                case "getState" -> pushState();
                case "pair" -> pair(payload.optString("address", ""), payload.optString("code", ""));
                case "pickCrash" -> pickFile(true);
                case "pickMods" -> pickFile(false);
                case "runAssistant" -> runAssistant(payload.optString("task", "analyze-crash"), payload.optString("prompt", ""));
                case "exportArtifact" -> exportArtifact(payload.optString("id", ""));
                default -> { message = "Unknown assistant action: " + method; pushState(); }
            }
        }
    }
}
