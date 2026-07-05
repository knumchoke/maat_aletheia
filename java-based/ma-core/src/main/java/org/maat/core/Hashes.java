package org.maat.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash-string convention from phase1_architecture.md §3.1: every hash in every
 * record is a string "alg:hex" (e.g. "sha-256:ab12..."), so records stay
 * algorithm-agile (finding L-1). Nothing in the codebase may carry a bare hex hash.
 */
public final class Hashes {

    public static final String SHA_256 = "sha-256";

    private Hashes() {}

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return SHA_256 + ":" + HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("JVM without SHA-256", e);
        }
    }

    /** Splits "alg:hex", rejecting anything not in the convention. */
    public static String algorithmOf(String hashString) {
        int i = indexOfSeparator(hashString);
        return hashString.substring(0, i);
    }

    public static byte[] rawBytes(String hashString) {
        int i = indexOfSeparator(hashString);
        return HexFormat.of().parseHex(hashString.substring(i + 1));
    }

    private static int indexOfSeparator(String hashString) {
        int i = hashString == null ? -1 : hashString.indexOf(':');
        if (i <= 0 || i == hashString.length() - 1) {
            throw new IllegalArgumentException("not an alg:hex hash string: " + hashString);
        }
        return i;
    }
}
