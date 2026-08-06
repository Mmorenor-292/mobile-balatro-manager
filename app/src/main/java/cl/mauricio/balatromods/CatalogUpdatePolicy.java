package cl.mauricio.balatromods;

/** Decides updates without pretending a Git commit hash is a semantic version. */
final class CatalogUpdatePolicy {
    private CatalogUpdatePolicy() {
    }

    static boolean updateAvailable(
            String catalogVersion,
            String installedVersion,
            String installedCatalogRevision
    ) {
        if (catalogVersion == null || catalogVersion.isBlank()) return false;
        if (VersionOrder.isSourceRevision(catalogVersion)) {
            return installedCatalogRevision == null
                    || !catalogVersion.equalsIgnoreCase(installedCatalogRevision.trim());
        }
        return VersionOrder.isNewer(catalogVersion, installedVersion);
    }
}
