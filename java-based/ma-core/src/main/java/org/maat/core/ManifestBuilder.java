package org.maat.core;

import org.maat.core.model.Manifest;

import java.time.Instant;
import java.util.List;

/**
 * Builds a document manifest from the FINAL page bytes (invariant 3: nothing
 * may rewrite bytes after hashing — the capstone's declared canonical format
 * is "whatever the scanner emitted", hashed as-is).
 * scan_time is set HERE, at hash time, never taken from scanner metadata
 * (finding M-1 / arch decision 1).
 */
public final class ManifestBuilder {

    private ManifestBuilder() {}

    public static Manifest build(String sessionId, String candidateCommitment,
                                 List<byte[]> pagesInOrder, Instant scanTime) {
        if (pagesInOrder.isEmpty()) {
            throw new IllegalArgumentException("a document has at least one page");
        }
        List<String> pageHashes = pagesInOrder.stream().map(Hashes::sha256).toList();
        return new Manifest(Manifest.SCHEMA, sessionId, candidateCommitment,
                pageHashes, pageHashes.size(), scanTime.toString());
    }
}
