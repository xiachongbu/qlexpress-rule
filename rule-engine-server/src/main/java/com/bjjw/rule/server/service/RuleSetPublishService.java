package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RulePushMessage;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.model.entity.RulePublishedSet;
import com.bjjw.rule.model.entity.RuleRuleSet;
import com.bjjw.rule.model.entity.RuleRuleSetMember;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.bjjw.rule.server.mapper.RulePublishedSetMapper;
import com.bjjw.rule.server.mapper.RuleRuleSetMemberMapper;
import com.bjjw.rule.server.publish.RulePublishedL2Service;
import com.bjjw.rule.server.publish.RulePushService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 规则集发布与下线（对齐单规则发布：MySQL + Redis L2 + Pub/Sub）
 */
@Service
public class RuleSetPublishService {

    @Resource
    private RuleRuleSetService ruleRuleSetService;

    @Resource
    private RuleRuleSetMemberMapper ruleRuleSetMemberMapper;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePublishedSetMapper publishedSetMapper;

    @Resource
    private PublishedRuleResolveService publishedRuleResolveService;

    @Resource
    private RulePublishedL2Service publishedL2Service;

    @Resource
    private RulePushService pushService;

    /**
     * 发布规则集：校验成员在目标作用域均已上线后写入 rule_published_set、L2 并广播
     *
     * @param scopeCompIds null 或空则自动取各成员已上线作用域的交集
     */
    @Transactional
    public String publish(Long setId, List<String> scopeCompIds) {
        RuleRuleSet set = ruleRuleSetService.getById(setId);
        if (set == null) {
            return "规则集不存在";
        }
        List<RuleRuleSetMember> members = ruleRuleSetMemberMapper.selectList(
                new LambdaQueryWrapper<RuleRuleSetMember>()
                        .eq(RuleRuleSetMember::getSetId, setId)
                        .orderByAsc(RuleRuleSetMember::getSortOrder));
        if (members.isEmpty()) {
            return "请先配置规则集成员";
        }
        List<String> orderedCodes = new ArrayList<>();
        for (RuleRuleSetMember m : members) {
            RuleDefinition def = definitionService.getById(m.getDefinitionId());
            if (def == null) {
                return "成员规则不存在，definitionId=" + m.getDefinitionId();
            }
            if (!set.getProjectId().equals(def.getProjectId())) {
                return "成员规则必须属于同一项目: " + def.getRuleCode();
            }
            orderedCodes.add(def.getRuleCode());
        }

        Set<String> toPublish;
        String projectCode = projectService.getById(set.getProjectId()).getProjectCode();
        if (scopeCompIds != null && !scopeCompIds.isEmpty()) {
            toPublish = scopeCompIds.stream().map(RuleCompIds::normalize).collect(Collectors.toCollection(LinkedHashSet::new));
            for (String comp : toPublish) {
                String err = verifyAllMembersOnline(orderedCodes, comp, projectCode);
                if (err != null) {
                    return err;
                }
            }
        } else {
            toPublish = computeIntersectionScopes(orderedCodes);
            if (toPublish.isEmpty()) {
                return "各成员规则在已上线作用域上没有交集，请指定作用域或先发布成员规则";
            }
        }

        String memberJson = JSON.toJSONString(orderedCodes);
        int batchVersion = (set.getPublishedVersion() != null ? set.getPublishedVersion() : 0) + 1;

        for (String compId : toPublish) {
            RulePublishedSet existing = publishedSetMapper.selectOne(
                    new LambdaQueryWrapper<RulePublishedSet>()
                            .eq(RulePublishedSet::getSetCode, set.getSetCode())
                            .eq(RulePublishedSet::getCompId, compId));
            int rowVer = existing != null && existing.getVersion() != null ? existing.getVersion() + 1 : 1;

            if (existing != null) {
                existing.setVersion(rowVer);
                existing.setMemberRuleCodes(memberJson);
                existing.setProjectCode(projectCode);
                existing.setStatus(1);
                existing.setPublishTime(LocalDateTime.now());
                existing.setOfflineTime(null);
                publishedSetMapper.updateById(existing);
            } else {
                RulePublishedSet row = new RulePublishedSet();
                row.setSetCode(set.getSetCode());
                row.setProjectCode(projectCode);
                row.setCompId(compId);
                row.setVersion(rowVer);
                row.setMemberRuleCodes(memberJson);
                row.setStatus(1);
                row.setPublishTime(LocalDateTime.now());
                publishedSetMapper.insert(row);
            }

            RulePushMessage push = new RulePushMessage();
            push.setAction("SET_PUBLISH");
            push.setSetCode(set.getSetCode());
            push.setMemberRuleCodes(memberJson);
            push.setVersion(rowVer);
            push.setModelType("RULE_SET");
            push.setProjectCode(projectCode);
            push.setCompId(compId);
            push.setPublishTime(System.currentTimeMillis());
            publishedL2Service.savePublishedSetSnapshot(push);
            pushService.push(push);
        }

        set.setPublishedVersion(batchVersion);
        set.setStatus(1);
        ruleRuleSetService.updateById(set);
        return null;
    }

    private String verifyAllMembersOnline(List<String> orderedCodes, String compId, String projectCode) {
        String n = RuleCompIds.normalize(compId);
        for (String code : orderedCodes) {
            RulePublished hit = publishedRuleResolveService.resolveOnline(code, n, projectCode);
            if (hit == null) {
                return "作用域 " + n + " 上成员规则未上线或不可解析: " + code;
            }
        }
        return null;
    }

    /**
     * 各成员规则「当前已上线」作用域集合的交集
     */
    private Set<String> computeIntersectionScopes(List<String> orderedCodes) {
        Set<String> inter = null;
        for (String code : orderedCodes) {
            List<RulePublished> rows = publishedMapper.selectList(
                    new LambdaQueryWrapper<RulePublished>()
                            .eq(RulePublished::getRuleCode, code)
                            .eq(RulePublished::getStatus, 1));
            Set<String> comps = rows.stream()
                    .map(r -> RuleCompIds.normalize(r.getCompId()))
                    .collect(Collectors.toCollection(HashSet::new));
            if (inter == null) {
                inter = new HashSet<>(comps);
            } else {
                inter.retainAll(comps);
            }
            if (inter.isEmpty()) {
                break;
            }
        }
        return inter == null ? new HashSet<>() : inter;
    }

    /**
     * 下线规则集：所有已发布作用域置下线并清理 L2
     */
    @Transactional
    public String unpublish(Long setId) {
        RuleRuleSet set = ruleRuleSetService.getById(setId);
        if (set == null) {
            return "规则集不存在";
        }
        List<RulePublishedSet> online = publishedSetMapper.selectList(
                new LambdaQueryWrapper<RulePublishedSet>()
                        .eq(RulePublishedSet::getSetCode, set.getSetCode())
                        .eq(RulePublishedSet::getStatus, 1));
        LocalDateTime now = LocalDateTime.now();
        for (RulePublishedSet p : online) {
            p.setStatus(0);
            p.setOfflineTime(now);
            publishedSetMapper.updateById(p);
        }
        set.setStatus(2);
        ruleRuleSetService.updateById(set);
        RuleProject project = projectService.getById(set.getProjectId());
        publishedL2Service.removeAllPublishedSetSnapshots(project.getProjectCode(), set.getSetCode());
        RulePushMessage push = new RulePushMessage();
        push.setAction("SET_UNPUBLISH");
        push.setSetCode(set.getSetCode());
        push.setProjectCode(project.getProjectCode());
        push.setPublishTime(System.currentTimeMillis());
        pushService.push(push);
        return null;
    }
}
