package cl.mauricio.balatromods;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModRepositoryCleanupTest {
    @Test
    public void recognizesOnlyBoundedManagerAndOsResidues() {
        assertTrue(ModRepository.isKnownJunkName(".bmm-trash--old", true));
        assertTrue(ModRepository.isKnownJunkName(".mbm-incoming--42", true));
        assertTrue(ModRepository.isKnownJunkName("__MACOSX", true));
        assertTrue(ModRepository.isKnownJunkName(".DS_Store", false));
        assertTrue(ModRepository.isKnownJunkName(".bmm-download.part", false));

        assertFalse(ModRepository.isKnownJunkName("Pokermon", true));
        assertFalse(ModRepository.isKnownJunkName("DisabledMod", true));
        assertFalse(ModRepository.isKnownJunkName("config.tmp", false));
        assertFalse(ModRepository.isKnownJunkName(".lovelyignore", false));
    }
}
