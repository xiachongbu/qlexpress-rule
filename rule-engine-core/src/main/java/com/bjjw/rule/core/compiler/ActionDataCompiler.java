package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * actionData JSON → QLExpress 脚本生成器（后端 Java 版）
 *
 * 支持块类型：assign, if-block, switch-block, func-call, foreach, ternary, in-check, template-str, http-call
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
            case "http-call": return compileHttpCall(block, indent);
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
                // 舍入结果保留 BigDecimal，不转 double，避免重新引入二进制浮点误差。
                // 后续比较无 equals/scale 陷阱：QLExpress4 的 ==/!=/in/switch-case 对 Number
                // 均为数值语义（实测 0.10 == 0.1、1.00 in [1] 皆为 true）。
                sb.append("\n").append(pad(indent))
                  .append(target).append(" = (new java.math.BigDecimal(\"\" + ")
                  .append(target).append(")).setScale(").append(dp)
                  .append(", java.math.RoundingMode.").append(rm).append(")");
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

    /**
     * HTTP 调用块 → 内置 httpCall(config) 调用。
     * config 为 QL map 字面量；url / header value / 文本 body 均作为支持 ${} 插值的字符串字面量；
     * JSON body 作为表达式原样传入（由 httpCall 内部 JSON 序列化）。
     */
    private static String compileHttpCall(JSONObject b, int indent) {
        String url = b.getString("url");
        if (empty(url)) return "";
        StringBuilder cfg = new StringBuilder();
        cfg.append("{");
        cfg.append("\"url\": ").append(qlStringLiteral(url));

        String method = b.getString("method");
        if (empty(method)) method = "GET";
        cfg.append(", \"method\": ").append(qlStringLiteral(method.trim().toUpperCase()));

        JSONArray headers = b.getJSONArray("headers");
        String headerMap = buildHeaderMap(headers);
        if (headerMap != null) {
            cfg.append(", \"headers\": ").append(headerMap);
        }

        String bodyMode = b.getString("bodyMode");
        String body = b.getString("body");
        if ("json".equals(bodyMode)) {
            if (!empty(body)) cfg.append(", \"body\": ").append(body.trim());
        } else if ("text".equals(bodyMode)) {
            if (!empty(body)) cfg.append(", \"body\": ").append(qlStringLiteral(body));
        }

        Integer ct = b.getInteger("connectTimeout");
        if (ct != null && ct > 0) cfg.append(", \"connectTimeout\": ").append(ct);
        Integer rt = b.getInteger("readTimeout");
        if (rt != null && rt > 0) cfg.append(", \"readTimeout\": ").append(rt);

        cfg.append("}");
        String call = "httpCall(" + cfg + ")";
        String target = b.getString("target");
        return pad(indent) + (!empty(target) ? target + " = " + call : call);
    }

    /**
     * headers 数组 [{key,value}] → QL map 字面量 {\"k\": \"v\"}；
     * value 作为支持 ${} 插值的字符串字面量。无有效项时返回 null。
     */
    private static String buildHeaderMap(JSONArray headers) {
        if (headers == null || headers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (int i = 0; i < headers.size(); i++) {
            JSONObject h = headers.getJSONObject(i);
            if (h == null) continue;
            String k = h.getString("key");
            if (empty(k)) continue;
            if (cnt > 0) sb.append(", ");
            sb.append(qlStringLiteral(k.trim())).append(": ").append(qlStringLiteral(h.getString("value")));
            cnt++;
        }
        if (cnt == 0) return null;
        return "{" + sb + "}";
    }

    /**
     * 文本 → QL 字符串字面量（保留 ${} 插值）；转义反斜杠与双引号，null 视为空串。
     */
    private static String qlStringLiteral(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
        String cond = QlCompareExpression.emitStructuredCondition(condVar, op, b.getString("condValue"), b.getString("condVarType"));
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

    /**
     * 条件分支上的可视化条件 → QL 布尔表达式。
     * condVarType 为前端选择条件变量时记录的变量类型，用于决定常量是否加引号。
     */
    private static String buildCond(JSONObject branch) {
        String v = branch.getString("condVar");
        if (empty(v)) return "true";
        String op = branch.getString("condOp");
        if (empty(op)) op = "==";
        return QlCompareExpression.emitStructuredCondition(v, op, branch.getString("condValue"), branch.getString("condVarType"));
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
