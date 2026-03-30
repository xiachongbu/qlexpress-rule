package com.bjjw.rule.server.service;

import com.bjjw.rule.core.compiler.*;
import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.server.mapper.RuleDefinitionContentMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class RuleCompileService {

    private final Map<String, RuleCompiler> compilers = new HashMap<>();

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    public RuleCompileService() {
        compilers.put("TABLE", new DecisionTableCompiler());
        compilers.put("TREE", new DecisionTreeCompiler());
        compilers.put("FLOW", new DecisionFlowCompiler());
        compilers.put("CROSS", new CrossTableCompiler());
        compilers.put("SCORE", new ScorecardCompiler());
        compilers.put("CROSS_ADV", new AdvancedCrossTableCompiler());
        compilers.put("SCORE_ADV", new AdvancedScorecardCompiler());
        compilers.put("SCRIPT", new ScriptPassthroughCompiler());
    }

    public CompileResult compile(Long definitionId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return CompileResult.fail("规则定义不存在");
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null) {
            return CompileResult.fail("规则内容不存在");
        }

        RuleCompiler compiler = compilers.get(definition.getModelType());
        if (compiler == null) {
            return CompileResult.fail("暂不支持的模型类型: " + definition.getModelType());
        }

        CompileResult result = compiler.compile(content.getModelJson());

        content.setCompileStatus(result.isSuccess() ? 1 : 2);
        content.setCompiledScript(result.getCompiledScript());
        content.setCompiledType(result.getCompiledType());
        content.setCompileMessage(result.isSuccess() ? null : result.getErrorMessage());
        content.setCompileTime(LocalDateTime.now());
        if (result.isSuccess()) {
            content.setScriptMode("visual");
        }
        contentMapper.updateById(content);

        return result;
    }

    /**
     * 验证手写脚本语法（不覆盖可视化模型编译结果）。
     * 通过 QLExpress 引擎试解析脚本，语法错误立即返回，
     * 运行时错误（变量未定义等）视为语法通过。
     */
    public CompileResult validateScript(String script) {
        try {
            com.alibaba.qlexpress4.Express4Runner runner =
                    new com.alibaba.qlexpress4.Express4Runner(
                            com.alibaba.qlexpress4.InitOptions.builder()
                                    .securityStrategy(com.alibaba.qlexpress4.security.QLSecurityStrategy.open())
                                    .build());
            AggregateBuiltinFunctionRegistry.register(runner);
            runner.execute(script, Collections.emptyMap(),
                    com.alibaba.qlexpress4.QLOptions.builder().cache(false).build());
            return CompileResult.ok(script, "QLEXPRESS");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isSyntaxError(msg)) {
                return CompileResult.fail(msg);
            }
            return CompileResult.ok(script, "QLEXPRESS");
        }
    }

    /** 区分语法错误和运行时错误（变量缺失等） */
    private boolean isSyntaxError(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("parse") || lower.contains("syntax")
                || lower.contains("unexpected") || lower.contains("token")
                || lower.contains("解析") || lower.contains("语法");
    }
}
