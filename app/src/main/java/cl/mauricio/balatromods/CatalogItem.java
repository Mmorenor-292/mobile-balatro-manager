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
        return toJson(installed, installedVersion,
                installed && VersionOrder.isNewer(version, installedVersion));
    }

    public JSONObject toJson(boolean installed, String installedVersion, boolean updateAvailable) {
        CatalogUpdatePolicy.Result status = new CatalogUpdatePolicy.Result(
                updateAvailable ? CatalogUpdatePolicy.Status.AVAILABLE : CatalogUpdatePolicy.Status.CURRENT,
                updateAvailable ? "A newer catalog version is available." : "No newer catalog version was detected."
        );
        return toJson(installed, installedVersion, status);
    }

    public JSONObject toJson(
            boolean installed,
            String installedVersion,
            CatalogUpdatePolicy.Result updateStatus
    ) {
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
            json.put("versionKind", VersionOrder.isSourceRevision(version)
                    ? "source-revision" : "release");
            CatalogUpdatePolicy.Result safeStatus = updateStatus == null
                    ? new CatalogUpdatePolicy.Result(CatalogUpdatePolicy.Status.UNKNOWN, "Version status unavailable.")
                    : updateStatus;
            json.put("updateAvailable", installed && safeStatus.updateAvailable());
            json.put("updateState", installed ? safeStatus.wireValue() : "not-installed");
            json.put("updateReason", installed ? safeStatus.reason() : "This mod is not installed.");
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

    public CatalogItem withReleaseMetadata(
            String resolvedVersion,
            String resolvedDownloadUrl,
            String resolvedHomepage,
            List<CatalogVersion> resolvedVersions
    ) {
        List<CatalogVersion> safeVersions = resolvedVersions == null ? List.of() : List.copyOf(resolvedVersions);
        return new CatalogItem(
                id, source, name, author,
                resolvedVersion == null || resolvedVersion.isBlank() ? version : resolvedVersion,
                summary,
                resolvedDownloadUrl == null || resolvedDownloadUrl.isBlank() ? downloadUrl : resolvedDownloadUrl,
                resolvedHomepage == null || resolvedHomepage.isBlank() ? homepage : resolvedHomepage,
                folderName, thumbnailUrl, categories, dependencies,
                requiresSteamodded, requiresTalisman, downloads, fileSize,
                safeVersions.isEmpty() ? versions : safeVersions
        );
    }

}
