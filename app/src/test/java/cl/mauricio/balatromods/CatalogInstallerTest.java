package cl.mauricio.balatromods;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CatalogInstallerTest {
    @Test
    public void extractsSafeLuaArchive() throws Exception {
        File root = Files.createTempDirectory("bmd-zip-test").toFile();
        File zip = new File(root, "mod.zip");
        File output = new File(root, "out");
        assertTrue(output.mkdirs());
        try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(zip))) {
            stream.putNextEntry(new ZipEntry("Demo/main.lua"));
            stream.write("return {}".getBytes(StandardCharsets.UTF_8));
            stream.closeEntry();
        }

        CatalogInstaller.ArchiveInspection inspection =
                CatalogInstaller.extractAndInspect(zip, output);

        assertEquals(1, inspection.entries());
        assertTrue(inspection.hasLua());
        assertTrue(new File(output, "Demo/main.lua").isFile());
    }

    @Test
    public void rejectsZipSlipAndNativeBinaries() throws Exception {
        assertRejected("../escape.lua", "outside");
        assertRejected("mod/native.dll", "mobile-incompatible");
    }

    @Test
    public void sanitizesFolderNames() {
        assertEquals("Bad_Name_", CatalogInstaller.sanitizeFolderName("Bad:Name?"));
        assertEquals("InstalledMod", CatalogInstaller.sanitizeFolderName("..."));
    }

    @Test
    public void usesNeutralMimeForModScriptsToPreserveTheirNames() {
        assertEquals("application/octet-stream", CatalogInstaller.mimeFor("main.lua"));
        assertEquals("application/octet-stream", CatalogInstaller.mimeFor("README.md"));
        assertEquals("application/json", CatalogInstaller.mimeFor("manifest.json"));
    }

    private static void assertRejected(String path, String expected) throws Exception {
        File root = Files.createTempDirectory("bmd-zip-reject").toFile();
        File zip = new File(root, "mod.zip");
        File output = new File(root, "out");
        assertTrue(output.mkdirs());
        try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(zip))) {
            stream.putNextEntry(new ZipEntry(path));
            stream.write("payload".getBytes(StandardCharsets.UTF_8));
            stream.closeEntry();
        }
        try {
            CatalogInstaller.extractAndInspect(zip, output);
            fail("Expected archive rejection");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(expected));
        }
    }
}
