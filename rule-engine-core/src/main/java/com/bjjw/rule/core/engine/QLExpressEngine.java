package com.bjjw.rule.core.engine;

import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.bjjw.rule.model.dto.RuleResult;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
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

    public QLExpressEngine(InitOptions initOptions) {
        this.runner = new Express4Runner(initOptions);
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    public RuleResult execute(String script, Map<String, Object> context) {
        return execute(script, context, false);
    }

    public RuleResult execute(String script, Map<String, Object> context, boolean trace) {
        RuleResult ruleResult = new RuleResult();
        long start = System.currentTimeMillis();
        try {
            QLOptions options = QLOptions.builder()
                    .cache(true)
                    .traceExpression(trace)
                    .build();
            QLResult result = runner.execute(script, context != null ? context : Collections.emptyMap(), options);
            ruleResult.setResult(result.getResult());
            ruleResult.setSuccess(true);
            if (trace && result.getExpressionTraces() != null) {
                ruleResult.setTraces(Collections.singletonList(result.getExpressionTraces()));
            }
        } catch (Exception e) {
            log.error("QLExpress execution error: {}", e.getMessage(), e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage(e.getMessage());
        } finally {
            ruleResult.setExecuteTimeMs(System.currentTimeMillis() - start);
        }
        return ruleResult;
    }

    public RuleResult execute(String script, Object context, boolean trace) {
        RuleResult ruleResult = new RuleResult();
        long start = System.currentTimeMillis();
        try {
            QLOptions options = QLOptions.builder()
                    .cache(true)
                    .traceExpression(trace)
                    .build();
            QLResult result = runner.execute(script, context != null ? context : Collections.emptyMap(), options);
            ruleResult.setResult(result.getResult());
            ruleResult.setSuccess(true);
            if (trace && result.getExpressionTraces() != null) {
                ruleResult.setTraces(Collections.singletonList(result.getExpressionTraces()));
            }
        } catch (Exception e) {
            log.error("QLExpress execution error: {}", e.getMessage(), e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage(e.getMessage());
        } finally {
            ruleResult.setExecuteTimeMs(System.currentTimeMillis() - start);
        }
        return ruleResult;
    }

    public Express4Runner getRunner() {
        return runner;
    }
}
