package com.bjjw.rule.server.controller.sync;

import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.entity.RulePublishedSet;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.service.PublishedRuleResolveService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则集同步接口（供 RuleEngineClient HTTP 拉取）
 */
@RestController
@RequestMapping("/api/rule/sync/set")
public class RuleSetSyncController {

    @Resource
    private PublishedRuleResolveService publishedRuleResolveService;

    /**
     * 按 compId 解析一条已上线规则集快照
     */
    @GetMapping("/{setCode}")
    public R<RulePublishedSet> getByCode(
            @PathVariable String setCode,
            @RequestParam(defaultValue = RuleCompIds.NATIONAL) String compId,
            @RequestParam String projectCode) {
        RulePublishedSet row = publishedRuleResolveService.resolveOnlineSet(setCode, compId, projectCode);
        return R.ok(row);
    }

    /**
     * 全量：对每个已上线 setCode 解析出一条（省优先回落全国）
     */
    @GetMapping("/all")
    public R<List<RulePublishedSet>> getAll(
            @RequestParam(defaultValue = RuleCompIds.NATIONAL) String compId,
            @RequestParam String projectCode ) {
        List<String> codes = publishedRuleResolveService.listDistinctOnlineSetCodes(projectCode);
        List<RulePublishedSet> out = new ArrayList<>();
        for (String code : codes) {
            RulePublishedSet r = publishedRuleResolveService.resolveOnlineSet(code, compId, projectCode);
            if (r != null) {
                out.add(r);
            }
        }
        return R.ok(out);
    }
}
