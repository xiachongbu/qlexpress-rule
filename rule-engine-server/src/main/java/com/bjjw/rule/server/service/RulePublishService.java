package com.bjjw.rule.server.service;

import com.bjjw.rule.core.compiler.CompileResult;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RulePushMessage;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleDefinitionVersion;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.server.mapper.RuleDefinitionVersionMapper;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.bjjw.rule.server.publish.RulePublishedL2Service;
import com.bjjw.rule.server.publish.RulePushService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RulePublishService {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleDefinitionVersionMapper versionMapper;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePushService pushService;

    @Resource
    private RulePublishedL2Service publishedL2Service;

    @Resource
    private RuleCompileService compileService;

    @Resource
    private RuleFunctionService functionService;

    @Resource
    private FunctionRegistrar functionRegistrar;

    /**
     * 将 SCRIPT 函数定义拼接到编译脚本前面，使客户端同步后可直接执行
     */
    private String buildFullScript(String compiledScript, Long projectId) {
        List<RuleFunction> allFuncs = functionService.listByProject(projectId);
        List<RuleFunction> scriptFuncs = allFuncs.stream()
                .filter(f -> "SCRIPT".equals(f.getImplType()))
                .collect(Collectors.toList());
        String funcPrefix = functionRegistrar.buildScriptFunctionPrefix(scriptFuncs);
        if (funcPrefix.isEmpty()) {
            return compiledScript;
        }
        return funcPrefix + compiledScript;
    }

    /**
     * 发布：对每个已编译成功的作用域写/更新 rule_published 并推送 L2 + 广播。
     * {@code scopeCompIds == null} 时发布全部已编译作用域；非 null 时须非空，仅发布列表与已编译内容的交集。
     *
     * @param scopeCompIds 要发布的 compId；null 表示全部
     */
    @Transactional
    public String publish(Long definitionId, String changeLog, List<String> scopeCompIds) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return "规则定义不存在";
        }

        List<RuleDefinitionContent> scopes = definitionService.listContents(definitionId);
        if (scopes.isEmpty()) {
            return "规则内容不存在";
        }

        for (RuleDefinitionContent c : scopes) {
            if (c.getCompileStatus() == null || c.getCompileStatus() != 1) {
                CompileResult cr = compileService.compile(definitionId, c.getScopeCompId());
                if (!cr.isSuccess()) {
                    return "编译失败(scope=" + RuleCompIds.normalize(c.getScopeCompId()) + "): " + cr.getErrorMessage();
                }
            }
        }

        scopes = definitionService.listContents(definitionId);
        List<RuleDefinitionContent> compiled = scopes.stream()
                .filter(x -> x.getCompileStatus() != null && x.getCompileStatus() == 1)
                .collect(Collectors.toList());
        if (compiled.isEmpty()) {
            return "没有可发布的已编译作用域";
        }

        if (scopeCompIds != null) {
            if (scopeCompIds.isEmpty()) {
                return "请至少选择一个要发布的作用域";
            }
            Set<String> wanted = scopeCompIds.stream()
                    .map(RuleCompIds::normalize)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            compiled = compiled.stream()
                    .filter(x -> wanted.contains(RuleCompIds.normalize(x.getScopeCompId())))
                    .collect(Collectors.toList());
            if (compiled.isEmpty()) {
                return "所选作用域中无可发布的已编译内容，请确认已编译成功且作用域选择正确";
            }
        }

        String projectCode = null;
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }

        // batchVersion 取所有待发布 content 中的最大 publishedVersion + 1
        int maxPubVer = compiled.stream()
                .mapToInt(c -> c.getPublishedVersion() != null ? c.getPublishedVersion() : 0)
                .max().orElse(0);
        int batchVersion = maxPubVer + 1;

        RuleDefinitionContent historySource = definitionService.getContent(definitionId, RuleCompIds.NATIONAL);
        if (historySource == null || historySource.getCompileStatus() == null || historySource.getCompileStatus() != 1) {
            historySource = compiled.get(0);
        }

        RuleDefinitionVersion version = new RuleDefinitionVersion();
        version.setDefinitionId(definitionId);
        version.setVersion(batchVersion);
        version.setModelJson(historySource.getModelJson());
        version.setCompiledScript(historySource.getCompiledScript());
        version.setCompiledType(historySource.getCompiledType());
        version.setChangeLog(changeLog);
        versionMapper.insert(version);

        for (RuleDefinitionContent content : compiled) {
            String compId = RuleCompIds.normalize(content.getScopeCompId());
            String fullScript = buildFullScript(content.getCompiledScript(), definition.getProjectId());

            RulePublished existing = publishedMapper.selectOne(
                    new LambdaQueryWrapper<RulePublished>()
                            .eq(RulePublished::getRuleCode, definition.getRuleCode())
                            .eq(RulePublished::getCompId, compId));

            int rowVer = existing != null && existing.getVersion() != null ? existing.getVersion() + 1 : 1;

            if (existing != null) {
                existing.setCompId(compId);
                existing.setVersion(rowVer);
                existing.setModelType(content.getModelType());
                existing.setCompiledScript(fullScript);
                existing.setCompiledType(content.getCompiledType());
                existing.setModelJson(content.getModelJson());
                existing.setProjectCode(projectCode);
                existing.setStatus(1);
                existing.setPublishTime(LocalDateTime.now());
                existing.setOfflineTime(null);
                publishedMapper.updateById(existing);
            } else {
                RulePublished published = new RulePublished();
                published.setRuleCode(definition.getRuleCode());
                published.setDefinitionId(definitionId);
                published.setProjectCode(projectCode);
                published.setCompId(compId);
                published.setVersion(rowVer);
                published.setModelType(content.getModelType());
                published.setCompiledScript(fullScript);
                published.setCompiledType(content.getCompiledType());
                published.setModelJson(content.getModelJson());
                published.setStatus(1);
                publishedMapper.insert(published);
            }

            RulePushMessage pushMessage = new RulePushMessage();
            pushMessage.setRuleCode(definition.getRuleCode());
            pushMessage.setCompId(compId);
            pushMessage.setVersion(rowVer);
            pushMessage.setModelType(content.getModelType());
            pushMessage.setCompiledScript(fullScript);
            pushMessage.setCompiledType(content.getCompiledType());
            pushMessage.setModelJson(content.getModelJson());
            pushMessage.setProjectCode(projectCode);
            pushMessage.setPublishTime(System.currentTimeMillis());
            pushMessage.setAction("PUBLISH");
            publishedL2Service.savePublishedSnapshot(pushMessage);
            pushService.push(pushMessage);
        }

        // 更新每个已发布 content 的 status 和 publishedVersion
        for (RuleDefinitionContent c : compiled) {
            c.setStatus(1);
            c.setPublishedVersion(batchVersion);
            definitionService.updateContentById(c);
        }

        return null;
    }

    /**
     * 下线：若 scopeCompId 不为空则只下线该省份；否则下线所有作用域。
     */
    @Transactional
    public String unpublish(Long definitionId, String scopeCompId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return "规则定义不存在";
        }

        LambdaQueryWrapper<RulePublished> pubQuery = new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getRuleCode, definition.getRuleCode())
                .eq(RulePublished::getStatus, 1);
        boolean scopedMode = scopeCompId != null && !scopeCompId.isEmpty();
        String normalizedScope = scopedMode ? RuleCompIds.normalize(scopeCompId) : null;
        if (scopedMode) {
            pubQuery.eq(RulePublished::getCompId, normalizedScope);
        }

        List<RulePublished> online = publishedMapper.selectList(pubQuery);
        LocalDateTime now = LocalDateTime.now();
        for (RulePublished p : online) {
            p.setStatus(0);
            p.setOfflineTime(now);
            publishedMapper.updateById(p);
        }

        RuleProject project = projectService.getById(definition.getProjectId());
        String projectCode = project.getProjectCode();

        // 更新对应 content 行的 status
        if (scopedMode) {
            RuleDefinitionContent content = definitionService.getContent(definitionId, normalizedScope);
            if (content != null) {
                content.setStatus(2);
                definitionService.updateContentById(content);
            }
            publishedL2Service.removePublishedSnapshot(projectCode, definition.getRuleCode(), normalizedScope);
        } else {
            // 下线所有 content
            List<RuleDefinitionContent> allContents = definitionService.listContents(definitionId);
            for (RuleDefinitionContent c : allContents) {
                if (c.getStatus() != null && c.getStatus() == 1) {
                    c.setStatus(2);
                    definitionService.updateContentById(c);
                }
            }
            publishedL2Service.removeAllPublishedSnapshots(projectCode, definition.getRuleCode());
        }

        RulePushMessage pushMessage = new RulePushMessage();
        pushMessage.setRuleCode(definition.getRuleCode());
        pushMessage.setProjectCode(project.getProjectCode());
        if (scopedMode) {
            pushMessage.setCompId(normalizedScope);
        }
        pushMessage.setPublishTime(System.currentTimeMillis());
        pushMessage.setAction("UNPUBLISH");
        pushService.push(pushMessage);

        return null;
    }
}
