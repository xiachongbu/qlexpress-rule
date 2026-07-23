package com.bjjw.rule.server.controller.sync;

import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.PublishedRuleResolveService;
import com.bjjw.rule.server.service.RuleFunctionService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/sync")
public class RuleSyncController {

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private PublishedRuleResolveService publishedRuleResolveService;

    /**
     * 按客户端 compId 解析后返回一条已上线发布快照
     *
     * @param ruleCode 规则编码
     * @param compId   省份 compId，缺省为 0（全国）
     */
    @GetMapping("/{ruleCode}")
    public R<RulePublished> getByCode(
            @PathVariable String ruleCode,
            @RequestParam(defaultValue = RuleCompIds.NATIONAL) String compId,
            @RequestParam String projectCode) {
        RulePublished published = publishedRuleResolveService.resolveOnline(ruleCode, compId, projectCode);
        return R.ok(published);
    }

    /**
     * 对每个已上线 ruleCode 解析出一条（省优先、否则全国）后返回列表
     */
    @GetMapping("/all")
    public R<List<RulePublished>> getAll(
            @RequestParam(defaultValue = RuleCompIds.NATIONAL) String compId,
            @RequestParam String projectCode) {
        List<String> codes = publishedRuleResolveService.listDistinctOnlineRuleCodes(projectCode);
        List<RulePublished> out = new java.util.ArrayList<>();
        for (String code : codes) {
            RulePublished r = publishedRuleResolveService.resolveOnline(code, compId, projectCode);
            if (r != null) {
                out.add(r);
            }
        }
        return R.ok(out);
    }

    /**
     * 各 ruleCode 在当前 compId 下解析命中行的版本号
     */
    @GetMapping("/versions")
    public R<Map<String, Integer>> getVersions(
            @RequestParam(defaultValue = RuleCompIds.NATIONAL) String compId,
            @RequestParam String projectCode) {
        List<String> codes = publishedRuleResolveService.listDistinctOnlineRuleCodes(projectCode);
        Map<String, Integer> versions = new LinkedHashMap<>();
        for (String code : codes) {
            RulePublished r = publishedRuleResolveService.resolveOnline(code, compId, projectCode);
            if (r != null && r.getVersion() != null) {
                versions.put(code, r.getVersion());
            }
        }
        return R.ok(versions);
    }

    /**
     * 同步项目下所有已启用的函数定义（JAVA/BEAN/SCRIPT），供客户端 SDK 拉取并注册
     */
    @GetMapping("/functions/{projectId}")
    public R<List<RuleFunction>> syncFunctions(@PathVariable Long projectId) {
        return R.ok(functionService.listByProject(projectId));
    }
}
