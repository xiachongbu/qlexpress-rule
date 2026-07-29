package com.bjjw.rule.client;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.client.cache.CachedRuleSet;
import com.bjjw.rule.client.cache.L1MemoryCache;
import com.bjjw.rule.client.function.ClientFunctionRegistrar;
import com.bjjw.rule.client.log.ExecutionLogReporter;
import com.bjjw.rule.client.log.HttpLogReporter;
import com.bjjw.rule.client.log.NoOpLogReporter;
import com.bjjw.rule.client.sync.HttpSyncClient;
import com.bjjw.rule.client.sync.RedisL2RuleCache;
import com.bjjw.rule.client.sync.RedisL2RuleSetCache;
import com.bjjw.rule.client.sync.RedisSubscriber;
import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.core.util.RuleSetHitPolicies;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 规则引擎客户端：无参 {@link #execute(String, Map)} 使用默认（通用）作用域；带 scopeCompId 的重载按作用域优先解析。
 * <p><b>与执行日志上报无关：</b>{@link HttpSyncClient} 始终使用 {@code server-url} 做规则/函数等 HTTP 拉取；
 * {@link ExecutionLogReporter} 只决定「执行结束后日志」走 Kafka 还是走日志 HTTP 接口，
 * 二者互不影响，不存在「改用 Kafka 发日志就不发同步 HTTP」的逻辑。</p>
 */
public class RuleEngineClient {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineClient.class);

    private final RuleEngineClientConfig config;
    private final L1MemoryCache l1Cache;
    private final RedisL2RuleCache redisL2RuleCache;
    private final RedisL2RuleSetCache redisL2RuleSetCache;
    /** 规则/函数同步等，始终请求 {@code server-url}，与日志是否走 Kafka 无关 */
    private final HttpSyncClient httpSyncClient;
    private final RedisSubscriber redisSubscriber;
    private final QLExpressEngine engine;
    /** 仅负责执行完成后的日志投递，不影响 {@link #httpSyncClient} */
    private final ExecutionLogReporter logReporter;
    private final ClientFunctionRegistrar functionRegistrar;
    private ScheduledExecutorService scheduler;

    private RuleEngineClient(RuleEngineClientConfig config, RedisConnectionFactory connectionFactory,
                             ExecutionLogReporter externalReporter, ApplicationContext applicationContext) {
        this.config = config;
        this.l1Cache = new L1MemoryCache(config.getL1CacheMaxSize(), config.getAppName());
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();
        this.redisL2RuleCache = new RedisL2RuleCache(stringRedisTemplate, config.isL2RedisCacheEnabled(), config.getAppName());
        this.redisL2RuleSetCache = new RedisL2RuleSetCache(stringRedisTemplate, config.isL2RedisCacheEnabled(), config.getAppName());
        this.httpSyncClient = new HttpSyncClient(config.getServerUrl(), config.getHttpTimeoutMs(), config.getToken(), config.getAppName());
        this.redisSubscriber = new RedisSubscriber(l1Cache, connectionFactory, config.getAppName());
        this.engine = new QLExpressEngine();
        this.functionRegistrar = new ClientFunctionRegistrar(engine, applicationContext);

        if (externalReporter != null) {
            this.logReporter = externalReporter;
        } else if (config.isLogReportEnabled()) {
            this.logReporter = new HttpLogReporter(config.getServerUrl(), config.getHttpTimeoutMs());
        } else {
            this.logReporter = new NoOpLogReporter();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start() {
        log.info("RuleEngineClient starting: serverUrl={}, appName={}, projectId={}, logReporter={}",
                config.getServerUrl(), config.getAppName(), config.getProjectId(), logReporter.getClass().getSimpleName());
        redisSubscriber.setFunctionRegistrar(functionRegistrar);
        redisSubscriber.start();
        fullSync();
        syncFunctions();
        warmUpScripts();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rule-client-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::fullSync,
                config.getHeartbeatIntervalMs(), config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
        log.info("RuleEngineClient started, {} rules cached", l1Cache.size());
    }

    public void close() {
        log.info("RuleEngineClient shutting down");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        redisSubscriber.stop();
    }

    /**
     * 按全国(通用)作用域执行规则。
     *
     * @param ruleCode   规则编码
     * @param params     入参
     * @param businessId 业务主键ID,用于关联具体业务记录
     */
    public RuleResult execute(String ruleCode, Map<String, Object> params, String businessId) {
        return doExecute(ruleCode, params, RuleCompIds.NATIONAL, businessId, true);
    }
    
    /**
     * 按全国(通用)作用域执行规则,可选择是否上报日志。
     *
     * @param ruleCode    规则编码
     * @param params      入参
     * @param businessId  业务主键ID,用于关联具体业务记录
     * @param reportLog   是否执行日志上报
     */
    public RuleResult execute(String ruleCode, Map<String, Object> params, String businessId, boolean reportLog) {
        return doExecute(ruleCode, params, RuleCompIds.NATIONAL, businessId, reportLog);
    }
    
    /**
     * 按指定 compId 解析并执行(服务端省优先、无则回落全国);compId 为 null 或空串时视为全国。
     *
     * @param ruleCode   规则编码
     * @param params     入参
     * @param compId     省份编码,null 或空串视为全国
     * @param businessId 业务主键ID,用于关联具体业务记录
     */
    public RuleResult execute(String ruleCode, Map<String, Object> params, String compId, String businessId) {
        return doExecute(ruleCode, params, RuleCompIds.normalize(compId), businessId, true);
    }
    
    /**
     * 按指定 compId 解析并执行(服务端省优先、无则回落全国);compId 为 null 或空串时视为全国,可选择是否上报日志。
     *
     * @param ruleCode    规则编码
     * @param params      入参
     * @param compId      省份编码,null 或空串视为全国
     * @param businessId  业务主键ID,用于关联具体业务记录
     * @param reportLog   是否执行日志上报
     */
    public RuleResult execute(String ruleCode, Map<String, Object> params, String compId, String businessId, boolean reportLog) {
        return doExecute(ruleCode, params, RuleCompIds.normalize(compId), businessId, reportLog);
    }
    
    /**
     * 按全国(通用)作用域执行规则;支持 Map 或 DTO/POJO(字段名即变量名)。
     *
     * @param ruleCode   规则编码
     * @param paramObj   入参(Map 或 DTO/POJO)
     * @param businessId 业务主键ID,用于关联具体业务记录
     */
    @SuppressWarnings("unchecked")
    public RuleResult execute(String ruleCode, Object paramObj, String businessId) {
        if (paramObj == null) {
            return doExecute(ruleCode, Collections.emptyMap(), RuleCompIds.NATIONAL, businessId, true);
        }
        if (paramObj instanceof Map) {
            return doExecute(ruleCode, (Map<String, Object>) paramObj, RuleCompIds.NATIONAL, businessId, true);
        }
        return doExecute(ruleCode, paramObj, RuleCompIds.NATIONAL, businessId, true);
    }
    
    /**
     * 按全国(通用)作用域执行规则;支持 Map 或 DTO/POJO(字段名即变量名),可选择是否上报日志。
     *
     * @param ruleCode    规则编码
     * @param paramObj    入参(Map 或 DTO/POJO)
     * @param businessId  业务主键ID,用于关联具体业务记录
     * @param reportLog   是否执行日志上报
     */
    @SuppressWarnings("unchecked")
    public RuleResult execute(String ruleCode, Object paramObj, String businessId, boolean reportLog) {
        if (paramObj == null) {
            return doExecute(ruleCode, Collections.emptyMap(), RuleCompIds.NATIONAL, businessId, reportLog);
        }
        if (paramObj instanceof Map) {
            return doExecute(ruleCode, (Map<String, Object>) paramObj, RuleCompIds.NATIONAL, businessId, reportLog);
        }
        return doExecute(ruleCode, paramObj, RuleCompIds.NATIONAL, businessId, reportLog);
    }
    
    /**
     * 按指定 compId 解析并执行;支持 Map 或 DTO/POJO(字段名即变量名)。
     *
     * @param ruleCode   规则编码
     * @param paramObj   入参(Map 或 DTO/POJO)
     * @param compId     省份编码,null 或空串视为全国
     * @param businessId 业务主键ID,用于关联具体业务记录
     */
    @SuppressWarnings("unchecked")
    public RuleResult execute(String ruleCode, Object paramObj, String compId, String businessId) {
        String scope = RuleCompIds.normalize(compId);
        if (paramObj == null) {
            return doExecute(ruleCode, Collections.emptyMap(), scope, businessId, true);
        }
        if (paramObj instanceof Map) {
            return doExecute(ruleCode, (Map<String, Object>) paramObj, scope, businessId, true);
        }
        return doExecute(ruleCode, paramObj, scope, businessId, true);
    }
    
    /**
     * 按指定 compId 解析并执行;支持 Map 或 DTO/POJO(字段名即变量名),可选择是否上报日志。
     *
     * @param ruleCode    规则编码
     * @param paramObj    入参(Map 或 DTO/POJO)
     * @param compId      省份编码,null 或空串视为全国
     * @param businessId  业务主键ID,用于关联具体业务记录
     * @param reportLog   是否执行日志上报
     */
    @SuppressWarnings("unchecked")
    public RuleResult execute(String ruleCode, Object paramObj, String compId, String businessId, boolean reportLog) {
        String scope = RuleCompIds.normalize(compId);
        if (paramObj == null) {
            return doExecute(ruleCode, Collections.emptyMap(), scope, businessId, reportLog);
        }
        if (paramObj instanceof Map) {
            return doExecute(ruleCode, (Map<String, Object>) paramObj, scope, businessId, reportLog);
        }
        return doExecute(ruleCode, paramObj, scope, businessId, reportLog);
    }

    /**
     * 按全国作用域执行规则集(成员规则顺序执行,上下文合并策略与文档一致)。
     *
     * @param setCode    规则集编码
     * @param params     入参
     * @param businessId 业务主键ID,用于关联具体业务记录
     */
    public RuleResult executeRuleSet(String setCode, Map<String, Object> params, String businessId) {
        return executeRuleSet(setCode, params, RuleCompIds.NATIONAL, businessId, true);
    }
    
    /**
     * 按全国作用域执行规则集(成员规则顺序执行,上下文合并策略与文档一致),可选择是否上报日志。
     *
     * @param setCode     规则集编码
     * @param params      入参
     * @param businessId  业务主键ID,用于关联具体业务记录
     * @param reportLog   是否执行日志上报
     */
    public RuleResult executeRuleSet(String setCode, Map<String, Object> params, String businessId, boolean reportLog) {
        return executeRuleSet(setCode, params, RuleCompIds.NATIONAL, businessId, reportLog);
    }
    
    /**
     * 按指定 compId 解析规则集并链式执行成员规则。
     *
     * @param setCode    规则集编码
     * @param params     入参
     * @param compId     省份编码,null 或空串视为全国
     * @param businessId 业务主键ID,用于关联具体业务记录
     */
    public RuleResult executeRuleSet(String setCode, Map<String, Object> params, String compId, String businessId) {
        return executeRuleSet(setCode, params, RuleCompIds.normalize(compId), businessId, true);
    }
    
    /**
     * 按指定 compId 解析规则集并链式执行成员规则,可选择是否上报日志。
     *
     * @param setCode     规则集编码
     * @param params      入参
     * @param compId      省份编码,null 或空串视为全国
     * @param businessId  业务主键ID,用于关联具体业务记录
     * @param reportLog   是否执行日志上报
     */
    public RuleResult executeRuleSet(String setCode, Map<String, Object> params, String compId, String businessId, boolean reportLog) {
        String scope = RuleCompIds.normalize(compId);
        long start = System.currentTimeMillis();
        CachedRuleSet setSnap = resolveCachedRuleSet(setCode, scope);
        if (setSnap == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则集未找到: " + setCode);
            return r;
        }
        if (setSnap.getMemberRuleCodes() == null || setSnap.getMemberRuleCodes().isEmpty()) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则集成员为空: " + setCode);
            if (reportLog) {
                reportLogSet(setCode, setSnap, params != null ? params : Collections.emptyMap(), r, System.currentTimeMillis() - start, businessId);
            }
            return r;
        }
        Map<String, Object> ctx = params != null ? params : new HashMap<>();
        String hitPolicy = RuleSetHitPolicies.normalize(setSnap.getHitPolicy());
        if (!RuleSetHitPolicies.ALL.equals(hitPolicy)) {
            RuleResult r = executeRuleSetCandidates(setSnap, scope, ctx, hitPolicy);
            if (reportLog) {
                reportLogSet(setCode, setSnap, ctx, r, System.currentTimeMillis() - start, businessId);
            }
            return r;
        }
        List<Map<String, Object>> traceSteps = new ArrayList<>();
        for (String memberCode : setSnap.getMemberRuleCodes()) {
            CachedRule cached = resolveCachedRule(memberCode, scope);
            if (cached == null) {
                RuleResult r = new RuleResult();
                r.setSuccess(false);
                r.setErrorMessage("规则集成员未找到: " + memberCode);
                r.setResult(traceSteps);
                if (reportLog) {
                    reportLogSet(setCode, setSnap, ctx, r, System.currentTimeMillis() - start, businessId);
                }
                return r;
            }
            RuleResult step = engine.execute(cached.getCompiledScript(), ctx, config.isTraceEnabled());
            Map<String, Object> stepInfo = new HashMap<>();
            stepInfo.put("ruleCode", memberCode);
            stepInfo.put("success", step.isSuccess());
            stepInfo.put("result", step.getResult());
            stepInfo.put("executeTimeMs", step.getExecuteTimeMs());
            traceSteps.add(stepInfo);
            if (!step.isSuccess()) {
                RuleResult r = new RuleResult();
                r.setSuccess(false);
                r.setErrorMessage("规则集成员执行失败: " + memberCode + " — " + step.getErrorMessage());
                r.setResult(traceSteps);
                if (reportLog) {
                    reportLogSet(setCode, setSnap, ctx, r, System.currentTimeMillis() - start, businessId);
                }
                return r;
            }
            mergeStepResultIntoContext(ctx, step.getResult());
        }
        RuleResult ok = new RuleResult();
        ok.setSuccess(true);
        Map<String, Object> out = new HashMap<>();
        out.put("context", ctx);
        out.put("steps", traceSteps);
        ok.setResult(out);
        if (reportLog) {
            reportLogSet(setCode, setSnap, ctx, ok, System.currentTimeMillis() - start, businessId);
        }
        return ok;
    }

    /**
     * FIRST/UNIQUE：成员为独立候选，均以原始入参副本执行，不做上下文累积
     *（避免未命中成员的全 null 输出覆盖同名入参污染后续候选）。
     * FIRST 首个命中即返回；无命中时成功返回 hitRuleCode=null。
     * UNIQUE 全部执行，命中数 != 1 则失败。
     */
    private RuleResult executeRuleSetCandidates(CachedRuleSet setSnap, String scope,
                                                Map<String, Object> params, String hitPolicy) {
        boolean isFirst = RuleSetHitPolicies.FIRST.equals(hitPolicy);
        List<Map<String, Object>> traceSteps = new ArrayList<>();
        int hitCount = 0;
        Object hitResult = null;
        String hitRuleCode = null;
        for (String memberCode : setSnap.getMemberRuleCodes()) {
            CachedRule cached = resolveCachedRule(memberCode, scope);
            if (cached == null) {
                RuleResult r = new RuleResult();
                r.setSuccess(false);
                r.setErrorMessage("规则集成员未找到: " + memberCode);
                r.setResult(traceSteps);
                return r;
            }
            RuleResult step = engine.execute(cached.getCompiledScript(), new HashMap<>(params), config.isTraceEnabled());
            Map<String, Object> stepInfo = new HashMap<>();
            stepInfo.put("ruleCode", memberCode);
            stepInfo.put("success", step.isSuccess());
            stepInfo.put("result", step.getResult());
            stepInfo.put("executeTimeMs", step.getExecuteTimeMs());
            traceSteps.add(stepInfo);
            if (!step.isSuccess()) {
                RuleResult r = new RuleResult();
                r.setSuccess(false);
                r.setErrorMessage("规则集成员执行失败: " + memberCode + " — " + step.getErrorMessage());
                r.setResult(traceSteps);
                return r;
            }
            if (RuleSetHitPolicies.isHit(step.getResult())) {
                hitCount++;
                if (hitResult == null) {
                    hitResult = step.getResult();
                    hitRuleCode = memberCode;
                }
                if (isFirst) {
                    break;
                }
            }
        }
        if (!isFirst && hitCount != 1) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("唯一命中(UNIQUE)策略校验失败：实际命中 " + hitCount + " 个成员，要求有且仅有 1 个");
            r.setResult(traceSteps);
            return r;
        }
        RuleResult ok = new RuleResult();
        ok.setSuccess(true);
        Map<String, Object> out = new HashMap<>();
        out.put("hitRuleCode", hitRuleCode);
        out.put("result", hitResult);
        out.put("steps", traceSteps);
        ok.setResult(out);
        return ok;
    }

    private static void mergeStepResultIntoContext(Map<String, Object> ctx, Object stepResult) {
        if (stepResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) stepResult;
            ctx.putAll(m);
        } else {
            ctx.put("_lastStepResult", stepResult);
        }
    }

    private CachedRuleSet resolveCachedRuleSet(String setCode, String scopeCompId) {
        String scope = RuleCompIds.normalize(scopeCompId);
        CachedRuleSet c = l1Cache.getSet(setCode, scope);
        if (c != null) {
            return c;
        }
        c = redisL2RuleSetCache.get(setCode, scope);
        if (c != null) {
            l1Cache.putSet(setCode, scope, c);
            return c;
        }
        c = httpSyncClient.fetchRuleSet(setCode, scope);
        if (c != null) {
            l1Cache.putSet(setCode, scope, c);
        }
        return c;
    }

    private void reportLogSet(String setCode, CachedRuleSet setSnap, Map<String, Object> params,
                              RuleResult result, long costMs, String businessId) {
        try {
            RuleExecutionLog entry = new RuleExecutionLog();
            entry.setRuleCode(setCode);
            entry.setProjectCode(setSnap.getProjectCode());
            entry.setRuleVersion(setSnap.getVersion());
            entry.setModelType("RULE_SET");
            entry.setSource("CLIENT");
            entry.setClientAppName(config.getAppName());
            entry.setBusinessId(businessId);
            entry.setInputParams(JSON.toJSONString(params, SerializerFeature.DisableCircularReferenceDetect));
            entry.setOutputResult(JSON.toJSONString(result.getResult()));
            entry.setSuccess(result.isSuccess() ? 1 : 0);
            entry.setErrorMessage(result.getErrorMessage());
            entry.setExecuteTimeMs(costMs);
            if (result.getTraces() != null) {
                entry.setTraceInfo(JSON.toJSONString(result.getTraces()));
            }
            logReporter.report(Collections.singletonList(entry));
        } catch (Exception e) {
            log.debug("Log report failed: {}", e.getMessage());
        }
    }

    private RuleResult doExecute(String ruleCode, Map<String, Object> params, String scopeCompId, String businessId) {
        return doExecute(ruleCode, params, scopeCompId, businessId, true);
    }

    private RuleResult doExecute(String ruleCode, Map<String, Object> params, String scopeCompId, String businessId, boolean reportLog) {
        long start = System.currentTimeMillis();

        CachedRule cached = resolveCachedRule(ruleCode, scopeCompId);
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }

        RuleResult result = engine.execute(cached.getCompiledScript(), params, config.isTraceEnabled());

        if (reportLog) {
            reportLog(ruleCode, cached, params, result, System.currentTimeMillis() - start, businessId);
        }
        return result;
    }

    private RuleResult doExecute(String ruleCode, Object params, String scopeCompId, String businessId) {
        return doExecute(ruleCode, params, scopeCompId, businessId, true);
    }

    private RuleResult doExecute(String ruleCode, Object params, String scopeCompId, String businessId, boolean reportLog) {
        long start = System.currentTimeMillis();

        CachedRule cached = resolveCachedRule(ruleCode, scopeCompId);
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }

        RuleResult result = engine.execute(cached.getCompiledScript(), params, config.isTraceEnabled());

        if (reportLog) {
            reportLog(ruleCode, cached, params, result, System.currentTimeMillis() - start, businessId);
        }
        return result;
    }

    /**
     * 按 L1 → Redis L2 → HTTP 顺序加载规则并回填 L1；scopeCompId 为解析请求作用域（与服务端 sync 的 compId 一致）。
     */
    private CachedRule resolveCachedRule(String ruleCode, String scopeCompId) {
        String scope = RuleCompIds.normalize(scopeCompId);
        CachedRule cached = l1Cache.get(ruleCode, scope);
        if (cached != null) {
            return cached;
        }
        cached = redisL2RuleCache.get(ruleCode, scope);
        if (cached != null) {
            l1Cache.put(ruleCode, scope, cached);
            return cached;
        }
        cached = httpSyncClient.fetchRule(ruleCode, scope);
        if (cached != null) {
            l1Cache.put(ruleCode, scope, cached);
        }
        return cached;
    }

    private void reportLog(String ruleCode, CachedRule cached, Map<String, Object> params,
                           RuleResult result, long costMs, String businessId) {
        try {
            RuleExecutionLog entry = new RuleExecutionLog();
            entry.setRuleCode(ruleCode);
            entry.setProjectCode(cached.getProjectCode());
            entry.setRuleVersion(cached.getVersion());
            entry.setModelType(cached.getModelType());
            entry.setSource("CLIENT");
            entry.setClientAppName(config.getAppName());
            entry.setBusinessId(businessId);
            entry.setInputParams(JSON.toJSONString(params, SerializerFeature.DisableCircularReferenceDetect));
            entry.setOutputResult(JSON.toJSONString(result.getResult()));
            entry.setSuccess(result.isSuccess() ? 1 : 0);
            entry.setErrorMessage(result.getErrorMessage());
            entry.setExecuteTimeMs(costMs);
            if (result.getTraces() != null) {
                entry.setTraceInfo(JSON.toJSONString(result.getTraces()));
            }
            logReporter.report(Collections.singletonList(entry));
        } catch (Exception e) {
            log.debug("Log report failed: {}", e.getMessage());
        }
    }

    private void reportLog(String ruleCode, CachedRule cached, Object params,
                           RuleResult result, long costMs, String businessId) {
        try {
            RuleExecutionLog entry = new RuleExecutionLog();
            entry.setRuleCode(ruleCode);
            entry.setProjectCode(cached.getProjectCode());
            entry.setRuleVersion(cached.getVersion());
            entry.setModelType(cached.getModelType());
            entry.setSource("CLIENT");
            entry.setClientAppName(config.getAppName());
            entry.setBusinessId(businessId);
            entry.setInputParams(JSON.toJSONString(params, SerializerFeature.DisableCircularReferenceDetect));
            entry.setOutputResult(JSON.toJSONString(result.getResult()));
            entry.setSuccess(result.isSuccess() ? 1 : 0);
            entry.setErrorMessage(result.getErrorMessage());
            entry.setExecuteTimeMs(costMs);
            if (result.getTraces() != null) {
                entry.setTraceInfo(JSON.toJSONString(result.getTraces()));
            }
            logReporter.report(Collections.singletonList(entry));
        } catch (Exception e) {
            log.debug("Log report failed: {}", e.getMessage());
        }
    }

    /**
     * 刷新全国作用域下该规则的本地缓存
     */
    public void refreshRule(String ruleCode) {
        refreshRule(ruleCode, RuleCompIds.NATIONAL);
    }

    /**
     * 刷新指定解析作用域下该规则的本地缓存
     */
    public void refreshRule(String ruleCode, String compId) {
        String scope = RuleCompIds.normalize(compId);
        CachedRule rule = httpSyncClient.fetchRule(ruleCode, scope);
        if (rule == null) {
            rule = redisL2RuleCache.get(ruleCode, scope);
        }
        if (rule != null) {
            l1Cache.put(ruleCode, scope, rule);
        }
    }

    public void refreshAll() {
        fullSync();
    }

    /**
     * 查看全国作用域下 L1 中已缓存的元数据
     */
    public CachedRule getRuleInfo(String ruleCode) {
        return getRuleInfo(ruleCode, RuleCompIds.NATIONAL);
    }

    /**
     * 查看指定解析作用域下 L1 中已缓存的元数据
     */
    public CachedRule getRuleInfo(String ruleCode, String compId) {
        return l1Cache.get(ruleCode, RuleCompIds.normalize(compId));
    }

    public QLExpressEngine getEngine() {
        return engine;
    }

    public ClientFunctionRegistrar getFunctionRegistrar() {
        return functionRegistrar;
    }

    private void syncFunctions() {
        if (config.getProjectId() <= 0) {
            log.warn("未同步服务端函数（projectId={}）：SCRIPT/JAVA/BEAN 类型自定义函数不会从服务端拉取。"
                            + " 若规则依赖这些函数，请在配置中设置 rule-engine.client.project-id 为管控端项目 ID（>0）；"
                            + " 或仅依赖 Redis 推送 FUNC_UPDATE 触发的注册。",
                    config.getProjectId());
            return;
        }
        try {
            List<JSONObject> functions = httpSyncClient.fetchFunctions(config.getProjectId());
            functionRegistrar.registerAll(functions);
            if (functions.isEmpty()) {
                log.warn("函数同步完成但列表为空：projectId={}，请确认服务端该项目下已创建函数；"
                                + "JAVA 类需在客户端 classpath，BEAN 需在 Spring 容器中可解析。",
                        config.getProjectId());
            } else {
                log.info("Function sync completed, {} functions registered (SCRIPT/JAVA/BEAN)", functions.size());
            }
        } catch (Exception e) {
            log.warn("Function sync failed: {}", e.getMessage());
        }
    }

    /**
     * 启动预热：对 L1 内已缓存的所有脚本，以空上下文触发 QLExpress4 内部解析缓存。
     * <p>后台单线程异步执行，不阻塞主线程启动（QLExpress4 内部编译缓存有全局锁，多线程无法加速）。
     * 预热期间将自定义函数替换为 no-op 空壳，避免触发真实 BEAN/JAVA/SCRIPT 函数副作用；
     * 预热完成后恢复真实函数，保证业务执行正确。</p>
     */
    private void warmUpScripts() {
        if (!config.isWarmUpOnStart()) {
            return;
        }

        List<String> scripts = new ArrayList<>();
        for (CachedRule rule : l1Cache.getAllRules()) {
            String script = rule.getCompiledScript();
            if (script != null && !script.isEmpty()) {
                scripts.add(script);
            }
        }
        if (scripts.isEmpty()) {
            return;
        }

        final int scriptCount = scripts.size();
        // 异步预热，不阻塞主线程（QLExpress4 内部有锁，并行无收益）
        Thread warmUpThread = new Thread(() -> {
            long start = System.currentTimeMillis();
            functionRegistrar.installNoOpStubs();
            try {
                for (String script : scripts) {
                    engine.warmUp(script);
                }
            } finally {
                functionRegistrar.restoreRealFunctions();
            }
            log.info("Script warm-up completed: {} scripts pre-compiled in {}ms (async)",
                    scriptCount, System.currentTimeMillis() - start);
        }, "rule-warmup");
        warmUpThread.setDaemon(true);
        warmUpThread.start();
        log.info("Script warm-up started asynchronously for {} scripts", scriptCount);
    }

    /**
     * 定时全量同步：仅同步全国（通用）作用域规则，省别规则在首次带 compId 执行时按需加载。
     */
    private void fullSync() {
        try {
            List<CachedRule> rules = httpSyncClient.fetchAll(RuleCompIds.NATIONAL);
            for (CachedRule rule : rules) {
                if (rule.getRuleCode() != null) {
                    l1Cache.put(rule.getRuleCode(), RuleCompIds.NATIONAL, rule);
                }
            }
            List<CachedRuleSet> sets = httpSyncClient.fetchAllSets(RuleCompIds.NATIONAL);
            for (CachedRuleSet s : sets) {
                if (s.getSetCode() != null) {
                    l1Cache.putSet(s.getSetCode(), RuleCompIds.NATIONAL, s);
                }
            }
            log.debug("Full sync completed, {} rules, {} rule sets (national scope)", rules.size(), sets.size());
        } catch (Exception e) {
            log.warn("Full sync failed: {}", e.getMessage());
        }
    }

    public static class Builder {
        private final RuleEngineClientConfig config = new RuleEngineClientConfig();
        private RedisConnectionFactory connectionFactory;
        private ExecutionLogReporter logReporter;
        private ApplicationContext applicationContext;

        public Builder serverUrl(String serverUrl) {
            config.setServerUrl(serverUrl);
            return this;
        }

        public Builder appName(String appName) {
            config.setAppName(appName);
            return this;
        }

        public Builder token(String token) {
            config.setToken(token);
            return this;
        }

        public Builder l1CacheMaxSize(int size) {
            config.setL1CacheMaxSize(size);
            return this;
        }

        public Builder httpTimeoutMs(int ms) {
            config.setHttpTimeoutMs(ms);
            return this;
        }

        public Builder logReportEnabled(boolean enabled) {
            config.setLogReportEnabled(enabled);
            return this;
        }

        public Builder projectId(long projectId) {
            config.setProjectId(projectId);
            return this;
        }

        public Builder traceEnabled(boolean traceEnabled) {
            config.setTraceEnabled(traceEnabled);
            return this;
        }

        public Builder l2RedisCacheEnabled(boolean enabled) {
            config.setL2RedisCacheEnabled(enabled);
            return this;
        }

        public Builder warmUpOnStart(boolean warmUpOnStart) {
            config.setWarmUpOnStart(warmUpOnStart);
            return this;
        }

        public Builder connectionFactory(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Builder logReporter(ExecutionLogReporter logReporter) {
            this.logReporter = logReporter;
            return this;
        }

        public Builder applicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            return this;
        }

        public RuleEngineClient build() {
            if (connectionFactory == null) {
                throw new IllegalStateException("RedisConnectionFactory is required. " +
                        "Please provide it via builder.connectionFactory(redisConnectionFactory)");
            }
            return new RuleEngineClient(config, connectionFactory, logReporter, applicationContext);
        }
    }
}
