package com.bjjw.rule.core.engine;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.exception.QLRuntimeException;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.bjjw.rule.model.dto.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

public class QLExpressEngine {

    private static final Logger log = LoggerFactory.getLogger(QLExpressEngine.class);

    private final Express4Runner runner;

    public QLExpressEngine() {
        this.runner = new Express4Runner(InitOptions.builder()
                .traceExpression(true)
                .securityStrategy(QLSecurityStrategy.open())
                .build());
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    /**
     * @param traceExpression 是否在 runner 级别开启表达式 trace（影响 parse/compile 性能）。
     *                        注意：设为 false 后，即使执行时 QLOptions.traceExpression(true) 也无法获取 trace 信息。
     */
    public QLExpressEngine(boolean traceExpression) {
        this.runner = new Express4Runner(InitOptions.builder()
                .traceExpression(traceExpression)
                .securityStrategy(QLSecurityStrategy.open())
                .build());
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    public QLExpressEngine(InitOptions initOptions) {
        this.runner = new Express4Runner(initOptions);
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    public RuleResult execute(String script, Map<String, Object> context) {
        return execute(script, context, false);
    }

    public RuleResult execute(String script, Map<String, Object> context, boolean trace) {
        return execute(script, context, trace, false);
    }

    public RuleResult execute(String script, Map<String, Object> context, boolean trace, boolean precise) {
        return execute(script, context, trace, precise, 0L);
    }

    /**
     * @param precise       是否启用高精度计算（BigDecimal）；由规则配置（CachedRule.precise / rule_definition.precise_mode）透传，非业务方传参入口
     * @param timeoutMillis 单次执行超时（毫秒），&lt;=0 表示不限制；由规则配置（CachedRule.timeoutMillis / rule_definition.timeout_millis）透传
     */
    public RuleResult execute(String script, Map<String, Object> context, boolean trace, boolean precise, long timeoutMillis) {
        RuleResult ruleResult = new RuleResult();
        long start = System.currentTimeMillis();
        try {
            QLOptions options = buildOptions(trace, precise, timeoutMillis);
            QLResult result = runner.execute(script, context != null ? context : Collections.emptyMap(), options);
            ruleResult.setResult(result.getResult());
            ruleResult.setSuccess(true);
            if (trace && result.getExpressionTraces() != null) {
                ruleResult.setTraces(Collections.singletonList(result.getExpressionTraces()));
            }
        } catch (Exception e) {
            log.error("QLExpress execution error: {}", e.getMessage(), e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage(extractErrorMessage(e));
        } finally {
            ruleResult.setExecuteTimeMs(System.currentTimeMillis() - start);
        }
        return ruleResult;
    }

    public RuleResult execute(String script, Object context, boolean trace) {
        return execute(script, context, trace, false);
    }

    public RuleResult execute(String script, Object context, boolean trace, boolean precise) {
        return execute(script, context, trace, precise, 0L);
    }

    /**
     * @param precise       是否启用高精度计算（BigDecimal）；由规则配置透传
     * @param timeoutMillis 单次执行超时（毫秒），&lt;=0 表示不限制；由规则配置透传
     */
    public RuleResult execute(String script, Object context, boolean trace, boolean precise, long timeoutMillis) {
        RuleResult ruleResult = new RuleResult();
        long start = System.currentTimeMillis();
        try {
            QLOptions options = buildOptions(trace, precise, timeoutMillis);
            QLResult result = runner.execute(script, context != null ? context : Collections.emptyMap(), options);
            ruleResult.setResult(result.getResult());
            ruleResult.setSuccess(true);
            if (trace && result.getExpressionTraces() != null) {
                ruleResult.setTraces(Collections.singletonList(result.getExpressionTraces()));
            }
        } catch (Exception e) {
            log.error("QLExpress execution error: {}", e.getMessage(), e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage(extractErrorMessage(e));
        } finally {
            ruleResult.setExecuteTimeMs(System.currentTimeMillis() - start);
        }
        return ruleResult;
    }

    /** 统一构建执行选项：cache 恒开，precise 与 timeoutMillis 由规则配置决定（timeoutMillis&lt;=0 不设超时） */
    private static QLOptions buildOptions(boolean trace, boolean precise, long timeoutMillis) {
        QLOptions.Builder builder = QLOptions.builder()
                .cache(true)
                .traceExpression(trace)
                .precise(precise);
        if (timeoutMillis > 0) {
            builder.timeoutMillis(timeoutMillis);
        }
        return builder.build();
    }

    /** 脚本主动 throw 语句对应的 QLExpress4 错误码 */
    private static final String ERR_CODE_QL_THROW = "QL_THROW";

    /**
     * 提取执行异常的错误信息：仅当脚本主动 {@code throw}（错误码 QL_THROW，如 UNIQUE 策略校验文案）
     * 时取被抛对象作为错误信息；其余运行时错误（NPE、除零等也会携带 catchObj）
     * 保留 QLExpress 原始描述，避免丢失 [Near: ...] 与行列定位信息。
     */
    private static String extractErrorMessage(Exception e) {
        if (e instanceof QLRuntimeException && ERR_CODE_QL_THROW.equals(((QLRuntimeException) e).getErrorCode())) {
            Object catchObj = ((QLRuntimeException) e).getCatchObj();
            if (catchObj != null) {
                return String.valueOf(catchObj);
            }
        }
        return e.getMessage();
    }

    public Express4Runner getRunner() {
        return runner;
    }

    /**
     * 预热脚本：以空上下文触发 QLExpress4 内部解析与编译缓存。
     * <p>预热时变量不存在等运行时异常属正常现象，忽略即可；
     * QLExpress4 在解析阶段已将编译结果写入内部缓存，首次真实执行将直接命中缓存。</p>
     */
    public void warmUp(String script) {
        if (script == null || script.isEmpty()) {
            return;
        }
        try {
            QLOptions options = QLOptions.builder()
                    .cache(true)
                    .traceExpression(false)
                    .build();
            runner.execute(script, Collections.emptyMap(), options);
        } catch (Exception ignored) {
            // 预热时上下文为空，变量缺失等运行时异常属正常现象，忽略即可
        }
    }
}
