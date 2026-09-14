package cl.mauricio.balatromods;

/** Pure, conservative catalog-to-installation identity comparison. */
final class CatalogIdentity {
    private CatalogIdentity() {
    }

    static boolean matches(CatalogItem item, ModEntry mod) {
        if (item == null || mod == null) return false;
        String[] catalogAliases = {
                ModRepository.normalizeId(item.id()),
                ModRepository.normalizeId(item.name()),
                ModRepository.normalizeId(item.folderName())
        };
        String[] installedAliases = {
                ModRepository.normalizeId(mod.id),
                ModRepository.normalizeId(mod.name),
                ModRepository.normalizeId(mod.folderName)
        };
        for (String catalogAlias : catalogAliases) {
            if (catalogAlias == null || catalogAlias.isBlank()) continue;
            for (String installedAlias : installedAliases) {
                if (!installedAlias.isBlank() && catalogAlias.equals(installedAlias)) return true;
            }
        }
        return false;
    }
}
