package org.maat.core.verify;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Machine-readable verifier output. One batch-level list + one finding per
 * document / roster entry. `detail` is a human sentence saying exactly what
 * disagreed with what — a verdict without its evidence is an accusation.
 */
public record VerificationReport(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("batch_findings") List<Finding> batchFindings,
        @JsonProperty("document_findings") List<Finding> documentFindings) {

    public record Finding(
            @JsonProperty("subject") String subject,   // manifest hash, roster commitment, or "batch"
            @JsonProperty("verdict") Verdict verdict,
            @JsonProperty("detail") String detail) {
    }
}
