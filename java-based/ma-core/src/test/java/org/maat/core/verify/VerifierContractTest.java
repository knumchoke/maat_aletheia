package org.maat.core.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.maat.core.*;
import org.maat.core.anchor.FakeAnchor;
import org.maat.core.model.BatchRecord;
import org.maat.core.model.Manifest;
import org.maat.core.model.Receipt;
import org.maat.core.model.SessionRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THE integration contract (capstone_scope.md §5, compressed): each test is one
 * demo tamper scenario. A component is done when these pass. Do not weaken an
 * assertion to make a test pass — the assertions ARE the spec.
 *
 * Fixtures are built in a temp workdir from testdata-like inputs, then tampered
 * on disk exactly the way the live demo will tamper them.
 */
class VerifierContractTest {

    static final String SESSION = "EXAM-2026-DEMO-V1";

    @TempDir Path workdir;
    Store store;
    Verifier verifier = new Verifier(new FakeAnchor());
    List<String> salts = new ArrayList<>();
    List<Manifest> manifests = new ArrayList<>();
    SessionRecord session;
    List<byte[]> docs = List.of(
            "candidate one page bytes".getBytes(StandardCharsets.UTF_8),
            "candidate two page bytes".getBytes(StandardCharsets.UTF_8),
            "candidate three page bytes".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void buildHappyPathWorkdir() throws Exception {
        store = new Store(workdir);
        // Registration authority, before the session (invariant 8):
        List<String> commitments = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String salt = RosterCommitment.newSaltHex();
            salts.add(salt);
            commitments.add(RosterCommitment.commit(salt, "CAND-00" + (i + 1)));
        }
        session = new SessionRecord(SessionRecord.SCHEMA, SESSION, "2026-07-05T08:00:00Z",
                "2026-07-05T12:00:00Z", commitments.stream().sorted().toList(), null)
                .withAnchor(new FakeAnchor().anchor("sha-256:00"));
        store.writeSession(session);
        // Operator, exam day:
        for (int i = 0; i < 3; i++) {
            Manifest m = ManifestBuilder.build(SESSION, commitments.get(i),
                    List.of(docs.get(i)), Instant.parse("2026-07-05T09:0" + i + ":00Z"));
            manifests.add(m);
            store.writeManifest(m);
            store.writePage(docs.get(i), "png");
        }
        BatchRecord batch = BatchBuilder.build(session, manifests, Instant.parse("2026-07-05T09:10:00Z"));
        store.writeBatch(batch.withAnchor(new FakeAnchor().anchor(CanonicalJson.hash(batch))));
    }

    private VerificationReport verify() {
        return verifier.verifyBatch(store.publicDir(), workdir.resolve("evidence"), SESSION);
    }

    private long count(List<VerificationReport.Finding> findings, Verdict v) {
        return findings.stream().filter(f -> f.verdict() == v).count();
    }

    @Test
    void happyPath_allDocumentsCurrent_butFakeAnchorIsSurfaced() {
        VerificationReport r = verify();
        assertEquals(3, count(r.documentFindings(), Verdict.CURRENT));
        // Invariant 4/6: a fake anchor is not silently OK — it is UNANCHORED (batch + session).
        assertEquals(2, count(r.batchFindings(), Verdict.UNANCHORED));
    }

    @Test
    void tamper1_modifiedPage_isTampered() throws Exception {
        Path page = store.pagesDir().resolve(
                Store.hexOf(manifests.get(0).pageHashes().get(0)) + ".png");
        Files.write(page, "doctored bytes".getBytes(StandardCharsets.UTF_8));
        VerificationReport r = verify();
        assertEquals(1, count(r.documentFindings(), Verdict.TAMPERED));
        assertEquals(2, count(r.documentFindings(), Verdict.CURRENT));
    }

    @Test
    void tamper2_omittedDocument_isMissingFromRoster() throws Exception {
        // Rebuild the batch from only 2 of 3 manifests, in a fresh workdir (append-only store).
        Path w2 = Files.createTempDirectory("ma-omit");
        Store s2 = new Store(w2);
        s2.writeSession(session);
        manifests.subList(0, 2).forEach(s2::writeManifest);
        s2.writePage(docs.get(0), "png");
        s2.writePage(docs.get(1), "png");
        BatchRecord partial = BatchBuilder.build(session, manifests.subList(0, 2), Instant.now());
        s2.writeBatch(partial.withAnchor(new FakeAnchor().anchor(CanonicalJson.hash(partial))));

        VerificationReport r = verifier.verifyBatch(s2.publicDir(), w2.resolve("evidence"), SESSION);
        assertEquals(1, count(r.documentFindings(), Verdict.MISSING_FROM_ROSTER),
                "omission must be as detectable as modification (C-1)");
        assertEquals(2, count(r.documentFindings(), Verdict.CURRENT));
    }

    @Test
    void tamper3_substitutedManifest_isTampered() throws Exception {
        // Overwrite the stored manifest file with a doctored one under the SAME filename.
        Manifest original = manifests.get(0);
        Manifest doctored = new Manifest(original.schema(), original.sessionId(),
                original.candidateCommitment(),
                List.of(Hashes.sha256("someone else's page".getBytes(StandardCharsets.UTF_8))),
                1, original.scanTime());
        Path file = store.manifestsDir().resolve(Store.hexOf(CanonicalJson.hash(original)) + ".json");
        Files.write(file, CanonicalJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(doctored));
        VerificationReport r = verify();
        assertEquals(1, count(r.documentFindings(), Verdict.TAMPERED));
    }

    @Test
    void tamper4_secondBatchForSameSession_isConflictingBatch() throws Exception {
        BatchRecord second = BatchBuilder.build(session, manifests.subList(0, 1), Instant.now());
        Files.write(store.publicDir().resolve("batch-" + SESSION + ".conflict-1.json"),
                CanonicalJson.mapper().writeValueAsBytes(second));
        VerificationReport r = verify();
        assertEquals(1, count(r.batchFindings(), Verdict.CONFLICTING_BATCH),
                "first anchor wins (invariant 5)");
    }

    @Disabled("TODO(Dev A, day 2 PM): enable when Verifier.verifyReceipt is implemented")
    @Test
    void receiptPath_confirmsInclusionAndOpensRosterCommitment() {
        Receipt receipt = new Receipt(CanonicalJson.hash(manifests.get(0)), SESSION, salts.get(0));
        VerificationReport r = verifier.verifyReceipt(
                store.publicDir(), workdir.resolve("evidence"), receipt, "CAND-001");
        assertEquals(1, count(r.documentFindings(), Verdict.CURRENT));
        // And a receipt for a manifest NOT in the batch must come back TAMPERED:
        Receipt forged = new Receipt(Hashes.sha256("never scanned".getBytes(StandardCharsets.UTF_8)),
                SESSION, salts.get(0));
        VerificationReport r2 = verifier.verifyReceipt(
                store.publicDir(), workdir.resolve("evidence"), forged, "CAND-001");
        assertEquals(1, count(r2.documentFindings(), Verdict.TAMPERED));
    }
}
