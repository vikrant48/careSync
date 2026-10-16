package com.vikrant.careSync.security.crypto;

import com.vikrant.careSync.security.EncryptionConverter;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** New values are AES-256-GCM; old ECB and plaintext values stay readable until re-encrypted. */
class FieldEncryptionTest {

    static final String KEY = "test-only-encryption-key-not-for-production";

    /** What the old converter wrote: AES/ECB with SHA-256(secret), Base64, no prefix. */
    static String legacyEcb(String plain) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(KEY.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private final FieldCipher cipher = new FieldCipher(KEY, true);

    @Test
    void newValuesUseGcmWithRandomIv() {
        String a = cipher.encrypt("Type 2 diabetes");
        String b = cipher.encrypt("Type 2 diabetes");
        assertTrue(a.startsWith("v2:"));
        assertNotEquals(a, b, "equal values must not look equal in the database");
        assertEquals("Type 2 diabetes", cipher.decryptCurrent(a));
    }

    @Test
    void modifiedCiphertextIsRejectedNotReturned() {
        String stored = cipher.encrypt("penicillin allergy");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v2:" + Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> cipher.decryptCurrent(tampered));
        assertThrows(IllegalStateException.class,
                () -> new FieldCipher("another-key", true).decryptCurrent(stored), "wrong key");
    }

    @Test
    void existingColumnsReadOldEcbAndPlaintextRows() throws Exception {
        EncryptionConverter converter = new EncryptionConverter(cipher);
        assertEquals("Asha Verma", converter.convertToEntityAttribute(legacyEcb("Asha Verma")));
        assertEquals("seeded plain text", converter.convertToEntityAttribute("seeded plain text"));
        assertTrue(converter.convertToDatabaseColumn("Asha").startsWith("v2:"), "writes are always the new format");
    }

    @Test
    void newlyEncryptedColumnsNeverMisreadPlaintext() throws Exception {
        EncryptedTextConverter converter = new EncryptedTextConverter(cipher);
        assertEquals("chest pain since morning", converter.convertToEntityAttribute("chest pain since morning"));
        assertEquals("v2: second opinion", converter.convertToEntityAttribute("v2: second opinion"));
        // An ECB-looking value in a column that never held ECB is plaintext, not something to decrypt
        String looksLikeEcb = legacyEcb("x");
        assertEquals(looksLikeEcb, converter.convertToEntityAttribute(looksLikeEcb));
    }

    @Test
    void strictModeRefusesAnythingNotWrittenByTheApp() throws Exception {
        FieldCipher strict = new FieldCipher(KEY, false);
        EncryptionConverter existing = new EncryptionConverter(strict);
        EncryptedTextConverter added = new EncryptedTextConverter(strict);

        assertThrows(IllegalStateException.class, () -> existing.convertToEntityAttribute(legacyEcb("Asha")));
        assertThrows(IllegalStateException.class, () -> existing.convertToEntityAttribute("plain"));
        assertThrows(IllegalStateException.class, () -> added.convertToEntityAttribute("plain"));
        assertEquals("ok", added.convertToEntityAttribute(strict.encrypt("ok")));
    }
}
