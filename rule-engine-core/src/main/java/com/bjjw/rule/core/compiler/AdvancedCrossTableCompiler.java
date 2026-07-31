package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 复杂交叉表编译器：支持多维行/列交叉 + 区间匹配。
 * 将多维分段的笛卡尔积生成 if/else if 链，每条为所有维度条件的 AND 组合。
 */
public class AdvancedCrossTableCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, Collections.emptySet(), null);
    }

    @Override
    public CompileResult compile(String modelJson, Set<String> constantNames, String constantPrefix) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            JSONArray rowDims = model.getJSONArray("rowDimensions");
            JSONArray colDims = model.getJSONArray("colDimensions");
            JSONObject resultVar = model.getJSONObject("resultVar");
            JSONArray cells = model.getJSONArray("cells");

            if (rowDims == null || rowDims.isEmpty()) {
                return CompileResult.fail("复杂交叉表缺少行维度定义");
            }
            if (colDims == null || colDims.isEmpty()) {
                return CompileResult.fail("复杂交叉表缺少列维度定义");
            }
            if (resultVar == null) {
                return CompileResult.fail("复杂交叉表缺少结果变量定义");
            }
            if (cells == null) {
                return CompileResult.fail("复杂交叉表缺少单元格数据");
            }

            String resCode = resultVar.getString("varCode");
            String resType = resultVar.getString("varType");

            // 结果变量是赋值目标，常量不允许被赋值（会覆盖常量序言的固化值）
            if (ConstantPrefixBuilder.isConstantName(constantNames, resCode)) {
                return CompileResult.fail("结果变量「" + resCode + "」是常量，常量不允许被赋值，请改选普通变量");
            }

            List<List<SegmentInfo>> rowProduct = cartesianProduct(rowDims);
            List<List<SegmentInfo>> colProduct = cartesianProduct(colDims);

            StringBuilder script = new StringBuilder();
            boolean first = true;

            for (int ri = 0; ri < rowProduct.size(); ri++) {
                List<SegmentInfo> rowSegs = rowProduct.get(ri);
                JSONArray rowCells = cells.getJSONArray(ri);
                if (rowCells == null) continue;

                for (int ci = 0; ci < colProduct.size(); ci++) {
                    List<SegmentInfo> colSegs = colProduct.get(ci);
                    String cellValue = getCellValue(rowCells, ci, colProduct.size());
                    if (cellValue == null || cellValue.trim().isEmpty()) continue;

                    script.append(first ? "if (" : " else if (");
                    first = false;

                    boolean firstCond = true;
                    for (SegmentInfo seg : rowSegs) {
                        if (!firstCond) script.append(" && ");
                        firstCond = false;
                        appendCondition(script, seg);
                    }
                    for (SegmentInfo seg : colSegs) {
                        if (!firstCond) script.append(" && ");
                        firstCond = false;
                        appendCondition(script, seg);
                    }

                    script.append(") {\n    ").append(resCode).append(" = ");
                    appendValue(script, cellValue, resType);
                    script.append("\n}");
                }
            }
            if (!first) {
                script.append("\n");
            }

            // 常量赋值序言前置到脚本最前，使脚本内引用的常量解析为固化值
            if (constantPrefix != null && !constantPrefix.isEmpty()) {
                script.insert(0, constantPrefix);
            }

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("复杂交叉表编译失败: " + e.getMessage());
        }
    }

    /** 计算所有维度分段的笛卡尔积 */
    private List<List<SegmentInfo>> cartesianProduct(JSONArray dimensions) {
        List<List<SegmentInfo>> result = new ArrayList<>();
        result.add(new ArrayList<>());

        for (int d = 0; d < dimensions.size(); d++) {
            JSONObject dim = dimensions.getJSONObject(d);
            String varCode = dim.getString("varCode");
            String varType = dim.getString("varType");
            JSONArray segments = dim.getJSONArray("segments");

            List<List<SegmentInfo>> newResult = new ArrayList<>();
            for (List<SegmentInfo> existing : result) {
                for (int s = 0; s < segments.size(); s++) {
                    JSONObject seg = segments.getJSONObject(s);
                    List<SegmentInfo> newList = new ArrayList<>(existing);
                    newList.add(new SegmentInfo(varCode, varType,
                            seg.getString("operator"),
                            seg.getString("value"),
                            seg.getString("min"),
                            seg.getString("max")));
                    newResult.add(newList);
                }
            }
            result = newResult;
        }
        return result;
    }

    /** 从多层嵌套的 cells 数组中取值 */
    private String getCellValue(JSONArray rowCells, int colIndex, int totalCols) {
        try {
            Object val = rowCells.get(colIndex);
            if (val instanceof JSONArray) {
                return ((JSONArray) val).getString(0);
            }
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 将单个分段条件追加到脚本 */
    private void appendCondition(StringBuilder sb, SegmentInfo seg) {
        sb.append(QlCompareExpression.emitSegmentCondition(
                seg.varCode, seg.varType, seg.operator, seg.value, seg.min, seg.max));
    }

    private void appendValue(StringBuilder sb, String value, String type) {
        if ("STRING".equals(type) || "ENUM".equals(type)) {
            sb.append("\"").append(value.replace("\"", "\\\"")).append("\"");
        } else {
            sb.append(value);
        }
    }

    /** 维度分段信息 */
    private static class SegmentInfo {
        final String varCode;
        final String varType;
        final String operator;
        final String value;
        final String min;
        final String max;

        SegmentInfo(String varCode, String varType, String operator, String value, String min, String max) {
            this.varCode = varCode;
            this.varType = varType;
            this.operator = operator;
            this.value = value;
            this.min = min;
            this.max = max;
        }
    }
}
