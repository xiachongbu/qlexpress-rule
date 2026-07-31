package com.bjjw.rule.core.compiler;

import com.bjjw.rule.model.entity.RuleVariable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 常量前缀构建器：将项目常量（{@code var_source=CONSTANT}）生成 QLExpress 赋值序言，
 * 拼接在编译脚本之前，使脚本中引用的常量名（scriptName）在执行时可解析为其固化值。
 * <p>
 * 常量值在编译时固化进脚本（预览/试跑/发布三处一致）；常量变更后需重新保存或发布规则方可生效，
 * 符合常量不可变语义。仅内联可安全表示的标量类型（NUMBER/BOOLEAN/STRING/ENUM/DATE），
 * 复杂类型（LIST/MAP/OBJECT）或非法值跳过，避免单个坏值破坏整段脚本。
 */
public final class ConstantPrefixBuilder {

    /** QLExpress 合法标识符（脚本变量名） */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** SCRIPT 类型序言块起始界定注释（compiledScript 会回流为编辑源，需可识别以便幂等剥离重建） */
    public static final String MARKER_BEGIN = "// ==== 常量序言(自动生成，保存时按最新常量重建，请勿手改) ====";

    /** SCRIPT 类型序言块结束界定注释 */
    public static final String MARKER_END = "// ==== 常量序言结束 ====";

    private ConstantPrefixBuilder() {
    }

    /**
     * 将常量序言包裹在界定注释中，供 SCRIPT 类型编译产物使用；
     * 脚本模式下编辑产物回流保存时可通过 {@link #stripMarkedPrefix} 幂等剥离后重建。
     *
     * @param prefix {@link #build} 生成的序言
     * @return 带界定注释的序言块，序言为空时返回空字符串
     */
    public static String wrapWithMarkers(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        return MARKER_BEGIN + "\n" + prefix + MARKER_END + "\n";
    }

    /**
     * 剥离脚本开头已存在的带界定注释序言块（循环剥离，防历史叠加）；
     * 无界定块或块不在开头（前面存在非空白内容）时原样返回。
     */
    public static String stripMarkedPrefix(String script) {
        if (script == null) {
            return null;
        }
        String s = script;
        while (true) {
            int begin = s.indexOf(MARKER_BEGIN);
            if (begin < 0 || !s.substring(0, begin).trim().isEmpty()) {
                break;
            }
            int end = s.indexOf(MARKER_END, begin);
            if (end < 0) {
                break;
            }
            int cut = end + MARKER_END.length();
            if (cut < s.length() && s.charAt(cut) == '\r') {
                cut++;
            }
            if (cut < s.length() && s.charAt(cut) == '\n') {
                cut++;
            }
            s = s.substring(cut);
        }
        return s;
    }

    /**
     * 生成常量赋值序言，如 {@code taxRate = 0.13;\nregionCode = "110000";\n}。
     *
     * @param variables 项目变量列表（内部自行过滤 CONSTANT）
     * @return 序言脚本，无可用常量时为空字符串
     */
    public static String build(List<RuleVariable> variables) {
        if (variables == null || variables.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RuleVariable v : variables) {
            if (v == null || !"CONSTANT".equals(v.getVarSource())) {
                continue;
            }
            String name = scriptName(v);
            if (name == null || !IDENTIFIER.matcher(name).matches()) {
                continue;
            }
            String literal = toQlLiteral(v.getVarType(), v.getDefaultValue());
            if (literal == null) {
                continue;
            }
            sb.append(name).append(" = ").append(literal).append(";\n");
        }
        return sb.toString();
    }

    /**
     * 收集项目内全部常量的脚本名集合，供编译器将常量从「输出变量/赋值目标」中排除，
     * 避免常量被预声明为 null 覆盖序言值，或被误当作赋值目标写入结果 Map。
     *
     * @param variables 项目变量列表（内部自行过滤 CONSTANT）
     * @return 常量脚本名集合（保持顺序、去重）
     */
    public static LinkedHashSet<String> constantNames(List<RuleVariable> variables) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (variables == null) {
            return names;
        }
        for (RuleVariable v : variables) {
            if (v == null || !"CONSTANT".equals(v.getVarSource())) {
                continue;
            }
            String name = scriptName(v);
            if (name != null && !name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 判断给定变量名是否属于常量集合（空集合恒为 false）。
     */
    public static boolean isConstantName(Collection<String> constantNames, String varCode) {
        if (constantNames == null || constantNames.isEmpty() || varCode == null) {
            return false;
        }
        return constantNames.contains(varCode.trim());
    }

    /** 常量在脚本中的引用名：优先 scriptName，回退 varCode。 */
    private static String scriptName(RuleVariable v) {
        String s = v.getScriptName();
        if (s != null && !s.trim().isEmpty()) {
            return s.trim();
        }
        return v.getVarCode();
    }

    /**
     * 按变量类型将默认值转为 QLExpress 字面量；无法安全内联时返回 null。
     */
    private static String toQlLiteral(String varType, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return null;
        }
        String type = varType == null ? "" : varType;
        switch (type) {
            case "NUMBER":
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException e) {
                    return null;
                }
                return value;
            case "BOOLEAN":
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return value.toLowerCase();
                }
                return null;
            case "STRING":
            case "ENUM":
            case "DATE":
                return quote(value);
            default:
                // LIST/MAP/OBJECT 等复杂类型暂不内联，避免生成非法脚本
                return null;
        }
    }

    /** 转义并加双引号，保证生成的字符串字面量语法合法 */
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
