package com.bjjw.rule.server.service;

import com.bjjw.rule.core.compiler.CompileResult;
import com.bjjw.rule.model.dto.RulePushMessage;
import com.bjjw.rule.model.entity.*;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleDefinitionVersion;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.server.mapper.RuleDefinitionContentMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionVersionMapper;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.bjjw.rule.server.publish.RulePushService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RulePublishService {

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RuleDefinitionVersionMapper versionMapper;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePushService pushService;

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

    @Transactional
    public String publish(Long definitionId, String changeLog) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) {
            return "规则定义不存在";
        }

        RuleDefinitionContent content = definitionService.getContent(definitionId);
        if (content == null) {
            return "规则内容不存在";
        }

        if (content.getCompileStatus() != 1) {
            CompileResult compileResult = compileService.compile(definitionId);
            if (!compileResult.isSuccess()) {
                return "编译失败: " + compileResult.getErrorMessage();
            }
            content = definitionService.getContent(definitionId);
        }

        int newVersion = (definition.getPublishedVersion() != null ? definition.getPublishedVersion() : 0) + 1;

        String fullScript = buildFullScript(content.getCompiledScript(), definition.getProjectId());

        String projectCode = null;
        if (definition.getProjectId() != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            if (project != null) {
                projectCode = project.getProjectCode();
            }
        }

        RuleDefinitionVersion version = new RuleDefinitionVersion();
        version.setDefinitionId(definitionId);
        version.setVersion(newVersion);
        version.setModelJson(content.getModelJson());
        version.setCompiledScript(content.getCompiledScript());
        version.setCompiledType(content.getCompiledType());
        version.setChangeLog(changeLog);
        versionMapper.insert(version);

        RulePublished existing = publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getRuleCode, definition.getRuleCode()));
        if (existing != null) {
            existing.setVersion(newVersion);
            existing.setModelType(definition.getModelType());
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
            published.setVersion(newVersion);
            published.setModelType(definition.getModelType());
            published.setCompiledScript(fullScript);
            published.setCompiledType(content.getCompiledType());
            published.setModelJson(content.getModelJson());
            published.setStatus(1);
            publishedMapper.insert(published);
        }

        definition.setPublishedVersion(newVersion);
        definition.setStatus(1);
        definitionService.updateById(definition);

        RulePushMessage pushMessage = new RulePushMessage();
        pushMessage.setRuleCode(definition.getRuleCode());
        pushMessage.setVersion(newVersion);
        pushMessage.setModelType(definition.getModelType());
        pushMessage.setCompiledScript(fullScript);
        pushMessage.setCompiledType(content.getCompiledType());
        pushMessage.setModelJson(content.getModelJson());
        pushMessage.setProjectCode(projectCode);
        pushMessage.setPublishTime(System.currentTimeMillis());
        pushMessage.setAction("PUBLISH");
        pushService.push(pushMessage);

        return null;
    }

    @Transactional
    public String unpublish(Long definitionId) {
        RuleDefinition definition = definitionService.getById(definitionId);
        if (definition == null) return "规则定义不存在";

        RulePublished published = publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>().eq(RulePublished::getRuleCode, definition.getRuleCode()));
        if (published != null) {
            published.setStatus(0);
            published.setOfflineTime(LocalDateTime.now());
            publishedMapper.updateById(published);
        }

        definition.setStatus(2);
        definitionService.updateById(definition);

        RulePushMessage pushMessage = new RulePushMessage();
        pushMessage.setRuleCode(definition.getRuleCode());
        pushMessage.setAction("UNPUBLISH");
        pushMessage.setPublishTime(System.currentTimeMillis());
        pushService.push(pushMessage);

        return null;
    }
}
