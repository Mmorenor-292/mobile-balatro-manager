package cl.mauricio.balatromods;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatalogUpdatePolicyTest {
    @Test
    public void sourceRevisionUsesRecordedRevisionInsteadOfInstalledSemver() {
        assertTrue(CatalogUpdatePolicy.updateAvailable(
                "3a9be1c", "3.8.1-0724a", "old1234"
        ));
        assertFalse(CatalogUpdatePolicy.updateAvailable(
                "3a9be1c", "3.8.1-0731b", "3a9be1c"
        ));
    }

    @Test
    public void semanticCatalogVersionsStillUseNaturalOrdering() {
        assertTrue(CatalogUpdatePolicy.updateAvailable(
                "3.8.1-0731b", "3.8.1-0724a", ""
        ));
        assertFalse(CatalogUpdatePolicy.updateAvailable(
                "3.8.1-0731b", "3.8.1-0731b", ""
        ));
    }
}
