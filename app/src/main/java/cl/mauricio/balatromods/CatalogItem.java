package cl.mauricio.balatromods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public record CatalogItem(
        String id,
        String source,
        String name,
        String author,
        String version,
        String summary,
        String downloadUrl,
        String homepage,
        String folderName,
        String thumbnailUrl,
        List<String> categories,
        List<String> dependencies,
        boolean requiresSteamodded,
        boolean requiresTalisman,
        long downloads,
        long fileSize,
        List<CatalogVersion> versions
) {
    public CatalogItem(
            String id,
            String source,
            String name,
            String author,
            String version,
            String summary,
            String downloadUrl,
            String homepage,
            String folderName,
            String thumbnailUrl,
            List<String> categories,
            List<String> dependencies,
            boolean requiresSteamodded,
            boolean requiresTalisman,
            long downloads,
            long fileSize
    ) {
        this(id, source, name, author, version, summary, downloadUrl, homepage, folderName,
                thumbnailUrl, categories, dependencies, requiresSteamodded, requiresTalisman,
                downloads, fileSize, List.of());
    }

    public JSONObject toJson(boolean installed) {
        return toJson(installed, "");
    }

    public JSONObject toJson(boolean installed, String installedVersion) {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("source", source);
            json.put("name", name);
            json.put("author", author);
            json.put("version", version);
            json.put("summary", summary);
            json.put("downloadUrl", downloadUrl);
            json.put("homepage", homepage);
            json.put("folderName", folderName);
            json.put("thumbnailUrl", thumbnailUrl);
            json.put("categories", new JSONArray(categories));
            json.put("dependencies", new JSONArray(dependencies));
            json.put("requiresSteamodded", requiresSteamodded);
            json.put("requiresTalisman", requiresTalisman);
            json.put("downloads", downloads);
            json.put("fileSize", fileSize);
            json.put("installed", installed);
            String current = installedVersion == null ? "" : installedVersion.trim();
            json.put("installedVersion", current);
            json.put("latestVersion", version);
            json.put("updateAvailable", installed && !current.isBlank() && !sameVersion(current, version));
            JSONArray releaseVersions = new JSONArray();
            if (versions != null && !versions.isEmpty()) {
                for (CatalogVersion release : versions) releaseVersions.put(release.toJson());
            } else if (version != null && !version.isBlank()) {
                releaseVersions.put(new CatalogVersion(version, downloadUrl, downloads, fileSize, "").toJson());
            }
            json.put("versions", releaseVersions);
            json.put("compatibility", "unknown");
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize catalog item.", error);
        }
        return json;
    }

    private static boolean sameVersion(String left, String right) {
        return left.trim().equalsIgnoreCase(right == null ? "" : right.trim());
    }
}
