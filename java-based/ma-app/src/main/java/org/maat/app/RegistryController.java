package org.maat.app;

import org.maat.core.CanonicalJson;
import org.maat.core.RosterCommitment;
import org.maat.core.Store;
import org.maat.core.anchor.AnchorAdapter;
import org.maat.core.model.SessionRecord;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registration-authority role (arch §4.1, trivial form). In the demo this runs
 * BEFORE "exam day" (invariant 8) — and in the report, stress it would be run
 * by an organizationally separate party, not the operator.
 *
 * Input: roster CSV, one candidate id per line (first column if commas).
 * Output: anchored session record in public/ + session pack (id → salt) for
 * the operator. The session pack never leaves the operator side (invariant 7).
 */
@RestController
public class RegistryController {

    private final Store store;
    private final AnchorAdapter anchor;

    public RegistryController(Store store, AnchorAdapter anchor) {
        this.store = store;
        this.anchor = anchor;
    }

    @PostMapping("/registry/commit")
    public Map<String, Object> commit(@RequestParam("roster") MultipartFile rosterCsv,
                                      @RequestParam("sessionId") String sessionId,
                                      @RequestParam("windowStart") String windowStart,
                                      @RequestParam("windowEnd") String windowEnd) throws Exception {
        Map<String, String> saltByCandidate = new LinkedHashMap<>();
        for (String line : new String(rosterCsv.getBytes(), StandardCharsets.UTF_8).split("\\R")) {
            String id = line.split(",")[0].strip();
            if (!id.isEmpty() && !id.equalsIgnoreCase("candidate_id")) {
                saltByCandidate.put(id, RosterCommitment.newSaltHex());
            }
        }
        var commitments = saltByCandidate.entrySet().stream()
                .map(e -> RosterCommitment.commit(e.getValue(), e.getKey()))
                .sorted()
                .toList();

        SessionRecord unanchored = new SessionRecord(SessionRecord.SCHEMA, sessionId,
                windowStart, windowEnd, commitments, null);
        SessionRecord anchored;
        try {
            anchored = unanchored.withAnchor(anchor.anchor(CanonicalJson.hash(unanchored)));
        } catch (AnchorAdapter.AnchorException e) {
            throw new IllegalStateException("anchoring failed — the session is NOT committed (invariant 2): "
                    + e.getMessage(), e);
        }
        store.writeSession(anchored);
        writeSessionPack(sessionId, saltByCandidate);

        return Map.of(
                "session_id", sessionId,
                "roster_size", commitments.size(),
                "session_record", "public/session-" + sessionId + ".json",
                "session_pack", "session-pack-" + sessionId + ".json (RA → operator hand-over, NOT public)",
                "anchor_class", anchored.anchor().anchorClass());
    }

    private void writeSessionPack(String sessionId, Map<String, String> saltByCandidate) throws IOException {
        var pack = Map.of("session_id", sessionId, "salts", saltByCandidate);
        var file = store.root().resolve("session-pack-" + sessionId + ".json");
        Files.createDirectories(store.root());
        if (Files.exists(file)) {
            throw new IllegalStateException("append-only: session pack already exists for " + sessionId);
        }
        Files.write(file, CanonicalJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(pack));
    }
}
