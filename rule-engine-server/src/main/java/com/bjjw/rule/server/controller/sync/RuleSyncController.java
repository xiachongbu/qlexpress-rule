package com.bjjw.rule.server.controller.sync;

import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.server.common.R;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.bjjw.rule.server.service.RuleFunctionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rule/sync")
public class RuleSyncController {

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RuleFunctionService functionService;

    @GetMapping("/{ruleCode}")
    public R<RulePublished> getByCode(@PathVariable String ruleCode) {
        RulePublished published = publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, ruleCode)
                        .eq(RulePublished::getStatus, 1));
        return R.ok(published);
    }

    @GetMapping("/all")
    public R<List<RulePublished>> getAll() {
        List<RulePublished> list = publishedMapper.selectList(
                new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getStatus, 1));
        return R.ok(list);
    }

    @GetMapping("/versions")
    public R<Map<String, Integer>> getVersions() {
        List<RulePublished> list = publishedMapper.selectList(
                new LambdaQueryWrapper<RulePublished>()
                        .select(RulePublished::getRuleCode, RulePublished::getVersion)
                        .eq(RulePublished::getStatus, 1));
        Map<String, Integer> versions = list.stream()
                .collect(Collectors.toMap(RulePublished::getRuleCode, RulePublished::getVersion));
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
