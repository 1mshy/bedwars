package com.imshy.bedwars;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe JSON persistence shared by {@code PlayerDatabase} and
 * {@code MapLearningService}: serialize to a sibling temp file, then move it
 * over the target atomically, so a JVM kill or a serialization error
 * mid-write can never truncate the only copy of the data.
 */
public final class JsonFileUtil {

    private JsonFileUtil() {}

    /** Serialize {@code root} to {@code target} via temp file + atomic move. */
    public static void writeAtomic(File target, Gson gson, JsonElement root) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        Writer writer = new OutputStreamWriter(Files.newOutputStream(tmp.toPath()), StandardCharsets.UTF_8);
        try {
            gson.toJson(root, writer);
        } finally {
            writer.close();
        }
        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Set an unparseable file aside as {@code <name>.corrupt} so the next
     * save doesn't overwrite the user's only (possibly hand-recoverable)
     * copy. Returns the quarantine file, or null if the move failed.
     */
    public static File quarantineCorrupt(File file) {
        File corrupt = new File(file.getPath() + ".corrupt");
        try {
            Files.move(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return corrupt;
        } catch (IOException e) {
            return null;
        }
    }
}
