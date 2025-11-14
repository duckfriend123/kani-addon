package kani.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class CopypastaManager {
    private static final String[] DEFAULT_FILES = { "navyseals.txt", "breaking_bad_reference.txt", "goblin.txt" };

    private static Path directory;
    private static boolean initialized;

    private CopypastaManager() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        directory = MeteorClient.FOLDER.toPath().resolve("kani").resolve("copypastas");
        try {
            Files.createDirectories(directory);
            for (String file : DEFAULT_FILES) copyDefaultIfMissing(file);
        } catch (IOException e) {
            MeteorClient.LOG.error("[Kani] Failed to prepare copypasta directory.", e);
        }
    }

    private static void copyDefaultIfMissing(String name) throws IOException {
        Path target = directory.resolve(name);
        if (Files.exists(target)) return;

        try (InputStream stream = CopypastaManager.class.getResourceAsStream("/copypastas/" + name)) {
            if (stream == null) return;
            Files.copy(stream, target);
        }
    }

    public static List<String> listPastas() {
        if (!initialized) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        try {
            Files.list(directory)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".txt"))
                .forEach(path -> result.add(stripExtension(path.getFileName().toString())));
        } catch (IOException ignored) {
        }
        return result;
    }

    public static Optional<String> read(String name) {
        if (!initialized) return Optional.empty();
        Path file = directory.resolve(name.endsWith(".txt") ? name : name + ".txt");
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            MeteorClient.LOG.error("[Kani] Failed to read copypasta {}.", file, e);
            return Optional.empty();
        }
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx == -1 ? fileName : fileName.substring(0, idx);
    }
}
