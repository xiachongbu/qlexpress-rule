package com.bjjw.rule.core.compiler;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * 将「多路赋值」场景的脚本包装为统一返回值：QLExpress 默认只返回最后一条表达式的值，
 * 通过前置 null 初始化 + 末尾 JSON 对象字面量（映射为 Map）汇总，使调用方一次拿到全部输出变量。
 */
public final class RuleScriptResultCollector {

    private RuleScriptResultCollector() {
    }

    /**
     * 在脚本最前面插入输出变量的 null 初始化，避免未命中分支时变量未定义而无法构建返回 Map。
     *
     * @param script   已生成的脚本正文
     * @param varCodes 需要参与返回的变量名（去重、忽略空串）
     */
    public static void prependOutputNullInits(StringBuilder script, Collection<String> varCodes) {
        LinkedHashSet<String> uniq = uniqueNonEmpty(varCodes);
        if (uniq.isEmpty()) {
            return;
        }
        StringBuilder head = new StringBuilder();
        for (String code : uniq) {
            head.append(code).append(" = null\n");
        }
        script.insert(0, head);
    }

    /**
     * 将列出的变量以 QLExpress 支持的 JSON 对象字面量形式赋给 _result，并以 _result 作为脚本最终表达式返回值
     *（键名与变量名一致，顺序与 varCodes 首次出现顺序一致）。
     *
     * @param script   脚本正文
     * @param varCodes 输出变量名（顺序保留，去重）
     */
    public static void appendResultMapReturn(StringBuilder script, Collection<String> varCodes) {
        LinkedHashSet<String> uniq = uniqueNonEmpty(varCodes);
        if (uniq.isEmpty()) {
            return;
        }
        script.append("\n_result = {");
        boolean first = true;
        for (String code : uniq) {
            if (!first) {
                script.append(", ");
            }
            first = false;
            script.append("\"").append(escapeJsonKeyForMapLiteral(code)).append("\": ").append(code);
        }
        script.append("}\n_result\n");
    }

    /**
     * 将键名转义后嵌入 JSON 对象字面量的双引号键中，避免反斜杠或引号破坏 QLExpress 语法。
     */
    private static String escapeJsonKeyForMapLiteral(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 从集合中提取非空、去重后的变量名，并保持首次出现顺序。
     */
    private static LinkedHashSet<String> uniqueNonEmpty(Collection<String> varCodes) {
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        if (varCodes == null) {
            return uniq;
        }
        for (String code : varCodes) {
            if (code != null && !code.trim().isEmpty()) {
                uniq.add(code.trim());
            }
        }
        return uniq;
    }
}
