package com.vikrant.careSync.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

        private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new Jdk8Module());
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                false);
                mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                JsonTypeInfo.As.PROPERTY);

                RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)))
                                .disableCachingNullValues();

                RedisCacheManager manager = RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(config)
                                // Patient caches (5 min TTL)
                                .withCacheConfiguration("PATIENT:PROFILE", config.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("PATIENT:HISTORY", config.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("PATIENT:APPOINTMENTS", config.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("PATIENT:DOCUMENTS", config.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("PATIENT:FINANCIAL", config.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("PATIENT:COMPLETE_DATA", config.entryTtl(Duration.ofMinutes(5)))
                                // Doctor caches (1 hour TTL)
                                .withCacheConfiguration("DOCTOR:PROFILE", config.entryTtl(Duration.ofHours(1)))
                                .withCacheConfiguration("DOCTOR:EXPERIENCE", config.entryTtl(Duration.ofHours(1)))
                                .withCacheConfiguration("DOCTOR:EDUCATION", config.entryTtl(Duration.ofHours(1)))
                                .withCacheConfiguration("DOCTOR:CERTIFICATES", config.entryTtl(Duration.ofHours(1)))
                                .withCacheConfiguration("DOCTOR:APPOINTMENTS", config.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("DOCTOR:DOCUMENTS", config.entryTtl(Duration.ofHours(1)))
                                // Analytics caches (15 min TTL)
                                .withCacheConfiguration("ANALYTICS:OVERALL", config.entryTtl(Duration.ofMinutes(15)))
                                .withCacheConfiguration("ANALYTICS:RATINGS", config.entryTtl(Duration.ofMinutes(15)))
                                .build();

                try {
                        connectionFactory.getConnection().ping();
                        log.info("[ServiceStatus] - Redis Cache     : CONNECTED ");
                } catch (Exception e) {
                        log.warn("[ServiceStatus] - Redis Cache     : DISCONNECTED ⚠ (caching disabled - {})",
                                        e.getMessage());
                }

                return manager;
        }

        @Override
        @Nullable
        public CacheErrorHandler errorHandler() {
                return new SimpleCacheErrorHandler() {
                        @Override
                        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                                log.warn("Redis Cache GET error for key [{}]: {}. Falling back to DB.", key,
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCachePutError(RuntimeException exception, Cache cache, Object key,
                                        @Nullable Object value) {
                                log.warn("Redis Cache PUT error for key [{}]: {}. Bypassing cache write.", key,
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                                log.warn("Redis Cache EVICT error for key [{}]: {}. Bypassing cache evict.", key,
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCacheClearError(RuntimeException exception, Cache cache) {
                                log.warn("Redis Cache CLEAR error: {}. Bypassing cache clear.", exception.getMessage());
                        }
                };
        }
}
