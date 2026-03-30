package com.bjjw.rule.server.service.parser;

import com.bjjw.rule.model.dto.ParsedField;
import com.bjjw.rule.model.dto.ParsedObject;
import com.bjjw.rule.core.util.ScriptNameUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 MySQL / OceanBase 等风格的 {@code CREATE TABLE} DDL 解析数据对象与字段。
 * <p>列展示名取自 {@code COMMENT '...'}；无 COMMENT 时用列名。类型映射为规则引擎的 STRING/NUMBER/DATE/BOOLEAN。</p>
 * <p>局限：不解析跨行拆开的列定义、双引号标识符、非标准语法；遇 PRIMARY KEY / KEY / INDEX 等约束行后停止解析列。</p>
 */
@Component
public class DdlTableParser {

    private static final Pattern CREATE_TABLE_HEAD = Pattern.compile(
            "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`([^`]+)`|(\\w+))\\s*\\(");

    private static final Pattern COLUMN_START = Pattern.compile(
            "^\\s*`?(\\w+)`?\\s+(\\w+)(\\([^)]*\\))?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMENT_CLAUSE = Pattern.compile(
            "COMMENT\\s+'((?:[^'\\\\]|'')*)'",
            Pattern.CASE_INSENSITIVE);

    /**
     * 解析脚本中出现的所有 {@code CREATE TABLE}，每个表对应一个 {@link ParsedObject}。
     */
    public List<ParsedObject> parseCreateTables(String ddlSource) {
        List<ParsedObject> out = new ArrayList<>();
        if (ddlSource == null || ddlSource.trim().isEmpty()) {
            return out;
        }
        Matcher head = CREATE_TABLE_HEAD.matcher(ddlSource);
        int searchFrom = 0;
        while (head.find(searchFrom)) {
            String tableName = firstNonNull(head.group(1), head.group(2));
            int bodyStart = head.end();
            int bodyEnd = indexOfMatchingCloseParen(ddlSource, bodyStart);
            if (bodyEnd < 0) {
                break;
            }
            String body = ddlSource.substring(bodyStart, bodyEnd);
            ParsedObject po = buildParsedObject(tableName, body);
            if (!po.getFields().isEmpty()) {
                out.add(po);
            }
            searchFrom = bodyEnd + 1;
        }
        return out;
    }

    /**
     * 将表体（括号内文本）按行扫描为字段列表。
     */
    private ParsedObject buildParsedObject(String rawTableName, String innerBody) {
        ParsedObject obj = new ParsedObject();
        String table = rawTableName == null ? "Unknown" : rawTableName.trim();
        String objectCode = tableNameToPascalCode(table);
        obj.setObjectCode(objectCode);
        obj.setObjectLabel(objectCode);
        obj.setScriptName(ScriptNameUtil.toCamelCase(objectCode));

        String[] lines = innerBody.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            line = line.replaceFirst(",\\s*$", "");
            if (line.isEmpty()) {
                continue;
            }
            if (isConstraintOrIndexLine(line)) {
                break;
            }
            ParsedField field = tryParseColumnLine(line);
            if (field != null) {
                obj.getFields().add(field);
            }
        }
        return obj;
    }

    /**
     * 将 {@code snake_case} / {@code t_snake} 表名转为 PascalCase，作为数据对象编码（与 Java 实体导入风格一致）。
     */
    private String tableNameToPascalCode(String table) {
        String t = table.replace('`', ' ').trim();
        if (t.isEmpty()) {
            return "Unknown";
        }
        String camel = ScriptNameUtil.toCamelCase(t);
        if (camel.isEmpty()) {
            return "Unknown";
        }
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    /**
     * 判断是否为表级约束或索引定义行（非列定义）。
     */
    private boolean isConstraintOrIndexLine(String line) {
        String u = line.toUpperCase(Locale.ROOT);
        return u.startsWith("PRIMARY KEY")
                || u.startsWith("UNIQUE KEY")
                || u.startsWith("UNIQUE INDEX")
                || u.startsWith("KEY ")
                || u.startsWith("INDEX ")
                || u.startsWith("CONSTRAINT ")
                || u.startsWith("FOREIGN KEY")
                || u.startsWith("FULLTEXT KEY")
                || u.startsWith("FULLTEXT INDEX")
                || u.startsWith("SPATIAL KEY")
                || u.startsWith("SPATIAL INDEX")
                || u.startsWith("CHECK ")
                || u.equals(")")
                || u.startsWith(") ");
    }

    /**
     * 尝试将一行解析为列定义；无法识别则返回 null。
     */
    private ParsedField tryParseColumnLine(String line) {
        Matcher cm = COLUMN_START.matcher(line);
        if (!cm.find()) {
            return null;
        }
        String colName = cm.group(1);
        String typeBase = cm.group(2).toLowerCase(Locale.ROOT);

        ParsedField f = new ParsedField();
        f.setFieldName(colName);
        f.setScriptName(ScriptNameUtil.toCamelCase(colName));
        f.setVarType(mapSqlTypeToVarType(typeBase));
        f.setFieldLabel(extractCommentLabel(line, colName));
        return f;
    }

    /**
     * 提取 {@code COMMENT '...'} 为展示名；MySQL 风格单引号内 {@code ''} 转义为单引号。
     */
    private String extractCommentLabel(String line, String fallbackColumnName) {
        Matcher m = COMMENT_CLAUSE.matcher(line);
        if (!m.find()) {
            return fallbackColumnName;
        }
        return m.group(1).replace("''", "'");
    }

    /**
     * 将 SQL 标量类型关键字映射为规则变量类型。
     */
    private String mapSqlTypeToVarType(String base) {
        if (base.startsWith("bigint") || base.startsWith("int") || base.startsWith("tinyint")
                || base.startsWith("smallint") || base.startsWith("mediumint")
                || base.startsWith("decimal") || base.startsWith("numeric")
                || base.startsWith("float") || base.startsWith("double") || base.startsWith("real")) {
            return "NUMBER";
        }
        if (base.startsWith("datetime") || base.startsWith("date") || base.startsWith("time")
                || base.startsWith("timestamp") || base.startsWith("year")) {
            return "DATE";
        }
        if (base.startsWith("bool") || base.equals("bit")) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    /**
     * 从 {@code bodyStart} 起在字符串字面量外匹配与 CREATE TABLE 左括号配对的右括号下标。
     */
    private int indexOfMatchingCloseParen(String ddl, int bodyStart) {
        int depth = 1;
        boolean inStr = false;
        for (int i = bodyStart; i < ddl.length(); i++) {
            char c = ddl.charAt(i);
            if (inStr) {
                if (c == '\'' && i + 1 < ddl.length() && ddl.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                if (c == '\'') {
                    inStr = false;
                }
                continue;
            }
            if (c == '\'') {
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

    private String firstNonNull(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        return b != null ? b : "";
    }
}
