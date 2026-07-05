package org.maat.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Session registry record — trimmed capstone form of ma/session/v1
 * (phase1_architecture.md §3.4). Produced by the registration authority
 * BEFORE the session and anchored (invariant 8). The batch references this
 * record by hash; the verifier checks every roster commitment here has a
 * manifest in the batch (omission detection, finding C-1).
 *
 * roster_commitments are salted candidate commitments (see RosterCommitment),
 * sorted lexicographically so the record is order-independent (invariant 7:
 * no candidate identity leaves the operator).
 */
public record SessionRecord(
        @JsonProperty("schema") String schema,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("window_start") String windowStart,
        @JsonProperty("window_end") String windowEnd,
        @JsonProperty("roster_commitments") List<String> rosterCommitments,
        @JsonProperty("anchor") AnchorProof anchor) {

    public static final String SCHEMA = "ma/session/v1";

    /** The anchored payload is the record WITHOUT the anchor field. */
    public SessionRecord unanchored() {
        return new SessionRecord(schema, sessionId, windowStart, windowEnd, rosterCommitments, null);
    }

    public SessionRecord withAnchor(AnchorProof proof) {
        return new SessionRecord(schema, sessionId, windowStart, windowEnd, rosterCommitments, proof);
    }
}
