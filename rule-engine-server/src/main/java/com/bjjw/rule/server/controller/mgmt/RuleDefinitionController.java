package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.core.compiler.CompileResult;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RuleResult;
import com.bjjw.rule.model.dto.RuleDefinitionDesignSnapshotListVO;
import com.bjjw.rule.model.dto.mgmt.*;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleCompileService;
import com.bjjw.rule.server.service.RuleDefinitionService;
import com.bjjw.rule.server.service.RuleExecuteService;
import com.bjjw.rule.server.service.RulePublishService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/definition")
public class RuleDefinitionController {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleExecuteService executeService;

    @Resource
    private RulePublishService publishService;

    /**
     * 分页查询规则定义（POST + JSON）。
     */
    @PostMapping("/list")
    public R<IPage<RuleDefinition>> list(@RequestBody RuleDefinitionListRequest req) {
        int pageNum = req.getPageNum() == null ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() == null ? 10 : req.getPageSize();
        return R.ok(definitionService.pageList(pageNum, pageSize, req.getProjectId(), req.getModelType(), req.getKeyword()));
    }

    @GetMapping("/{id}")
    public R<RuleDefinition> get(@PathVariable Long id) {
        return R.ok(definitionService.getById(id));
    }

    @PostMapping
    public R<RuleDefinition> create(@RequestBody RuleDefinition definition) {
        return R.ok(definitionService.createWithContent(definition));
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleDefinition definition) {
        definitionService.updateById(definition);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        definitionService.deleteWithContent(id);
        return R.ok();
    }

    /**
     * 按省份删除规则内容（已发布状态不允许删除）
     */
    @PostMapping("/content/delete")
    public R<Void> deleteScopedContent(@RequestBody RuleDefinitionScopedDeleteRequest req) {
        try {
            definitionService.deleteScopedContent(req.getDefinitionId(), req.getScopeCompId());
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 查询单作用域设计内容；默认全国 scopeCompId=0
     */
    @PostMapping("/content/query")
    public R<RuleDefinitionContent> getContent(@RequestBody RuleDefinitionContentQueryRequest req) {
        String scope = req.getScopeCompId() != null && !req.getScopeCompId().isEmpty()
                ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        return R.ok(definitionService.getContent(req.getDefinitionId(), scope));
    }

    /**
     * 列出某定义下全部作用域内容
     */
    @PostMapping("/content/list")
    public R<List<RuleDefinitionContent>> listContents(@RequestBody RuleDefinitionContentListRequest req) {
        return R.ok(definitionService.listContents(req.getDefinitionId()));
    }

    /**
     * 新增省份作用域空内容行
     */
    @PostMapping("/content/scope")
    public R<Void> addContentScope(@RequestBody RuleDefinitionAddScopeRequest req) {
        definitionService.addContentScope(req.getDefinitionId(), req.getScopeCompId());
        return R.ok();
    }

    /**
     * 删除省份作用域内容（scopeCompId 不可为 0）
     */
    @PostMapping("/content/scope/remove")
    public R<Void> deleteContentScope(@RequestBody RuleDefinitionRemoveScopeRequest req) {
        definitionService.deleteContentScope(req.getDefinitionId(), req.getScopeCompId());
        return R.ok();
    }

    /**
     * 保存模型；可选 scopeCompId（默认 0）、changeLog、recordHistory（默认 true）
     */
    @PostMapping("/save")
    public R<Void> saveContent(@RequestBody RuleDefinitionSaveContentRequest req) {
        String scopeCompId = req.getScopeCompId() != null ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        boolean recordHistory = parseRecordHistory(req.getRecordHistory(), true);
        definitionService.saveContent(req.getDefinitionId(), scopeCompId, req.getModelJson(), req.getChangeLog(), recordHistory);
        return R.ok();
    }

    private static boolean parseRecordHistory(Boolean raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        return raw;
    }

    /**
     * 设计保存快照分页列表（不含 model_json）
     */
    @PostMapping("/content/snapshot/list")
    public R<IPage<RuleDefinitionDesignSnapshotListVO>> listDesignSnapshots(@RequestBody RuleDefinitionDesignSnapshotListRequest req) {
        int pageNum = req.getPageNum() == null ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() == null ? 20 : req.getPageSize();
        String scope = req.getScopeCompId() != null && !req.getScopeCompId().isEmpty()
                ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        return R.ok(definitionService.pageDesignSnapshots(req.getDefinitionId(), scope, pageNum, pageSize));
    }

    /**
     * 获取单条设计快照的 modelJson
     */
    @PostMapping("/content/snapshot/get")
    public R<RuleDefinitionSnapshotModelJsonVO> getDesignSnapshot(@RequestBody RuleDefinitionDesignSnapshotGetRequest req) {
        String scope = req.getScopeCompId() != null && !req.getScopeCompId().isEmpty()
                ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        String modelJson = definitionService.getDesignSnapshotModelJson(req.getId(), req.getDefinitionId(), scope);
        if (modelJson == null) {
            return R.fail("快照不存在或无权访问");
        }
        RuleDefinitionSnapshotModelJsonVO vo = new RuleDefinitionSnapshotModelJsonVO();
        vo.setModelJson(modelJson);
        return R.ok(vo);
    }

    /**
     * 编译指定作用域；默认全国 0
     */
    @PostMapping("/compile/{definitionId}")
    public R<CompileResult> compile(@PathVariable Long definitionId, @RequestBody(required = false) RuleDefinitionCompileRequest req) {
        String scope = RuleCompIds.NATIONAL;
        if (req != null && req.getScopeCompId() != null && !req.getScopeCompId().isEmpty()) {
            scope = req.getScopeCompId();
        }
        return R.ok(compileService.compile(definitionId, scope));
    }

    /**
     * 试跑
     */
    @PostMapping("/execute")
    public R<RuleResult> execute(@RequestBody RuleDefinitionTestExecuteRequest req) {
        String scopeCompId = req.getScopeCompId() != null ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        Map<String, Object> params = req.getParams() != null ? req.getParams() : Collections.emptyMap();
        return R.ok(executeService.testExecute(req.getDefinitionId(), scopeCompId, params, req.getBusinessId()));
    }

    @PostMapping("/publish/{definitionId}")
    public R<Void> publish(@PathVariable Long definitionId, @RequestBody(required = false) RuleDefinitionPublishBodyRequest body) {
        String changeLog = body != null ? body.getChangeLog() : null;
        List<String> scopeCompIds = body != null ? body.getScopeCompIds() : null;
        String error = publishService.publish(definitionId, changeLog, scopeCompIds);
        return error == null ? R.ok() : R.fail(error);
    }

    @PostMapping("/unpublish")
    public R<Void> unpublish(@RequestBody RuleDefinitionUnpublishRequest req) {
        String error = publishService.unpublish(req.getDefinitionId(), req.getScopeCompId());
        return error == null ? R.ok() : R.fail(error);
    }

    /**
     * 技术人员直接保存脚本（脚本模式）
     */
    @PostMapping("/script/{definitionId:\\d+}")
    public R<Void> saveScript(@PathVariable Long definitionId, @RequestBody RuleDefinitionScriptSaveRequest req) {
        String scope = req.getScopeCompId() != null && !req.getScopeCompId().isEmpty()
                ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        String script = req.getScript();
        if (script == null || script.trim().isEmpty()) {
            return R.fail("脚本内容不能为空");
        }
        definitionService.saveScript(definitionId, scope, script.trim());
        return R.ok();
    }

    /**
     * 更新编辑模式（visual/script）
     */
    @PostMapping("/scriptMode/{definitionId:\\d+}")
    public R<Void> updateScriptMode(@PathVariable Long definitionId, @RequestBody RuleDefinitionScriptModeRequest req) {
        String scope = req.getScopeCompId() != null && !req.getScopeCompId().isEmpty()
                ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        String mode = req.getScriptMode();
        if (mode == null || (!"visual".equals(mode) && !"script".equals(mode))) {
            return R.fail("scriptMode 必须为 visual 或 script");
        }
        definitionService.updateScriptMode(definitionId, scope, mode);
        return R.ok();
    }

    @PostMapping("/validateScript/{definitionId:\\d+}")
    public R<CompileResult> validateScript(@PathVariable Long definitionId,
                                           @RequestBody RuleDefinitionValidateScriptRequest req) {
        String script = req.getScript();
        if (script == null || script.trim().isEmpty()) {
            return R.fail("脚本内容不能为空");
        }
        return R.ok(compileService.validateScript(script.trim()));
    }

    /**
     * 复制规则到目标省份作用域
     */
    @PostMapping("/copy")
    public R<Void> copyToScopes(@RequestBody RuleDefinitionCopyRequest req) {
        if (req.getDefinitionId() == null) {
            return R.fail("definitionId 不能为空");
        }
        if (req.getTargetScopeCompIds() == null || req.getTargetScopeCompIds().isEmpty()) {
            return R.fail("目标省份列表不能为空");
        }
        definitionService.copyToScopes(req.getDefinitionId(), req.getTargetScopeCompIds());
        return R.ok();
    }

    /**
     * 修改规则内容的省份归属和说明
     */
    @PostMapping("/content/updateMeta")
    public R<Void> updateContentMeta(@RequestBody RuleDefinitionContentUpdateRequest req) {
        try {
            definitionService.updateContentMeta(req.getDefinitionId(), req.getScopeCompId(), req.getCompId(), req.getDescription());
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        }
    }
}
