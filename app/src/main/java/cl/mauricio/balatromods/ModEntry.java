package cl.mauricio.balatromods;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public final class ModEntry {
    public final String id;
    public final String name;
    public final String folderName;
    public final String version;
    public final String author;
    public final String description;
    public final String website;
    public final String metadataFile;
    public final boolean hidden;
    public final int rootItemCount;
    public final long lastModified;
    public final List<String> dependencies;
    public final List<String> diagnostics;
    public final String severity;
    public final String thumbnail;
    public final DocumentFile directory;

    public ModEntry(
            String id,
            String name,
            String folderName,
            String version,
            String author,
            String description,
            String website,
            String metadataFile,
            boolean hidden,
            int rootItemCount,
            long lastModified,
            List<String> dependencies,
            List<String> diagnostics,
            String severity,
            DocumentFile directory
    ) {
        this(id, name, folderName, version, author, description, website, metadataFile, hidden,
                rootItemCount, lastModified, dependencies, diagnostics, severity, directory, "");
    }

    public ModEntry(
            String id,
            String name,
            String folderName,
            String version,
            String author,
            String description,
            String website,
            String metadataFile,
            boolean hidden,
            int rootItemCount,
            long lastModified,
            List<String> dependencies,
            List<String> diagnostics,
            String severity,
            DocumentFile directory,
            String thumbnail
    ) {
        this.id = clean(id, folderName);
        this.name = clean(name, folderName);
        this.folderName = clean(folderName, "Mod");
        this.version = clean(version, "unknown version");
        this.author = clean(author, "unknown author");
        this.description = clean(description, "No description available.");
        this.website = clean(website, "");
        this.metadataFile = clean(metadataFile, "not detected");
        this.hidden = hidden;
        this.rootItemCount = rootItemCount;
        this.lastModified = lastModified;
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.severity = clean(severity, "ok");
        this.thumbnail = thumbnail == null ? "" : thumbnail;
        this.directory = directory;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("folder", folderName);
            json.put("version", version);
            json.put("author", author);
            json.put("description", description);
            json.put("website", website);
            json.put("metadataFile", metadataFile);
            json.put("hidden", hidden);
            json.put("rootItemCount", rootItemCount);
            json.put("lastModified", lastModified);
            json.put("severity", severity);
            json.put("dependencies", new JSONArray(dependencies));
            json.put("diagnostics", new JSONArray(diagnostics));
            json.put("thumbnail", thumbnail);
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize mod.", error);
        }
        return json;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
