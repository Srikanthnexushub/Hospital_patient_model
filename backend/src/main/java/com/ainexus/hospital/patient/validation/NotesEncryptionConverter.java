package com.ainexus.hospital.patient.validation;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that encrypts/decrypts clinical note fields using AES-256-GCM.
 *
 * Storage format: Base64(IV || ciphertext)
 * IV: 12 random bytes per encryption (GCM recommended size)
 * Tag length: 128 bits (GCM authentication tag)
 *
 * HIPAA: plaintext never logged — converter operates silently.
 * Null passthrough: null input → null output (no spurious empty ciphertext stored).
 */
@Converter
@Component
public class NotesEncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public NotesEncryptionConverter(SecretKeySpec notesEncryptionKey) {
        this.secretKey = notesEncryptionKey;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] ivAndCiphertext = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, ivAndCiphertext, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt clinical note field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_LENGTH_BYTES);

            int ciphertextLength = ivAndCiphertext.length - IV_LENGTH_BYTES;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(ivAndCiphertext, IV_LENGTH_BYTES, ciphertext, 0, ciphertextLength);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt clinical note field", e);
        }
    }
}
