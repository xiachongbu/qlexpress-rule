package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CrossTableCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, Collections.emptySet(), null);
    }

    @Override
    public CompileResult compile(String modelJson, Set<String> constantNames, String constantPrefix) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            JSONObject rowVar = model.getJSONObject("rowVar");
            JSONObject colVar = model.getJSONObject("colVar");
            JSONObject resultVar = model.getJSONObject("resultVar");
            JSONArray rowHeaders = model.getJSONArray("rowHeaders");
            JSONArray colHeaders = model.getJSONArray("colHeaders");
            JSONArray cells = model.getJSONArray("cells");

            if (rowVar == null || colVar == null || resultVar == null) {
                return CompileResult.fail("交叉表缺少行变量、列变量或结果变量定义");
            }

            if (rowHeaders == null || colHeaders == null || cells == null) {
                return CompileResult.fail("交叉表缺少行表头、列表头或单元格数据");
            }

            String rowCode = rowVar.getString("varCode");
            String rowType = rowVar.getString("varType");
            String colCode = colVar.getString("varCode");
            String colType = colVar.getString("varType");
            String resCode = resultVar.getString("varCode");
            String resType = resultVar.getString("varType");

            // 结果变量是赋值目标，常量不允许被赋值（会覆盖常量序言的固化值）
            if (ConstantPrefixBuilder.isConstantName(constantNames, resCode)) {
                return CompileResult.fail("结果变量「" + resCode + "」是常量，常量不允许被赋值，请改选普通变量");
            }

            StringBuilder script = new StringBuilder();
            boolean first = true;

            for (int r = 0; r < rowHeaders.size(); r++) {
                for (int c = 0; c < colHeaders.size(); c++) {
                    String cellValue = cells.getJSONArray(r).getString(c);
                    if (cellValue == null || cellValue.trim().isEmpty()) continue;

                    script.append(first ? "if (" : " else if (");
                    first = false;

                    script.append(rowCode).append(" == ");
                    appendValue(script, rowHeaders.getString(r), rowType);
                    script.append(" && ").append(colCode).append(" == ");
                    appendValue(script, colHeaders.getString(c), colType);

                    script.append(") {\n    ").append(resCode).append(" = ");
                    appendValue(script, cellValue, resType);
                    script.append("\n}");
                }
            }
            script.append("\n");

            // 解析输出变量初始值配置，为结果变量添加初始化
            Map<String, String> initValueMap = RuleScriptResultCollector.parseOutputVarInits(model);
            LinkedHashSet<String> outputVars = new LinkedHashSet<>();
            outputVars.add(resCode);
            RuleScriptResultCollector.prependOutputInits(script, outputVars, initValueMap);
            RuleScriptResultCollector.appendResultMapReturn(script, outputVars);

            // 常量赋值序言前置到脚本最前，使脚本内引用的常量解析为固化值
            if (constantPrefix != null && !constantPrefix.isEmpty()) {
                script.insert(0, constantPrefix);
            }

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("交叉表编译失败: " + e.getMessage());
        }
    }

    private void appendValue(StringBuilder sb, String value, String type) {
        if ("STRING".equals(type) || "ENUM".equals(type)) {
            sb.append("\"").append(value.replace("\"", "\\\"")).append("\"");
        } else {
            sb.append(value);
        }
    }
}
