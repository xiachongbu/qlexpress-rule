package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.dto.RuleValidationResult;
import com.bjjw.rule.model.dto.mgmt.*;
import com.bjjw.rule.model.entity.RuleVariable;
import com.bjjw.rule.model.entity.RuleVariableOption;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.BatchTestService;
import com.bjjw.rule.server.service.RuleDataObjectService;
import com.bjjw.rule.server.service.RuleVariableService;
import com.bjjw.rule.server.service.SchemaSyncService;
import com.bjjw.rule.core.util.ScriptNameUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/variable")
public class RuleVariableController {

    @Resource
    private RuleVariableService variableService;

    @Resource
    private RuleDataObjectService dataObjectService;

    @Resource
    private BatchTestService batchTestService;

    @Resource
    private SchemaSyncService schemaSyncService;

    /** 健康检查，用于验证变量管理接口是否正常注册 */
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }

    /**
     * 分页查询变量（POST + JSON）
     */
    @PostMapping("/list")
    public R<IPage<RuleVariable>> list(@RequestBody RuleVariableListRequest req) {
        int pageNum = req.getPageNum() == null ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() == null ? 10 : req.getPageSize();
        return R.ok(variableService.pageList(pageNum, pageSize, req.getProjectId(), req.getVarType(),
                req.getKeyword(), req.getStandaloneOnly(), req.getVarSource()));
    }

    /** 从 Java 常量类批量导入常量 */
    @PostMapping("/import/constants/java")
    public R<Map<String, Object>> importConstantsJava(@RequestBody RuleVariableImportConstantsJavaRequest req) {
        Map<String, Object> result = variableService.importConstantsFromJava(req.getProjectId(), req.getJavaSource());
        trySyncSchema();
        return R.ok(result);
    }

    /** 从扁平 JSON 批量导入常量 */
    @PostMapping("/import/constants/json")
    public R<Map<String, Object>> importConstantsJson(@RequestBody RuleVariableImportConstantsJsonRequest req) {
        Map<String, Object> result = variableService.importConstantsFromJson(req.getProjectId(), req.getJsonContent());
        trySyncSchema();
        return R.ok(result);
    }

    /** 按项目列出变量 */
    @PostMapping("/project/query")
    public R<List<RuleVariable>> listByProject(@RequestBody RuleVariableProjectQueryRequest req) {
        return R.ok(variableService.listByProject(req.getProjectId()));
    }

    @GetMapping("/{id:\\d+}")
    public R<RuleVariable> get(@PathVariable Long id) {
        return R.ok(variableService.getById(id));
    }

    @PostMapping
    public R<RuleVariable> create(@RequestBody RuleVariable variable) {
        if (variable.getScriptName() == null || variable.getScriptName().isEmpty()) {
            variable.setScriptName(ScriptNameUtil.toCamelCase(variable.getVarCode()));
        }
        try {
            variableService.save(variable);
            return R.ok(variable);
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleVariable variable) {
        try {
            variableService.updateById(variable);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        variableService.deleteWithOptions(id);
        return R.ok();
    }

    @GetMapping("/{variableId:\\d+}/options")
    public R<List<RuleVariableOption>> getOptions(@PathVariable Long variableId) {
        return R.ok(variableService.getOptions(variableId));
    }

    @PostMapping("/{variableId:\\d+}/options")
    public R<Void> saveOptions(@PathVariable Long variableId, @RequestBody List<RuleVariableOption> options) {
        variableService.saveOptions(variableId, options);
        return R.ok();
    }

    /** 变量树（设计器） */
    @PostMapping("/tree/query")
    public R<List<Map<String, Object>>> tree(@RequestBody RuleVariableTreeQueryRequest req) {
        return R.ok(dataObjectService.getVariableTree(req.getProjectId()));
    }

    /** 项目维度批量校验规则 */
    @PostMapping("/batch-validate")
    public R<List<RuleValidationResult>> batchValidate(@RequestBody RuleVariableBatchValidateRequest req) {
        return R.ok(batchTestService.validateProjectRules(req.getProjectId()));
    }

    private void trySyncSchema() {
        try {
            schemaSyncService.syncAndGetStatus();
        } catch (Exception ignored) {
        }
    }
}
