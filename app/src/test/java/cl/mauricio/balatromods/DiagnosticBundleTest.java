package cl.mauricio.balatromods;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class DiagnosticBundleTest {
    @Test
    public void writesReadableSupportArchive() throws Exception {
        File file = Files.createTempFile("mbm-diagnostic", ".zip").toFile();
        Map<String, byte[]> entries = DiagnosticBundle.entries();
        entries.put("README.txt", DiagnosticBundle.utf8("local-only"));
        entries.put("inventory/mods.json", DiagnosticBundle.utf8("[]"));
        DiagnosticBundle.write(file, entries);

        try (ZipFile zip = new ZipFile(file)) {
            assertEquals(2, zip.size());
            assertTrue(zip.getEntry("README.txt") != null);
            assertTrue(zip.getEntry("inventory/mods.json") != null);
        }
    }

    @Test
    public void rejectsTraversalPaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DiagnosticBundle.safeEntryName("../save.jkr")
        );
    }

    @Test
    public void redactsSecretLikeLinesAndBearerTokens() {
        String redacted = DiagnosticBundle.redact(
                "api_key=abc123\nAuthorization: Bearer secret.jwt.value\nurl=https://x.test?a=1&token=hidden"
        );
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("secret.jwt.value"));
        assertFalse(redacted.contains("token=hidden"));
        assertTrue(redacted.contains("[REDACTED]"));
    }
}
