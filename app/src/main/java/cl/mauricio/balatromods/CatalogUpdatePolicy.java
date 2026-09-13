package cl.mauricio.balatromods;

/** Decides updates without pretending a Git commit hash is a semantic version. */
final class CatalogUpdatePolicy {
    enum Status {
        AVAILABLE,
        CURRENT,
        UNKNOWN
    }

    record Result(Status status, String reason) {
        boolean updateAvailable() {
            return status == Status.AVAILABLE;
        }

        String wireValue() {
            return status.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private CatalogUpdatePolicy() {
    }

    static Result evaluate(
            String catalogVersion,
            String installedVersion,
            String installedCatalogRevision
    ) {
        String remote = clean(catalogVersion);
        String local = clean(installedVersion);
        String receipt = clean(installedCatalogRevision);
        if (remote.isBlank()) {
            return new Result(Status.UNKNOWN, "The catalog did not publish a usable version.");
        }
        if (VersionOrder.isSourceRevision(remote)) {
            if (receipt.isBlank() || !VersionOrder.isSourceRevision(receipt)) {
                return new Result(
                        Status.UNKNOWN,
                        "Latest source is available, but this installation has no comparable source receipt."
                );
            }
            return remote.equalsIgnoreCase(receipt)
                    ? new Result(Status.CURRENT, "Installed source revision matches the catalog.")
                    : new Result(Status.AVAILABLE, "A different source revision is available.");
        }
        if (local.isBlank() || local.equalsIgnoreCase("unknown version")) {
            return new Result(Status.UNKNOWN, "The installed mod did not declare a comparable version.");
        }
        return VersionOrder.isNewer(remote, local)
                ? new Result(Status.AVAILABLE, "A newer published release is available.")
                : new Result(Status.CURRENT, "The installed version is current or newer.");
    }

    static boolean updateAvailable(
            String catalogVersion,
            String installedVersion,
            String installedCatalogRevision
    ) {
        return evaluate(catalogVersion, installedVersion, installedCatalogRevision).updateAvailable();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
