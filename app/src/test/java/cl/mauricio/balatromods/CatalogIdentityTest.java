package cl.mauricio.balatromods;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatalogIdentityTest {
    @Test
    public void ignoresEmptyAliasesButMatchesExplicitFolderIdentity() {
        CatalogItem empty = item("", "", "");
        ModEntry noMetadata = mod("", "", "OtherMod");
        assertFalse(CatalogIdentity.matches(empty, noMetadata));

        CatalogItem identified = item("", "", "KnownMod");
        assertTrue(CatalogIdentity.matches(identified, mod("", "", "KnownMod")));
    }

    @Test
    public void doesNotTreatDifferentInstalledCopiesAsTheSameCatalogItem() {
        CatalogItem item = item("real-id", "Known Mod", "KnownMod");
        assertTrue(CatalogIdentity.matches(item, mod("real-id", "Different", "DifferentFolder")));
        assertFalse(CatalogIdentity.matches(item, mod("", "Other", "OtherFolder")));
    }

    private static CatalogItem item(String id, String name, String folder) {
        return new CatalogItem(id, "BMI", name, "author", "1.0.0", "", "", "", folder,
                "", List.of(), List.of(), false, false, 0, 0);
    }

    private static ModEntry mod(String id, String name, String folder) {
        return new ModEntry(id, name, folder, "1.0.0", "author", "", "", "", false,
                0, 0, List.of(), List.of(), "ok", null);
    }
}
