package org.maat.app;

import com.fasterxml.jackson.core.type.TypeReference;
import org.maat.core.*;
import org.maat.core.anchor.AnchorAdapter;
import org.maat.core.model.BatchRecord;
import org.maat.core.model.Manifest;
import org.maat.core.model.Receipt;
import org.maat.core.model.SessionRecord;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scanner Gateway (arch §4.2, trivial form): the "scanner" is a browser file
 * upload — one uploaded image = one single-page document for one candidate,
 * matched to the session pack in upload order. Multi-page documents and real
 * scanner ingestion are future work (capstone_scope §3.2).
 *
 * Flow per demo script: /gateway/ingest builds manifests + receipts + the
 * unanchored batch; /gateway/anchor anchors it. Processing/grading would only
 * ever start AFTER anchor confirmation (invariant 2) — say so in the demo.
 */
@RestController
public class GatewayController {

    private final Store store;
    private final AnchorAdapter anchor;

    public GatewayController(Store store, AnchorAdapter anchor) {
        this.store = store;
        this.anchor = anchor;
    }

    @PostMapping("/gateway/ingest")
    public Map<String, Object> ingest(@RequestParam("sessionId") String sessionId,
                                      @RequestParam("scans") List<MultipartFile> scans) throws Exception {
        SessionRecord session = store.readSession(store.publicDir(), sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "no pre-anchored session record for " + sessionId + " — registry runs FIRST (invariant 8)"));
        Map<String, String> salts = readSessionPack(sessionId);
        if (scans.size() > salts.size()) {
            throw new IllegalArgumentException("more scans than roster entries");
        }

        List<Manifest> manifests = new ArrayList<>();
        List<String> receipts = new ArrayList<>();
        var candidateIds = salts.keySet().stream().toList();
        for (int i = 0; i < scans.size(); i++) {
            byte[] bytes = scans.get(i).getBytes();          // the FINAL bytes — hashed as-is (invariant 3)
            String candidateId = candidateIds.get(i);
            String salt = salts.get(candidateId);
            String commitment = RosterCommitment.commit(salt, candidateId);

            Manifest m = ManifestBuilder.build(sessionId, commitment, List.of(bytes), Instant.now());
            store.writePage(bytes, extOf(scans.get(i).getOriginalFilename()));
            store.writeManifest(m);
            Receipt receipt = new Receipt(CanonicalJson.hash(m), sessionId, salt);
            ReceiptWriter.writePng(receipt, store.receiptsDir());
            receipts.add(receipt.encode());
            manifests.add(m);
        }

        BatchRecord batch = BatchBuilder.build(session, manifests, Instant.now());
        store.writeBatch(batch);   // unanchored yet — /gateway/anchor is the next step
        return Map.of(
                "session_id", sessionId,
                "documents", manifests.size(),
                "batch_record", "public/batch-" + sessionId + ".json (UNANCHORED — call /gateway/anchor)",
                "receipts", receipts);
    }

    /**
     * TODO(Dev B, day 2 AM): the batch was written unanchored by /ingest and the
     * store is append-only, so this endpoint must (1) read the batch, (2) anchor
     * CanonicalJson.hash(batch.unanchored()), (3) write the anchored record as
     * the single authoritative batch file. Options: relax Store for exactly this
     * one anchor-fill transition (recommended: Store.writeAnchoredBatch that
     * requires existing file to be the identical unanchored payload), or hold
     * the unanchored batch out of public/ until anchored. Decide with Dev A —
     * whichever you pick, tamper4 (conflicting batch) semantics must survive.
     */
    @PostMapping("/gateway/anchor")
    public Map<String, Object> anchorBatch(@RequestParam("sessionId") String sessionId) {
        throw new UnsupportedOperationException("TODO(Dev B, day 2 AM) — see method javadoc");
    }

    private Map<String, String> readSessionPack(String sessionId) throws Exception {
        var file = store.root().resolve("session-pack-" + sessionId + ".json");
        var node = CanonicalJson.mapper().readTree(file.toFile());
        return CanonicalJson.mapper().convertValue(node.get("salts"), new TypeReference<>() {});
    }

    private static String extOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "bin";
    }
}
