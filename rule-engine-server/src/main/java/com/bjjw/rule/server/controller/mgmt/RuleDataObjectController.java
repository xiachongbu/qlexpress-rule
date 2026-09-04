package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.dto.mgmt.*;
import com.bjjw.rule.model.entity.RuleDataObject;
import com.bjjw.rule.model.entity.RuleDataObjectField;
import com.bjjw.rule.model.entity.RuleDataObjectFieldOption;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleDataObjectService;
import com.bjjw.rule.server.service.SchemaSyncService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/dataobject")
public class RuleDataObjectController {

    @Resource
    private RuleDataObjectService dataObjectService;

    @Resource
    private SchemaSyncService schemaSyncService;

    @PostMapping("/import/java")
    public R<Map<String, Object>> importJava(@RequestBody RuleDataObjectImportJavaRequest req) {
        String objectType = req.getObjectType() != null ? req.getObjectType() : "INPUT";
        Map<String, Object> result = dataObjectService.importFromJava(req.getProjectId(), req.getJavaSource(), objectType);
        trySyncSchema();
        return R.ok(result);
    }

    @PostMapping("/import/java-file")
    public R<Map<String, Object>> importJavaFile(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "INPUT") String objectType,
            @RequestParam("file") MultipartFile file) throws Exception {
        String javaSource = new String(file.getBytes(), StandardCharsets.UTF_8);
        Map<String, Object> result = dataObjectService.importFromJava(projectId, javaSource, objectType);
        trySyncSchema();
        return R.ok(result);
    }

    @PostMapping("/import/json")
    public R<Map<String, Object>> importJson(@RequestBody RuleDataObjectImportJsonRequest req) {
        String objectType = req.getObjectType() != null ? req.getObjectType() : "INPUT";
        Map<String, Object> result = dataObjectService.importFromJson(req.getProjectId(), req.getJsonContent(),
                req.getObjectCode(), objectType);
        trySyncSchema();
        return R.ok(result);
    }

    /** 从建表 DDL 导入数据对象与字段 */
    @PostMapping("/import/ddl")
    public R<Map<String, Object>> importDdl(@RequestBody RuleDataObjectImportDdlRequest req) {
        String objectType = req.getObjectType() != null ? req.getObjectType() : "INPUT";
        Map<String, Object> result = dataObjectService.importFromDdl(req.getProjectId(), req.getDdlSource(), objectType);
        trySyncSchema();
        return R.ok(result);
    }

    /** 按项目列出数据对象 */
    @PostMapping("/project/query")
    public R<List<RuleDataObject>> listByProject(@RequestBody RuleDataObjectProjectQueryRequest req) {
        return R.ok(dataObjectService.listByProject(req.getProjectId()));
    }

    @GetMapping("/{id:\\d+}")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        Map<String, Object> data = dataObjectService.getObjectWithVariables(id);
        return data != null ? R.ok(data) : R.fail("数据对象不存在");
    }

    @PostMapping("/tree/query")
    public R<List<Map<String, Object>>> tree(@RequestBody RuleDataObjectTreeQueryRequest req) {
        return R.ok(dataObjectService.getVariableTree(req.getProjectId()));
    }

    @PutMapping("/{id:\\d+}/type")
    public R<Void> updateType(@PathVariable Long id, @RequestBody RuleDataObjectUpdateTypeRequest req) {
        dataObjectService.updateObjectType(id, req.getObjectType());
        return R.ok();
    }

    /** 更新数据对象的脚本引用名 */
    @PutMapping("/{id:\\d+}/script-name")
    public R<Void> updateScriptName(@PathVariable Long id, @RequestBody RuleDataObjectUpdateScriptNameRequest req) {
        dataObjectService.updateScriptName(id, req.getScriptName());
        return R.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        dataObjectService.deleteWithVariables(id);
        trySyncSchema();
        return R.ok();
    }

    /** 在数据对象下新增字段 */
    @PostMapping("/{objectId:\\d+}/field")
    public R<RuleDataObjectField> createField(@PathVariable Long objectId, @RequestBody RuleDataObjectField field) {
        return R.ok(dataObjectService.createObjectField(objectId, field));
    }

    /** 更新对象字段 */
    @PutMapping("/field")
    public R<Void> updateField(@RequestBody RuleDataObjectField field) {
        dataObjectService.updateObjectField(field);
        trySyncSchema();
        return R.ok();
    }

    /** 删除对象字段及其枚举选项 */
    @DeleteMapping("/field/{fieldId:\\d+}")
    public R<Void> deleteField(@PathVariable Long fieldId) {
        dataObjectService.deleteObjectField(fieldId);
        trySyncSchema();
        return R.ok();
    }

    @GetMapping("/field/{fieldId:\\d+}/options")
    public R<List<RuleDataObjectFieldOption>> getFieldOptions(@PathVariable Long fieldId) {
        return R.ok(dataObjectService.getFieldOptions(fieldId));
    }

    @PostMapping("/field/{fieldId:\\d+}/options")
    public R<Void> saveFieldOptions(@PathVariable Long fieldId, @RequestBody List<RuleDataObjectFieldOption> options) {
        dataObjectService.saveFieldOptions(fieldId, options);
        trySyncSchema();
        return R.ok();
    }

    private void trySyncSchema() {
        try {
            schemaSyncService.syncAndGetStatus();
        } catch (Exception ignored) {}
    }
}
