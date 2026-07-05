package org.maat.core.anchor;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.maat.core.Hashes;
import org.maat.core.model.AnchorProof;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dev B's day-1 definition of done: remove @Disabled, both tests pass against
 * a live public TSA. Needs network — keep @Disabled in CI afterwards and pin a
 * captured token in a follow-up offline test (day 1 PM).
 */
class Rfc3161AnchorLiveTest {

    static final String TSA_URL = "https://freetsa.org/tsr";

    @Disabled("TODO(Dev B, day 1): the sprint's first task — see Rfc3161Anchor javadoc")
    @Test
    void anchorRoundTrip() throws Exception {
        Rfc3161Anchor tsa = new Rfc3161Anchor(TSA_URL);
        String hash = Hashes.sha256("hello anchor".getBytes(StandardCharsets.UTF_8));
        AnchorProof proof = tsa.anchor(hash);
        assertEquals(AnchorProof.CLASS_RFC3161, proof.anchorClass());
        assertEquals(hash, proof.anchoredHash());
        assertNotNull(proof.tokenB64());
        assertTrue(tsa.verify(proof, hash));
    }

    @Disabled("TODO(Dev B, day 1): a token must not verify a DIFFERENT hash")
    @Test
    void tokenDoesNotVerifyOtherHash() throws Exception {
        Rfc3161Anchor tsa = new Rfc3161Anchor(TSA_URL);
        String hash = Hashes.sha256("hello anchor".getBytes(StandardCharsets.UTF_8));
        AnchorProof proof = tsa.anchor(hash);
        String other = Hashes.sha256("evil twin".getBytes(StandardCharsets.UTF_8));
        assertFalse(tsa.verify(proof, other));
    }
}
