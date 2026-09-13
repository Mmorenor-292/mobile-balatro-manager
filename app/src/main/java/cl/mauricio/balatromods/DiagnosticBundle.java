package cl.mauricio.balatromods;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes a bounded, traversal-safe support archive. */
final class DiagnosticBundle {
    private static final int MAX_ENTRIES = 5_200;
    private static final long MAX_BYTES = 16L * 1024L * 1024L;

    private DiagnosticBundle() {
    }

    static File write(File destination, Map<String, byte[]> entries) throws Exception {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("The diagnostic archive has no entries.");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("The diagnostic archive contains too many entries.");
        }
        long bytes = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(destination))) {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                String name = safeEntryName(item.getKey());
                byte[] content = item.getValue() == null ? new byte[0] : item.getValue();
                bytes += content.length;
                if (bytes > MAX_BYTES) {
                    throw new IllegalArgumentException("Diagnostic text exceeded the 16 MB safety limit.");
                }
                zip.putNextEntry(new ZipEntry(name));
                zip.write(content);
                zip.closeEntry();
            }
        } catch (Exception error) {
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
            throw error;
        }
        return destination;
    }

    static Map<String, byte[]> entries() {
        return new LinkedHashMap<>();
    }

    static byte[] utf8(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    static String redact(String value) {
        if (value == null) return "";
        return value
                .replaceAll(
                        "(?im)^(\\s*[\\\"']?(?:token|authorization|password|passwd|secret|cookie|api[_-]?key)[\\\"']?\\s*[:=]).*$",
                        "$1 [REDACTED]"
                )
                .replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1[REDACTED]")
                .replaceAll("(?i)([?&](?:token|key|secret|password)=)[^&\\s]+", "$1[REDACTED]");
    }

    static String safeEntryName(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isBlank() || normalized.contains("../") || normalized.equals("..")) {
            throw new IllegalArgumentException("Unsafe diagnostic entry path.");
        }
        return normalized.replaceAll("[\\p{Cntrl}]", "_");
    }
}
