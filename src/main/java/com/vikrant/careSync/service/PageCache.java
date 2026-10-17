package com.vikrant.careSync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;
import com.vikrant.careSync.util.ErrorLog;

/**
 * Read-through cache for list pages and counts: return the cached value, else load, store and return it.
 * Cache errors (Redis down) fall back to the loader. Replaces three hand-written copies that also prefetched
 * neighbouring pages on the request thread (each prefetch was a full extra query per request).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PageCache {

    private final CacheManager cacheManager;

    @SuppressWarnings("unchecked")
    public <T> List<T> list(String cacheName, String key, Supplier<List<T>> loader) {
        Object cached = read(cacheName, key);
        if (cached instanceof List<?> list) {
            return (List<T>) list;
        }
        List<T> loaded = loader.get();
        write(cacheName, key, loaded);
        return loaded;
    }

    public long count(String cacheName, String key, Supplier<Long> loader) {
        Object cached = read(cacheName, key);
        if (cached instanceof Long count) {
            return count;
        }
        long loaded = loader.get();
        write(cacheName, key, loaded);
        return loaded;
    }

    private Object read(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            Cache.ValueWrapper wrapper = cache != null ? cache.get(key) : null;
            return wrapper != null ? wrapper.get() : null;
        } catch (Exception e) {
            ErrorLog.warn(e, "Cache read failed for [{}] {}. Using the database.", cacheName, key);
            return null;
        }
    }

    private void write(String cacheName, String key, Object value) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
            }
        } catch (Exception e) {
            ErrorLog.warn(e, "Cache write failed for [{}] {}", cacheName, key);
        }
    }
}
