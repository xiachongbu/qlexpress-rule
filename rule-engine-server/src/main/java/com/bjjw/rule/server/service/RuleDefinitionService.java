package com.bjjw.rule.server.service;

import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RulePublished;
import com.bjjw.rule.model.dto.RulePushMessage;
import com.bjjw.rule.server.mapper.RuleDefinitionContentMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionMapper;
import com.bjjw.rule.server.mapper.RulePublishedMapper;
import com.bjjw.rule.server.publish.RulePushService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class RuleDefinitionService extends ServiceImpl<RuleDefinitionMapper, RuleDefinition> {

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RulePushService pushService;

    public IPage<RuleDefinition> pageList(int pageNum, int pageSize, Long projectId, String modelType, String keyword) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(RuleDefinition::getProjectId, projectId);
        }
        if (modelType != null && !modelType.isEmpty()) {
            wrapper.eq(RuleDefinition::getModelType, modelType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(RuleDefinition::getRuleName, keyword)
                              .or()
                              .like(RuleDefinition::getRuleCode, keyword));
        }
        wrapper.orderByDesc(RuleDefinition::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Transactional
    public RuleDefinition createWithContent(RuleDefinition definition) {
        save(definition);
        RuleDefinitionContent content = new RuleDefinitionContent();
        content.setDefinitionId(definition.getId());
        content.setModelJson("{}");
        content.setCompileStatus(0);
        contentMapper.insert(content);
        return definition;
    }

    @Transactional
    public void deleteWithContent(Long id) {
        removeById(id);
        contentMapper.delete(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, id));
    }

    public RuleDefinitionContent getContent(Long definitionId) {
        return contentMapper.selectOne(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId));
    }

    public void saveContent(Long definitionId, String modelJson) {
        RuleDefinitionContent content = getContent(definitionId);
        if (content != null) {
            content.setModelJson(modelJson);
            content.setCompileStatus(0);
            contentMapper.updateById(content);
        }
        RuleDefinition definition = getById(definitionId);
        if (definition != null) {
            definition.setCurrentVersion(definition.getCurrentVersion() + 1);
            updateById(definition);
        }
    }

    /**
     * 技术人员手动编辑脚本，直接写入 compiledScript，跳过编译器。
     * compileStatus 置为 1（成功），compileMessage 标注来源，scriptMode 置为 script。
     * 若规则已发布，自动同步更新已发布脚本并推送给客户端。
     */
    @Transactional
    public void saveScript(Long definitionId, String script) {
        RuleDefinitionContent content = getContent(definitionId);
        if (content == null) {
            throw new IllegalArgumentException("规则内容不存在，definitionId=" + definitionId);
        }
        content.setCompiledScript(script);
        content.setCompiledType("QLEXPRESS");
        content.setCompileStatus(1);
        content.setCompileMessage("手动编辑脚本（已跳过编译器）");
        content.setCompileTime(LocalDateTime.now());
        content.setScriptMode("script");
        contentMapper.updateById(content);

        RuleDefinition definition = getById(definitionId);
        if (definition != null && definition.getStatus() == 1) {
            syncPublishedScript(definition, script);
        }
    }

    /**
     * 将手动编辑的脚本同步到已发布表，并推送给客户端
     */
    private void syncPublishedScript(RuleDefinition definition, String script) {
        RulePublished published = publishedMapper.selectOne(
                new LambdaQueryWrapper<RulePublished>()
                        .eq(RulePublished::getRuleCode, definition.getRuleCode()));
        if (published == null) {
            return;
        }
        published.setCompiledScript(script);
        published.setPublishTime(LocalDateTime.now());
        publishedMapper.updateById(published);

        RulePushMessage msg = new RulePushMessage();
        msg.setRuleCode(definition.getRuleCode());
        msg.setVersion(published.getVersion());
        msg.setModelType(definition.getModelType());
        msg.setCompiledScript(script);
        msg.setCompiledType("QLEXPRESS");
        msg.setProjectCode(published.getProjectCode());
        msg.setPublishTime(System.currentTimeMillis());
        msg.setAction("PUBLISH");
        pushService.push(msg);
    }

    /**
     * 更新编辑模式（visual/script）
     */
    public void updateScriptMode(Long definitionId, String scriptMode) {
        RuleDefinitionContent content = getContent(definitionId);
        if (content != null) {
            content.setScriptMode(scriptMode);
            contentMapper.updateById(content);
        }
    }
}
