package cl.mauricio.balatromods;

/** Narrow, reversible source patch for IMM's strict Balatro version parser. */
public final class ImmCompatibility {
    static final String STRICT_PATTERN = "([%w_~*.%-+]*)$')";
    static final String MOBILE_PATTERN = "([%w_~*.%-+]*)')";

    private ImmCompatibility() {
    }

    public static PatchResult patchVersionParser(String source) {
        if (source == null || !source.contains("require(\"imm.lib.constructor\")")) {
            throw new IllegalArgumentException("The detected version.lua is not IMM's parser.");
        }
        if (source.contains(STRICT_PATTERN)) {
            return new PatchResult(source.replace(STRICT_PATTERN, MOBILE_PATTERN), true);
        }
        if (source.contains(MOBILE_PATTERN)) {
            return new PatchResult(source, false);
        }
        throw new IllegalArgumentException("This IMM release uses an unknown version parser; no file was changed.");
    }

    public record PatchResult(String content, boolean changed) {
    }
}
