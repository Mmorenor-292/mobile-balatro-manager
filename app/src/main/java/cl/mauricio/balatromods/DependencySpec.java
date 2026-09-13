package cl.mauricio.balatromods;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed Steamodded/Thunderstore dependency without losing its version rule. */
final class DependencySpec {
    private static final Pattern RULE = Pattern.compile(
            "^(.+?)\\s*\\(\\s*(>=|<=|>>|<<|==|=|>|<)\\s*([^\\)]+)\\s*\\)$"
    );
    private static final Pattern THUNDERSTORE = Pattern.compile(
            "^[^-]+-(.+)-(\\d+(?:\\.\\d+)+(?:[-+~._a-zA-Z0-9]*))$"
    );

    final String raw;
    final String id;
    final String operator;
    final String version;

    private DependencySpec(String raw, String id, String operator, String version) {
        this.raw = raw;
        this.id = id;
        this.operator = operator;
        this.version = version;
    }

    static DependencySpec parse(String value) {
        String raw = value == null ? "" : value.trim();
        Matcher rule = RULE.matcher(raw);
        if (rule.matches()) {
            return new DependencySpec(
                    raw,
                    canonicalId(rule.group(1)),
                    normalizeOperator(rule.group(2)),
                    rule.group(3).trim()
            );
        }
        Matcher thunderstore = THUNDERSTORE.matcher(raw);
        if (thunderstore.matches()) {
            return new DependencySpec(
                    raw,
                    canonicalId(thunderstore.group(1)),
                    ">=",
                    thunderstore.group(2).trim()
            );
        }
        return new DependencySpec(raw, canonicalId(raw), "", "");
    }

    boolean isSatisfiedBy(String installedVersion) {
        if (version.isBlank()) return true;
        if (!VersionOrder.isComparable(installedVersion) || !VersionOrder.isComparable(version)) {
            return false;
        }
        int compared = VersionOrder.compare(installedVersion, version);
        return switch (operator) {
            case ">", ">>" -> compared > 0;
            case ">=" -> compared >= 0;
            case "<", "<<" -> compared < 0;
            case "<=" -> compared <= 0;
            case "=" , "==" -> compared == 0;
            default -> true;
        };
    }

    String requirementLabel() {
        return version.isBlank() ? id : id + " " + operator + " " + version;
    }

    static String canonicalId(String value) {
        String normalized = ModRepository.normalizeId(value);
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "smods", "steamodded" -> "steamodded";
            default -> normalized;
        };
    }

    private static String normalizeOperator(String operator) {
        return switch (operator) {
            case ">>" -> ">";
            case "<<" -> "<";
            case "=" -> "==";
            default -> operator;
        };
    }
}
