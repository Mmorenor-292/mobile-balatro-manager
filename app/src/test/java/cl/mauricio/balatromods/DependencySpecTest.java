package cl.mauricio.balatromods;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DependencySpecTest {
    @Test
    public void preservesAndChecksSteamoddedVersionRules() {
        DependencySpec spec = DependencySpec.parse("Pokermon (>=3.8.1-0731b)");

        assertEquals("pokermon", spec.id);
        assertEquals(">=", spec.operator);
        assertEquals("3.8.1-0731b", spec.version);
        assertFalse(spec.isSatisfiedBy("3.8.1-0724a"));
        assertTrue(spec.isSatisfiedBy("3.8.1-0731b"));
    }

    @Test
    public void neverTreatsPokermonForkAsTheOriginalId() {
        assertEquals("pokermon", DependencySpec.canonicalId("Pokermon"));
        assertEquals("pokermonmaelmc", DependencySpec.canonicalId("PokermonMaelmc"));
        assertFalse(DependencySpec.canonicalId("PokermonMaelmc")
                .equals(DependencySpec.canonicalId("Pokermon")));
    }

    @Test
    public void parsesThunderstoreOwnerPackageVersion() {
        DependencySpec spec = DependencySpec.parse("Steamodded-Steamodded-1.0.0");

        assertEquals("steamodded", spec.id);
        assertEquals(">=", spec.operator);
        assertEquals("1.0.0", spec.version);
    }

    @Test
    public void unknownInstalledVersionNeverSatisfiesAVersionRule() {
        DependencySpec spec = DependencySpec.parse("Pokermon (>=3.8.1-0731b)");

        assertFalse(spec.isSatisfiedBy(""));
        assertFalse(spec.isSatisfiedBy("unknown"));
    }
}
