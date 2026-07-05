package org.maat.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Batch record — capstone form of ma/batch/v1 (phase1_architecture.md §3.3),
 * with ONE deliberate simplification (capstone_scope decision): the package-set
 * commitment is not an RFC 6962 Merkle root; it is the hash of the JCS bytes of
 * the sorted manifest_hashes list, and the full list is published alongside.
 * At exam-session scale the detection power is identical; Merkle trees +
 * inclusion proofs are future work (arch §5.2).
 *
 * session_ref is the hash of the pre-anchored SessionRecord (anchored payload,
 * i.e. without its anchor field) — the batch is built AGAINST the roster
 * pre-commitment (invariant 8). Exactly one batch per session_id may exist:
 * first anchor wins (invariant 5); a second record is verdict CONFLICTING_BATCH.
 */
public record BatchRecord(
        @JsonProperty("schema") String schema,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("session_ref") String sessionRef,
        @JsonProperty("manifest_hashes") List<String> manifestHashes,
        @JsonProperty("commitment") String commitment,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("anchor") AnchorProof anchor) {

    public static final String SCHEMA = "ma/batch/v1";

    /** The anchored payload is the record WITHOUT the anchor field. */
    public BatchRecord unanchored() {
        return new BatchRecord(schema, sessionId, sessionRef, manifestHashes, commitment, createdAt, null);
    }

    public BatchRecord withAnchor(AnchorProof proof) {
        return new BatchRecord(schema, sessionId, sessionRef, manifestHashes, commitment, createdAt, proof);
    }
}
