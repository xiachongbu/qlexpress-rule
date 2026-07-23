package com.bjjw.rule.core.compiler;

/**
 * 将规则设计器中的比较运算符编译为 QLExpress（Java 语义）可执行的表达式片段。
 * <p>
 * 字符串类运算使用 {@code toString()} 与 {@code String.valueOf}，避免左侧为非字符串类型时脚本无法执行。
 */
public final class QlCompareExpression {

    private QlCompareExpression() {
    }

    /**
     * 将常量右侧按变量类型格式化为 QL 字面量（字符串加双引号并转义）。
     */
    public static String formatConstantRhs(String varType, String value) {
        if (value == null) {
            return "\"\"";
        }
        String t = varType != null ? varType : "STRING";
        if ("STRING".equals(t) || "ENUM".equals(t) || "DATE".equals(t)) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    /**
     * 编译叶节点比较：支持普通二元运算符与字符串包含/前匹配/后匹配。
     *
     * @param varCode    左侧变量编码（QL 表达式左值）
     * @param operator   运算符（如 ==、contains）
     * @param valueKind  CONST 或 VAR
     * @param varType    左侧/常量类型（格式化常量用）
     * @param value      常量值或右侧变量编码
     * @return 可嵌入 if/&& 的布尔表达式；缺参时返回 "true"
     */
    public static String emitLeafComparison(String varCode, String operator, String valueKind, String varType, String value) {
        if (varCode == null || varCode.trim().isEmpty()) {
            return "true";
        }
        String op = operator != null ? operator : "==";
        if ("*".equals(op)) {
            return "true";
        }

        String kind = valueKind != null ? valueKind : "CONST";
        boolean varRhs = "VAR".equalsIgnoreCase(kind);

        if (varRhs) {
            if (value == null || value.trim().isEmpty()) {
                return "true";
            }
            String rhsVar = value.trim();
            return emitWithRhs(varCode, op, rhsVar, true);
        }

        if (value == null || value.isEmpty()) {
            return "true";
        }
        String vt = varType != null ? varType : "STRING";
        String rhsLiteral = formatConstantRhs(vt, value);
        return emitWithRhs(varCode, op, rhsLiteral, false);
    }

    /**
     * 结构化条件（如复杂评分卡）中单条比较：右侧为字符串原值，数值型无引号。
     */
    public static String emitStructuredCondition(String varCode, String operator, String value) {
        if (varCode == null || varCode.trim().isEmpty()) {
            return "true";
        }
        String op = operator != null ? operator : "==";
        if (value == null || value.isEmpty()) {
            return "true";
        }
        String rhs = isNumericValue(value) ? value : formatConstantRhs("STRING", value);
        return emitWithRhs(varCode, op, rhs, false);
    }

    /**
     * 交叉表维度分段：与 {@link #emitLeafComparison} 相同语义，常量按维度 varType 格式化。
     */
    public static String emitSegmentCondition(String varCode, String varType, String operator, String value, String min, String max) {
        if (varCode == null || varCode.trim().isEmpty()) {
            return "true";
        }
        String op = operator != null ? operator : "==";
        if ("range".equals(op)) {
            String vt = varType != null ? varType : "STRING";
            StringBuilder sb = new StringBuilder();
            sb.append(varCode).append(" >= ");
            sb.append(formatConstantRhs(vt, min != null ? min : ""));
            sb.append(" && ").append(varCode).append(" < ");
            sb.append(formatConstantRhs(vt, max != null ? max : ""));
            return sb.toString();
        }
        if (value == null || value.isEmpty()) {
            return "true";
        }
        String vt = varType != null ? varType : "STRING";
        String rhs = formatConstantRhs(vt, value);
        return emitWithRhs(varCode, op, rhs, false);
    }

    /**
     * 按运算符输出比较片段；字符串三运算右侧已为字面量或变量标识符。
     */
    private static String emitWithRhs(String varCode, String op, String rhsExpr, boolean rhsIsVariable) {
        switch (op) {
            case "contains":
                if (rhsIsVariable) {
                    return "(" + varCode + " != null && " + varCode + ".toString().contains(String.valueOf(" + rhsExpr + ")))";
                }
                return "(" + varCode + " != null && " + varCode + ".toString().contains(" + rhsExpr + "))";
            case "startsWith":
                if (rhsIsVariable) {
                    return "(" + varCode + " != null && " + varCode + ".toString().startsWith(String.valueOf(" + rhsExpr + ")))";
                }
                return "(" + varCode + " != null && " + varCode + ".toString().startsWith(" + rhsExpr + "))";
            case "endsWith":
                if (rhsIsVariable) {
                    return "(" + varCode + " != null && " + varCode + ".toString().endsWith(String.valueOf(" + rhsExpr + ")))";
                }
                return "(" + varCode + " != null && " + varCode + ".toString().endsWith(" + rhsExpr + "))";
            default:
                return varCode + " " + op + " " + rhsExpr;
        }
    }

    private static boolean isNumericValue(String value) {
        if (value == null) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
