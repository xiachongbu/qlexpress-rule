package com.bjjw.rule.server.service;

import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.model.entity.RulePublishedSet;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.bjjw.rule.server.mapper.RulePublishedSetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 按客户端 compId 解析应使用的已上线发布行：省行优先，否则回落全国（0）。
 */
@Service
public class PublishedRuleResolveService {

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePublishedSetMapper publishedSetMapper;

    /**
     * 解析单条规则：非全国时先查省行，再查全国行。
     *
     * @param ruleCode 规则编码
     * @param compId   客户端 compId，空视为全国
     * @return 命中行；无则 null
     */
    public RulePublished resolveOnline(String ruleCode, String compId, String projectCode) {
        String n = RuleCompIds.normalize(compId);
        if (!RuleCompIds.NATIONAL.equals(n)) {
            RulePublished provincial = publishedMapper.selectOne(
                    new LambdaQueryWrapper<RulePublished>()
                            .eq(RulePublished::getRuleCode, ruleCode)
                            .eq(RulePublished::getProjectCode, projectCode)
                            .eq(RulePublished::getCompId, n)
                            .eq(RulePublished::getStatus, 1));
            if (provincial != null) {
                return provincial;
            }
        }
        return publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, ruleCode)
                        .eq(RulePublished::getProjectCode, projectCode)
                        .eq(RulePublished::getCompId, RuleCompIds.NATIONAL)
                        .eq(RulePublished::getStatus, 1));
    }

    /**
     * 所有当前已上线的 ruleCode 去重列表（任意 comp 有一条上线即计入）。
     *
     * @return ruleCode 列表
     */
    public List<String> listDistinctOnlineRuleCodes(String projectCode) {
        List<RulePublished> rows = publishedMapper.selectList(
                new LambdaQueryWrapper<RulePublished>()
                        .select(RulePublished::getRuleCode)
                        .eq(RulePublished::getProjectCode, projectCode)
                        .eq(RulePublished::getStatus, 1));
        return rows.stream()
                .map(RulePublished::getRuleCode)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 解析已上线规则集快照：省优先，否则全国。
     */
    public RulePublishedSet resolveOnlineSet(String setCode, String compId, String projectCode) {
        String n = RuleCompIds.normalize(compId);
        if (!RuleCompIds.NATIONAL.equals(n)) {
            RulePublishedSet provincial = publishedSetMapper.selectOne(
                    new LambdaQueryWrapper<RulePublishedSet>()
                            .eq(RulePublishedSet::getSetCode, setCode)
                            .eq(RulePublishedSet::getProjectCode, projectCode)
                            .eq(RulePublishedSet::getCompId, n)
                            .eq(RulePublishedSet::getStatus, 1));
            if (provincial != null) {
                return provincial;
            }
        }
        return publishedSetMapper.selectOne(
                new LambdaQueryWrapper<RulePublishedSet>()
                        .eq(RulePublishedSet::getSetCode, setCode)
                        .eq(RulePublishedSet::getProjectCode, projectCode)
                        .eq(RulePublishedSet::getCompId, RuleCompIds.NATIONAL)
                        .eq(RulePublishedSet::getStatus, 1));
    }

    /**
     * 当前已上线的规则集编码去重列表
     */
    public List<String> listDistinctOnlineSetCodes(String projectCode) {
        List<Object> objs = publishedSetMapper.selectObjs(
                new LambdaQueryWrapper<RulePublishedSet>()
                        .select(RulePublishedSet::getSetCode)
                        .eq(RulePublishedSet::getProjectCode, projectCode)
                        .eq(RulePublishedSet::getStatus, 1));
        return objs.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .collect(Collectors.toList());
    }
}
