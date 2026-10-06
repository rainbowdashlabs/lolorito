/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import de.chojo.lolorito.LoloritoModule;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for opaque strings we hand out to Discord (access + refresh
 * tokens). The key comes from {@code http.tokenEncryptionKey} — 32 raw
 * bytes base64-encoded. Empty / blank input is a caller error;
 * {@link LoloritoModule} is responsible for auto-generating and
 * persisting a fresh key when config is missing one, so this class can
 * stay pure and strict about validation.
 */
public final class TokenCipher {
    static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("http.tokenEncryptionKey is required");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("http.tokenEncryptionKey is not valid base64", e);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "http.tokenEncryptionKey must decode to exactly 32 bytes, got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Generate a fresh base64-encoded 32-byte AES key. Used to seed a first-boot config. */
    public static String generateBase64Key() {
        byte[] raw = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    public Sealed encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Sealed(ct, iv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv) {
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    public record Sealed(byte[] ciphertext, byte[] iv) {}
}
