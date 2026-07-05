package org.maat.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anchor proof — capstone form of ma/anchor-proof/v1 (phase1_architecture.md §3.6).
 * anchor_class records WHAT kind of anchor this is (finding H-1); the verifier
 * surfaces it — a "fake" anchor must be impossible to mistake for a real one.
 *
 * For rfc3161-tsa: token_b64 is the DER TimeStampToken from the TSA, base64.
 * The anchored hash is the hash of the JCS bytes of the record's anchored
 * payload (record without its anchor field).
 */
public record AnchorProof(
        @JsonProperty("anchor_class") String anchorClass,
        @JsonProperty("anchored_hash") String anchoredHash,
        @JsonProperty("tsa_url") String tsaUrl,
        @JsonProperty("token_b64") String tokenB64,
        @JsonProperty("anchored_at") String anchoredAt) {

    public static final String CLASS_RFC3161 = "rfc3161-tsa";
    /** Demo-only, provides NO tamper evidence. The verifier must report it as UNANCHORED. */
    public static final String CLASS_FAKE = "fake";
}
