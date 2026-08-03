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
        long fileSize
) {
    public JSONObject toJson(boolean installed) {
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
            json.put("compatibility", "unknown");
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize catalog item.", error);
        }
        return json;
    }
}
