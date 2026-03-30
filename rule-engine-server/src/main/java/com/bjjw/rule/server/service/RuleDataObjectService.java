package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bjjw.rule.model.dto.ParsedField;
import com.bjjw.rule.model.dto.ParsedObject;
import com.bjjw.rule.model.entity.RuleDataObject;
import com.bjjw.rule.model.entity.RuleDataObjectField;
import com.bjjw.rule.model.entity.RuleDataObjectFieldOption;
import com.bjjw.rule.server.mapper.RuleDataObjectFieldMapper;
import com.bjjw.rule.server.mapper.RuleDataObjectFieldOptionMapper;
import com.bjjw.rule.server.mapper.RuleDataObjectMapper;
import com.bjjw.rule.server.service.parser.DdlTableParser;
import com.bjjw.rule.server.service.parser.JavaEntityParser;
import com.bjjw.rule.server.service.parser.JsonSchemaParser;
import com.bjjw.rule.core.util.ScriptNameUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RuleDataObjectService extends ServiceImpl<RuleDataObjectMapper, RuleDataObject> {

    @Resource
    private RuleDataObjectFieldMapper objectFieldMapper;

    @Resource
    private RuleDataObjectFieldOptionMapper objectFieldOptionMapper;

    @Resource
    private JavaEntityParser javaEntityParser;

    @Resource
    private JsonSchemaParser jsonSchemaParser;

    @Resource
    private DdlTableParser ddlTableParser;

    /**
     * 将对象字段转为与前端「变量行」一致的结构，并标记 {@code objectField=true}。
     */
    public static Map<String, Object> toVariableRow(RuleDataObjectField f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("projectId", f.getProjectId());
        m.put("objectId", f.getObjectId());
        m.put("varCode", f.getVarCode());
        m.put("varLabel", f.getVarLabel());
        m.put("scriptName", f.getScriptName());
        m.put("varType", f.getVarType());
        m.put("refObjectCode", f.getRefObjectCode());
        m.put("varSource", "INPUT");
        m.put("sortOrder", f.getSortOrder());
        m.put("status", f.getStatus());
        m.put("objectField", Boolean.TRUE);
        return m;
    }

    @Transactional
    public Map<String, Object> importFromDdl(Long projectId, String ddlSource, String objectType) {
        List<ParsedObject> parsed = ddlTableParser.parseCreateTables(ddlSource);
        int objectCount = 0;
        int varCount = 0;
        for (int i = 0; i < parsed.size(); i++) {
            ParsedObject po = parsed.get(i);
            String src = (i == 0) ? ddlSource : null;
            RuleDataObject obj = findOrCreateObject(projectId, po.getObjectCode(), po.getScriptName(), objectType, "DDL", src);
            varCount += batchCreateFields(projectId, obj.getId(), po, null);
            objectCount++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("objectCount", objectCount);
        result.put("variableCount", varCount);
        return result;
    }

    @Transactional
    public Map<String, Object> importFromJava(Long projectId, String javaSource, String objectType) {
        List<ParsedObject> parsed = javaEntityParser.parseEntities(javaSource);
        int objectCount = 0;
        int varCount = 0;
        for (ParsedObject po : parsed) {
            RuleDataObject obj = findOrCreateObject(projectId, po.getObjectCode(), po.getScriptName(), objectType, "JAVA", javaSource);
            varCount += batchCreateFields(projectId, obj.getId(), po, null);
            objectCount++;
            for (ParsedObject nested : po.getNestedObjects()) {
                RuleDataObject nestedObj = findOrCreateObject(projectId, nested.getObjectCode(), nested.getScriptName(), objectType, "JAVA", null);
                nestedObj.setParentObjectId(obj.getId());
                updateById(nestedObj);
                varCount += batchCreateFields(projectId, nestedObj.getId(), nested, null);
                objectCount++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("objectCount", objectCount);
        result.put("variableCount", varCount);
        return result;
    }

    @Transactional
    public Map<String, Object> importFromJson(Long projectId, String jsonContent, String objectCode, String objectType) {
        ParsedObject parsed = jsonSchemaParser.parseObject(jsonContent, objectCode);
        RuleDataObject obj = findOrCreateObject(projectId, objectCode, parsed.getScriptName(), objectType, "JSON", jsonContent);
        int varCount = batchCreateFields(projectId, obj.getId(), parsed, null);
        int objectCount = 1;

        for (ParsedObject nested : parsed.getNestedObjects()) {
            RuleDataObject nestedObj = findOrCreateObject(projectId, nested.getObjectCode(), nested.getScriptName(), objectType, "JSON", null);
            nestedObj.setParentObjectId(obj.getId());
            updateById(nestedObj);
            varCount += batchCreateFields(projectId, nestedObj.getId(), nested, null);
            objectCount++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("objectCount", objectCount);
        result.put("variableCount", varCount);
        return result;
    }

    public List<RuleDataObject> listByProject(Long projectId) {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getProjectId, projectId)
               .orderByDesc(RuleDataObject::getCreateTime);
        return list(wrapper);
    }

    public Map<String, Object> getObjectWithVariables(Long objectId) {
        RuleDataObject obj = getById(objectId);
        if (obj == null) return null;
        List<RuleDataObjectField> fields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getObjectId, objectId)
                        .orderByAsc(RuleDataObjectField::getSortOrder));
        List<Map<String, Object>> rows = fields.stream().map(RuleDataObjectService::toVariableRow).collect(Collectors.toList());

        List<RuleDataObject> children = list(new LambdaQueryWrapper<RuleDataObject>()
                .eq(RuleDataObject::getParentObjectId, objectId));

        Map<String, Object> result = new HashMap<>();
        result.put("object", obj);
        result.put("variables", rows);
        result.put("children", children);
        return result;
    }

    @Transactional
    public void deleteWithVariables(Long objectId) {
        List<RuleDataObjectField> fields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>().eq(RuleDataObjectField::getObjectId, objectId));
        for (RuleDataObjectField f : fields) {
            objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                    .eq(RuleDataObjectFieldOption::getFieldId, f.getId()));
        }
        objectFieldMapper.delete(new LambdaQueryWrapper<RuleDataObjectField>().eq(RuleDataObjectField::getObjectId, objectId));

        List<RuleDataObject> children = list(new LambdaQueryWrapper<RuleDataObject>()
                .eq(RuleDataObject::getParentObjectId, objectId));
        for (RuleDataObject child : children) {
            deleteWithVariables(child.getId());
        }
        removeById(objectId);
    }

    public void updateObjectType(Long id, String objectType) {
        RuleDataObject obj = getById(id);
        if (obj != null) {
            obj.setObjectType(objectType);
            updateById(obj);
        }
    }

    /** 更新数据对象的脚本引用名 */
    public void updateScriptName(Long id, String scriptName) {
        RuleDataObject obj = getById(id);
        if (obj != null) {
            obj.setScriptName(scriptName);
            updateById(obj);
        }
    }

    public List<Map<String, Object>> getVariableTree(Long projectId) {
        List<RuleDataObject> objects = listByProject(projectId);
        List<RuleDataObjectField> allFields = objectFieldMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectField>()
                        .eq(RuleDataObjectField::getProjectId, projectId)
                        .orderByAsc(RuleDataObjectField::getSortOrder));

        Map<Long, List<RuleDataObjectField>> byObject = allFields.stream()
                .collect(Collectors.groupingBy(RuleDataObjectField::getObjectId));

        List<Map<String, Object>> tree = new ArrayList<>();
        for (RuleDataObject obj : objects) {
            Map<String, Object> node = new HashMap<>();
            node.put("object", obj);
            List<Map<String, Object>> vars = byObject.getOrDefault(obj.getId(), Collections.emptyList()).stream()
                    .map(RuleDataObjectService::toVariableRow)
                    .collect(Collectors.toList());
            node.put("variables", vars);
            tree.add(node);
        }
        return tree;
    }

    @Transactional
    public RuleDataObjectField createObjectField(Long objectId, RuleDataObjectField field) {
        RuleDataObject obj = getById(objectId);
        if (obj == null) throw new IllegalArgumentException("数据对象不存在");
        field.setId(null);
        field.setProjectId(obj.getProjectId());
        field.setObjectId(objectId);
        if (field.getScriptName() == null || field.getScriptName().isEmpty()) {
            field.setScriptName(ScriptNameUtil.toCamelCase(field.getVarCode()));
        }
        field.setStatus(1);
        if (field.getSortOrder() == null) {
            List<RuleDataObjectField> tail = objectFieldMapper.selectList(
                    new LambdaQueryWrapper<RuleDataObjectField>()
                            .eq(RuleDataObjectField::getObjectId, objectId)
                            .orderByDesc(RuleDataObjectField::getSortOrder)
                            .last("LIMIT 1"));
            int next = tail.isEmpty() ? 0 : tail.get(0).getSortOrder() + 1;
            field.setSortOrder(next);
        }
        objectFieldMapper.insert(field);
        return field;
    }

    @Transactional
    public void updateObjectField(RuleDataObjectField field) {
        objectFieldMapper.updateById(field);
    }

    @Transactional
    public void deleteObjectField(Long fieldId) {
        objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                .eq(RuleDataObjectFieldOption::getFieldId, fieldId));
        objectFieldMapper.deleteById(fieldId);
    }

    public List<RuleDataObjectFieldOption> getFieldOptions(Long fieldId) {
        return objectFieldOptionMapper.selectList(
                new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                        .eq(RuleDataObjectFieldOption::getFieldId, fieldId)
                        .orderByAsc(RuleDataObjectFieldOption::getSortOrder));
    }

    @Transactional
    public void saveFieldOptions(Long fieldId, List<RuleDataObjectFieldOption> options) {
        objectFieldOptionMapper.delete(new LambdaQueryWrapper<RuleDataObjectFieldOption>()
                .eq(RuleDataObjectFieldOption::getFieldId, fieldId));
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                RuleDataObjectFieldOption opt = options.get(i);
                opt.setId(null);
                opt.setFieldId(fieldId);
                opt.setSortOrder(i);
                objectFieldOptionMapper.insert(opt);
            }
        }
    }

    private RuleDataObject findOrCreateObject(Long projectId, String objectCode, String scriptName, String objectType, String sourceType, String sourceContent) {
        LambdaQueryWrapper<RuleDataObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDataObject::getProjectId, projectId)
               .eq(RuleDataObject::getObjectCode, objectCode);
        RuleDataObject existing = getOne(wrapper, false);
        if (existing != null) {
            existing.setObjectType(objectType);
            existing.setSourceType(sourceType);
            if (sourceContent != null) existing.setSourceContent(sourceContent);
            if (scriptName != null && existing.getScriptName() == null) existing.setScriptName(scriptName);
            updateById(existing);
            return existing;
        }
        RuleDataObject obj = new RuleDataObject();
        obj.setProjectId(projectId);
        obj.setObjectCode(objectCode);
        obj.setObjectLabel(objectCode);
        obj.setScriptName(scriptName != null ? scriptName : ScriptNameUtil.toCamelCase(objectCode));
        obj.setObjectType(objectType);
        obj.setSourceType(sourceType);
        obj.setSourceContent(sourceContent);
        obj.setStatus(1);
        save(obj);
        return obj;
    }

    private int batchCreateFields(Long projectId, Long objectId, ParsedObject parsed, Long parentFieldId) {
        int count = 0;
        int order = 0;
        for (ParsedField field : parsed.getFields()) {
            String varCode = field.getFieldName();
            LambdaQueryWrapper<RuleDataObjectField> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(RuleDataObjectField::getObjectId, objectId)
                        .eq(RuleDataObjectField::getVarCode, varCode);
            RuleDataObjectField existing = objectFieldMapper.selectOne(existWrapper);

            if (existing != null) {
                existing.setVarType(field.getVarType());
                existing.setParentFieldId(parentFieldId);
                existing.setRefObjectCode(field.getRefObjectCode());
                if (field.getFieldLabel() != null && !field.getFieldLabel().isEmpty()) {
                    existing.setVarLabel(field.getFieldLabel());
                }
                if (field.getScriptName() != null && existing.getScriptName() == null) {
                    existing.setScriptName(field.getScriptName());
                }
                objectFieldMapper.updateById(existing);
            } else {
                RuleDataObjectField f = new RuleDataObjectField();
                f.setProjectId(projectId);
                f.setObjectId(objectId);
                f.setVarCode(varCode);
                f.setVarLabel(field.getFieldLabel() != null ? field.getFieldLabel() : varCode);
                f.setScriptName(field.getScriptName() != null ? field.getScriptName() : ScriptNameUtil.toCamelCase(varCode));
                f.setVarType(field.getVarType());
                f.setRefObjectCode(field.getRefObjectCode());
                f.setParentFieldId(parentFieldId);
                f.setSortOrder(order);
                f.setStatus(1);
                objectFieldMapper.insert(f);
            }
            count++;
            order++;
        }
        return count;
    }
}
