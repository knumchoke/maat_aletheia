package org.maat.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the two frozen conventions everything else builds on. Green from day 0. */
class CoreConventionsTest {

    @Test
    void hashStringConvention() {
        String h = Hashes.sha256("abc".getBytes(StandardCharsets.UTF_8));
        // Known SHA-256("abc") test vector, in alg:hex form.
        assertEquals("sha-256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h);
        assertEquals("sha-256", Hashes.algorithmOf(h));
        assertEquals(32, Hashes.rawBytes(h).length);
        assertThrows(IllegalArgumentException.class, () -> Hashes.algorithmOf("deadbeef"));
    }

    @Test
    void canonicalJsonSortsKeysAndStripsWhitespace() throws Exception {
        var node = CanonicalJson.mapper().readTree("{\"b\": 2,\n  \"a\": \"x\"}");
        assertEquals("{\"a\":\"x\",\"b\":2}", CanonicalJson.canonicalString(node));
    }

    @Test
    void rosterCommitmentIsDeterministicAndSaltDependent() {
        String salt = "00112233445566778899aabbccddeeff";
        assertEquals(RosterCommitment.commit(salt, "CAND-001"), RosterCommitment.commit(salt, "CAND-001"));
        assertNotEquals(RosterCommitment.commit(salt, "CAND-001"), RosterCommitment.commit(salt, "CAND-002"));
        assertNotEquals(RosterCommitment.commit(RosterCommitment.newSaltHex(), "CAND-001"),
                RosterCommitment.commit(RosterCommitment.newSaltHex(), "CAND-001"));
    }
}
