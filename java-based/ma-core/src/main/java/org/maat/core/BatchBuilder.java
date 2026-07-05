package org.maat.core;

import org.maat.core.model.BatchRecord;
import org.maat.core.model.Manifest;
import org.maat.core.model.SessionRecord;

import java.time.Instant;
import java.util.List;

/**
 * Builds the (unanchored) batch record against the pre-anchored session record
 * (invariant 8). Commitment scheme — capstone simplification of arch §3.3:
 * commitment = sha-256( JCS( sorted manifest_hashes ) ), full list published.
 */
public final class BatchBuilder {

    private BatchBuilder() {}

    public static BatchRecord build(SessionRecord session, List<Manifest> manifests, Instant createdAt) {
        List<String> manifestHashes = manifests.stream()
                .map(CanonicalJson::hash)
                .sorted()
                .toList();
        String commitment = Hashes.sha256(CanonicalJson.canonicalBytes(manifestHashes));
        String sessionRef = CanonicalJson.hash(session.unanchored());
        return new BatchRecord(BatchRecord.SCHEMA, session.sessionId(), sessionRef,
                manifestHashes, commitment, createdAt.toString(), null);
    }
}
