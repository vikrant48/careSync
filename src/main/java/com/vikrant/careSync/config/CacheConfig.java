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

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(config)
                                .withCacheConfiguration("analytics", config.entryTtl(Duration.ofMinutes(15)))
                                .withCacheConfiguration("patientData", config.entryTtl(Duration.ofMinutes(5)))
                                .withCacheConfiguration("doctorListing", config.entryTtl(Duration.ofHours(1)))
                                .build();
        }

        @Override
        @Nullable
        public CacheErrorHandler errorHandler() {
                return new SimpleCacheErrorHandler() {
                        @Override
                        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                                log.error("Cache get error: {}", exception.getMessage());
                        }

                        @Override
                        public void handleCachePutError(RuntimeException exception, Cache cache, Object key,
                                        @Nullable Object value) {
                                log.error("Cache put error: {}", exception.getMessage());
                        }

                        @Override
                        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                                log.error("Cache evict error: {}", exception.getMessage());
                        }

                        @Override
                        public void handleCacheClearError(RuntimeException exception, Cache cache) {
                                log.error("Cache clear error: {}", exception.getMessage());
                        }
                };
        }
}
