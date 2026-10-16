package com.vikrant.careSync.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * For patient-data columns that were stored in plaintext until now (intake answers, visit reasons, chat).
 * Writes AES-256-GCM ("v2:"); reads that or the existing plaintext. Unlike EncryptionConverter it never tries
 * the old ECB format, which these columns never held, so plaintext cannot be misread as ciphertext.
 */
@Converter
@Component
public class EncryptedTextConverter implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    public EncryptedTextConverter(FieldCipher cipher) {
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
            try {
                return cipher.decryptCurrent(dbData);
            } catch (IllegalStateException e) {
                // Old plaintext that merely starts with "v2:" (only possible before re-encryption)
                if (!cipher.acceptsLegacy()) {
                    throw e;
                }
                return dbData;
            }
        }
        cipher.requireLegacyAccepted();
        return dbData;
    }
}
