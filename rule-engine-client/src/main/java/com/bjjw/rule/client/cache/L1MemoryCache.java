package com.bjjw.rule.client.cache;

import com.bjjw.rule.model.constant.RuleCompIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 L1 规则缓存；键为 ruleCode + 作用域 compId，支持同进程按不同 compId 命中不同快照。
 */
public class L1MemoryCache {

    private static final Logger log = LoggerFactory.getLogger(L1MemoryCache.class);
    private final ConcurrentHashMap<String, CachedRule> cache;
    /** 已发布规则集 L1，键格式见 {@link RuleL1SetKeys} */
    private final ConcurrentHashMap<String, CachedRuleSet> setCache;
    private final int maxSize;
    private final String projectCode;

    public L1MemoryCache(int maxSize, String projectCode) {
        this.maxSize = maxSize;
        this.projectCode = projectCode;
        this.cache = new ConcurrentHashMap<>(maxSize);
        this.setCache = new ConcurrentHashMap<>(Math.max(16, maxSize / 4));
    }

    /**
     * 按规则编码与解析作用域读取缓存
     */
    public CachedRule get(String ruleCode, String scopeCompId) {
        return cache.get(RuleL1CacheKeys.key(projectCode, ruleCode, scopeCompId));
    }

    /**
     * 写入缓存；复合键由 ruleCode 与 scopeCompId 推导
     */
    public void put(String ruleCode, String scopeCompId, CachedRule rule) {
        String compoundKey = RuleL1CacheKeys.key(projectCode, ruleCode, RuleCompIds.normalize(scopeCompId));
        if (cache.size() >= maxSize && !cache.containsKey(compoundKey)) {
            String toEvict = cache.keySet().iterator().next();
            cache.remove(toEvict);
            log.debug("L1 cache evicted: {}", toEvict);
        }
        cache.put(compoundKey, rule);
    }

    /**
     * 删除某规则在所有作用域下的 L1 条目（规则发布/下线推送时使用）
     */
    public void removeAllForRule(String ruleCode) {
        if (ruleCode == null) {
            return;
        }
        for (Iterator<String> it = cache.keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            if (RuleL1CacheKeys.isKeyForRule(k, projectCode, ruleCode)) {
                it.remove();
            }
        }
    }

    /**
     * 读取规则集 L1
     */
    public CachedRuleSet getSet(String setCode, String scopeCompId) {
        return setCache.get(RuleL1SetKeys.key(projectCode, setCode, scopeCompId));
    }

    /**
     * 写入规则集 L1
     */
    public void putSet(String setCode, String scopeCompId, CachedRuleSet ruleSet) {
        String compoundKey = RuleL1SetKeys.key(projectCode, setCode, RuleCompIds.normalize(scopeCompId));
        if (setCache.size() >= maxSize && !setCache.containsKey(compoundKey)) {
            String toEvict = setCache.keySet().iterator().next();
            setCache.remove(toEvict);
            log.debug("L1 set cache evicted: {}", toEvict);
        }
        setCache.put(compoundKey, ruleSet);
    }

    /**
     * 规则集发布/下线推送时按 setCode 失效全部作用域条目
     */
    public void removeAllForSet(String setCode) {
        if (setCode == null) {
            return;
        }
        for (Iterator<String> it = setCache.keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            if (RuleL1SetKeys.isKeyForSet(k, projectCode, setCode)) {
                it.remove();
            }
        }
    }

    public void clear() {
        cache.clear();
        setCache.clear();
    }

    public int size() {
        return cache.size();
    }

    public Map<String, Integer> getVersions() {
        Map<String, Integer> versions = new LinkedHashMap<>();
        cache.forEach((k, v) -> versions.put(k, v.getVersion()));
        return versions;
    }

    /**
     * 返回当前 L1 缓存中所有规则的快照集合，用于启动预热等场景。
     */
    public java.util.Collection<CachedRule> getAllRules() {
        return cache.values();
    }
}
