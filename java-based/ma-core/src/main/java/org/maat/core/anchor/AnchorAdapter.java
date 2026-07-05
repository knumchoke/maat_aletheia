package org.maat.core.anchor;

import org.maat.core.model.AnchorProof;

/**
 * Anchor adapter interface (arch §4.3). The anchor — not any operator key —
 * is the load-bearing control (invariant 4). Phase 1 capstone ships one real
 * implementation (RFC 3161 TSA) plus a clearly-labelled fake for offline dev.
 * The interface stays even with one implementation: it is the honest shape of
 * the architecture and the Phase 3 Rekor adapter slots in here.
 */
public interface AnchorAdapter {

    /** Anchors an alg:hex hash string; returns the proof to embed in the record. */
    AnchorProof anchor(String hashString) throws AnchorException;

    /**
     * Verifies that the proof genuinely anchors {@code hashString}
     * (signature chains to the pinned TSA cert, message imprint matches).
     */
    boolean verify(AnchorProof proof, String hashString) throws AnchorException;

    class AnchorException extends Exception {
        public AnchorException(String message, Throwable cause) { super(message, cause); }
        public AnchorException(String message) { super(message); }
    }
}
