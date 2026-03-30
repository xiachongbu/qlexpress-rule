package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

public class ScorecardCompiler implements RuleCompiler {

    /**
     * 将阈值档位文案转义后嵌入 QLExpress 的双引号字符串字面量，避免引号或反斜杠破坏脚本语法。
     */
    private static String escapeForQlDoubleQuotedString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public CompileResult compile(String modelJson) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            double initialScore = model.getDoubleValue("initialScore");
            JSONArray items = model.getJSONArray("scoreItems");
            JSONObject resultVar = model.getJSONObject("resultVar");
            JSONArray thresholds = model.getJSONArray("thresholds");

            String resCode = resultVar != null ? resultVar.getString("varCode") : "totalScore";

            StringBuilder script = new StringBuilder();
            script.append(resCode).append(" = ").append(initialScore).append("\n\n");

            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String cond = item.getString("condition");
                    double score = item.getDoubleValue("score");
                    double weight = item.containsKey("weight") ? item.getDoubleValue("weight") : 1.0;

                    script.append("if (").append(cond).append(") {\n");
                    script.append("    ").append(resCode).append(" = ").append(resCode)
                          .append(" + ").append(score * weight).append("\n");
                    script.append("}\n\n");
                }
            }

            String levelVar = "riskLevel";
            if (thresholds != null && thresholds.size() > 0) {
                JSONObject firstTh = thresholds.getJSONObject(0);
                if (firstTh.containsKey("resultVar")) {
                    String rv = firstTh.getString("resultVar");
                    if (rv != null && !rv.isEmpty()) {
                        levelVar = rv;
                    }
                }
                script.append(levelVar).append(" = \"未知\"\n");
                for (int i = 0; i < thresholds.size(); i++) {
                    JSONObject th = thresholds.getJSONObject(i);
                    double min = th.getDoubleValue("min");
                    double max = th.getDoubleValue("max");
                    String result = th.getString("result");

                    script.append(i == 0 ? "if (" : " else if (");
                    script.append(resCode).append(" >= ").append(min).append(" && ")
                          .append(resCode).append(" < ").append(max).append(") {\n");
                    script.append("    ").append(levelVar).append(" = \"")
                          .append(escapeForQlDoubleQuotedString(result)).append("\"\n}");
                }
                script.append("\n");
            }

            // 配置了阈值时返回 Map（分数变量名 + 档位变量名），与复杂评分卡一致；否则仅返回数值，保持兼容
            if (thresholds != null && thresholds.size() > 0) {
                LinkedHashSet<String> outVars = new LinkedHashSet<>();
                outVars.add(resCode);
                outVars.add(levelVar);
                RuleScriptResultCollector.appendResultMapReturn(script, outVars);
            } else {
                script.append(resCode).append("\n");
            }

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("评分卡编译失败: " + e.getMessage());
        }
    }
}
