package cl.mauricio.balatromods;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative natural ordering for the varied version tags used by Balatro mods. */
final class VersionOrder {
    private static final Pattern TOKEN = Pattern.compile("\\d+|[a-z]+");
    private static final Pattern PLAIN_RELEASE = Pattern.compile("\\d+(?:\\.\\d+)*");

    private VersionOrder() {
    }

    static boolean isNewer(String candidate, String installed) {
        String next = normalize(candidate);
        String current = normalize(installed);
        if (next.isBlank() || current.isBlank() || next.equals(current)) return false;
        if (!containsDigit(next) || !containsDigit(current)) return false;

        // A plain release is newer than a prerelease sharing the exact numeric core.
        if (PLAIN_RELEASE.matcher(next).matches() && current.startsWith(next + "-")) return true;
        if (PLAIN_RELEASE.matcher(current).matches() && next.startsWith(current + "-")) return false;

        List<String> left = tokens(next);
        List<String> right = tokens(current);
        int length = Math.max(left.size(), right.size());
        for (int index = 0; index < length; index++) {
            if (index >= left.size()) return false;
            if (index >= right.size()) return true;
            String a = left.get(index);
            String b = right.get(index);
            boolean aNumber = Character.isDigit(a.charAt(0));
            boolean bNumber = Character.isDigit(b.charAt(0));
            int compared;
            if (aNumber && bNumber) compared = new BigInteger(a).compareTo(new BigInteger(b));
            else if (aNumber != bNumber) compared = aNumber ? 1 : -1;
            else compared = a.compareTo(b);
            if (compared != 0) return compared > 0;
        }
        return false;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("v") && value.length() > 1 && Character.isDigit(value.charAt(1))) {
            value = value.substring(1);
        }
        int metadata = value.indexOf('+');
        return metadata < 0 ? value : value.substring(0, metadata);
    }

    private static boolean containsDigit(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) return true;
        }
        return false;
    }

    private static List<String> tokens(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}
