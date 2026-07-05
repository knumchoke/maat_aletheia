package org.maat.core.model;

/**
 * Candidate receipt — MA1 QR payload (phase1_architecture.md §3.5, finding M-3).
 * Wire form: "MA1|<manifest_hash>|<session_id>|<salt_hex>"
 * Carries the FULL manifest hash (no truncation — grinding resistance) plus the
 * per-record salt so the candidate can open their roster commitment.
 * A receipt leaks nothing about the candidate (invariant 7): salt + commitment
 * only mean something to the person already holding their own candidate ID.
 */
public record Receipt(String manifestHash, String sessionId, String saltHex) {

    public static final String PREFIX = "MA1";

    public String encode() {
        return PREFIX + "|" + manifestHash + "|" + sessionId + "|" + saltHex;
    }

    public static Receipt decode(String payload) {
        String[] parts = payload.strip().split("\\|");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("not an MA1 receipt: " + payload);
        }
        return new Receipt(parts[1], parts[2], parts[3]);
    }
}
