package com.vikrant.careSync.security;

import com.vikrant.careSync.security.crypto.FieldCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encrypts columns that have always been encrypted. Writes AES-256-GCM ("v2:"); reads that, the old AES/ECB
 * format, and plaintext left by direct SQL seeding, until FieldReencryptionService has rewritten every row.
 */
@Converter
@Component
@Slf4j
public class EncryptionConverter implements AttributeConverter<String, String> {

    private static final AtomicBoolean PLAINTEXT_REPORTED = new AtomicBoolean();

    private final FieldCipher cipher;

    public EncryptionConverter(FieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (cipher.isCurrent(dbData)) {
            return cipher.decryptCurrent(dbData);
        }
        cipher.requireLegacyAccepted();
        return cipher.decryptLegacyEcb(dbData).orElseGet(() -> {
            // Inserted without encryption (e.g. direct SQL seeding); the re-encryption job fixes it
            if (PLAINTEXT_REPORTED.compareAndSet(false, true)) {
                log.warn("Unencrypted value found in an encrypted column; run the field re-encryption job");
            }
            return dbData;
        });
    }
}
