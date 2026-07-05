package org.maat.core.verify;

/**
 * Multi-valued verdicts (invariant 6 — NEVER collapse to pass/fail).
 * Capstone subset of the arch §4.5 verdict set; SUPERSEDED only arrives with
 * the stretch goal.
 */
public enum Verdict {
    /** Document verifies: manifest in anchored batch, pages match, roster entry matches. */
    CURRENT,
    /** Bytes disagree with the anchored commitment: modified page, altered or substituted manifest. */
    TAMPERED,
    /** A pre-anchored roster commitment has no manifest in the batch (omission, C-1). */
    MISSING_FROM_ROSTER,
    /** Manifest's candidate commitment is not in the pre-anchored roster. */
    NOT_IN_ROSTER,
    /** Record has no anchor, a fake anchor, or an anchor that fails cryptographic verification. */
    UNANCHORED,
    /** More than one batch record exists for one session id (first-anchor-wins, invariant 5). */
    CONFLICTING_BATCH,
    /** Referenced evidence (manifest/page file) is absent from the evidence store. */
    EVIDENCE_MISSING
}
