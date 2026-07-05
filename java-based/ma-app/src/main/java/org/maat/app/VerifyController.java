package org.maat.app;

import org.maat.core.Store;
import org.maat.core.anchor.AnchorAdapter;
import org.maat.core.model.Receipt;
import org.maat.core.verify.VerificationReport;
import org.maat.core.verify.Verifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verify page — a convenience frontend over the SAME verdict engine that
 * ma-verify (the standalone CLI) uses. The engine reads only public/ +
 * evidence/; for the independence story in the demo, run ma-verify from a
 * different machine/copy of those directories and show identical verdicts.
 */
@RestController
public class VerifyController {

    private final Store store;
    private final Verifier verifier;

    public VerifyController(Store store, AnchorAdapter anchorAdapter) {
        this.store = store;
        this.verifier = new Verifier(anchorAdapter);
    }

    @PostMapping("/verify/batch")
    public VerificationReport verifyBatch(@RequestParam("sessionId") String sessionId) {
        return verifier.verifyBatch(store.publicDir(), store.root().resolve("evidence"), sessionId);
    }

    /** Candidate id is used in-memory only and never logged (invariant 7). */
    @PostMapping("/verify/receipt")
    public VerificationReport verifyReceipt(@RequestParam("receipt") String receiptPayload,
                                            @RequestParam("candidateId") String candidateId) {
        Receipt receipt = Receipt.decode(receiptPayload);
        return verifier.verifyReceipt(store.publicDir(), store.root().resolve("evidence"), receipt, candidateId);
    }
}
