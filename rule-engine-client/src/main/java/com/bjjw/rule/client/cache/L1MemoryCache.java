package com.bjjw.rule.client.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class L1MemoryCache {

    private static final Logger log = LoggerFactory.getLogger(L1MemoryCache.class);
    private final ConcurrentHashMap<String, CachedRule> cache;
    private final int maxSize;

    public L1MemoryCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>(maxSize);
    }

    public CachedRule get(String ruleCode) {
        return cache.get(ruleCode);
    }

    public void put(CachedRule rule) {
        if (cache.size() >= maxSize && !cache.containsKey(rule.getRuleCode())) {
            String toEvict = cache.keySet().iterator().next();
            cache.remove(toEvict);
            log.debug("L1 cache evicted: {}", toEvict);
        }
        cache.put(rule.getRuleCode(), rule);
    }

    public void remove(String ruleCode) {
        cache.remove(ruleCode);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public Map<String, Integer> getVersions() {
        Map<String, Integer> versions = new LinkedHashMap<>();
        cache.forEach((k, v) -> versions.put(k, v.getVersion()));
        return versions;
    }
}
