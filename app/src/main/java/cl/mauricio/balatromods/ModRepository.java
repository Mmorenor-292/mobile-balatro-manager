package cl.mauricio.balatromods;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModRepository {
    static final int MAX_METADATA_CHARS = 262_144;

    private ModRepository() {
    }

    public static ScanResult scan(Context context, Uri selectedTree) throws Exception {
        DocumentFile selected = DocumentFile.fromTreeUri(context, selectedTree);
        if (selected == null || !selected.exists() || !selected.isDirectory()) {
            throw new IllegalStateException("The selected folder is no longer available.");
        }

        DocumentFile modsFolder = resolveModsFolder(selected);
        if (modsFolder == null) {
            throw new IllegalArgumentException(
                    "Choose Mods, ASET, or the root folder containing ASET/Mods."
            );
        }

        List<ModEntry> mods = new ArrayList<>();
        List<String> scanErrors = new ArrayList<>();
        List<String> junkNames = new ArrayList<>();
        DocumentFile[] children = safeListFiles(modsFolder);
        Arrays.sort(children, Comparator.comparing(
                file -> safe(file.getName()).toLowerCase(Locale.ROOT)
        ));

        for (DocumentFile child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            String folderName = safe(child.getName());
            if (isKnownJunkName(folderName, true)) {
                junkNames.add(folderName);
                continue;
            }
            try {
                mods.add(scanMod(context, child, folderName));
            } catch (Exception error) {
                String message = readable(error);
                scanErrors.add(folderName + ": " + message);
                mods.add(new ModEntry(
                        folderName,
                        folderName,
                        folderName,
                        "",
                        "",
                        "This mod could not be fully inspected.",
                        "",
                        "",
                        markerExists(child),
                        0,
                        child.lastModified(),
                        List.of(),
                        List.of("Scan error: " + message),
                        "error",
                        child
                ));
            }
        }

        for (DocumentFile child : children) {
            if (!child.isFile()) continue;
            String name = safe(child.getName());
            if (isKnownJunkName(name, false)) junkNames.add(name);
        }

        applyCrossModDiagnostics(mods);
        return new ScanResult(
                safe(modsFolder.getName()),
                modsFolder.getUri(),
                modsFolder,
                List.copyOf(mods),
                List.copyOf(scanErrors),
                List.copyOf(junkNames)
        );
    }

    /**
     * Permanently removes only manager residues and OS metadata that are safe
     * to classify without guessing. Real mod folders, disabled mods and user
     * backups are never included.
     */
    public static CleanupReport cleanKnownJunk(DocumentFile modsFolder) throws Exception {
        if (modsFolder == null || !modsFolder.exists() || !modsFolder.isDirectory()) {
            throw new IllegalStateException("The Mods folder is no longer available.");
        }
        List<String> removed = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (DocumentFile child : safeListFiles(modsFolder)) {
            String name = safe(child.getName());
            if (!isKnownJunkName(name, child.isDirectory())) continue;
            try {
                deleteDocumentTree(child);
                removed.add(name);
            } catch (Exception error) {
                failures.add(name + ": " + readable(error));
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Some junk could not be removed: " + String.join("; ", failures));
        }
        return new CleanupReport(removed.size(), List.copyOf(removed));
    }

    static boolean isKnownJunkName(String rawName, boolean directory) {
        String name = safe(rawName).toLowerCase(Locale.ROOT);
        if (directory) {
            return name.startsWith(".bmm-trash--")
                    || name.startsWith(".bmm-incoming--")
                    || name.startsWith(".bmm-staging--")
                    || name.startsWith(".mbm-trash--")
                    || name.startsWith(".mbm-incoming--")
                    || name.equals("__macosx");
        }
        return name.equals(".ds_store")
                || name.equals("thumbs.db")
                || name.equals("desktop.ini")
                || ((name.startsWith(".bmm-") || name.startsWith(".mbm-"))
                    && (name.endsWith(".tmp") || name.endsWith(".part")));
    }

    private static ModEntry scanMod(Context context, DocumentFile child, String folderName)
            throws Exception {
        DocumentFile[] rootFiles = safeListFiles(child);
        DocumentFile marker = child.findFile(".lovelyignore");
        DocumentFile metadata = findMetadata(rootFiles, folderName);
        String rawMetadata = metadata == null ? "" : readText(context, metadata);
        ModMetadataParser.ParsedMetadata parsed =
                ModMetadataParser.parse(rawMetadata, folderName);

        List<String> diagnostics = new ArrayList<>();
        String severity = "ok";
        if (metadata == null) {
            diagnostics.add("No metadata file detected");
            severity = "warning";
        } else if (!parsed.valid()) {
            diagnostics.add("Corrupted metadata: " + parsed.error());
            severity = "warning";
        }
        if (rootFiles.length == 0) {
            diagnostics.add("Empty mod folder");
            severity = "error";
        }
        return new ModEntry(
                parsed.id(),
                parsed.name(),
                folderName,
                parsed.version(),
                parsed.author(),
                parsed.description(),
                parsed.website(),
                metadata == null ? "" : metadata.getName(),
                marker != null && marker.exists(),
                rootFiles.length,
                child.lastModified(),
                parsed.dependencies(),
                diagnostics,
                severity,
                child,
                findThumbnail(context, rootFiles)
        );
    }

    private static String findThumbnail(Context context, DocumentFile[] files) {
        DocumentFile candidate = null;
        for (DocumentFile file : files) {
            if (!file.isFile() || file.getName() == null) continue;
            String name = file.getName().toLowerCase(Locale.ROOT);
            String type = file.getType() == null ? "" : file.getType().toLowerCase(Locale.ROOT);
            boolean image = type.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
            if (!image) continue;
            if (name.startsWith("icon") || name.startsWith("cover") || name.startsWith("logo") || name.startsWith("thumbnail")) { candidate = file; break; }
            if (candidate == null) candidate = file;
        }
        if (candidate == null) return "";
        try {
            long size = candidate.length();
            if (size <= 0 || size > 512L * 1024L) return "";
            InputStream input = context.getContentResolver().openInputStream(candidate.getUri());
            if (input == null) return "";
            try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream((int) size)) {
                byte[] buffer = new byte[16_384]; int read; while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
                String mime = candidate.getType();
                if (mime == null || mime.isBlank()) mime = "image/png";
                return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(output.toByteArray());
            }
        } catch (Exception ignored) { return ""; }
    }

    private static void applyCrossModDiagnostics(List<ModEntry> mods) {
        Map<String, Integer> ids = new HashMap<>();
        Set<String> available = new HashSet<>();
        for (ModEntry mod : mods) {
            String id = normalizeId(mod.id);
            ids.put(id, ids.getOrDefault(id, 0) + 1);
            available.add(id);
            available.add(normalizeId(mod.name));
            available.add(normalizeId(mod.folderName));
        }

        for (int i = 0; i < mods.size(); i++) {
            ModEntry mod = mods.get(i);
            List<String> diagnostics = new ArrayList<>(mod.diagnostics);
            String severity = mod.severity;
            if (ids.getOrDefault(normalizeId(mod.id), 0) > 1) {
                diagnostics.add("Duplicate mod ID: " + mod.id);
                severity = worst(severity, "error");
            }
            for (String dependency : mod.dependencies) {
                String normalized = normalizeId(dependency);
                if (!normalized.isBlank() && !containsCompatible(available, normalized)) {
                    diagnostics.add("Missing dependency: " + dependency);
                    severity = worst(severity, "error");
                }
            }
            if (!diagnostics.equals(mod.diagnostics) || !severity.equals(mod.severity)) {
                mods.set(i, copyWithDiagnostics(mod, diagnostics, severity));
            }
        }
    }

    private static boolean containsCompatible(Set<String> available, String dependency) {
        if (available.contains(dependency)) {
            return true;
        }
        for (String candidate : available) {
            if (candidate.contains(dependency) || dependency.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static ModEntry copyWithDiagnostics(
            ModEntry mod,
            List<String> diagnostics,
            String severity
    ) {
        return new ModEntry(
                mod.id,
                mod.name,
                mod.folderName,
                mod.version,
                mod.author,
                mod.description,
                mod.website,
                mod.metadataFile,
                mod.hidden,
                mod.rootItemCount,
                mod.lastModified,
                mod.dependencies,
                diagnostics,
                severity,
                mod.directory,
                mod.thumbnail
        );
    }

    public static void setHidden(Context context, ModEntry mod, boolean hidden) throws Exception {
        if (mod.directory == null) {
            throw new IllegalStateException("The mod folder is not writable.");
        }
        if (hidden) {
            DocumentFile existing = mod.directory.findFile(".lovelyignore");
            if (existing != null && existing.exists()) {
                return;
            }
            DocumentFile marker = mod.directory.createFile(
                    "application/octet-stream",
                    ".lovelyignore"
            );
            if (marker == null) {
                throw new IllegalStateException("Could not create .lovelyignore.");
            }
            try (OutputStream output = context.getContentResolver().openOutputStream(
                    marker.getUri(), "wt"
            )) {
                if (output == null) {
                    throw new IllegalStateException("The provider denied write access.");
                }
                output.write("Disabled by MBM - Mobile Balatro Manager\n".getBytes(StandardCharsets.UTF_8));
            }
        } else {
            DocumentFile marker = mod.directory.findFile(".lovelyignore");
            if (marker != null && marker.exists() && !marker.delete()) {
                throw new IllegalStateException("Could not remove .lovelyignore.");
            }
        }
    }

    public static void deletePermanently(ModEntry mod) throws Exception {
        if (mod == null || mod.directory == null || !mod.directory.exists()) {
            throw new IllegalStateException("The mod folder is no longer available.");
        }
        if (isEssential(mod)) {
            throw new IllegalStateException(
                    "Steamodded/Lovely are protected. Disable them instead of deleting the framework."
            );
        }
        deleteDocumentTree(mod.directory);
    }

    static void deleteDocumentTree(DocumentFile root) throws Exception {
        if (root == null || !root.exists()) return;
        if (root.isDirectory()) {
            for (DocumentFile child : safeListFiles(root)) {
                deleteDocumentTree(child);
            }
        }
        if (root.exists() && !root.delete()) {
            throw new IllegalStateException("Android could not permanently delete " + safe(root.getName()) + ".");
        }
    }

    public static void applyStates(
            Context context,
            List<ModEntry> mods,
            Map<String, Boolean> hiddenByFolder
    ) throws Exception {
        List<String> failures = new ArrayList<>();
        for (ModEntry mod : mods) {
            Boolean hidden = hiddenByFolder.get(mod.folderName);
            if (hidden == null || hidden == mod.hidden) {
                continue;
            }
            try {
                setHidden(context, mod, hidden);
            } catch (Exception error) {
                failures.add(mod.folderName + ": " + readable(error));
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(String.join("; ", failures));
        }
    }

    public static boolean isEssential(ModEntry mod) {
        String name = normalizeId(mod.folderName + " " + mod.id + " " + mod.name);
        return name.contains("lovely")
                || name.contains("steamodded")
                || name.contains("smods");
    }

    static DocumentFile resolveModsFolder(DocumentFile selected) {
        String selectedName = safe(selected.getName());
        if ("mods".equalsIgnoreCase(selectedName)) {
            return selected;
        }

        DocumentFile direct = selected.findFile("Mods");
        if (direct != null && direct.isDirectory()) {
            return direct;
        }

        DocumentFile aset = selected.findFile("ASET");
        if (aset != null && aset.isDirectory()) {
            DocumentFile nested = aset.findFile("Mods");
            if (nested != null && nested.isDirectory()) {
                return nested;
            }
        }
        return null;
    }

    private static DocumentFile findMetadata(DocumentFile[] files, String folderName) {
        for (String preferred : new String[]{
                "manifest.json", folderName + ".json", "mod.json", "metadata.json", "smods.json"
        }) {
            for (DocumentFile file : files) {
                if (file.isFile() && preferred.equalsIgnoreCase(safe(file.getName()))) {
                    return file;
                }
            }
        }
        for (DocumentFile file : files) {
            String name = safe(file.getName()).toLowerCase(Locale.ROOT);
            if (file.isFile()
                    && name.endsWith(".json")
                    && !name.contains("config")
                    && !name.startsWith(".luarc")) {
                return file;
            }
        }
        return null;
    }

    private static String readText(Context context, DocumentFile file) throws Exception {
        StringBuilder result = new StringBuilder();
        try (InputStream input = context.getContentResolver().openInputStream(file.getUri())) {
            if (input == null) {
                throw new IllegalStateException("Metadata could not be opened.");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            )) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1
                        && result.length() < MAX_METADATA_CHARS) {
                    int accepted = Math.min(read, MAX_METADATA_CHARS - result.length());
                    result.append(buffer, 0, accepted);
                }
                if (reader.read() != -1) {
                    throw new IllegalArgumentException("Metadata exceeds the 256 KiB safety limit.");
                }
            }
        }
        return result.toString();
    }

    private static DocumentFile[] safeListFiles(DocumentFile folder) {
        DocumentFile[] files = folder.listFiles();
        return files == null ? new DocumentFile[0] : files;
    }

    private static boolean markerExists(DocumentFile directory) {
        try {
            DocumentFile marker = directory.findFile(".lovelyignore");
            return marker != null && marker.exists();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String worst(String current, String candidate) {
        return rank(candidate) > rank(current) ? candidate : current;
    }

    private static int rank(String value) {
        return switch (value) {
            case "error" -> 3;
            case "warning" -> 2;
            case "info" -> 1;
            default -> 0;
        };
    }

    static String normalizeId(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String readable(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record ScanResult(
            String folderName,
            Uri folderUri,
            DocumentFile folder,
            List<ModEntry> mods,
            List<String> scanErrors,
            List<String> junkNames
    ) {
    }

    public record CleanupReport(int removed, List<String> names) {
    }
}
