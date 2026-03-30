package com.bjjw.rule.server.service.parser;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.model.dto.*;
import com.bjjw.rule.core.util.ScriptNameUtil;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaEntityParser {

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:public\\s+)?class\\s+(\\w+)(?:\\s+extends\\s+\\w+)?(?:\\s+implements\\s+[\\w,\\s]+)?\\s*\\{");

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "(?:private|protected|public)\\s+(?!static\\s)(\\S+)\\s+(\\w+)\\s*;");

    /** 匹配到 {@code =} 为止；初始化器可能跨多行（数组、List、Map 等），由 {@link #extractConstantInitializer} 单独截取 */
    private static final Pattern CONST_HEAD = Pattern.compile(
            "(?:public|private|protected)?\\s*static\\s+final\\s+(\\S+)\\s+(\\w+)\\s*=");

    private static final Pattern LIST_FACTORY_CALL = Pattern.compile(
            "(?i)^(Arrays\\.asList|List\\.of|Set\\.of|ImmutableList\\.of|Collections\\.singletonList)\\s*\\(");

    private static final Pattern NEW_ARRAY_INIT = Pattern.compile(
            "(?i)^new\\s+[\\w.$]+(?:\\s*\\[\\s*\\])+(?:\\s*\\[\\s*\\])*\\s*\\{");

    private static final Pattern MAP_OF_CALL = Pattern.compile("(?i)^Map\\.of\\s*\\(");

    private static final Pattern GENERIC_PATTERN = Pattern.compile(
            "(\\w+)<(.+)>");

    /** 实例字段整行（含行尾 // 注释），用于与文档/注解扫描对齐 */
    private static final Pattern FIELD_LINE_PATTERN = Pattern.compile(
            "^\\s*(?:private|protected|public)\\s+(?!static\\s)(\\S+)\\s+(\\w+)\\s*;\\s*(?://\\s*(.*))?$");

    private static final Pattern API_MODEL_PROPERTY_ANNO = Pattern.compile("@ApiModelProperty\\s*\\(");

    private static final Map<String, String> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put("String", "STRING");
        TYPE_MAP.put("string", "STRING");
        TYPE_MAP.put("int", "NUMBER");
        TYPE_MAP.put("Integer", "NUMBER");
        TYPE_MAP.put("long", "NUMBER");
        TYPE_MAP.put("Long", "NUMBER");
        TYPE_MAP.put("float", "NUMBER");
        TYPE_MAP.put("Float", "NUMBER");
        TYPE_MAP.put("double", "NUMBER");
        TYPE_MAP.put("Double", "NUMBER");
        TYPE_MAP.put("BigDecimal", "NUMBER");
        TYPE_MAP.put("BigInteger", "NUMBER");
        TYPE_MAP.put("short", "NUMBER");
        TYPE_MAP.put("Short", "NUMBER");
        TYPE_MAP.put("byte", "NUMBER");
        TYPE_MAP.put("Byte", "NUMBER");
        TYPE_MAP.put("boolean", "BOOLEAN");
        TYPE_MAP.put("Boolean", "BOOLEAN");
        TYPE_MAP.put("Date", "DATE");
        TYPE_MAP.put("LocalDate", "DATE");
        TYPE_MAP.put("LocalDateTime", "DATE");
        TYPE_MAP.put("LocalTime", "DATE");
        TYPE_MAP.put("Timestamp", "DATE");
    }

    /**
     * Parse Java source for entity classes: extract class name + instance fields.
     * Supports multiple classes in a single source file.
     * <p>字段展示名（fieldLabel）优先取自：{@code @ApiModelProperty} 的 value/notes（或首个字符串参数） &gt;
     * 紧邻的 JavaDoc 首行 &gt; 独立行 {@code //} 注释 &gt; 行尾 {@code //} &gt; 字段名。
     * 局限：多行字段声明、注释中含未配对大括号、与解析顺序不一致的写法可能解析不准。</p>
     */
    public List<ParsedObject> parseEntities(String javaSource) {
        List<ParsedObject> results = new ArrayList<>();
        String cleaned = removeComments(javaSource);
        String blockCommentsStripped = removeBlockCommentsOnly(javaSource);

        List<ClassBlock> cleanedBlocks = extractClassBlocks(cleaned);
        List<ClassBlock> strippedBlocks = extractClassBlocks(blockCommentsStripped);

        for (int i = 0; i < cleanedBlocks.size(); i++) {
            ClassBlock block = cleanedBlocks.get(i);
            ParsedObject obj = new ParsedObject();
            obj.setObjectCode(block.className);
            obj.setObjectLabel(block.className);
            obj.setScriptName(ScriptNameUtil.toCamelCase(block.className));

            String labelScanBody = resolveLabelScanBody(block, strippedBlocks, i);
            List<String> labelsInOrder = extractFieldLabelsInDeclarationOrder(labelScanBody);

            Matcher fieldMatcher = FIELD_PATTERN.matcher(block.body);
            int fieldIndex = 0;
            while (fieldMatcher.find()) {
                String rawType = fieldMatcher.group(1);
                String fieldName = fieldMatcher.group(2);

                ParsedField field = new ParsedField();
                field.setFieldName(fieldName);
                String resolved = fieldIndex < labelsInOrder.size() ? labelsInOrder.get(fieldIndex) : null;
                if (resolved == null || resolved.isEmpty()) {
                    resolved = fieldName;
                }
                field.setFieldLabel(resolved);
                field.setScriptName(ScriptNameUtil.toCamelCase(fieldName));
                resolveType(rawType, field);
                obj.getFields().add(field);
                fieldIndex++;
            }
            results.add(obj);
        }

        linkNestedObjects(results);
        return results;
    }

    /**
     * 与 cleaned 块下标对齐，取仍保留行注释的类体用于扫描文档/注解；类名不一致时退回 cleaned 体（无展示名线索）。
     */
    private String resolveLabelScanBody(ClassBlock cleanedBlock, List<ClassBlock> strippedBlocks, int index) {
        if (strippedBlocks == null || index >= strippedBlocks.size()) {
            return cleanedBlock.body;
        }
        ClassBlock stripped = strippedBlocks.get(index);
        if (stripped.className.equals(cleanedBlock.className)) {
            return stripped.body;
        }
        return cleanedBlock.body;
    }

    /**
     * 按源码顺序扫描类体，为每个实例字段解析展示名。
     * 优先级：ApiModelProperty &gt; JavaDoc 首行 &gt; 独立 // 行 &gt; 行尾 //。
     */
    private List<String> extractFieldLabelsInDeclarationOrder(String classBody) {
        List<String> labels = new ArrayList<>();
        if (classBody == null || classBody.isEmpty()) {
            return labels;
        }
        String[] lines = classBody.split("\\R");
        String pendingApi = null;
        String pendingJavaDoc = null;
        String pendingSlashLine = null;
        boolean inJavaDoc = false;
        StringBuilder javaDocBuf = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine;
            if (inJavaDoc) {
                javaDocBuf.append(line).append('\n');
                if (line.contains("*/")) {
                    pendingJavaDoc = firstMeaningfulJavaDocLine(javaDocBuf.toString());
                    inJavaDoc = false;
                    javaDocBuf.setLength(0);
                }
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("/**")) {
                if (trimmed.contains("*/")) {
                    pendingJavaDoc = firstMeaningfulJavaDocLine(trimmed);
                } else {
                    inJavaDoc = true;
                    javaDocBuf.append(line).append('\n');
                }
                continue;
            }

            String apiLabel = extractApiModelPropertyLabel(line);
            if (apiLabel != null) {
                pendingApi = apiLabel;
                continue;
            }

            if (trimmed.startsWith("//")) {
                pendingSlashLine = trimmed.substring(2).trim();
                continue;
            }

            Matcher fieldLine = FIELD_LINE_PATTERN.matcher(line);
            if (fieldLine.matches()) {
                String inline = fieldLine.group(3);
                String inlineTrim = inline != null ? inline.trim() : null;

                String label = firstNonEmpty(pendingApi, pendingJavaDoc, pendingSlashLine, inlineTrim);
                labels.add(label != null ? label : "");

                pendingApi = null;
                pendingJavaDoc = null;
                pendingSlashLine = null;
            }
        }

        return labels;
    }

    /**
     * 从 @ApiModelProperty(...) 中取 value/notes 或首个字符串字面量作为展示名。
     */
    private String extractApiModelPropertyLabel(String line) {
        Matcher start = API_MODEL_PROPERTY_ANNO.matcher(line);
        if (!start.find()) {
            return null;
        }
        int depth = 1;
        StringBuilder inner = new StringBuilder();
        for (int i = start.end(); i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '(') {
                depth++;
                inner.append(c);
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    break;
                }
                inner.append(c);
            } else {
                inner.append(c);
            }
        }
        String inside = inner.toString();
        Matcher named = Pattern.compile("(?:value|notes)\\s*=\\s*[\"']([^\"']*)[\"']").matcher(inside);
        if (named.find()) {
            return named.group(1).trim();
        }
        Matcher firstStr = Pattern.compile("[\"']([^\"']*)[\"']").matcher(inside);
        if (firstStr.find()) {
            return firstStr.group(1).trim();
        }
        return null;
    }

    /**
     * 取 JavaDoc 块中第一行有效描述（跳过空行与仅含 * 的行）。
     */
    private String firstMeaningfulJavaDocLine(String javaDocBlock) {
        if (javaDocBlock == null) {
            return null;
        }
        String[] parts = javaDocBlock.split("\\R");
        for (String part : parts) {
            String t = part.replaceFirst("^\\s*/\\*\\*?", "")
                    .replaceFirst("\\*/\\s*$", "")
                    .replaceFirst("^\\s*\\*+", "")
                    .trim();
            if (t.isEmpty() || t.startsWith("@")) {
                continue;
            }
            return t;
        }
        return null;
    }

    private String firstNonEmpty(String a, String b, String c, String d) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        if (b != null && !b.isEmpty()) {
            return b;
        }
        if (c != null && !c.isEmpty()) {
            return c;
        }
        if (d != null && !d.isEmpty()) {
            return d;
        }
        return null;
    }

    /**
     * 仅移除块注释，保留行注释，便于大括号配对且仍能读取 // 说明。
     */
    private String removeBlockCommentsOnly(String source) {
        if (source == null) {
            return "";
        }
        return source.replaceAll("/\\*[\\s\\S]*?\\*/", "");
    }

    /**
     * 解析 {@code static final} 常量：支持跨行的数组 / {@code new Type[]\{\}} / {@code List.of}、{@code Arrays.asList}、{@code Map.of} 等。
     * 复杂表达式（拼接、方法调用）仍按原文本存入，类型多为 STRING。
     */
    public ParsedConstantGroup parseConstants(String javaSource) {
        String cleaned = removeComments(javaSource);
        ParsedConstantGroup group = new ParsedConstantGroup();

        Matcher classMatcher = CLASS_PATTERN.matcher(cleaned);
        if (classMatcher.find()) {
            group.setGroupCode(classMatcher.group(1));
            group.setGroupLabel(classMatcher.group(1));
            group.setScriptName(ScriptNameUtil.toCamelCase(classMatcher.group(1)));
        } else {
            group.setGroupCode("Constants");
            group.setGroupLabel("Constants");
            group.setScriptName("constants");
        }

        Matcher head = CONST_HEAD.matcher(cleaned);
        int pos = 0;
        while (head.find(pos)) {
            String rawType = head.group(1);
            String name = head.group(2);
            int valueStart = head.end();
            InitSpan span = extractConstantInitializer(cleaned, valueStart);
            if (span == null) {
                break;
            }
            String rawInit = span.text;
            pos = span.nextIndex;

            String stored = normalizeConstantInitializer(rawType, rawInit);
            String constType = mapConstantConstType(rawType, stored, rawInit);

            ParsedConstant pc = new ParsedConstant();
            pc.setConstCode(name);
            pc.setConstLabel(name);
            pc.setScriptName(ScriptNameUtil.toCamelCase(name));
            pc.setConstType(constType);
            pc.setConstValue(stored);
            group.getConstants().add(pc);
        }
        return group;
    }

    /**
     * 从 {@code =} 后截取初始化表达式直到顶层分号（忽略字符串与括号嵌套内的分号）。
     */
    private InitSpan extractConstantInitializer(String s, int from) {
        if (from > s.length()) {
            return null;
        }
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        int start = i;
        int brace = 0;
        int bracket = 0;
        int paren = 0;
        boolean inStr = false;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < s.length()) {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == '{') {
                brace++;
            } else if (c == '}') {
                brace--;
            } else if (c == '[') {
                bracket++;
            } else if (c == ']') {
                bracket--;
            } else if (c == '(') {
                paren++;
            } else if (c == ')') {
                paren--;
            } else if (c == ';' && brace == 0 && bracket == 0 && paren == 0) {
                return new InitSpan(s.substring(start, i).trim(), i + 1);
            }
        }
        return new InitSpan(s.substring(start).trim(), s.length());
    }

    /**
     * 将常见集合/数组字面量转为 JSON 文本便于存储与消费；无法结构化时退回标量清洗或原文。
     */
    private String normalizeConstantInitializer(String rawType, String rawInit) {
        String init = rawInit == null ? "" : rawInit.trim();
        if (init.isEmpty()) {
            return init;
        }

        Matcher listM = LIST_FACTORY_CALL.matcher(init);
        if (listM.find()) {
            int openParen = listM.end() - 1;
            int closeParen = indexOfMatchingParen(init, openParen);
            if (closeParen > openParen) {
                String inner = init.substring(openParen + 1, closeParen);
                JSONArray arr = parseCommaSeparatedLiteralsToJsonArray(inner);
                if (arr != null) {
                    return arr.toJSONString();
                }
            }
        }

        Matcher newArr = NEW_ARRAY_INIT.matcher(init);
        if (newArr.find()) {
            int lb = init.indexOf('{', newArr.start());
            int rb = indexOfMatchingBrace(init, lb);
            if (rb > lb) {
                JSONArray arr = parseBraceArrayToJsonArray(init.substring(lb, rb + 1));
                if (arr != null) {
                    return arr.toJSONString();
                }
            }
        }

        if (init.startsWith("{")) {
            int rb = indexOfMatchingBrace(init, 0);
            if (rb == init.length() - 1) {
                String compactType = rawType.replaceAll("\\s+", "");
                if (compactType.contains("Map") || compactType.contains("HashMap") || compactType.contains("LinkedHashMap")) {
                    String jsonObj = mapInitializerBodyToJsonObject(init.substring(1, init.length() - 1));
                    if (jsonObj != null) {
                        return jsonObj;
                    }
                }
                JSONArray arr = parseBraceArrayToJsonArray(init);
                if (arr != null) {
                    return arr.toJSONString();
                }
            }
        }

        Matcher mapM = MAP_OF_CALL.matcher(init);
        if (mapM.find()) {
            int openParen = mapM.end() - 1;
            int closeParen = indexOfMatchingParen(init, openParen);
            if (closeParen > openParen) {
                String inner = init.substring(openParen + 1, closeParen);
                String jsonObj = mapInitializerBodyToJsonObject(inner);
                if (jsonObj != null) {
                    return jsonObj;
                }
            }
        }

        if (init.equalsIgnoreCase("Collections.emptyList()") || init.equalsIgnoreCase("List.of()")) {
            return "[]";
        }
        if (init.equalsIgnoreCase("Collections.emptyMap()") || init.equalsIgnoreCase("Map.of()")) {
            return "{}";
        }

        return cleanLiteralValue(init);
    }

    /**
     * {@code Map.of} 或与数组相同的 {@code k,v,k2,v2} 逗号分隔键值对。
     */
    private String mapInitializerBodyToJsonObject(String inner) {
        List<String> parts = splitTopLevelComma(inner);
        if (parts.size() % 2 != 0) {
            return null;
        }
        JSONObject o = new JSONObject(true);
        for (int i = 0; i < parts.size(); i += 2) {
            Object kObj = parseJavaLiteralOrExpression(parts.get(i));
            if (!(kObj instanceof String)) {
                return null;
            }
            Object vObj = parseJavaLiteralOrExpression(parts.get(i + 1));
            if (vObj == null && !parts.get(i + 1).trim().isEmpty()) {
                return null;
            }
            o.put((String) kObj, vObj);
        }
        return o.toJSONString();
    }

    private JSONArray parseBraceArrayToJsonArray(String block) {
        block = block.trim();
        if (!block.startsWith("{") || !block.endsWith("}")) {
            return null;
        }
        return parseCommaSeparatedLiteralsToJsonArray(block.substring(1, block.length() - 1));
    }

    /**
     * 仅当每一项均为字面量（或 null）时返回数组；否则返回 null。
     */
    private JSONArray parseCommaSeparatedLiteralsToJsonArray(String inner) {
        List<String> parts = splitTopLevelComma(inner);
        JSONArray arr = new JSONArray();
        for (String p : parts) {
            if (p.trim().isEmpty()) {
                continue;
            }
            Object lit = parseJavaLiteralOrExpression(p);
            if (lit == null && !p.trim().isEmpty()) {
                return null;
            }
            arr.add(lit);
        }
        return arr;
    }

    /**
     * 在忽略字符串与嵌套括号的前提下，按顶层逗号拆分。
     */
    private List<String> splitTopLevelComma(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int brace = 0;
        int bracket = 0;
        int paren = 0;
        boolean inStr = false;
        for (int i = 0; i <= s.length(); i++) {
            if (i < s.length()) {
                char c = s.charAt(i);
                if (inStr) {
                    if (c == '\\' && i + 1 < s.length()) {
                        i++;
                        continue;
                    }
                    if (c == '"') {
                        inStr = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inStr = true;
                    continue;
                }
                if (c == '{') {
                    brace++;
                } else if (c == '}') {
                    brace--;
                } else if (c == '[') {
                    bracket++;
                } else if (c == ']') {
                    bracket--;
                } else if (c == '(') {
                    paren++;
                } else if (c == ')') {
                    paren--;
                } else if (c == ',' && brace == 0 && bracket == 0 && paren == 0) {
                    out.add(s.substring(start, i));
                    start = i + 1;
                    continue;
                }
            } else {
                if (start < s.length()) {
                    out.add(s.substring(start));
                }
            }
        }
        return out;
    }

    /**
     * 解析 Java 字面量；非常量表达式返回 null。
     */
    private Object parseJavaLiteralOrExpression(String part) {
        String p = part.trim();
        if (p.isEmpty()) {
            return null;
        }
        if (p.startsWith("\"")) {
            return parseJavaDoubleQuotedStringValue(p);
        }
        if (p.length() >= 2 && p.charAt(0) == '\'' && p.charAt(p.length() - 1) == '\'') {
            return p.substring(1, p.length() - 1);
        }
        if ("true".equals(p) || "false".equals(p)) {
            return Boolean.parseBoolean(p);
        }
        if ("null".equalsIgnoreCase(p)) {
            return null;
        }
        if (p.matches("-?\\d+")) {
            try {
                return Long.parseLong(p);
            } catch (NumberFormatException e) {
                return p;
            }
        }
        if (p.matches("-?\\d+\\.\\d+")) {
            try {
                return Double.parseDouble(p);
            } catch (NumberFormatException e) {
                return p;
            }
        }
        if (p.matches("-?\\d+[LlFfDd]")) {
            return p.substring(0, p.length() - 1);
        }
        return null;
    }

    private String parseJavaDoubleQuotedStringValue(String part) {
        if (!part.startsWith("\"")) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        while (i < part.length()) {
            char c = part.charAt(i);
            if (c == '\\' && i + 1 < part.length()) {
                char n = part.charAt(i + 1);
                switch (n) {
                    case 'n':
                        sb.append('\n');
                        i += 2;
                        continue;
                    case 't':
                        sb.append('\t');
                        i += 2;
                        continue;
                    case 'r':
                        sb.append('\r');
                        i += 2;
                        continue;
                    case '"':
                        sb.append('"');
                        i += 2;
                        continue;
                    case '\\':
                        sb.append('\\');
                        i += 2;
                        continue;
                    case 'u':
                        if (i + 5 < part.length()) {
                            int code = Integer.parseInt(part.substring(i + 2, i + 6), 16);
                            sb.append((char) code);
                            i += 6;
                            continue;
                        }
                        break;
                    default:
                        sb.append(n);
                        i += 2;
                        continue;
                }
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private int indexOfMatchingParen(String s, int openIdx) {
        int depth = 0;
        boolean inStr = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < s.length()) {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int indexOfMatchingBrace(String s, int openIdx) {
        int depth = 0;
        boolean inStr = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < s.length()) {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 常量变量类型：JSON 数组 → LIST，JSON 对象 → MAP，否则按 Java 标量类型映射。
     */
    private String mapConstantConstType(String rawType, String stored, String rawInit) {
        String st = stored == null ? "" : stored.trim();
        if (st.startsWith("[")) {
            return "LIST";
        }
        if (st.startsWith("{") && (rawType.contains("Map") || rawType.contains("HashMap") || rawType.contains("LinkedHashMap")
                || rawInit.trim().matches("(?i)Map\\.of\\s*\\(.*"))) {
            return "MAP";
        }
        String t = rawType.replaceAll("\\s+", "");
        if (t.endsWith("[]")) {
            return st.startsWith("[") ? "LIST" : mapSimpleType(t.substring(0, t.length() - 2));
        }
        if (t.startsWith("List") || t.startsWith("Set") || t.startsWith("Collection")) {
            return st.startsWith("[") ? "LIST" : "STRING";
        }
        if (t.startsWith("Map")) {
            return st.startsWith("{") ? "MAP" : "STRING";
        }
        return mapSimpleType(t.contains("<") ? t.substring(0, t.indexOf('<')) : t);
    }

    private static final class InitSpan {
        final String text;
        final int nextIndex;

        InitSpan(String text, int nextIndex) {
            this.text = text;
            this.nextIndex = nextIndex;
        }
    }

    private void resolveType(String rawType, ParsedField field) {
        Matcher genericMatcher = GENERIC_PATTERN.matcher(rawType);
        if (genericMatcher.matches()) {
            String container = genericMatcher.group(1);
            String inner = genericMatcher.group(2).trim();

            if ("List".equals(container) || "Set".equals(container) || "Collection".equals(container) || "ArrayList".equals(container)) {
                field.setVarType("LIST");
                field.setGenericType(mapSimpleType(inner));
                if (field.getGenericType().equals("OBJECT")) {
                    field.setRefObjectCode(inner);
                }
            } else if ("Map".equals(container) || "HashMap".equals(container)) {
                field.setVarType("MAP");
                String[] parts = inner.split("\\s*,\\s*", 2);
                if (parts.length == 2) {
                    field.setGenericType(mapSimpleType(parts[0]) + "," + mapSimpleType(parts[1]));
                } else {
                    field.setGenericType(inner);
                }
            } else {
                field.setVarType("OBJECT");
                field.setRefObjectCode(container);
            }
        } else {
            String mapped = mapSimpleType(rawType);
            field.setVarType(mapped);
            if ("OBJECT".equals(mapped)) {
                field.setRefObjectCode(rawType);
            }
        }
    }

    private String mapSimpleType(String javaType) {
        if (javaType == null) return "STRING";
        String simple = javaType.contains(".") ? javaType.substring(javaType.lastIndexOf('.') + 1) : javaType;
        return TYPE_MAP.getOrDefault(simple, "OBJECT");
    }

    private void linkNestedObjects(List<ParsedObject> objects) {
        Set<String> knownCodes = new HashSet<>();
        for (ParsedObject obj : objects) {
            knownCodes.add(obj.getObjectCode());
        }
        for (ParsedObject obj : objects) {
            for (ParsedField field : obj.getFields()) {
                if ("OBJECT".equals(field.getVarType()) && field.getRefObjectCode() != null
                        && knownCodes.contains(field.getRefObjectCode())) {
                    ParsedObject nested = objects.stream()
                            .filter(o -> o.getObjectCode().equals(field.getRefObjectCode()))
                            .findFirst().orElse(null);
                    if (nested != null && !obj.getNestedObjects().contains(nested)) {
                        obj.getNestedObjects().add(nested);
                    }
                }
            }
        }
    }

    private String removeComments(String source) {
        return source
                .replaceAll("/\\*[\\s\\S]*?\\*/", "")
                .replaceAll("//[^\n]*", "");
    }

    private String cleanLiteralValue(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.endsWith("L") || v.endsWith("l") || v.endsWith("f") || v.endsWith("F") || v.endsWith("d") || v.endsWith("D")) {
            v = v.substring(0, v.length() - 1);
        }
        if (v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    private List<ClassBlock> extractClassBlocks(String source) {
        List<ClassBlock> blocks = new ArrayList<>();
        Matcher classMatcher = CLASS_PATTERN.matcher(source);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            int braceStart = source.indexOf('{', classMatcher.start());
            if (braceStart < 0) continue;

            int depth = 1;
            int pos = braceStart + 1;
            while (pos < source.length() && depth > 0) {
                char c = source.charAt(pos);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                pos++;
            }
            String body = source.substring(braceStart + 1, pos - 1);
            blocks.add(new ClassBlock(className, body));
        }
        return blocks;
    }

    private static class ClassBlock {
        final String className;
        final String body;
        ClassBlock(String className, String body) {
            this.className = className;
            this.body = body;
        }
    }
}
