package com.vikrant.careSync.security.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import com.vikrant.careSync.util.ErrorLog;

/**
 * Encryption of patient-data columns.
 * <ul>
 * <li>Current format "v2:" + Base64(IV || ciphertext || tag): AES-256-GCM with a random IV per value, so equal
 * values look different and any change to the stored value is detected.</li>
 * <li>Legacy format: AES/ECB without a prefix (deterministic, no integrity). Still readable so existing rows
 * keep working until FieldReencryptionService rewrites them; never written any more.</li>
 * </ul>
 */
@Component
@Slf4j
public class FieldCipher {

    public static final String PREFIX = "v2:";

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec gcmKey;
    private final SecretKeySpec legacyEcbKey;
    private final boolean acceptLegacy;

    public FieldCipher(@Value("${app.security.encryption-key}") String secret,
            @Value("${app.security.field-encryption.accept-legacy:true}") boolean acceptLegacy) {
        this.acceptLegacy = acceptLegacy;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Encryption key is missing (ENCRYPTION_KEY)");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            // Same derivation as the old converter, so existing rows stay readable
            this.legacyEcbKey = new SecretKeySpec(sha.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
            // Separate key for the new format, derived with a purpose label
            this.gcmKey = new SecretKeySpec(
                    sha.digest(("caresync-db-fields-v2:" + secret).getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            ErrorLog.debug(e);
            throw new IllegalStateException("Cannot derive encryption keys", e);
        }
    }

    /**
     * While true, old ECB and plaintext values are still read. Set FIELD_ENCRYPTION_ACCEPT_LEGACY=false once the
     * re-encryption job reports nothing left, so any value not written by this app is refused.
     */
    public boolean acceptsLegacy() {
        return acceptLegacy;
    }

    public void requireLegacyAccepted() {
        if (!acceptLegacy) {
            throw new IllegalStateException("Unencrypted or legacy-encrypted value in an encrypted column");
        }
    }

    public boolean isCurrent(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, gcmKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(IV_LENGTH + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            ErrorLog.debug(e);
            throw new IllegalStateException("Error encrypting field", e);
        }
    }

    /**
     * Decrypts the current format. A value that fails its integrity check was changed outside the app or
     * written with another key: that is an error, never silently returned.
     */
    public String decryptCurrent(String stored) {
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, gcmKey, new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            return new String(cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            ErrorLog.debug(e);
            throw new IllegalStateException("Encrypted field failed its integrity check (modified or wrong key)", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            ErrorLog.debug(e);
            throw new IllegalStateException("Encrypted field is malformed", e);
        }
    }

    /** The old AES/ECB format, if the value really is one (it must decrypt to valid UTF-8 text). */
    public Optional<String> decryptLegacyEcb(String stored) {
        try {
            byte[] data = Base64.getDecoder().decode(stored);
            if (data.length == 0 || data.length % 16 != 0) {
                return Optional.empty();
            }
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, legacyEcbKey);
            byte[] plain = cipher.doFinal(data);
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(plain)).toString());
        } catch (GeneralSecurityException | IllegalArgumentException | CharacterCodingException e) {
            ErrorLog.debug(e);
            return Optional.empty();
        }
    }
}
