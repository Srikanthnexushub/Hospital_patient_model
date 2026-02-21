package com.ainexus.hospital.patient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class NotesEncryptionConfig {

    @Value("${app.notes.encryption-key}")
    private String base64Key;

    @Bean
    public SecretKeySpec notesEncryptionKey() {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "APP_NOTES_ENCRYPTION_KEY must decode to exactly 32 bytes (256-bit AES key); got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
