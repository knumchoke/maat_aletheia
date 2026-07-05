package org.maat.core;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.maat.core.model.BatchRecord;
import org.maat.core.model.Manifest;
import org.maat.core.model.SessionRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Workdir layout — capstone two-tier form of arch §5.3:
 *
 *   workdir/
 *     public/                what an independent verifier gets; NEVER contains pixels (invariant 7)
 *       session-<id>.json      anchored session record
 *       batch-<id>.json        anchored batch record (carries the full manifest-hash list)
 *       batch-<id>.conflict-*.json   any additional record for the same id (first-anchor-wins, inv. 5)
 *     evidence/              manifests + page images, content-addressed
 *       manifests/<manifest-hash-hex>.json
 *       pages/<page-hash-hex>.<ext>
 *     receipts/              QR PNGs + payload text (operator side, handed to candidates)
 *     session-pack-<id>.json RA→operator hand-over: candidate_id → salt (NOT public)
 *
 * Append-only by construction (invariant 1): writes refuse to overwrite.
 */
public final class Store {

    private final Path root;

    public Store(Path root) {
        this.root = root;
    }

    public Path publicDir()   { return root.resolve("public"); }
    public Path manifestsDir(){ return root.resolve("evidence/manifests"); }
    public Path pagesDir()    { return root.resolve("evidence/pages"); }
    public Path receiptsDir() { return root.resolve("receipts"); }
    public Path root()        { return root; }

    public void writeSession(SessionRecord record) {
        writeNew(publicDir().resolve("session-" + record.sessionId() + ".json"), record);
    }

    public void writeBatch(BatchRecord record) {
        writeNew(publicDir().resolve("batch-" + record.sessionId() + ".json"), record);
    }

    public void writeManifest(Manifest manifest) {
        String hex = hexOf(CanonicalJson.hash(manifest));
        writeNew(manifestsDir().resolve(hex + ".json"), manifest);
    }

    public void writePage(byte[] pageBytes, String ext) {
        String hex = hexOf(Hashes.sha256(pageBytes));
        Path target = pagesDir().resolve(hex + "." + ext);
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {              // same bytes ⇒ same file; re-ingest is a no-op
                Files.write(target, pageBytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<SessionRecord> readSession(Path publicDir, String sessionId) {
        return readJson(publicDir.resolve("session-" + sessionId + ".json"), SessionRecord.class);
    }

    public Optional<BatchRecord> readBatch(Path publicDir, String sessionId) {
        return readJson(publicDir.resolve("batch-" + sessionId + ".json"), BatchRecord.class);
    }

    public Optional<Manifest> readManifest(Path evidenceDir, String manifestHash) {
        return readJson(evidenceDir.resolve("manifests").resolve(hexOf(manifestHash) + ".json"), Manifest.class);
    }

    public Optional<byte[]> readPage(Path evidenceDir, String pageHash) {
        try (var files = Files.list(evidenceDir.resolve("pages"))) {
            List<Path> matches = new ArrayList<>();
            String hex = hexOf(pageHash);
            files.filter(p -> p.getFileName().toString().startsWith(hex + ".")).forEach(matches::add);
            if (matches.isEmpty()) return Optional.empty();
            return Optional.of(Files.readAllBytes(matches.getFirst()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> readJson(Path file, Class<T> type) {
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(CanonicalJson.mapper().readValue(file.toFile(), type));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Stored records are pretty-printed for humans; hashes are ALWAYS over JCS bytes, never file bytes. */
    private void writeNew(Path target, Object record) {
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                throw new IllegalStateException("append-only store: refusing to overwrite " + target
                        + " (invariant 1 — replacement only via anchored supersession, which is out of capstone scope)");
            }
            ObjectWriter w = CanonicalJson.mapper().writerWithDefaultPrettyPrinter();
            Files.write(target, w.writeValueAsBytes(record));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** File names use the bare hex part of an alg:hex hash string. */
    public static String hexOf(String hashString) {
        Hashes.algorithmOf(hashString); // validates the convention
        return hashString.substring(hashString.indexOf(':') + 1);
    }
}
