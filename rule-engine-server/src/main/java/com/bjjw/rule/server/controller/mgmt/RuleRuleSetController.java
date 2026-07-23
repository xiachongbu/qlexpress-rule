package com.bjjw.rule.server.controller.mgmt;

import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RuleRuleSetExecuteSummary;
import com.bjjw.rule.model.dto.mgmt.*;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleRuleSet;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.RuleRuleSetService;
import com.bjjw.rule.server.service.RuleSetPublishService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 规则集管理 API（POST + JSON 与现有规则模块一致）
 */
@RestController
@RequestMapping("/api/rule/set")
public class RuleRuleSetController {

    @Resource
    private RuleRuleSetService ruleRuleSetService;

    @Resource
    private RuleSetPublishService ruleSetPublishService;

    /**
     * 分页列表
     */
    @PostMapping("/list")
    public R<IPage<RuleRuleSet>> list(@RequestBody RuleRuleSetListRequest req) {
        int pageNum = req.getPageNum() == null ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() == null ? 10 : req.getPageSize();
        return R.ok(ruleRuleSetService.pageList(pageNum, pageSize, req.getProjectId(), req.getKeyword()));
    }

    @GetMapping("/{id}")
    public R<RuleRuleSet> get(@PathVariable Long id) {
        return R.ok(ruleRuleSetService.getById(id));
    }

    @PostMapping
    public R<RuleRuleSet> create(@RequestBody RuleRuleSet set) {
        String err = ruleRuleSetService.createWithValidation(set);
        return err == null ? R.ok(set) : R.fail(err);
    }

    @PutMapping
    public R<Void> update(@RequestBody RuleRuleSet set) {
        String err = ruleRuleSetService.updateMeta(set);
        return err == null ? R.ok() : R.fail(err);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        String err = ruleRuleSetService.deleteSet(id);
        return err == null ? R.ok() : R.fail(err);
    }

    /**
     * 全量保存成员顺序
     */
    @PostMapping("/members/save")
    public R<Void> saveMembers(@RequestBody RuleRuleSetMembersSaveRequest req) {
        String err = ruleRuleSetService.saveMembers(req.getSetId(), req.getDefinitionIdsInOrder());
        return err == null ? R.ok() : R.fail(err);
    }

    /**
     * 成员规则列表（有序）
     */
    @PostMapping("/members/list")
    public R<List<RuleDefinition>> listMembers(@RequestBody RuleRuleSetMemberListRequest req) {
        if (req.getSetId() == null) {
            return R.ok(Collections.emptyList());
        }
        return R.ok(ruleRuleSetService.listOrderedMembers(req.getSetId()));
    }

    @PostMapping("/publish/{setId}")
    public R<Void> publish(@PathVariable Long setId, @RequestBody(required = false) RuleRuleSetPublishBodyRequest body) {
        List<String> scopes = body != null ? body.getScopeCompIds() : null;
        String err = ruleSetPublishService.publish(setId, scopes);
        return err == null ? R.ok() : R.fail(err);
    }

    @PostMapping("/unpublish/{setId}")
    public R<Void> unpublish(@PathVariable Long setId) {
        String err = ruleSetPublishService.unpublish(setId);
        return err == null ? R.ok() : R.fail(err);
    }

    /**
     * 链式试跑（设计态编译脚本）
     */
    @PostMapping("/execute")
    public R<RuleRuleSetExecuteSummary> execute(@RequestBody RuleRuleSetExecuteRequest req) {
        Long setId = req.getSetId();
        if (setId == null && req.getSetCode() != null && !req.getSetCode().isEmpty()) {
            RuleRuleSet one = ruleRuleSetService.getOne(
                    new LambdaQueryWrapper<RuleRuleSet>()
                            .eq(RuleRuleSet::getSetCode, req.getSetCode().trim())
                            .last("LIMIT 1"));
            setId = one != null ? one.getId() : null;
        }
        if (setId == null) {
            RuleRuleSetExecuteSummary bad = new RuleRuleSetExecuteSummary();
            bad.setSuccess(false);
            bad.setErrorMessage("请指定 setId 或有效的 setCode");
            return R.ok(bad);
        }
        String scope = req.getScopeCompId() != null ? req.getScopeCompId() : RuleCompIds.NATIONAL;
        Map<String, Object> params = req.getParams() != null ? req.getParams() : Collections.emptyMap();
        return R.ok(ruleRuleSetService.executeChain(setId, scope, params, req.getBusinessId()));
    }
}
