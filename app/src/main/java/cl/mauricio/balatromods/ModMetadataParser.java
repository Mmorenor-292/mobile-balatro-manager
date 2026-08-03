package cl.mauricio.balatromods;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ModMetadataParser {
    private ModMetadataParser() {
    }

    public static ParsedMetadata parse(String json, String fallbackName) {
        ParsedMetadata fallback = new ParsedMetadata(
                fallbackName, fallbackName, "", "", "", "", List.of(), false, ""
        );
        if (json == null || json.trim().isEmpty()) {
            return fallback;
        }

        try {
            JSONObject object = new JSONObject(json);
            String id = firstNonBlank(
                    object.optString("id"),
                    object.optString("mod_id"),
                    object.optString("name"),
                    fallbackName
            );
            String name = firstNonBlank(
                    object.optString("display_name"),
                    object.optString("name"),
                    object.optString("title"),
                    id,
                    fallbackName
            );
            String version = firstNonBlank(
                    object.optString("version"),
                    object.optString("version_number"),
                    object.optString("mod_version")
            );
            String description = firstNonBlank(
                    object.optString("description"),
                    object.optString("desc"),
                    object.optString("summary")
            );
            String website = firstNonBlank(
                    object.optString("website_url"),
                    object.optString("website"),
                    object.optString("homepage"),
                    object.optString("repo")
            );
            return new ParsedMetadata(
                    id,
                    name,
                    version,
                    parseAuthor(object),
                    description,
                    website,
                    parseDependencies(object),
                    true,
                    ""
            );
        } catch (Exception error) {
            return new ParsedMetadata(
                    fallback.id(),
                    fallback.name(),
                    fallback.version(),
                    fallback.author(),
                    fallback.description(),
                    fallback.website(),
                    fallback.dependencies(),
                    false,
                    safeMessage(error)
            );
        }
    }

    private static String parseAuthor(JSONObject object) {
        Object value = object.opt("author");
        if (value == null || value == JSONObject.NULL) {
            value = object.opt("authors");
        }
        return joinValue(value);
    }

    private static List<String> parseDependencies(JSONObject object) {
        Object value = object.opt("dependencies");
        if (value == null || value == JSONObject.NULL) {
            value = object.opt("requires");
        }
        List<String> result = new ArrayList<>();
        if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i).trim();
                if (!item.isEmpty()) {
                    result.add(normalizeDependency(item));
                }
            }
        } else if (value instanceof String string && !string.isBlank()) {
            for (String item : string.split(",")) {
                if (!item.isBlank()) {
                    result.add(normalizeDependency(item));
                }
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeDependency(String value) {
        String normalized = value.trim();
        int versionSeparator = normalized.lastIndexOf('-');
        if (versionSeparator > 0
                && versionSeparator + 1 < normalized.length()
                && Character.isDigit(normalized.charAt(versionSeparator + 1))) {
            normalized = normalized.substring(0, versionSeparator);
        }
        int ownerSeparator = normalized.indexOf('-');
        if (ownerSeparator > 0 && normalized.contains("@")) {
            normalized = normalized.substring(ownerSeparator + 1);
        }
        return normalized;
    }

    private static String joinValue(Object value) {
        if (value instanceof JSONArray array) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i).trim();
                if (!item.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append(item);
                }
            }
            return builder.toString();
        }
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public record ParsedMetadata(
            String id,
            String name,
            String version,
            String author,
            String description,
            String website,
            List<String> dependencies,
            boolean valid,
            String error
    ) {
    }
}
