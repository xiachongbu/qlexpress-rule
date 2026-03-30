package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

/**
 * 决策表：将 JSON 模型编译为 QLExpress 脚本。
 * 条件支持每条规则上的 {@code conditionRoot} 树（与/或嵌套）或旧版「条件列 + 行条件数组」。
 * 动作支持每条规则内自带变量定义（varCode/varType/value），或旧版「全局 actions 列 + 行上仅 value」。
 */
public class DecisionTableCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            String hitPolicy = model.getString("hitPolicy");
            if (hitPolicy == null) hitPolicy = "FIRST";
            JSONArray legacyColumnDefs = model.getJSONArray("conditions");
            if (legacyColumnDefs == null) legacyColumnDefs = new JSONArray();
            JSONArray globalActionDefs = model.getJSONArray("actions");
            if (globalActionDefs == null) globalActionDefs = new JSONArray();
            JSONArray rules = model.getJSONArray("rules");

            if (rules == null) {
                return CompileResult.fail("决策表模型缺少必要字段: rules");
            }

            LinkedHashSet<String> outputVarCodes = collectOutputVarCodes(rules, globalActionDefs);

            StringBuilder script = new StringBuilder();
            boolean isFirst = "FIRST".equals(hitPolicy);

            for (int i = 0; i < rules.size(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                JSONArray ruleConditions = rule.getJSONArray("conditions");
                JSONArray ruleActions = rule.getJSONArray("actions");

                if (ruleConditions == null) ruleConditions = new JSONArray();
                if (ruleActions == null) ruleActions = new JSONArray();

                if (i == 0 || !isFirst) {
                    script.append("if (");
                } else {
                    script.append(" else if (");
                }

                script.append(buildRulePredicate(rule, ruleConditions, legacyColumnDefs));
                script.append(") {\n");

                appendRuleAssignments(script, ruleActions, globalActionDefs);

                script.append("}");
                if (!isFirst) script.append("\n");
            }
            script.append("\n");

            if (!outputVarCodes.isEmpty()) {
                RuleScriptResultCollector.prependOutputNullInits(script, outputVarCodes);
                RuleScriptResultCollector.appendResultMapReturn(script, outputVarCodes);
            }

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("决策表编译失败: " + e.getMessage());
        }
    }

    /**
     * 汇总所有规则中会出现的输出变量（用于结果 Map 与初始化）。
     */
    static LinkedHashSet<String> collectOutputVarCodes(JSONArray rules, JSONArray globalActionDefs) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            JSONArray ruleActions = rule.getJSONArray("actions");
            if (ruleActions == null) continue;
            for (int k = 0; k < ruleActions.size(); k++) {
                JSONObject act = ruleActions.getJSONObject(k);
                if (act == null) continue;
                String vc = null;
                if (act.containsKey("varCode")) {
                    vc = act.getString("varCode");
                } else if (globalActionDefs != null && k < globalActionDefs.size()) {
                    JSONObject def = globalActionDefs.getJSONObject(k);
                    vc = def != null ? def.getString("varCode") : null;
                }
                if (vc != null && !vc.trim().isEmpty()) {
                    set.add(vc.trim());
                }
            }
        }
        return set;
    }

    /**
     * 输出单条规则 then 体内的赋值语句。
     */
    static void appendRuleAssignments(StringBuilder script, JSONArray ruleActions, JSONArray globalActionDefs) {
        for (int k = 0; k < ruleActions.size(); k++) {
            JSONObject act = ruleActions.getJSONObject(k);
            JSONObject actDef = k < globalActionDefs.size() ? globalActionDefs.getJSONObject(k) : null;

            String varCode;
            String varType;
            String value;

            if (act != null && act.containsKey("varCode")) {
                varCode = act.getString("varCode");
                varType = act.getString("varType");
                if (varType == null) varType = "STRING";
                value = act.getString("value");
            } else {
                varCode = actDef != null ? actDef.getString("varCode") : "out" + k;
                varType = actDef != null ? actDef.getString("varType") : "NUMBER";
                value = act != null ? act.getString("value") : null;
            }

            if (varCode == null || varCode.trim().isEmpty()) {
                continue;
            }
            if (value == null) {
                continue;
            }

            script.append("    ").append(varCode.trim()).append(" = ");
            if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                script.append("\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                script.append(value);
            }
            script.append(";\n");
        }
    }

    /**
     * 生成单条规则的条件布尔表达式：优先 {@code conditionRoot}，否则旧版列对齐的 AND。
     */
    static String buildRulePredicate(JSONObject rule, JSONArray legacyRuleConditions, JSONArray legacyColumnDefs) {
        JSONObject root = rule.getJSONObject("conditionRoot");
        if (root != null && !root.isEmpty() && "group".equals(root.getString("type"))) {
            return compileConditionNode(root);
        }
        return compileLegacyFlatAnd(legacyRuleConditions, legacyColumnDefs);
    }

    /**
     * 递归编译条件树节点（组或叶）。
     */
    static String compileConditionNode(JSONObject node) {
        if (node == null || node.isEmpty()) {
            return "true";
        }
        String type = node.getString("type");
        if ("group".equals(type)) {
            String op = node.getString("op");
            if (op == null) op = "AND";
            JSONArray children = node.getJSONArray("children");
            if (children == null || children.isEmpty()) {
                return "true";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    sb.append("AND".equals(op) ? " && " : " || ");
                }
                JSONObject ch = children.getJSONObject(i);
                sb.append(compileConditionNode(ch));
            }
            sb.append(")");
            return sb.toString();
        }
        if ("leaf".equals(type)) {
            return compileLeaf(node);
        }
        return "true";
    }

    /**
     * 编译叶条件：比较左侧变量与常量或其它变量。
     */
    static String compileLeaf(JSONObject leaf) {
        String varCode = leaf.getString("varCode");
        if (varCode == null || varCode.trim().isEmpty()) {
            return "true";
        }
        String operator = leaf.getString("operator");
        if (operator == null) operator = "==";
        if ("*".equals(operator)) {
            return "true";
        }

        String valueKind = leaf.getString("valueKind");
        if (valueKind == null) valueKind = "CONST";

        if ("VAR".equalsIgnoreCase(valueKind)) {
            String right = leaf.getString("value");
            if (right == null || right.trim().isEmpty()) {
                return "true";
            }
            return varCode + " " + operator + " " + right.trim();
        }

        String value = leaf.getString("value");
        if (value == null || value.isEmpty()) {
            return "true";
        }

        String varType = leaf.getString("varType");
        if (varType == null) varType = "STRING";

        String rhs = formatConstantRhs(varType, value);
        return varCode + " " + operator + " " + rhs;
    }

    /**
     * 按类型格式化常量右侧（引号与转义）。
     */
    static String formatConstantRhs(String varType, String value) {
        if ("STRING".equals(varType) || "ENUM".equals(varType) || "DATE".equals(varType)) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    /**
     * 旧版：多列条件，全部 AND。
     */
    static String compileLegacyFlatAnd(JSONArray ruleConditions, JSONArray conditions) {
        if (ruleConditions == null || ruleConditions.isEmpty()) {
            return "true";
        }
        StringBuilder script = new StringBuilder();
        for (int j = 0; j < ruleConditions.size(); j++) {
            if (j > 0) script.append(" && ");
            JSONObject cond = ruleConditions.getJSONObject(j);
            JSONObject condDef = j < conditions.size() ? conditions.getJSONObject(j) : null;
            String varCode = condDef != null ? condDef.getString("varCode") : "var" + j;
            String operator = cond.getString("operator");
            String value = cond.getString("value");

            if (value == null || value.isEmpty()) {
                script.append("true");
                continue;
            }

            String varType = condDef != null ? condDef.getString("varType") : "STRING";
            script.append(varCode).append(" ").append(operator).append(" ");
            if ("STRING".equals(varType) || "ENUM".equals(varType)) {
                script.append("\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                script.append(value);
            }
        }
        return script.toString();
    }
}
