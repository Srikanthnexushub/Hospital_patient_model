package com.ainexus.hospital.patient.unit.validation;

import com.ainexus.hospital.patient.validation.NotesEncryptionConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NotesEncryptionConverter.
 *
 * Uses a 32-byte all-zero AES-256 key for deterministic test setup.
 * Real AES-256-GCM uses a random 12-byte IV per encryption, so the same
 * plaintext produces different ciphertext on every call — tested in test 4.
 */
class NotesEncryptionConverterTest {

    // 32 zero-bytes → valid 256-bit AES key (test only; never use zero-key in production)
    private static final SecretKeySpec TEST_KEY = new SecretKeySpec(new byte[32], "AES");

    private NotesEncryptionConverter converter;

    @BeforeEach
    void setUp() {
        converter = new NotesEncryptionConverter(TEST_KEY);
    }

    // ── Test 1: encrypt then decrypt returns original plaintext ──────────────

    @Test
    void encryptDecrypt_roundtrip_returnsOriginalPlaintext() {
        String original = "Hello, HIPAA-compliant world!";

        String encrypted = converter.convertToDatabaseColumn(original);

        // Encrypted value must differ from plaintext
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(original);

        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    // ── Test 2: encrypting null returns null (no spurious empty ciphertext) ──

    @Test
    void encryptNull_returnsNull() {
        String result = converter.convertToDatabaseColumn(null);
        assertThat(result).isNull();
    }

    // ── Test 3: decrypting null returns null ──────────────────────────────────

    @Test
    void decryptNull_returnsNull() {
        String result = converter.convertToEntityAttribute(null);
        assertThat(result).isNull();
    }

    // ── Test 4: same plaintext encrypted twice produces different ciphertext ──
    //
    // AES-GCM uses a random 12-byte IV on every call, so ciphertext is
    // non-deterministic. This property is required for semantic security.

    @Test
    void sameInput_encryptedTwice_producesDifferentCiphertext() {
        String plaintext = "Sensitive clinical note";

        String firstEncryption  = converter.convertToDatabaseColumn(plaintext);
        String secondEncryption = converter.convertToDatabaseColumn(plaintext);

        assertThat(firstEncryption).isNotEqualTo(secondEncryption);

        // Both must still decrypt correctly
        assertThat(converter.convertToEntityAttribute(firstEncryption)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(secondEncryption)).isEqualTo(plaintext);
    }

    // ── Bonus: empty string round-trips correctly ─────────────────────────────

    @Test
    void encryptDecrypt_emptyString_roundtripsCorrectly() {
        String original = "";

        String encrypted = converter.convertToDatabaseColumn(original);
        assertThat(encrypted).isNotNull();

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    // ── Bonus: unicode / multi-byte characters round-trip correctly ───────────

    @Test
    void encryptDecrypt_unicodeContent_roundtripsCorrectly() {
        String original = "Patient notes: 患者情報 — café résumé ñoño";

        String encrypted = converter.convertToDatabaseColumn(original);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    // ── Bonus: long text round-trips correctly ────────────────────────────────

    @Test
    void encryptDecrypt_longClinicalNote_roundtripsCorrectly() {
        String longNote = "A".repeat(5000);

        String encrypted = converter.convertToDatabaseColumn(longNote);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(longNote);
    }
}
