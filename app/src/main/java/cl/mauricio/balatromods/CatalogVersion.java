package cl.mauricio.balatromods;

import org.json.JSONObject;

/** A concrete release that can be selected from a catalog entry. */
public record CatalogVersion(
        String version,
        String downloadUrl,
        long downloads,
        long fileSize,
        String dateCreated
) {
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("version", version == null ? "" : version);
            json.put("downloadUrl", downloadUrl == null ? "" : downloadUrl);
            json.put("downloads", downloads);
            json.put("fileSize", fileSize);
            json.put("dateCreated", dateCreated == null ? "" : dateCreated);
        } catch (Exception error) {
            throw new IllegalStateException("Could not serialize catalog version.", error);
        }
        return json;
    }
}
