package org.maat.core;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Salted candidate commitment (proposal: Privacy by Design; arch §3.4).
 * Phase 1 capstone uses the salted-hash scheme (not HMAC — no independent key
 * custodian role exists yet; see architecture ambiguity A-3).
 *
 * CONTRACT (frozen — the registry, gateway, verifier and receipts all depend
 * on these exact bytes):  commitment = sha-256( salt_bytes || 0x00 || utf8(candidate_id) )
 * with salt = 16 random bytes, carried as lowercase hex.
 */
public final class RosterCommitment {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RosterCommitment() {}

    public static String newSaltHex() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public static String commit(String saltHex, String candidateId) {
        byte[] salt = HexFormat.of().parseHex(saltHex);
        byte[] id = candidateId.getBytes(StandardCharsets.UTF_8);
        byte[] preimage = new byte[salt.length + 1 + id.length];
        System.arraycopy(salt, 0, preimage, 0, salt.length);
        preimage[salt.length] = 0x00;
        System.arraycopy(id, 0, preimage, salt.length + 1, id.length);
        return Hashes.sha256(preimage);
    }
}
