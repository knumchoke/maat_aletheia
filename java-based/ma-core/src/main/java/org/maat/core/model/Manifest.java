package org.maat.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Document manifest — trimmed capstone form of ma/manifest/v1
 * (phase1_architecture.md §3.2; signature and supersession fields deferred,
 * see capstone_scope.md §3.3).
 *
 * page_hashes are ordered — page order is part of what is protected
 * (a re-ordered script must verify as tampered).
 * candidate_commitment matches one entry of the pre-anchored roster
 * (never a raw candidate ID — invariant 7).
 */
public record Manifest(
        @JsonProperty("schema") String schema,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("candidate_commitment") String candidateCommitment,
        @JsonProperty("page_hashes") List<String> pageHashes,
        @JsonProperty("page_count") int pageCount,
        @JsonProperty("scan_time") String scanTime) {

    public static final String SCHEMA = "ma/manifest/v1";
}
