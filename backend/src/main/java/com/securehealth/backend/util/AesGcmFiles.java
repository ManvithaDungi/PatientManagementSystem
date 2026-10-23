package com.securehealth.backend.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM file encryption using the same key and layout convention as stored uploads:
 * {@code [12-byte IV][ciphertext + 128-bit tag]}. Used to encrypt database backups at rest.
 */
public final class AesGcmFiles {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private AesGcmFiles() {
    }

    public static SecretKey keyFromBase64(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public static void encrypt(Path plain, Path encrypted, SecretKey key) throws IOException, GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        try (InputStream in = Files.newInputStream(plain);
             OutputStream raw = Files.newOutputStream(encrypted)) {
            raw.write(iv);
            try (CipherOutputStream out = new CipherOutputStream(raw, cipher)) {
                in.transferTo(out);
            }
        }
    }

    /** Decrypts a file produced by {@link #encrypt}; fails (IOException) if the data was tampered with. */
    public static void decrypt(Path encrypted, Path plain, SecretKey key) throws IOException, GeneralSecurityException {
        try (InputStream raw = Files.newInputStream(encrypted)) {
            byte[] iv = raw.readNBytes(IV_LENGTH);
            if (iv.length != IV_LENGTH) {
                throw new IOException("Encrypted file is too short");
            }
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            try (CipherInputStream in = new CipherInputStream(raw, cipher);
                 OutputStream out = Files.newOutputStream(plain)) {
                in.transferTo(out);
            }
        }
    }
}
