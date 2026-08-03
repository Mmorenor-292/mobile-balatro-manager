package cl.mauricio.balatromods;

import java.util.Locale;

public final class ModFilter {
    private ModFilter() {
    }

    public enum Status {
        ALL,
        ACTIVE,
        HIDDEN
    }

    public static boolean matches(ModEntry mod, String query, Status status) {
        if (status == Status.ACTIVE && mod.hidden) {
            return false;
        }
        if (status == Status.HIDDEN && !mod.hidden) {
            return false;
        }

        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        String searchable = String.join(" ",
                mod.name,
                mod.folderName,
                mod.version,
                mod.author,
                mod.description
        ).toLowerCase(Locale.ROOT);
        return searchable.contains(normalized);
    }
}
