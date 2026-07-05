package org.maat.core.verify;

import org.maat.core.CanonicalJson;
import org.maat.core.Hashes;
import org.maat.core.RosterCommitment;
import org.maat.core.Store;
import org.maat.core.anchor.AnchorAdapter;
import org.maat.core.model.AnchorProof;
import org.maat.core.model.BatchRecord;
import org.maat.core.model.Manifest;
import org.maat.core.model.Receipt;
import org.maat.core.model.SessionRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The verdict engine — THE product (arch §4.5). Owner: Dev A.
 *
 * Independence discipline: this class may read ONLY publicDir + evidenceDir
 * and the pinned anchor adapter. It must never import anything from ma-app,
 * never read session packs, receipts/, or any operator-side state. That
 * restriction is what makes "an independent verifier detects it" true.
 *
 * Implemented in the skeleton: batch/session loading, conflicting-batch scan,
 * anchor-class gate, roster completeness both directions, manifest recompute,
 * page-byte recompute.
 * TODO(Dev A): receipt path (verifyReceipt), anchor cryptographic verification
 * wiring once Rfc3161Anchor.verify lands (until then anchored records with
 * class rfc3161-tsa are reported CURRENT with detail "anchor token not yet
 * cryptographically verified" — remove that carve-out before the demo).
 */
public final class Verifier {

    private final Store store = new Store(null); // read-only helpers only
    private final AnchorAdapter anchorAdapter;

    public Verifier(AnchorAdapter anchorAdapter) {
        this.anchorAdapter = anchorAdapter;
    }

    public VerificationReport verifyBatch(Path publicDir, Path evidenceDir, String sessionId) {
        List<VerificationReport.Finding> batch = new ArrayList<>();
        List<VerificationReport.Finding> docs = new ArrayList<>();

        Optional<SessionRecord> sessionOpt = store.readSession(publicDir, sessionId);
        Optional<BatchRecord> batchOpt = store.readBatch(publicDir, sessionId);
        if (sessionOpt.isEmpty() || batchOpt.isEmpty()) {
            batch.add(new VerificationReport.Finding("batch", Verdict.EVIDENCE_MISSING,
                    "missing " + (sessionOpt.isEmpty() ? "session" : "batch") + " record for " + sessionId));
            return new VerificationReport(sessionId, batch, docs);
        }
        SessionRecord session = sessionOpt.get();
        BatchRecord record = batchOpt.get();

        // Invariant 5: first anchor wins — any second record for this session id is a failure.
        findConflicts(publicDir, sessionId).forEach(conflict ->
                batch.add(new VerificationReport.Finding("batch", Verdict.CONFLICTING_BATCH,
                        "second batch record present: " + conflict.getFileName())));

        // Anchor gate (H-1): class recorded per record; fake/absent ⇒ UNANCHORED.
        checkAnchor(record.anchor(), record.unanchored(), "batch").ifPresent(batch::add);
        checkAnchor(session.anchor(), session.unanchored(), "session").ifPresent(batch::add);

        // Commitment must match the published manifest-hash list.
        String recomputed = Hashes.sha256(CanonicalJson.canonicalBytes(record.manifestHashes()));
        if (!recomputed.equals(record.commitment())) {
            batch.add(new VerificationReport.Finding("batch", Verdict.TAMPERED,
                    "batch commitment does not match published manifest-hash list"));
        }
        // Batch must be built against THIS pre-anchored session record (invariant 8).
        String sessionRef = CanonicalJson.hash(session.unanchored());
        if (!sessionRef.equals(record.sessionRef())) {
            batch.add(new VerificationReport.Finding("batch", Verdict.TAMPERED,
                    "batch session_ref does not match the pre-anchored session record"));
        }

        // Per-document checks + roster completeness (C-1), both directions.
        Set<String> rosterSeen = new HashSet<>();
        for (String manifestHash : record.manifestHashes()) {
            docs.add(checkDocument(evidenceDir, session, manifestHash, rosterSeen));
        }
        for (String commitment : session.rosterCommitments()) {
            if (!rosterSeen.contains(commitment)) {
                docs.add(new VerificationReport.Finding(commitment, Verdict.MISSING_FROM_ROSTER,
                        "pre-anchored roster entry has no document in the anchored batch (omission)"));
            }
        }
        return new VerificationReport(sessionId, batch, docs);
    }

    /**
     * Candidate receipt path (M-3). TODO(Dev A, day 2 PM):
     *   1. decode Receipt, locate batch for receipt.sessionId()
     *   2. receipt.manifestHash() ∈ batch.manifestHashes()  — else TAMPERED
     *      ("the document you watched being scanned is not in the anchored batch")
     *   3. candidate enters their candidate id locally; recompute
     *      RosterCommitment.commit(receipt.saltHex(), id) and check it is in the
     *      session roster AND equals the manifest's candidate_commitment
     *   4. then run the normal document check for that manifest
     * The candidate id is user input to the verifier and must never be written
     * anywhere (invariant 7).
     */
    public VerificationReport verifyReceipt(Path publicDir, Path evidenceDir, Receipt receipt, String candidateId) {
        throw new UnsupportedOperationException(
                "TODO(Dev A): receipt verification — see method javadoc; RosterCommitment.commit is the opener: "
                        + RosterCommitment.class.getName());
    }

    private VerificationReport.Finding checkDocument(Path evidenceDir, SessionRecord session,
                                                     String manifestHash, Set<String> rosterSeen) {
        Optional<Manifest> manifestOpt = store.readManifest(evidenceDir, manifestHash);
        if (manifestOpt.isEmpty()) {
            return new VerificationReport.Finding(manifestHash, Verdict.EVIDENCE_MISSING,
                    "manifest listed in anchored batch but absent from evidence store");
        }
        Manifest manifest = manifestOpt.get();

        // Substituted/altered manifest: its JCS hash must equal the anchored list entry.
        if (!CanonicalJson.hash(manifest).equals(manifestHash)) {
            return new VerificationReport.Finding(manifestHash, Verdict.TAMPERED,
                    "manifest bytes do not hash to the anchored manifest hash (substitution or alteration)");
        }
        if (!session.rosterCommitments().contains(manifest.candidateCommitment())) {
            return new VerificationReport.Finding(manifestHash, Verdict.NOT_IN_ROSTER,
                    "candidate commitment not present in the pre-anchored roster");
        }
        rosterSeen.add(manifest.candidateCommitment());

        // Modified page: recompute every page hash from stored bytes, in order.
        for (String pageHash : manifest.pageHashes()) {
            Optional<byte[]> page = store.readPage(evidenceDir, pageHash);
            if (page.isEmpty()) {
                return new VerificationReport.Finding(manifestHash, Verdict.EVIDENCE_MISSING,
                        "page " + pageHash + " absent from evidence store");
            }
            if (!Hashes.sha256(page.get()).equals(pageHash)) {
                return new VerificationReport.Finding(manifestHash, Verdict.TAMPERED,
                        "page bytes do not match anchored page hash " + pageHash);
            }
        }
        return new VerificationReport.Finding(manifestHash, Verdict.CURRENT, "all checks passed");
    }

    private Optional<VerificationReport.Finding> checkAnchor(AnchorProof proof, Object anchoredPayload, String subject) {
        if (proof == null || AnchorProof.CLASS_FAKE.equals(proof.anchorClass())) {
            return Optional.of(new VerificationReport.Finding(subject, Verdict.UNANCHORED,
                    subject + " record has " + (proof == null ? "no anchor" : "a demo-only fake anchor")));
        }
        String payloadHash = CanonicalJson.hash(anchoredPayload);
        if (!payloadHash.equals(proof.anchoredHash())) {
            return Optional.of(new VerificationReport.Finding(subject, Verdict.TAMPERED,
                    subject + " record does not hash to its own anchored_hash"));
        }
        try {
            if (!anchorAdapter.verify(proof, payloadHash)) {
                return Optional.of(new VerificationReport.Finding(subject, Verdict.UNANCHORED,
                        subject + " anchor token failed cryptographic verification"));
            }
        } catch (AnchorAdapter.AnchorException e) {
            // TODO(Dev A + Dev B): once Rfc3161Anchor.verify lands this branch must
            // become UNANCHORED. Until then we surface the gap honestly in the detail.
            return Optional.of(new VerificationReport.Finding(subject, Verdict.CURRENT,
                    subject + " anchor token not yet cryptographically verified (adapter TODO): " + e.getMessage()));
        }
        return Optional.empty();
    }

    private List<Path> findConflicts(Path publicDir, String sessionId) {
        try (var files = Files.list(publicDir)) {
            return files.filter(p -> p.getFileName().toString().startsWith("batch-" + sessionId + ".conflict"))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
