package com.bjjw.rule.core.compiler;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型类型 → 编译器 静态注册表。
 * <p>供「显式编译」（RuleCompileService）与「保存即编译」（RuleDefinitionService.saveContent）共用，
 * 保证两条链路使用完全一致的编译器实例与类型映射。编译器均为无状态实现，可安全共享。</p>
 */
public final class RuleModelCompilers {

    private static final Map<String, RuleCompiler> COMPILERS = new HashMap<>();

    static {
        COMPILERS.put("TABLE", new DecisionTableCompiler());
        COMPILERS.put("TREE", new DecisionTreeCompiler());
        COMPILERS.put("FLOW", new DecisionFlowCompiler());
        COMPILERS.put("CROSS", new CrossTableCompiler());
        COMPILERS.put("SCORE", new ScorecardCompiler());
        COMPILERS.put("CROSS_ADV", new AdvancedCrossTableCompiler());
        COMPILERS.put("SCORE_ADV", new AdvancedScorecardCompiler());
        COMPILERS.put("SCRIPT", new ScriptPassthroughCompiler());
    }

    private RuleModelCompilers() {
    }

    /** 按模型类型获取编译器；不支持的类型返回 null */
    public static RuleCompiler get(String modelType) {
        return COMPILERS.get(modelType);
    }
}
