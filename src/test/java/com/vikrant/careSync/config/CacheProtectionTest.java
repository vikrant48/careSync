package com.vikrant.careSync.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikrant.careSync.dto.DoctorDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Cached values are encrypted and authenticated, and only allow-listed types can be read back. */
class CacheProtectionTest {

    private EncryptingRedisSerializer serializer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(CacheConfig.CACHE_TYPES, ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        serializer = new EncryptingRedisSerializer(new GenericJackson2JsonRedisSerializer(mapper), "test-key");
    }

    @Test
    void valuesRoundTripAndAreUnreadableInRedis() {
        DoctorDto doctor = DoctorDto.builder().username("doctorX").email("x@test.local").build();
        byte[] stored = serializer.serialize(new java.util.ArrayList<>(List.of(doctor)));

        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("x@test.local"));
        Object back = serializer.deserialize(stored);
        assertEquals("x@test.local", ((DoctorDto) ((List<?>) back).get(0)).getEmail());
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        assertFalse(java.util.Arrays.equals(serializer.serialize(Map.of("a", "b")), serializer.serialize(Map.of("a", "b"))));
    }

    @Test
    void modifiedOrForeignValuesReadAsCacheMiss() {
        byte[] stored = serializer.serialize(Map.of("illness", "asthma"));
        stored[stored.length - 1] ^= 1;
        assertNull(serializer.deserialize(stored), "tampered value");

        // An old plaintext entry or one written by someone without the key
        assertNull(serializer.deserialize("{\"@class\":\"java.util.HashMap\"}".getBytes(StandardCharsets.UTF_8)));

        EncryptingRedisSerializer otherKey = new EncryptingRedisSerializer(
                new GenericJackson2JsonRedisSerializer(new ObjectMapper()), "another-key");
        assertNull(otherKey.deserialize(serializer.serialize(Map.of("a", "b"))));
    }

    @Test
    void typeOutsideTheAllowListIsRejected() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(CacheConfig.CACHE_TYPES, ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        String json = "[\"javax.swing.JLabel\",{}]";
        assertThrows(Exception.class, () -> mapper.readValue(json, Object.class));
    }
}
