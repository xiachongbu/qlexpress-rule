package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * 将「多路赋值」场景的脚本包装为统一返回值：QLExpress 默认只返回最后一条表达式的值，
 * 通过前置 null 初始化 + 末尾 JSON 对象字面量（映射为 Map）汇总，使调用方一次拿到全部输出变量。
 */
public final class RuleScriptResultCollector {

    private RuleScriptResultCollector() {
    }

    /**
     * 在脚本最前面插入输出变量的初始化，支持自定义初始值。
     * 如果 initValueMap 中某变量有配置初始值，则使用配置值；否则使用 null。
     *
     * @param script       已生成的脚本正文
     * @param varCodes     需要参与返回的变量名（去重、忽略空串）
     * @param initValueMap 变量初始值映射（varCode -> initValue），可为 null
     */
    public static void prependOutputInits(StringBuilder script, Collection<String> varCodes, Map<String, String> initValueMap) {
        LinkedHashSet<String> uniq = uniqueNonEmpty(varCodes);
        if (uniq.isEmpty()) {
            return;
        }
        StringBuilder head = new StringBuilder();
        for (String code : uniq) {
            head.append(code).append(" = ");
            String initValue = (initValueMap != null) ? initValueMap.get(code) : null;
            if (initValue != null && !initValue.trim().isEmpty()) {
                head.append(initValue.trim());
            } else {
                head.append("null");
            }
            head.append("\n");
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

    /**
     * 解析 modelJson 中的 outputVarInits 数组，返回变量初始值映射。
     * outputVarInits 格式：[{"varCode": "taxRate", "initValue": "0"}, ...]
     *
     * @param modelJson 模型 JSON 对象
     * @return 变量初始值映射（varCode -> initValue），如果没有配置则返回空 Map
     */
    public static Map<String, String> parseOutputVarInits(JSONObject modelJson) {
        Map<String, String> result = new HashMap<>();
        if (modelJson == null) {
            return result;
        }
        JSONArray outputVarInits = modelJson.getJSONArray("outputVarInits");
        if (outputVarInits == null || outputVarInits.isEmpty()) {
            return result;
        }
        for (int i = 0; i < outputVarInits.size(); i++) {
            JSONObject item = outputVarInits.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String varCode = item.getString("varCode");
            String initValue = item.getString("initValue");
            if (varCode != null && !varCode.trim().isEmpty()) {
                result.put(varCode.trim(), initValue);
            }
        }
        return result;
    }
}
