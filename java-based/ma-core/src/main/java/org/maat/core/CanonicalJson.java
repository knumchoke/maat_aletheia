package org.maat.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * RFC 8785 (JCS) canonicalization — phase1_architecture.md §3.1.
 * Invariant 3 ("hash the final bytes") for JSON records means: a record's hash
 * is ALWAYS computed over its JCS bytes, never over pretty-printed bytes.
 * Every record hash in the system goes through {@link #hash(Object)}.
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalJson() {}

    public static byte[] canonicalBytes(Object record) {
        try {
            String json = MAPPER.writeValueAsString(record);
            return new JsonCanonicalizer(json).getEncodedUTF8();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String canonicalString(Object record) {
        return new String(canonicalBytes(record), StandardCharsets.UTF_8);
    }

    /** The one true record-hash function: sha-256 over JCS bytes. */
    public static String hash(Object record) {
        return Hashes.sha256(canonicalBytes(record));
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
