package cl.mauricio.balatromods;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionOrderTest {
    @Test
    public void identifiesNewerNumericAndBuildVersions() {
        assertTrue(VersionOrder.isNewer("1.17.0-0040", "1.17.0-0031"));
        assertTrue(VersionOrder.isNewer("v2.5.2", "2.5.1"));
        assertTrue(VersionOrder.isNewer("1.2.4b", "1.2.4a"));
    }

    @Test
    public void neverDowngradesOrGuessesBranchNames() {
        assertFalse(VersionOrder.isNewer("1.9.0", "2.0.0"));
        assertFalse(VersionOrder.isNewer("main", "nightly"));
        assertFalse(VersionOrder.isNewer("1.0.0-beta2", "1.0.0"));
        assertTrue(VersionOrder.isNewer("1.0.0", "1.0.0-beta2"));
    }
}
