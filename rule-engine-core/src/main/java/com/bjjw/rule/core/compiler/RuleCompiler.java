package com.bjjw.rule.core.compiler;

import java.util.Collections;
import java.util.Set;

public interface RuleCompiler {

    /**
     * 编译模型为脚本（不注入常量信息，兼容旧调用与无常量场景）。
     */
    CompileResult compile(String modelJson);

    /**
     * 编译模型为脚本，并感知项目常量：
     * <ul>
     *   <li>{@code constantNames} 中的变量为常量，编译器不得将其作为输出变量、null 预声明或结果 Map 键，
     *       避免覆盖常量序言并保持「常量不出现在赋值左侧」语义；</li>
     *   <li>{@code constantPrefix} 为常量赋值序言，需前置到脚本最前，使脚本内引用的常量解析为固化值。</li>
     * </ul>
     * 默认实现忽略常量信息，回退到 {@link #compile(String)}；需要常量支持的编译器应覆写本方法。
     *
     * @param modelJson       模型 JSON
     * @param constantNames   常量脚本名集合（可空）
     * @param constantPrefix  常量赋值序言（可空/空串表示无常量）
     */
    default CompileResult compile(String modelJson, Set<String> constantNames, String constantPrefix) {
        return compile(modelJson);
    }

    /** 无常量信息时的空集合常量。 */
    default Set<String> emptyConstants() {
        return Collections.emptySet();
    }
}
