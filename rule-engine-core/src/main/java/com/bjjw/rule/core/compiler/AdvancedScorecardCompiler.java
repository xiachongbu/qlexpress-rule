package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

/**
 * 复杂评分卡编译器：支持分组维度 + 结构化条件。
 * 同一维度内的规则互斥（if/else if），维度间得分累加。
 */
public class AdvancedScorecardCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            double initialScore = model.getDoubleValue("initialScore");
            JSONObject resultVar = model.getJSONObject("resultVar");
            JSONArray dimensionGroups = model.getJSONArray("dimensionGroups");
            JSONArray thresholds = model.getJSONArray("thresholds");

            String resCode = resultVar != null ? resultVar.getString("varCode") : "totalScore";

            StringBuilder script = new StringBuilder();
            script.append(resCode).append(" = ").append(initialScore).append("\n");

            if (dimensionGroups != null) {
                for (int g = 0; g < dimensionGroups.size(); g++) {
                    JSONObject group = dimensionGroups.getJSONObject(g);
                    String groupLabel = group.getString("groupLabel");
                    JSONArray dimensions = group.getJSONArray("dimensions");
                    if (dimensions == null) continue;

                    script.append("\n// ---- ").append(groupLabel != null ? groupLabel : "维度组" + (g + 1)).append(" ----\n");

                    for (int d = 0; d < dimensions.size(); d++) {
                        JSONObject dim = dimensions.getJSONObject(d);
                        String dimLabel = dim.getString("varLabel");
                        JSONArray rules = dim.getJSONArray("rules");
                        if (rules == null || rules.isEmpty()) continue;

                        String dimScoreVar = "_dim_" + g + "_" + d;
                        script.append(dimScoreVar).append(" = 0\n");

                        for (int r = 0; r < rules.size(); r++) {
                            JSONObject rule = rules.getJSONObject(r);
                            JSONArray conditions = rule.getJSONArray("conditions");
                            double score = rule.getDoubleValue("score");

                            script.append(r == 0 ? "if (" : " else if (");
                            appendConditions(script, conditions);
                            script.append(") {\n    ");
                            script.append(dimScoreVar).append(" = ").append(score);
                            script.append("\n}");
                        }
                        script.append("\n");

                        script.append("// ").append(dimLabel != null ? dimLabel : "维度" + (d + 1))
                              .append(" 得分累加\n");
                        script.append(resCode).append(" = ").append(resCode)
                              .append(" + ").append(dimScoreVar).append("\n");
                    }
                }
            }

            if (thresholds != null && !thresholds.isEmpty()) {
                script.append("\n// ---- 等级判定 ----\n");
                String levelVar = "riskLevel";

                JSONObject firstThreshold = thresholds.getJSONObject(0);
                if (firstThreshold.containsKey("resultVar")) {
                    levelVar = firstThreshold.getString("resultVar");
                }

                script.append(levelVar).append(" = \"未知\"\n");
                for (int i = 0; i < thresholds.size(); i++) {
                    JSONObject th = thresholds.getJSONObject(i);
                    double min = th.getDoubleValue("min");
                    double max = th.getDoubleValue("max");
                    String result = th.getString("result");

                    script.append(i == 0 ? "if (" : " else if (");
                    script.append(resCode).append(" >= ").append(min)
                          .append(" && ").append(resCode).append(" < ").append(max);
                    script.append(") {\n    ").append(levelVar)
                          .append(" = \"").append(escapeForQlDoubleQuotedString(result)).append("\"\n}");
                }
                script.append("\n");
            }

            // 确定等级变量名，将分数和等级组合为 Map 返回
            String levelVarForResult = "riskLevel";
            if (thresholds != null && !thresholds.isEmpty()) {
                JSONObject firstTh = thresholds.getJSONObject(0);
                if (firstTh.containsKey("resultVar")) {
                    levelVarForResult = firstTh.getString("resultVar");
                }
            }
            LinkedHashSet<String> outVars = new LinkedHashSet<>();
            outVars.add(resCode);
            if (thresholds != null && !thresholds.isEmpty()) {
                outVars.add(levelVarForResult);
            }
            RuleScriptResultCollector.appendResultMapReturn(script, outVars);

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("复杂评分卡编译失败: " + e.getMessage());
        }
    }

    /**
     * 将阈值档位文案转义后嵌入 QLExpress 的双引号字符串字面量，避免引号或反斜杠破坏脚本语法。
     */
    private static String escapeForQlDoubleQuotedString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 将结构化条件数组拼接为 QLExpress 表达式 */
    private void appendConditions(StringBuilder sb, JSONArray conditions) {
        if (conditions == null || conditions.isEmpty()) {
            sb.append("true");
            return;
        }
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(" && ");
            JSONObject cond = conditions.getJSONObject(i);
            String varCode = cond.getString("varCode");
            String operator = cond.getString("operator");
            String value = cond.getString("value");

            sb.append(varCode).append(" ").append(operator).append(" ");
            if (isNumericValue(value)) {
                sb.append(value);
            } else {
                sb.append("\"").append(value.replace("\"", "\\\"")).append("\"");
            }
        }
    }

    private boolean isNumericValue(String value) {
        if (value == null) return false;
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
