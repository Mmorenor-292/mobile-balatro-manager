package cl.mauricio.balatromods;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ImmCompatibilityTest {
    private static final String HEADER = "local constructor = require(\"imm.lib.constructor\")\n";

    @Test
    public void relaxesOnlyTheEndAnchorThatRejectsMobileVersionSuffixes() {
        String strict = HEADER
                + "local major, minor, patch, rev = str:match('^(%d+)%.?([%d%*]*)%.?([%d%*]*)([%w_~*.%-+]*)$')";

        ImmCompatibility.PatchResult result = ImmCompatibility.patchVersionParser(strict);

        assertTrue(result.changed());
        assertFalse(result.content().contains(ImmCompatibility.STRICT_PATTERN));
        assertTrue(result.content().contains(ImmCompatibility.MOBILE_PATTERN));
    }

    @Test
    public void isIdempotentAndRejectsUnknownFiles() {
        ImmCompatibility.PatchResult alreadyPatched = ImmCompatibility.patchVersionParser(
                HEADER + "local parsed = str:match('^(%d+)([%w_~*.%-+]*)')"
        );
        assertFalse(alreadyPatched.changed());
        assertThrows(IllegalArgumentException.class,
                () -> ImmCompatibility.patchVersionParser("return true"));
    }
}
