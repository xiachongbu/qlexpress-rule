package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * actionData JSON → QLExpress 脚本生成器（后端 Java 版）
 *
 * 支持块类型：assign, if-block, switch-block, func-call, foreach, ternary, in-check, template-str
 */
public class ActionDataCompiler {

    public static String compile(JSONArray actionData) {
        if (actionData == null || actionData.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actionData.size(); i++) {
            String code = compileBlock(actionData.getJSONObject(i), 0);
            if (code != null && !code.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(code);
            }
        }
        return sb.toString();
    }

    private static String compileBlock(JSONObject block, int indent) {
        if (block == null) return "";
        String type = block.getString("type");
        if (type == null) return "";
        switch (type) {
            case "assign": return compileAssign(block, indent);
            case "if-block": return compileIfBlock(block, indent);
            case "switch-block": return compileSwitchBlock(block, indent);
            case "func-call": return compileFuncCall(block, indent);
            case "foreach": return compileForeach(block, indent);
            case "ternary": return compileTernary(block, indent);
            case "in-check": return compileInCheck(block, indent);
            case "template-str": return compileTemplateStr(block, indent);
            default: return "";
        }
    }

    private static String compileAssign(JSONObject b, int indent) {
        String target = b.getString("target");
        String value = b.getString("value");
        if (empty(target) || empty(value)) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(pad(indent)).append(target).append(" = ").append(value);
        Boolean enableRounding = b.getBoolean("enableRounding");
        if (Boolean.TRUE.equals(enableRounding)) {
            Integer dp = b.getInteger("decimalPlaces");
            if (dp != null && dp >= 0) {
                String rm = b.getString("roundingMode");
                if (empty(rm)) rm = "HALF_UP";
                sb.append("\n").append(pad(indent))
                  .append(target).append(" = (new java.math.BigDecimal(\"\" + ")
                  .append(target).append(")).setScale(").append(dp)
                  .append(", java.math.RoundingMode.").append(rm).append(").doubleValue()");
            }
        }
        return sb.toString();
    }

    private static String compileIfBlock(JSONObject b, int indent) {
        JSONArray branches = b.getJSONArray("branches");
        if (branches == null || branches.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < branches.size(); i++) {
            JSONObject br = branches.getJSONObject(i);
            String bt = br.getString("type");
            if ("if".equals(bt)) sb.append(pad(indent)).append("if (").append(buildCond(br)).append(") {\n");
            else if ("elseif".equals(bt)) sb.append(pad(indent)).append("} else if (").append(buildCond(br)).append(") {\n");
            else sb.append(pad(indent)).append("} else {\n");
            sb.append(compileActions(br.getJSONArray("actions"), indent + 1));
        }
        sb.append(pad(indent)).append("}");
        return sb.toString();
    }

    private static String compileSwitchBlock(JSONObject b, int indent) {
        String matchVar = b.getString("matchVar");
        if (empty(matchVar)) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(pad(indent)).append("switch (").append(matchVar).append(") {\n");
        JSONArray cases = b.getJSONArray("cases");
        if (cases != null) {
            for (int i = 0; i < cases.size(); i++) {
                JSONObject c = cases.getJSONObject(i);
                String val = c.getString("value");
                if (empty(val)) continue;
                sb.append(pad(indent + 1)).append("case ").append(wrapValue(val)).append(" -> {\n");
                sb.append(compileActions(c.getJSONArray("actions"), indent + 2));
                sb.append(pad(indent + 1)).append("}\n");
            }
        }
        JSONArray defaults = b.getJSONArray("defaultActions");
        if (defaults != null && !defaults.isEmpty()) {
            sb.append(pad(indent + 1)).append("default -> {\n");
            sb.append(compileActions(defaults, indent + 2));
            sb.append(pad(indent + 1)).append("}\n");
        }
        sb.append(pad(indent)).append("}");
        return sb.toString();
    }

    private static String compileFuncCall(JSONObject b, int indent) {
        String funcName = b.getString("funcName");
        if (empty(funcName)) return "";
        JSONArray args = b.getJSONArray("args");
        StringBuilder ab = new StringBuilder();
        if (args != null) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) ab.append(", ");
                ab.append(args.getString(i));
            }
        }
        String call = funcName + "(" + ab + ")";
        String target = b.getString("target");
        return pad(indent) + (!empty(target) ? target + " = " + call : call);
    }

    private static String compileForeach(JSONObject b, int indent) {
        String itemVar = b.getString("itemVar");
        String listExpr = b.getString("listExpr");
        if (empty(itemVar) || empty(listExpr)) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(pad(indent)).append("for (").append(itemVar).append(" : ").append(listExpr).append(") {\n");
        sb.append(compileActions(b.getJSONArray("actions"), indent + 1));
        sb.append(pad(indent)).append("}");
        return sb.toString();
    }

    private static String compileTernary(JSONObject b, int indent) {
        String target = b.getString("target");
        String condVar = b.getString("condVar");
        if (empty(target) || empty(condVar)) return "";
        String op = b.getString("condOp");
        if (empty(op)) op = "==";
        String cond = condVar + " " + op + " " + wrapValue(b.getString("condValue"));
        String tv = b.getString("trueValue");
        String fv = b.getString("falseValue");
        return pad(indent) + target + " = " + cond + " ? " + (empty(tv) ? "\"\"" : tv) + " : " + (empty(fv) ? "\"\"" : fv);
    }

    private static String compileInCheck(JSONObject b, int indent) {
        String target = b.getString("target");
        String checkVar = b.getString("checkVar");
        if (empty(target) || empty(checkVar)) return "";
        JSONArray vals = b.getJSONArray("inValues");
        StringBuilder vb = new StringBuilder();
        if (vals != null) {
            for (int i = 0; i < vals.size(); i++) {
                String v = vals.getString(i);
                if (v != null && !v.trim().isEmpty()) {
                    if (vb.length() > 0) vb.append(", ");
                    vb.append(wrapValue(v));
                }
            }
        }
        String tv = b.getString("trueValue");
        String fv = b.getString("falseValue");
        return pad(indent) + target + " = " + checkVar + " in [" + vb + "] ? " + (empty(tv) ? "true" : tv) + " : " + (empty(fv) ? "false" : fv);
    }

    private static String compileTemplateStr(JSONObject b, int indent) {
        String target = b.getString("target");
        JSONArray parts = b.getJSONArray("parts");
        if (empty(target) || parts == null || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            JSONObject p = parts.getJSONObject(i);
            if ("expr".equals(p.getString("type"))) sb.append("${").append(p.getString("content")).append("}");
            else sb.append(p.getString("content"));
        }
        return pad(indent) + target + " = \"" + sb.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String compileActions(JSONArray actions, int indent) {
        if (actions == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actions.size(); i++) {
            String code = compileBlock(actions.getJSONObject(i), indent);
            if (code != null && !code.isEmpty()) {
                sb.append(code).append("\n");
            }
        }
        return sb.toString();
    }

    private static String buildCond(JSONObject branch) {
        String v = branch.getString("condVar");
        if (empty(v)) return "true";
        String op = branch.getString("condOp");
        if (empty(op)) op = "==";
        return v + " " + op + " " + wrapValue(branch.getString("condValue"));
    }

    private static String wrapValue(String val) {
        if (val == null || val.isEmpty()) return "\"\"";
        String s = val.trim();
        if ("true".equals(s) || "false".equals(s) || "null".equals(s)) return s;
        try { Double.parseDouble(s); return s; } catch (NumberFormatException ignored) {}
        if (s.matches("[a-zA-Z_]\\w*(\\.\\w+)*")) return s;
        if (s.startsWith("\"") || s.startsWith("'")) return s;
        if (s.matches(".*[+\\-*/()><=!&|,\\[\\]{}].*")) return s;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }

    private static String pad(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) sb.append("    ");
        return sb.toString();
    }
}
