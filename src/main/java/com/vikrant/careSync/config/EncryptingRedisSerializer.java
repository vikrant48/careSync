package com.vikrant.careSync.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Encrypts cache values (AES-256-GCM, random IV per value) before they reach Redis, so patient data is never
 * stored there in readable form and a value written by anyone without the key is rejected.
 * Values that cannot be decrypted (old plaintext entries, tampering) read as a cache miss.
 */
public class EncryptingRedisSerializer implements RedisSerializer<Object> {

    private static final Logger log = LoggerFactory.getLogger(EncryptingRedisSerializer.class);

    private static final byte[] HEADER = "csv1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisSerializer<Object> delegate;
    private final SecretKeySpec key;

    public EncryptingRedisSerializer(RedisSerializer<Object> delegate, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Cache encryption key is missing");
        }
        this.delegate = delegate;
        // Own key, derived from the app secret with a purpose label, so it differs from the column encryption key
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(("caresync-redis-cache:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive cache encryption key", e);
        }
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        byte[] plain = delegate.serialize(value);
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(HEADER);
            byte[] encrypted = cipher.doFinal(plain);

            byte[] out = new byte[HEADER.length + IV_LENGTH + encrypted.length];
            System.arraycopy(HEADER, 0, out, 0, HEADER.length);
            System.arraycopy(iv, 0, out, HEADER.length, IV_LENGTH);
            System.arraycopy(encrypted, 0, out, HEADER.length + IV_LENGTH, encrypted.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new SerializationException("Cannot encrypt cache value", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null) {
            return null;
        }
        if (bytes.length <= HEADER.length + IV_LENGTH
                || !Arrays.equals(Arrays.copyOfRange(bytes, 0, HEADER.length), HEADER)) {
            log.debug("Ignoring unencrypted cache value");
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, bytes, HEADER.length, IV_LENGTH));
            cipher.updateAAD(HEADER);
            byte[] plain = cipher.doFinal(bytes, HEADER.length + IV_LENGTH, bytes.length - HEADER.length - IV_LENGTH);
            return delegate.deserialize(plain);
        } catch (GeneralSecurityException e) {
            log.warn("Ignoring cache value that failed decryption (wrong key or modified in Redis)");
            return null;
        }
    }
}
