package cl.mauricio.balatromods;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

public final class CatalogClient {
    private static final String BMI =
            "https://api-bmi.dasguney.com/mods?limit=200&sort=downloads_desc";
    private static final String THUNDERSTORE =
            "https://thunderstore.io/c/balatro/api/v1/package/";
    private static final String AWESOME_BALATRO =
            "https://raw.githubusercontent.com/jie65535/awesome-balatro/main/README.md";
    private static final String GITHUB_API = "https://api.github.com/repos/";
    private static final Pattern README_LINK = Pattern.compile(
            "\\[[^]]{2,120}\\]\\(https://github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)(?:/[^)]*)?\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_RESPONSE = 16 * 1024 * 1024;
    private static final long CACHE_MAX_AGE = 24L * 60L * 60L * 1000L;

    private final File cache;

    public CatalogClient(Context context) {
        cache = new File(context.getFilesDir(), "catalog-v2.json");
    }

    public List<CatalogItem> fetch() throws Exception {
        List<CatalogItem> merged = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            merged.addAll(fetchBmi());
        } catch (Exception error) {
            errors.add("BMI: " + readable(error));
        }
        try {
            merged.addAll(fetchThunderstore());
        } catch (Exception error) {
            errors.add("Thunderstore: " + readable(error));
        }
        try {
            merged.addAll(fetchAwesomeBalatro());
        } catch (Exception error) {
            errors.add("Awesome Balatro: " + readable(error));
        }
        if (merged.isEmpty()) {
            List<CatalogItem> cached = loadCache();
            if (!cached.isEmpty()) {
                return cached;
            }
            throw new IllegalStateException(String.join("; ", errors));
        }

        List<CatalogItem> deduplicated = deduplicate(merged);
        deduplicated.sort(
                Comparator.comparingLong(CatalogItem::downloads).reversed()
                        .thenComparing(CatalogItem::name, String.CASE_INSENSITIVE_ORDER)
        );
        saveCache(deduplicated);
        return List.copyOf(deduplicated);
    }

    public List<CatalogItem> cached() {
        if (!cache.exists()) {
            return List.of();
        }
        if (System.currentTimeMillis() - cache.lastModified() > CACHE_MAX_AGE) {
            return loadCache();
        }
        return loadCache();
    }

    public String resolveDownloadUrl(CatalogItem item) throws Exception {
        return resolveDownloadUrl(item, "", "");
    }

    public String resolveDownloadUrl(CatalogItem item, String requestedVersion, String requestedUrl) throws Exception {
        if (requestedUrl != null && !requestedUrl.isBlank()) {
            return requestedUrl;
        }
        if (requestedVersion != null && !requestedVersion.isBlank()) {
            for (CatalogVersion release : item.versions()) {
                if (release.version().equalsIgnoreCase(requestedVersion)
                        && !release.downloadUrl().isBlank()) {
                    return release.downloadUrl();
                }
            }
            if (!requestedVersion.equalsIgnoreCase(item.version())) {
                throw new IllegalArgumentException("The selected version has no verified download archive.");
            }
        }
        if (!item.downloadUrl().isBlank()) {
            return item.downloadUrl();
        }
        if (!"BMI".equals(item.source())) {
            throw new IllegalArgumentException("This catalog item has no download URL.");
        }
        URL url = new URL(
                "https://api-bmi.dasguney.com/mods/"
                        + Uri.encode(item.id())
                        + "/download"
        );
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        configure(connection);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("BMI returned HTTP " + status);
        }
        String body = read(connection, MAX_RESPONSE);
        if (body.isBlank()) {
            return fetchBmiDetailDownload(item.id());
        }
        JSONObject response = new JSONObject(body);
        String resolved = first(
                response.optString("download_url"),
                response.optString("downloadUrl"),
                response.optString("url")
        );
        return resolved.isBlank() ? fetchBmiDetailDownload(item.id()) : resolved;
    }

    private List<CatalogItem> fetchBmi() throws Exception {
        JSONObject response = new JSONObject(get(BMI));
        JSONArray items = response.optJSONArray("items");
        if (items == null) {
            return List.of();
        }
        List<CatalogItem> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject downloads = item.optJSONObject("downloads");
            List<CatalogVersion> releases = catalogVersions(item.optJSONArray("versions"));
            if (releases.isEmpty()) {
                releases = List.of(new CatalogVersion(
                        item.optString("version"),
                        first(item.optString("download_url"), item.optString("downloadURL")),
                        downloads == null ? 0 : downloads.optLong("total"),
                        0,
                        String.valueOf(item.optLong("updated_at"))
                ));
            }
            result.add(new CatalogItem(
                    item.optString("id"),
                    "BMI",
                    first(item.optString("name"), item.optString("title"), item.optString("id")),
                    item.optString("author"),
                    item.optString("version"),
                    stripMarkdown(item.optString("summary")),
                    first(item.optString("download_url"), item.optString("downloadURL")),
                    first(item.optString("homepage"), item.optString("repo")),
                    first(item.optString("folder_name"), item.optString("folderName"), item.optString("id")),
                    normalizeBmiThumbnail(item.optString("thumbnail_url")),
                    strings(item.optJSONArray("categories")),
                    strings(item.optJSONArray("dependencies")),
                    item.optBoolean("requires_steamodded"),
                    item.optBoolean("requires_talisman"),
                    downloads == null ? 0 : downloads.optLong("total"),
                    0,
                    releases
            ));
        }
        return result;
    }

    private List<CatalogItem> fetchThunderstore() throws Exception {
        JSONArray packages = new JSONArray(get(THUNDERSTORE));
        List<CatalogItem> result = new ArrayList<>();
        for (int i = 0; i < packages.length(); i++) {
            JSONObject item = packages.optJSONObject(i);
            if (item == null
                    || item.optBoolean("is_deprecated")
                    || item.optBoolean("has_nsfw_content")) {
                continue;
            }
            String name = item.optString("name");
            if ("r2modman".equalsIgnoreCase(name) || "lovely".equalsIgnoreCase(name)) {
                continue;
            }
            JSONArray versions = item.optJSONArray("versions");
            JSONObject latest = versions == null ? null : versions.optJSONObject(0);
            if (latest == null || !latest.optBoolean("is_active", true)) {
                continue;
            }
            result.add(new CatalogItem(
                    item.optString("full_name", item.optString("name")),
                    "Thunderstore",
                    name,
                    item.optString("owner"),
                    latest.optString("version_number"),
                    latest.optString("description"),
                    latest.optString("download_url"),
                    first(latest.optString("website_url"), item.optString("package_url")),
                    name,
                    latest.optString("icon"),
                    strings(item.optJSONArray("categories")),
                    strings(latest.optJSONArray("dependencies")),
                    true,
                    false,
                    latest.optLong("downloads"),
                    latest.optLong("file_size"),
                    thunderstoreVersions(versions)
            ));
        }
        return result;
    }

    /**
     * Awesome Balatro is a human-curated directory. GitHub entries are still
     * real repositories, so a tagged release is preferred and the repository
     * source archive is a safe fallback when the author has not published a
     * release asset. Non-GitHub links remain visible but are not guessed at.
     */
    private List<CatalogItem> fetchAwesomeBalatro() throws Exception {
        String readme = getText(AWESOME_BALATRO);
        List<CatalogItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher matcher = README_LINK.matcher(readme);
        int inspected = 0;
        // Keep refreshes bounded on a phone and stay well below GitHub's
        // unauthenticated API rate limit. Discover is a directory, not a bulk
        // crawler; the next refresh rotates only when the README changes.
        while (matcher.find() && result.size() < 32 && inspected < 16) {
            String repository = matcher.group(1);
            String normalized = repository.toLowerCase(Locale.ROOT);
            if (normalized.equals("jie65535/awesome-balatro") || !seen.add(normalized)) {
                continue;
            }
            inspected++;
            try {
                JSONObject metadata = new JSONObject(get(GITHUB_API + repository));
                if (metadata.optBoolean("archived") || metadata.optBoolean("disabled")) {
                    continue;
                }
                String name = first(metadata.optString("name"), repository.substring(repository.indexOf('/') + 1));
                String owner = metadata.optJSONObject("owner") == null
                        ? repository.substring(0, repository.indexOf('/'))
                        : metadata.optJSONObject("owner").optString("login");
                String branch = first(metadata.optString("default_branch"), "main");
                List<CatalogVersion> releases = githubReleases(repository);
                String releaseUrl = releases.isEmpty()
                        ? githubSourceArchive(repository, branch)
                        : releases.get(0).downloadUrl();
                String latestVersion = releases.isEmpty() ? branch : releases.get(0).version();
                if (releases.isEmpty()) {
                    releases = List.of(new CatalogVersion(
                            branch, releaseUrl, metadata.optLong("stargazers_count"), 0, ""
                    ));
                }
                result.add(new CatalogItem(
                        "awesome:" + repository,
                        "Awesome Balatro",
                        name,
                        owner,
                        latestVersion,
                        stripMarkdown(first(metadata.optString("description"), "A real GitHub repository from the Awesome Balatro collection. MBM downloads its release or source archive and inspects it before installation.")),
                        releaseUrl,
                        first(metadata.optString("html_url"), "https://github.com/" + repository),
                        name,
                        "",
                        List.of("Community", "Awesome Balatro"),
                        List.of(),
                        false,
                        false,
                        metadata.optLong("stargazers_count"),
                        0,
                        releases
                ));
            } catch (Exception ignored) {
                // A stale or rate-limited link must not make the whole catalog fail.
            }
        }
        return result;
    }

    private List<CatalogVersion> githubReleases(String repository) {
        try {
            JSONArray releases = new JSONArray(get(GITHUB_API + repository + "/releases?per_page=10"));
            List<CatalogVersion> result = new ArrayList<>();
            for (int releaseIndex = 0; releaseIndex < releases.length(); releaseIndex++) {
                JSONObject release = releases.optJSONObject(releaseIndex);
                if (release == null || release.optBoolean("draft") || release.optBoolean("prerelease")) continue;
                String version = first(release.optString("tag_name"), release.optString("name"));
                String url = "";
                long downloads = 0;
                long size = 0;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                        JSONObject asset = assets.optJSONObject(assetIndex);
                        if (asset == null) continue;
                        String name = asset.optString("name").toLowerCase(Locale.ROOT);
                        if (url.isBlank() && name.endsWith(".zip")) {
                            url = asset.optString("browser_download_url");
                            size = asset.optLong("size");
                        }
                        downloads += asset.optLong("download_count");
                    }
                }
                if (url.isBlank()) url = release.optString("zipball_url");
                if (!version.isBlank() && !url.isBlank()) {
                    result.add(new CatalogVersion(
                            version, url, downloads, size, release.optString("published_at")
                    ));
                }
            }
            return List.copyOf(result);
        } catch (Exception ignored) {
            // The caller falls back to the repository source archive.
        }
        return List.of();
    }

    private String githubSourceArchive(String repository, String branch) {
        String safeBranch = branch == null || branch.isBlank() ? "main" : branch.trim();
        return "https://github.com/" + repository + "/archive/refs/heads/"
                + Uri.encode(safeBranch, "/") + ".zip";
    }

    private String fetchBmiDetailDownload(String id) throws Exception {
        JSONObject detail = new JSONObject(
                get("https://api-bmi.dasguney.com/mods/" + Uri.encode(id))
        );
        String url = first(detail.optString("download_url"), detail.optString("downloadURL"));
        if (url.isBlank()) {
            throw new IllegalStateException("BMI did not provide a downloadable archive.");
        }
        return url;
    }

    private static List<CatalogItem> deduplicate(List<CatalogItem> input) {
        List<CatalogItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CatalogItem item : input) {
            String key = ModRepository.normalizeId(item.name() + item.author());
            if (key.isBlank() || !seen.add(key)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private void saveCache(List<CatalogItem> items) {
        JSONArray array = new JSONArray();
        for (CatalogItem item : items) {
            array.put(serialize(item));
        }
        try (FileOutputStream output = new FileOutputStream(cache)) {
            output.write(array.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Network results remain usable even if the cache cannot be written.
        }
    }

    private List<CatalogItem> loadCache() {
        if (!cache.exists() || cache.length() > MAX_RESPONSE) {
            return List.of();
        }
        try (InputStream input = new FileInputStream(cache)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16_384];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(json);
            List<CatalogItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    items.add(deserialize(item));
                }
            }
            return List.copyOf(items);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static JSONObject serialize(CatalogItem item) {
        JSONObject json = item.toJson(false);
        json.remove("installed");
        json.remove("compatibility");
        return json;
    }

    private static CatalogItem deserialize(JSONObject item) {
        return new CatalogItem(
                item.optString("id"),
                item.optString("source"),
                item.optString("name"),
                item.optString("author"),
                item.optString("version"),
                item.optString("summary"),
                item.optString("downloadUrl"),
                item.optString("homepage"),
                item.optString("folderName"),
                item.optString("thumbnailUrl"),
                strings(item.optJSONArray("categories")),
                strings(item.optJSONArray("dependencies")),
                item.optBoolean("requiresSteamodded"),
                item.optBoolean("requiresTalisman"),
                item.optLong("downloads"),
                item.optLong("fileSize"),
                catalogVersions(item.optJSONArray("versions"))
        );
    }

    private static List<CatalogVersion> thunderstoreVersions(JSONArray versions) {
        return catalogVersions(versions);
    }

    private static List<CatalogVersion> catalogVersions(JSONArray versions) {
        if (versions == null) return List.of();
        List<CatalogVersion> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < versions.length(); i++) {
            JSONObject release = versions.optJSONObject(i);
            if (release == null || !release.optBoolean("is_active", true)) continue;
            String version = first(release.optString("version_number"), release.optString("version"));
            String url = first(release.optString("download_url"), release.optString("downloadUrl"));
            if (version.isBlank() || !seen.add(version.toLowerCase(Locale.ROOT))) continue;
            result.add(new CatalogVersion(
                    version,
                    url,
                    release.optLong("downloads"),
                    release.optLong("file_size", release.optLong("fileSize")),
                    first(release.optString("date_created"), release.optString("dateCreated"))
            ));
        }
        return List.copyOf(result);
    }

    private static String get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        configure(connection);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status);
        }
        return read(connection, MAX_RESPONSE);
    }

    private static String getText(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        configure(connection);
        connection.setRequestProperty("Accept", "text/plain, text/markdown, */*");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status);
        }
        return read(connection, MAX_RESPONSE);
    }

    private static void configure(HttpURLConnection connection) {
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Balatro-Mobile-Mod-Manager-Android/2.0");
        connection.setInstanceFollowRedirects(true);
    }

    private static String read(HttpURLConnection connection, int maxBytes) throws Exception {
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16_384];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Server response exceeded the safety limit.");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static List<String> strings(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i).trim();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeBmiThumbnail(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.startsWith("http")
                ? value
                : "https://api-bmi.dasguney.com" + (value.startsWith("/") ? value : "/" + value);
    }

    private static String stripMarkdown(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replaceAll("[#*_`>]", "")
                .trim();
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String readable(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
