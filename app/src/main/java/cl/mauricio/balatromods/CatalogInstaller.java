package cl.mauricio.balatromods;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CatalogInstaller {
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_UNPACKED_BYTES = 250L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 20_000;
    private static final int MAX_REDIRECTS = 6;
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".ps1", ".apk"
    );

    private CatalogInstaller() {
    }

    public static InstallResult install(
            Context context,
            DocumentFile modsFolder,
            CatalogItem item,
            String resolvedUrl
    ) throws Exception {
        return install(context, modsFolder, item, resolvedUrl, false);
    }

    public static InstallResult install(
            Context context,
            DocumentFile modsFolder,
            CatalogItem item,
            String resolvedUrl,
            boolean replaceExisting
    ) throws Exception {
        return install(context, modsFolder, item, resolvedUrl, replaceExisting, null);
    }

    public static InstallResult install(
            Context context,
            DocumentFile modsFolder,
            CatalogItem item,
            String resolvedUrl,
            boolean replaceExisting,
            ModEntry replacing
    ) throws Exception {
        if (modsFolder == null || !modsFolder.isDirectory() || !modsFolder.canWrite()) {
            throw new IllegalStateException("The Mods folder is not writable.");
        }
        validateUrl(resolvedUrl);
        String folderName = replacing == null
                ? sanitizeFolderName(item.folderName().isBlank() ? item.name() : item.folderName())
                : replacing.folderName;
        DocumentFile existing = replacing == null ? modsFolder.findFile(folderName) : replacing.directory;
        if (existing != null && !replaceExisting) {
            throw new IllegalStateException(
                    folderName + " is already installed. Choose Update or another version instead."
            );
        }

        File operationRoot = new File(
                context.getCacheDir(),
                "install-" + UUID.randomUUID()
        );
        File archive = new File(operationRoot, "download.zip");
        File extracted = new File(operationRoot, "extracted");
        File previous = new File(operationRoot, "previous");
        if (!extracted.mkdirs()) {
            throw new IllegalStateException("Could not create secure staging storage.");
        }

        try {
            DownloadInfo download = download(resolvedUrl, archive);
            ArchiveInspection inspection = extractAndInspect(archive, extracted);
            File contentRoot = chooseContentRoot(extracted);
            if (existing != null) {
                if (!previous.mkdirs()) {
                    throw new IllegalStateException("Could not create rollback storage for the current mod.");
                }
                int[] backupEntries = {0};
                long[] backupBytes = {0};
                copyDocumentTreeToLocal(context, existing, previous, backupEntries, backupBytes, false);
            }
            DocumentFile target = null;
            boolean replacementStarted = false;
            try {
                if (existing != null) {
                    replacementStarted = true;
                    ModRepository.deleteDocumentTree(existing);
                }
                target = modsFolder.createDirectory(folderName);
                if (target == null) {
                    throw new IllegalStateException("Could not create " + folderName + " in Mods.");
                }
                copyDirectory(context, contentRoot, target);
                removeRootIgnore(target);
            } catch (Exception error) {
                try {
                    if (target != null) {
                        ModRepository.deleteDocumentTree(target);
                    } else {
                        DocumentFile partial = modsFolder.findFile(folderName);
                        if (partial != null) ModRepository.deleteDocumentTree(partial);
                    }
                    if (replacementStarted && previous.exists()) {
                        DocumentFile restored = modsFolder.createDirectory(folderName);
                        if (restored == null) throw new IllegalStateException("Could not restore the previous version.");
                        copyDirectory(context, previous, restored);
                    }
                } catch (Exception restoreError) {
                    error.addSuppressed(restoreError);
                }
                throw error;
            }
            return new InstallResult(
                    folderName,
                    download.bytes(),
                    inspection.entries(),
                    inspection.hasLua(),
                    inspection.warnings()
            );
        } finally {
            deleteTree(operationRoot.toPath());
        }
    }

    /** Install a user-selected ZIP from SAF after bounded local inspection. */
    public static InstallResult installArchive(
            Context context,
            DocumentFile modsFolder,
            Uri archiveUri,
            String suggestedName
    ) throws Exception {
        if (modsFolder == null || !modsFolder.isDirectory() || !modsFolder.canWrite()) {
            throw new IllegalStateException("The Mods folder is not writable.");
        }
        File operationRoot = new File(context.getCacheDir(), "import-" + UUID.randomUUID());
        File archive = new File(operationRoot, "selected.zip");
        File extracted = new File(operationRoot, "extracted");
        if (!extracted.mkdirs()) {
            throw new IllegalStateException("Could not create secure staging storage.");
        }
        try {
            copyUriToFile(context, archiveUri, archive, MAX_DOWNLOAD_BYTES);
            ArchiveInspection inspection = extractAndInspect(archive, extracted);
            File contentRoot = chooseContentRoot(extracted);
            String folderName = sanitizeFolderName(stripArchiveExtension(suggestedName));
            return installPrepared(context, modsFolder, contentRoot, folderName, inspection);
        } finally {
            deleteTree(operationRoot.toPath());
        }
    }

    /** Install a user-selected folder from SAF after bounded tree inspection. */
    public static InstallResult installDirectory(
            Context context,
            DocumentFile modsFolder,
            DocumentFile source,
            String suggestedName
    ) throws Exception {
        if (modsFolder == null || !modsFolder.isDirectory() || !modsFolder.canWrite()) {
            throw new IllegalStateException("The Mods folder is not writable.");
        }
        if (source == null || !source.isDirectory()) {
            throw new IllegalArgumentException("Choose a mod folder, not a file.");
        }
        File operationRoot = new File(context.getCacheDir(), "import-folder-" + UUID.randomUUID());
        File local = new File(operationRoot, "folder");
        if (!local.mkdirs()) {
            throw new IllegalStateException("Could not create secure staging storage.");
        }
        try {
            int[] entries = {0};
            long[] bytes = {0};
            copyDocumentTreeToLocal(context, source, local, entries, bytes);
            if (entries[0] == 0) {
                throw new IllegalArgumentException("The selected folder is empty.");
            }
            List<String> warnings = new ArrayList<>();
            if (!containsLua(local)) {
                warnings.add("No Lua files were found; verify that this package is a Balatro mod.");
            }
            ArchiveInspection inspection = new ArchiveInspection(entries[0], bytes[0], containsLua(local), List.copyOf(warnings));
            String folderName = sanitizeFolderName(stripArchiveExtension(suggestedName));
            return installPrepared(context, modsFolder, local, folderName, inspection);
        } finally {
            deleteTree(operationRoot.toPath());
        }
    }

    private static InstallResult installPrepared(
            Context context,
            DocumentFile modsFolder,
            File contentRoot,
            String folderName,
            ArchiveInspection inspection
    ) throws Exception {
        if (modsFolder.findFile(folderName) != null) {
            throw new IllegalStateException(folderName + " is already installed. Remove or rename it before reinstalling.");
        }
        DocumentFile target = modsFolder.createDirectory(folderName);
        if (target == null) {
            throw new IllegalStateException("Could not create " + folderName + " in Mods.");
        }
        try {
            copyDirectory(context, contentRoot, target);
            removeRootIgnore(target);
        } catch (Exception error) {
            ModRepository.deleteDocumentTree(target);
            throw error;
        }
        return new InstallResult(folderName, 0, inspection.entries(), inspection.hasLua(), inspection.warnings());
    }

    private static void removeRootIgnore(DocumentFile target) throws Exception {
        DocumentFile marker = target.findFile(".lovelyignore");
        if (marker != null && marker.exists() && !marker.delete()) {
            throw new IllegalStateException("The installed mod could not be enabled.");
        }
    }

    private static void copyUriToFile(Context context, Uri uri, File destination, long maxBytes) throws Exception {
        InputStream raw = context.getContentResolver().openInputStream(uri);
        if (raw == null) throw new IllegalArgumentException("The selected archive could not be read.");
        try (InputStream input = new BufferedInputStream(raw);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            long total = 0;
            byte[] buffer = new byte[32_768];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("Archive exceeds the 100 MB safety limit.");
                output.write(buffer, 0, read);
            }
            if (total < 4) throw new IllegalArgumentException("The selected archive is empty.");
        }
    }

    private static void copyDocumentTreeToLocal(
            Context context,
            DocumentFile source,
            File destination,
            int[] entries,
            long[] bytes
    ) throws Exception {
        copyDocumentTreeToLocal(context, source, destination, entries, bytes, true);
    }

    private static void copyDocumentTreeToLocal(
            Context context,
            DocumentFile source,
            File destination,
            int[] entries,
            long[] bytes,
            boolean inspectCompatibility
    ) throws Exception {
        for (DocumentFile child : source.listFiles()) {
            String name = sanitizeFileName(child.getName());
            if (name.isBlank() || name.equals(".") || name.equals("..")) continue;
            if (child.isDirectory()) {
                File nested = new File(destination, name);
                if (!nested.mkdirs()) throw new IllegalStateException("Could not stage " + name);
                copyDocumentTreeToLocal(context, child, nested, entries, bytes, inspectCompatibility);
                continue;
            }
            entries[0]++;
            if (entries[0] > MAX_ENTRIES) throw new IllegalArgumentException("Folder has too many files.");
            String lower = name.toLowerCase(Locale.ROOT);
            if (inspectCompatibility) {
                for (String extension : BLOCKED_EXTENSIONS) {
                    if (lower.endsWith(extension)) throw new IllegalArgumentException("Blocked mobile-incompatible file: " + name);
                }
            }
            File target = new File(destination, name);
            InputStream raw = context.getContentResolver().openInputStream(child.getUri());
            if (raw == null) throw new IllegalStateException("Could not read " + name);
            try (InputStream input = new BufferedInputStream(raw);
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                byte[] buffer = new byte[16_384];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    bytes[0] += read;
                    if (bytes[0] > MAX_UNPACKED_BYTES) throw new IllegalArgumentException("Folder exceeds the 250 MB safety limit.");
                    output.write(buffer, 0, read);
                }
            }
        }
    }

    private static boolean containsLua(File root) throws Exception {
        try (var paths = Files.walk(root.toPath())) {
            return paths.anyMatch(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".lua"));
        }
    }

    private static String sanitizeFileName(String value) {
        return value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static String stripArchiveExtension(String value) {
        return value == null ? "" : value.replaceFirst("(?i)\\.(zip|love|7z|rar)$", "");
    }

    private static DownloadInfo download(String address, File destination) throws Exception {
        URL current = new URL(address);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            validateUrl(current.toString());
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "Balatro-Mobile-Mod-Manager-Android/2.0");
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) {
                    throw new IllegalStateException("Download redirect had no destination.");
                }
                current = new URL(current, location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IllegalStateException("Download returned HTTP " + status);
            }
            long advertised = connection.getContentLengthLong();
            if (advertised > MAX_DOWNLOAD_BYTES) {
                connection.disconnect();
                throw new IllegalArgumentException("Archive exceeds the 100 MB download limit.");
            }
            long total = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
                byte[] buffer = new byte[32_768];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IllegalArgumentException(
                                "Archive exceeded the 100 MB download limit."
                        );
                    }
                    output.write(buffer, 0, read);
                }
            } finally {
                connection.disconnect();
            }
            if (total < 4) {
                throw new IllegalArgumentException("Downloaded archive is empty.");
            }
            return new DownloadInfo(total);
        }
        throw new IllegalStateException("Too many download redirects.");
    }

    static ArchiveInspection extractAndInspect(File archive, File destination) throws Exception {
        Path destinationPath = destination.toPath().toAbsolutePath().normalize();
        int entries = 0;
        long unpacked = 0;
        boolean hasLua = false;
        List<String> warnings = new ArrayList<>();

        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(archive))
        )) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Archive has too many files.");
                }
                String rawName = entry.getName().replace('\\', '/');
                if (rawName.isBlank() || rawName.indexOf('\0') >= 0) {
                    throw new IllegalArgumentException("Archive contains an invalid path.");
                }
                Path outputPath = destinationPath.resolve(rawName).normalize();
                if (!outputPath.startsWith(destinationPath)) {
                    throw new IllegalArgumentException(
                            "Archive tried to write outside the staging folder."
                    );
                }
                String lower = rawName.toLowerCase(Locale.ROOT);
                for (String extension : BLOCKED_EXTENSIONS) {
                    if (lower.endsWith(extension)) {
                        throw new IllegalArgumentException(
                                "Blocked mobile-incompatible file: " + rawName
                        );
                    }
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                    continue;
                }
                if (lower.endsWith(".lua")) {
                    hasLua = true;
                }
                Files.createDirectories(outputPath.getParent());
                try (OutputStream output = new BufferedOutputStream(
                        new FileOutputStream(outputPath.toFile())
                )) {
                    byte[] buffer = new byte[16_384];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        unpacked += read;
                        if (unpacked > MAX_UNPACKED_BYTES) {
                            throw new IllegalArgumentException(
                                    "Archive exceeded the 250 MB unpacked limit."
                            );
                        }
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
        if (entries == 0) {
            throw new IllegalArgumentException("Archive contains no files.");
        }
        if (!hasLua) {
            warnings.add("No Lua files were found; verify that this package is a Balatro mod.");
        }
        return new ArchiveInspection(entries, unpacked, hasLua, List.copyOf(warnings));
    }

    private static File chooseContentRoot(File extracted) {
        File[] children = extracted.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) {
            return children[0];
        }
        return extracted;
    }

    private static void copyDirectory(
            Context context,
            File source,
            DocumentFile destination
    ) throws Exception {
        File[] children = source.listFiles();
        if (children == null) {
            throw new IllegalStateException("The extracted folder could not be read.");
        }
        for (File child : children) {
            if (child.isDirectory()) {
                DocumentFile nested = destination.createDirectory(child.getName());
                if (nested == null) {
                    throw new IllegalStateException("Could not create " + child.getName());
                }
                copyDirectory(context, child, nested);
            } else {
                DocumentFile target = destination.createFile(
                        mimeFor(child.getName()),
                        child.getName()
                );
                if (target == null) {
                    throw new IllegalStateException("Could not create " + child.getName());
                }
                try (InputStream input = new BufferedInputStream(new FileInputStream(child));
                     OutputStream output = context.getContentResolver().openOutputStream(
                             target.getUri(),
                             "wt"
                     )) {
                    if (output == null) {
                        throw new IllegalStateException(
                                "The provider denied writing " + child.getName()
                        );
                    }
                    byte[] buffer = new byte[16_384];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    static String sanitizeFolderName(String value) {
        String safe = value == null ? "" : value
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\.+$", "")
                .trim();
        if (safe.isBlank()) {
            safe = "InstalledMod";
        }
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private static void validateUrl(String address) throws Exception {
        URI uri = URI.create(address);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Only HTTPS downloads are allowed.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = host.equals("github.com")
                || host.endsWith(".github.com")
                || host.endsWith(".githubusercontent.com")
                || host.equals("thunderstore.io")
                || host.endsWith(".thunderstore.io")
                || host.equals("codeberg.org")
                || host.endsWith(".codeberg.org")
                || host.equals("gitlab.com")
                || host.endsWith(".gitlab.com");
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Download host is not on the trusted list: " + host
            );
        }
    }

    static String mimeFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        // DocumentsProvider implementations may append an extension inferred
        // from a text MIME type (for example main.lua -> main.lua.txt).
        // A neutral MIME preserves mod filenames exactly.
        return "application/octet-stream";
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Cache cleanup is best-effort.
                }
            });
        } catch (Exception ignored) {
            // Cache cleanup is best-effort.
        }
    }

    private record DownloadInfo(long bytes) {
    }

    public record ArchiveInspection(
            int entries,
            long unpackedBytes,
            boolean hasLua,
            List<String> warnings
    ) {
    }

    public record InstallResult(
            String folderName,
            long downloadedBytes,
            int entries,
            boolean hasLua,
            List<String> warnings
    ) {
    }
}
