package com.bjjw.rule.client;

import com.bjjw.rule.client.cache.CachedRule;
import com.bjjw.rule.client.cache.L1MemoryCache;
import com.bjjw.rule.client.function.ClientFunctionRegistrar;
import com.bjjw.rule.client.log.ExecutionLogReporter;
import com.bjjw.rule.client.log.HttpLogReporter;
import com.bjjw.rule.client.log.NoOpLogReporter;
import com.bjjw.rule.client.sync.HttpSyncClient;
import com.bjjw.rule.client.sync.RedisSubscriber;
import com.bjjw.rule.core.engine.QLExpressEngine;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.entity.RuleExecutionLog;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class RuleEngineClient {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineClient.class);

    private final RuleEngineClientConfig config;
    private final L1MemoryCache l1Cache;
    private final HttpSyncClient httpSyncClient;
    private final RedisSubscriber redisSubscriber;
    private final QLExpressEngine engine;
    private final ExecutionLogReporter logReporter;
    private final ClientFunctionRegistrar functionRegistrar;
    private ScheduledExecutorService scheduler;

    private RuleEngineClient(RuleEngineClientConfig config, RedisConnectionFactory connectionFactory,
                             ExecutionLogReporter externalReporter, ApplicationContext applicationContext) {
        this.config = config;
        this.l1Cache = new L1MemoryCache(config.getL1CacheMaxSize());
        this.httpSyncClient = new HttpSyncClient(config.getServerUrl(), config.getHttpTimeoutMs(), config.getToken());
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
        log.info("RuleEngineClient starting: serverUrl={}, appName={}, logReporter={}",
                config.getServerUrl(), config.getAppName(), logReporter.getClass().getSimpleName());
        redisSubscriber.setFunctionRegistrar(functionRegistrar);
        redisSubscriber.start();
        fullSync();
        syncFunctions();
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
        if (scheduler != null) scheduler.shutdownNow();
        redisSubscriber.stop();
    }

    /**
     * 执行规则，默认开启表达式追踪（成功时 traceInfo 始终回传）
     */
    public RuleResult execute(String ruleCode, Map<String, Object> params) {
        return doExecute(ruleCode, params);
    }

    /**
     * 执行规则，支持传入 Java 对象（DTO / Model / POJO）作为参数。
     * 对象的字段会通过 Fastjson 自动转换为 Map&lt;String, Object&gt; 后注入表达式上下文。
     *
     * @param ruleCode 规则编码
     * @param paramObj Java 对象，字段名即为表达式中的变量名
     */
    @SuppressWarnings("unchecked")
    public RuleResult execute(String ruleCode, Object paramObj) {
        if (paramObj == null) {
            return doExecute(ruleCode, Collections.emptyMap());
        }
        if (paramObj instanceof Map) {
            return doExecute(ruleCode, (Map<String, Object>) paramObj);
        }
        return doExecute(ruleCode, paramObj);
    }

    private RuleResult doExecute(String ruleCode, Map<String, Object> params) {
        long start = System.currentTimeMillis();

        CachedRule cached = l1Cache.get(ruleCode);
        if (cached == null) {
            cached = httpSyncClient.fetchRule(ruleCode);
            if (cached != null) {
                l1Cache.put(cached);
            }
        }
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }

        RuleResult result = engine.execute(cached.getCompiledScript(), params, config.isTraceEnabled());

        reportLog(ruleCode, cached, params, result, System.currentTimeMillis() - start);
        return result;
    }

    private RuleResult doExecute(String ruleCode, Object params) {
        long start = System.currentTimeMillis();

        CachedRule cached = l1Cache.get(ruleCode);
        if (cached == null) {
            cached = httpSyncClient.fetchRule(ruleCode);
            if (cached != null) {
                l1Cache.put(cached);
            }
        }
        if (cached == null) {
            RuleResult r = new RuleResult();
            r.setSuccess(false);
            r.setErrorMessage("规则未找到: " + ruleCode);
            return r;
        }

        RuleResult result = engine.execute(cached.getCompiledScript(), params, config.isTraceEnabled());

        reportLog(ruleCode, cached, params, result, System.currentTimeMillis() - start);
        return result;
    }

    private void reportLog(String ruleCode, CachedRule cached, Map<String, Object> params,
                           RuleResult result, long costMs) {
        try {
            RuleExecutionLog entry = new RuleExecutionLog();
            entry.setRuleCode(ruleCode);
            entry.setProjectCode(cached.getProjectCode());
            entry.setRuleVersion(cached.getVersion());
            entry.setModelType(cached.getModelType());
            entry.setSource("CLIENT");
            entry.setClientAppName(config.getAppName());
            entry.setInputParams(JSON.toJSONString(params));
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
                           RuleResult result, long costMs) {
        try {
            RuleExecutionLog entry = new RuleExecutionLog();
            entry.setRuleCode(ruleCode);
            entry.setProjectCode(cached.getProjectCode());
            entry.setRuleVersion(cached.getVersion());
            entry.setModelType(cached.getModelType());
            entry.setSource("CLIENT");
            entry.setClientAppName(config.getAppName());
            entry.setInputParams(JSON.toJSONString(params));
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

    public void refreshRule(String ruleCode) {
        CachedRule rule = httpSyncClient.fetchRule(ruleCode);
        if (rule != null) {
            l1Cache.put(rule);
        }
    }

    public void refreshAll() {
        fullSync();
    }

    public CachedRule getRuleInfo(String ruleCode) {
        return l1Cache.get(ruleCode);
    }

    /**
     * 获取内部 QLExpress 引擎实例，用于注册自定义函数等扩展操作
     */
    public QLExpressEngine getEngine() {
        return engine;
    }

    /**
     * 获取客户端函数注册器，用于手动注册函数
     */
    public ClientFunctionRegistrar getFunctionRegistrar() {
        return functionRegistrar;
    }

    private void syncFunctions() {
        if (config.getProjectId() <= 0) return;
        try {
            List<JSONObject> functions = httpSyncClient.fetchFunctions(config.getProjectId());
            functionRegistrar.registerAll(functions);
            log.info("Function sync completed, {} functions registered", functions.size());
        } catch (Exception e) {
            log.warn("Function sync failed: {}", e.getMessage());
        }
    }

    private void fullSync() {
        try {
            List<CachedRule> rules = httpSyncClient.fetchAll();
            for (CachedRule rule : rules) {
                l1Cache.put(rule);
            }
            log.debug("Full sync completed, {} rules", rules.size());
        } catch (Exception e) {
            log.warn("Full sync failed: {}", e.getMessage());
        }
    }

    public static class Builder {
        private final RuleEngineClientConfig config = new RuleEngineClientConfig();
        private RedisConnectionFactory connectionFactory;
        private ExecutionLogReporter logReporter;
        private ApplicationContext applicationContext;

        public Builder serverUrl(String serverUrl) { config.setServerUrl(serverUrl); return this; }
        public Builder appName(String appName) { config.setAppName(appName); return this; }
        public Builder token(String token) { config.setToken(token); return this; }
        public Builder l1CacheMaxSize(int size) { config.setL1CacheMaxSize(size); return this; }
        public Builder httpTimeoutMs(int ms) { config.setHttpTimeoutMs(ms); return this; }
        public Builder logReportEnabled(boolean enabled) { config.setLogReportEnabled(enabled); return this; }
        /** 设置项目 ID，启动时自动从服务端同步 JAVA/BEAN/SCRIPT 函数（0 表示不同步） */
        public Builder projectId(long projectId) { config.setProjectId(projectId); return this; }
        /** 设置是否开启表达式追踪，默认 true */
        public Builder traceEnabled(boolean traceEnabled) { config.setTraceEnabled(traceEnabled); return this; }

        public Builder connectionFactory(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Builder logReporter(ExecutionLogReporter logReporter) {
            this.logReporter = logReporter;
            return this;
        }

        /** 设置 Spring ApplicationContext，用于 BEAN 类型函数注册 */
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
