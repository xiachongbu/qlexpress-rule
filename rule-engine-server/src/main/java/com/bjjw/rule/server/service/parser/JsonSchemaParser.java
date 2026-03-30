package com.bjjw.rule.server.service.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bjjw.rule.model.dto.*;
import com.bjjw.rule.core.util.ScriptNameUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JsonSchemaParser {

    /**
     * Parse a JSON sample into a ParsedObject tree.
     * Nested objects become child ParsedObjects; arrays become LIST fields.
     */
    public ParsedObject parseObject(String jsonContent, String objectCode) {
        JSONObject json = JSON.parseObject(jsonContent);
        return parseJsonObject(json, objectCode);
    }

    /**
     * 解析扁平 JSON 为常量列表（无常量组概念时占位元数据仅用于解析器内部）。
     */
    public ParsedConstantGroup parseConstants(String jsonContent) {
        return parseConstants(jsonContent, "IMPORT", "导入的常量");
    }

    /**
     * Parse flat JSON key-value pairs as constants.
     * All top-level primitive keys become constants with inferred types.
     */
    public ParsedConstantGroup parseConstants(String jsonContent, String groupCode, String groupLabel) {
        JSONObject json = JSON.parseObject(jsonContent);
        ParsedConstantGroup group = new ParsedConstantGroup();
        group.setGroupCode(groupCode);
        group.setGroupLabel(groupLabel);
        group.setScriptName(ScriptNameUtil.toCamelCase(groupCode));

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof JSONObject || value instanceof JSONArray) {
                continue;
            }

            ParsedConstant pc = new ParsedConstant();
            pc.setConstCode(key);
            pc.setConstLabel(key);
            pc.setScriptName(ScriptNameUtil.toCamelCase(key));
            pc.setConstType(inferPrimitiveType(value));
            pc.setConstValue(value == null ? "" : String.valueOf(value));
            group.getConstants().add(pc);
        }
        return group;
    }

    private ParsedObject parseJsonObject(JSONObject json, String objectCode) {
        ParsedObject obj = new ParsedObject();
        obj.setObjectCode(objectCode);
        obj.setObjectLabel(objectCode);
        obj.setScriptName(ScriptNameUtil.toCamelCase(objectCode));

        for (Map.Entry<String, Object> entry : json.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            ParsedField field = new ParsedField();
            field.setFieldName(key);
            field.setFieldLabel(key);
            field.setScriptName(ScriptNameUtil.toCamelCase(key));

            if (value instanceof JSONObject) {
                field.setVarType("OBJECT");
                String childCode = capitalize(key);
                field.setRefObjectCode(childCode);
                ParsedObject nested = parseJsonObject((JSONObject) value, childCode);
                obj.getNestedObjects().add(nested);
            } else if (value instanceof JSONArray) {
                field.setVarType("LIST");
                JSONArray arr = (JSONArray) value;
                if (!arr.isEmpty()) {
                    Object first = arr.get(0);
                    if (first instanceof JSONObject) {
                        String childCode = capitalize(key) + "Item";
                        field.setGenericType("OBJECT");
                        field.setRefObjectCode(childCode);
                        ParsedObject nested = parseJsonObject((JSONObject) first, childCode);
                        obj.getNestedObjects().add(nested);
                    } else {
                        field.setGenericType(inferPrimitiveType(first));
                    }
                } else {
                    field.setGenericType("STRING");
                }
            } else {
                field.setVarType(inferPrimitiveType(value));
            }
            obj.getFields().add(field);
        }
        return obj;
    }

    private String inferPrimitiveType(Object value) {
        if (value == null) return "STRING";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Number) return "NUMBER";
        return "STRING";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
