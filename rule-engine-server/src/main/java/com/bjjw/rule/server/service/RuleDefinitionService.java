package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bjjw.rule.core.compiler.*;
import com.bjjw.rule.model.constant.RuleCompIds;
import com.bjjw.rule.model.dto.RuleDefinitionDesignSnapshotListVO;
import com.bjjw.rule.model.entity.RuleDefinition;
import com.bjjw.rule.model.entity.RuleDefinitionContent;
import com.bjjw.rule.model.entity.RuleDefinitionDesignSnapshot;
import com.bjjw.rule.model.entity.RuleProject;
import com.bjjw.rule.server.mapper.RuleDefinitionContentMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionDesignSnapshotMapper;
import com.bjjw.rule.server.mapper.RuleDefinitionMapper;
import com.bjjw.rule.server.publish.RulePublishedL2Service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuleDefinitionService extends ServiceImpl<RuleDefinitionMapper, RuleDefinition> {

    @Resource
    private RuleDefinitionContentMapper contentMapper;

    @Resource
    private RuleDefinitionDesignSnapshotMapper designSnapshotMapper;

    @Resource
    private RuleSysDictService ruleSysDictService;

    @Resource
    private RulePublishedL2Service publishedL2Service;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RuleVariableService variableService;

    private static final int CHANGE_LOG_MAX = 512;

    /**
     * 分页查询规则定义，并填充每条记录的各省份内容摘要。
     * modelType 过滤通过子查询匹配 content 表。
     * 非管理员只能看到 content.comp_id 匹配本省或 '0' 的内容行。
     */
    public IPage<RuleDefinition> pageList(int pageNum, int pageSize, Long projectId, String modelType, String keyword) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(RuleDefinition::getProjectId, projectId);
        }
        // modelType 过滤：通过 content 子查询
        if (modelType != null && !modelType.isEmpty()) {
            wrapper.inSql(RuleDefinition::getId,
                    "SELECT definition_id FROM rule_definition_content WHERE model_type = '" + modelType + "'");
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(RuleDefinition::getRuleName, keyword)
                    .or()
                    .like(RuleDefinition::getRuleCode, keyword));
        }

        wrapper.orderByDesc(RuleDefinition::getId);
        IPage<RuleDefinition> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillContentSummaries(page.getRecords());
        return page;
    }

    /**
     * 作用域过滤扩展点：开源版不做登录用户驱动的过滤，返回 null 表示不过滤全部作用域。
     * 如需按作用域/租户隔离，可在此接入自定义的当前用户上下文。
     */
    private List<String> getAllowedCompIds() {
        return null;
    }

    /**
     * 查询每个 definition 下所有 content 行，按权限过滤后填充到 contentSummaries 字段。
     *
     * @param records 当前页规则行
     */
    private void fillContentSummaries(List<RuleDefinition> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ids = records.stream().map(RuleDefinition::getId).collect(Collectors.toSet());
        List<RuleDefinitionContent> contents = contentMapper.selectList(new LambdaQueryWrapper<RuleDefinitionContent>()
                .in(RuleDefinitionContent::getDefinitionId, ids)
                .select(RuleDefinitionContent::getId, RuleDefinitionContent::getDefinitionId,
                        RuleDefinitionContent::getScopeCompId, RuleDefinitionContent::getModelType,
                        RuleDefinitionContent::getCurrentVersion, RuleDefinitionContent::getPublishedVersion,
                        RuleDefinitionContent::getStatus, RuleDefinitionContent::getCompId,
                        RuleDefinitionContent::getDescription));

        // 权限过滤：非管理员只看本省或全国
        List<String> allowedCompIds = getAllowedCompIds();
        if (allowedCompIds != null) {
            contents = contents.stream()
                    .filter(c -> allowedCompIds.contains(RuleCompIds.normalize(c.getCompId())))
                    .collect(Collectors.toList());
        }

        Map<Long, List<RuleDefinitionContent>> grouped = contents.stream()
                .collect(Collectors.groupingBy(RuleDefinitionContent::getDefinitionId));
        for (RuleDefinition r : records) {
            r.setContentSummaries(grouped.getOrDefault(r.getId(), Collections.emptyList()));
        }
    }


    /**
     * 创建规则定义：在 content 行上设置 modelType/compId/status。
     * 始终插入全国（0）内容行；若请求指定非 0 的 initialScopeCompId，再插入该作用域空行。
     */
    @Transactional
    public RuleDefinition createWithContent(RuleDefinition definition) {
        save(definition);
        Long defId = definition.getId();
        String modelType = definition.getModelType();

        RuleDefinitionContent national = new RuleDefinitionContent();
        national.setDefinitionId(defId);
        national.setScopeCompId(RuleCompIds.NATIONAL);
        national.setModelJson("{}");
        national.setCompileStatus(0);
        national.setModelType(modelType);
        national.setStatus(0);
        national.setCurrentVersion(0);
        national.setCompId(RuleCompIds.NATIONAL);
        national.setDescription(definition.getDescription());
        contentMapper.insert(national);

        // 新建时若指定非全国作用域，再插入一条该 scope 的空内容行
        String initial = RuleCompIds.normalize(definition.getInitialScopeCompId());
        if (!RuleCompIds.NATIONAL.equals(initial) && getContent(defId, initial) == null) {
            RuleDefinitionContent scoped = new RuleDefinitionContent();
            scoped.setDefinitionId(defId);
            scoped.setScopeCompId(initial);
            scoped.setModelJson("{}");
            scoped.setCompileStatus(0);
            scoped.setModelType(modelType);
            scoped.setStatus(0);
            scoped.setCurrentVersion(0);
            scoped.setCompId(initial);
            scoped.setDescription(definition.getDescription());
            contentMapper.insert(scoped);
        }
        return definition;
    }

    /**
     * 按省份删除规则内容。已发布状态不允许删除。
     * 若删完后无任何 content 行，则级联删除 definition。
     */
    @Transactional
    public void deleteScopedContent(Long definitionId, String scopeCompId) {
        String scope = RuleCompIds.normalize(scopeCompId);
        RuleDefinitionContent content = getContent(definitionId, scope);
        if (content == null) {
            throw new IllegalArgumentException("规则内容不存在");
        }
        if (content.getStatus() != null && content.getStatus() == 1) {
            throw new IllegalArgumentException("已发布状态的规则不能删除，请先下线");
        }
        contentMapper.deleteById(content.getId());

        // 清理对应的已发布行和 L2 缓存
        RuleDefinition definition = getById(definitionId);
        if (definition != null) {
            RuleProject project = projectService.getById(definition.getProjectId());
            publishedL2Service.removePublishedSnapshot(project.getProjectCode(), definition.getRuleCode(), scope);
        }

        // 若删完后无任何 content，级联删除 definition
        Long remaining = contentMapper.selectCount(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId));
        if (remaining == null || remaining == 0) {
            removeById(definitionId);
        }
    }

    /**
     * 删除规则及其全部作用域内容（兼容旧接口）
     */
    @Transactional
    public void deleteWithContent(Long id) {
        removeById(id);
        contentMapper.delete(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, id));
    }

    /**
     * 按定义 ID 与作用域查询单条设计内容
     *
     * @param definitionId 定义 ID
     * @param scopeCompId  省份 compId，空视为全国 0
     */
    public RuleDefinitionContent getContent(Long definitionId, String scopeCompId) {
        String scope = RuleCompIds.normalize(scopeCompId);
        return contentMapper.selectOne(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId)
                .eq(RuleDefinitionContent::getScopeCompId, scope));
    }

    /**
     * 列出某定义下所有作用域的设计内容（按 scope_comp_id 排序）
     */
    public List<RuleDefinitionContent> listContents(Long definitionId) {
        return contentMapper.selectList(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId)
                .orderByAsc(RuleDefinitionContent::getScopeCompId));
    }

    /**
     * 保存模型 JSON；若作用域行不存在则返回（不自动创建，需先 {@link #addContentScope}）。
     * 默认写入设计快照历史；编译前隐式保存可传 recordHistory=false。
     * <p>所有模型类型统一为“保存即编译”：同一事务内编译校验失败抛异常回滚（不落库），
     * 成功则写入编译产物并置 compileStatus=1，保存后可直接发布，无需单独编译步骤。</p>
     *
     * @param changeLog      版本说明，可空
     * @param recordHistory  为 true 且在内容行存在并更新成功时插入 design_snapshot
     */
    @Transactional
    public void saveContent(Long definitionId, String scopeCompId, String modelJson, String changeLog, boolean recordHistory) {
        String scope = RuleCompIds.normalize(scopeCompId);
        RuleDefinitionContent content = getContent(definitionId, scope);
        boolean updated = false;
        if (content != null) {
            content.setModelJson(modelJson);
            int newVer = (content.getCurrentVersion() != null ? content.getCurrentVersion() : 0) + 1;
            content.setCurrentVersion(newVer);
            if ("SCRIPT".equals(content.getModelType())) {
                // SCRIPT 类型：剥离编辑回流的序言块后校验脚本正文，再按最新常量重建序言固化进产物
                String script = ConstantPrefixBuilder.stripMarkedPrefix(extractScript(modelJson));
                CompileResult cr = ScriptSyntaxValidator.validate(script);
                if (!cr.isSuccess()) {
                    throw new IllegalArgumentException("脚本存在错误，无法保存：" + cr.getErrorMessage());
                }
                content.setCompiledScript(prependConstantPrologue(definitionId, script));
                content.setCompiledType("QLEXPRESS");
                content.setScriptMode("script");
            } else {
                // 可视化类型：用对应编译器将模型编译为脚本，编译失败视同校验不通过，拒绝保存
                RuleCompiler compiler = RuleModelCompilers.get(content.getModelType());
                if (compiler == null) {
                    throw new IllegalArgumentException("暂不支持的模型类型: " + content.getModelType());
                }
                // 注入项目常量：排除常量出输出集并前置常量序言，与显式编译链路保持一致
                RuleDefinition def = getById(definitionId);
                java.util.List<com.bjjw.rule.model.entity.RuleVariable> vars =
                        (def != null && def.getProjectId() != null)
                                ? variableService.listByProject(def.getProjectId())
                                : null;
                java.util.LinkedHashSet<String> constantNames = ConstantPrefixBuilder.constantNames(vars);
                String constantPrefix = ConstantPrefixBuilder.build(vars);
                CompileResult cr = compiler.compile(modelJson, constantNames, constantPrefix);
                if (!cr.isSuccess()) {
                    throw new IllegalArgumentException("规则校验未通过，无法保存：" + cr.getErrorMessage());
                }
                content.setCompiledScript(cr.getCompiledScript());
                content.setCompiledType(cr.getCompiledType());
                content.setScriptMode("visual");
            }
            content.setCompileStatus(1);
            content.setCompileMessage(null);
            content.setCompileTime(LocalDateTime.now());
            contentMapper.updateById(content);
            updated = true;

            if (recordHistory) {
                RuleDefinitionDesignSnapshot snap = new RuleDefinitionDesignSnapshot();
                snap.setDefinitionId(definitionId);
                snap.setScopeCompId(scope);
                snap.setModelJson(modelJson);
                snap.setChangeLog(truncateChangeLog(changeLog));
                snap.setDesignVersion(newVer);
                snap.setCreateTime(LocalDateTime.now());
                designSnapshotMapper.insert(snap);
            }
        }
    }

    /** 从 modelJson 中提取脚本正文（{"script": "..."}）；非 JSON 包装则视为脚本原文 */
    private String extractScript(String modelJson) {
        if (modelJson == null) {
            return "";
        }
        try {
            com.alibaba.fastjson.JSONObject obj = com.alibaba.fastjson.JSON.parseObject(modelJson);
            String s = obj != null ? obj.getString("script") : null;
            return s != null ? s : modelJson;
        } catch (Exception e) {
            return modelJson;
        }
    }

    /**
     * 截断版本说明至表字段长度，避免写入失败。
     */
    private static String truncateChangeLog(String changeLog) {
        if (changeLog == null || changeLog.isEmpty()) {
            return null;
        }
        if (changeLog.length() <= CHANGE_LOG_MAX) {
            return changeLog;
        }
        return changeLog.substring(0, CHANGE_LOG_MAX);
    }

    /**
     * 分页查询某定义某作用域下的设计保存快照列表（不含 model_json）。
     */
    public IPage<RuleDefinitionDesignSnapshotListVO> pageDesignSnapshots(
            Long definitionId, String scopeCompId, int pageNum, int pageSize) {
        String scope = RuleCompIds.normalize(scopeCompId);
        Page<RuleDefinitionDesignSnapshot> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RuleDefinitionDesignSnapshot> q = new LambdaQueryWrapper<RuleDefinitionDesignSnapshot>()
                .eq(RuleDefinitionDesignSnapshot::getDefinitionId, definitionId)
                .eq(RuleDefinitionDesignSnapshot::getScopeCompId, scope)
                .orderByDesc(RuleDefinitionDesignSnapshot::getCreateTime)
                .select(
                        RuleDefinitionDesignSnapshot::getId,
                        RuleDefinitionDesignSnapshot::getDefinitionId,
                        RuleDefinitionDesignSnapshot::getScopeCompId,
                        RuleDefinitionDesignSnapshot::getChangeLog,
                        RuleDefinitionDesignSnapshot::getDesignVersion,
                        RuleDefinitionDesignSnapshot::getCreateBy,
                        RuleDefinitionDesignSnapshot::getCreateTime);
        IPage<RuleDefinitionDesignSnapshot> raw = designSnapshotMapper.selectPage(page, q);
        Page<RuleDefinitionDesignSnapshotListVO> voPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        voPage.setRecords(raw.getRecords().stream().map(this::toSnapshotListVo).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 实体转列表 VO。
     */
    private RuleDefinitionDesignSnapshotListVO toSnapshotListVo(RuleDefinitionDesignSnapshot e) {
        RuleDefinitionDesignSnapshotListVO vo = new RuleDefinitionDesignSnapshotListVO();
        vo.setId(e.getId());
        vo.setDefinitionId(e.getDefinitionId());
        vo.setScopeCompId(e.getScopeCompId());
        vo.setChangeLog(e.getChangeLog());
        vo.setDesignVersion(e.getDesignVersion());
        vo.setCreateBy(e.getCreateBy());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    /**
     * 按主键取快照全文，并校验属于指定定义与作用域。
     *
     * @return modelJson，非法则返回 null
     */
    public String getDesignSnapshotModelJson(Long snapshotId, Long definitionId, String scopeCompId) {
        String scope = RuleCompIds.normalize(scopeCompId);
        RuleDefinitionDesignSnapshot row = designSnapshotMapper.selectById(snapshotId);
        if (row == null) {
            return null;
        }
        if (!definitionId.equals(row.getDefinitionId()) || !scope.equals(RuleCompIds.normalize(row.getScopeCompId()))) {
            return null;
        }
        return row.getModelJson();
    }

    /**
     * 新增某省份作用域的设计行（空模型，未编译）；全国行已存在时勿对 0 再调
     */
    @Transactional
    public void addContentScope(Long definitionId, String scopeCompId) {
        String scope = RuleCompIds.normalize(scopeCompId);
        if (RuleCompIds.NATIONAL.equals(scope)) {
            throw new IllegalArgumentException("全国默认作用域已存在，无需新增");
        }
        RuleDefinitionContent existing = getContent(definitionId, scope);
        if (existing != null) {
            return;
        }
        RuleDefinitionContent c = new RuleDefinitionContent();
        c.setDefinitionId(definitionId);
        c.setScopeCompId(scope);
        c.setModelJson("{}");
        c.setCompileStatus(0);
        contentMapper.insert(c);
    }

    /**
     * 删除某省份作用域设计行；不允许删除全国（0）
     */
    @Transactional
    public void deleteContentScope(Long definitionId, String scopeCompId) {
        String scope = RuleCompIds.normalize(scopeCompId);
        if (RuleCompIds.NATIONAL.equals(scope)) {
            throw new IllegalArgumentException("不能删除全国默认作用域内容");
        }
        contentMapper.delete(new LambdaQueryWrapper<RuleDefinitionContent>()
                .eq(RuleDefinitionContent::getDefinitionId, definitionId)
                .eq(RuleDefinitionContent::getScopeCompId, scope));
    }

    /**
     * 技术人员手动编辑脚本，直接写入 compiledScript，跳过可视化编译器。
     * 与 SCRIPT 类型的 saveContent 一致：同一事务内“校验 + 持久化”，
     * 语法校验失败抛异常回滚（不落库），避免无效脚本以已编译状态被直接发布。
     * <p>产物开头以带界定注释的常量序言固化项目常量：先剥离编辑回流的旧序言块，
     * 校验脚本正文后按最新常量重建，保证重复保存幂等且常量值以配置管理为准。</p>
     */
    @Transactional
    public void saveScript(Long definitionId, String scopeCompId, String script) {
        RuleDefinitionContent content = getContent(definitionId, scopeCompId);
        if (content == null) {
            throw new IllegalArgumentException("规则内容不存在，definitionId=" + definitionId + " scope=" + RuleCompIds.normalize(scopeCompId));
        }
        String body = ConstantPrefixBuilder.stripMarkedPrefix(script);
        CompileResult cr = ScriptSyntaxValidator.validate(body);
        if (!cr.isSuccess()) {
            throw new IllegalArgumentException("脚本存在错误，无法保存：" + cr.getErrorMessage());
        }
        content.setCompiledScript(prependConstantPrologue(definitionId, body));
        content.setCompiledType("QLEXPRESS");
        content.setCompileStatus(1);
        content.setCompileMessage(null);
        content.setCompileTime(LocalDateTime.now());
        content.setScriptMode("script");
        contentMapper.updateById(content);
    }

    /**
     * 为 SCRIPT 类型脚本正文前置带界定注释的常量序言（无常量时原样返回）。
     */
    private String prependConstantPrologue(Long definitionId, String script) {
        RuleDefinition definition = getById(definitionId);
        java.util.List<com.bjjw.rule.model.entity.RuleVariable> vars =
                (definition != null && definition.getProjectId() != null)
                        ? variableService.listByProject(definition.getProjectId())
                        : null;
        String wrapped = ConstantPrefixBuilder.wrapWithMarkers(ConstantPrefixBuilder.build(vars));
        return wrapped.isEmpty() ? script : wrapped + script;
    }

    /**
     * 更新编辑模式（visual / script）
     */
    public void updateScriptMode(Long definitionId, String scopeCompId, String scriptMode) {
        RuleDefinitionContent content = getContent(definitionId, scopeCompId);
        if (content != null) {
            content.setScriptMode(scriptMode);
            contentMapper.updateById(content);
        }
    }

    /**
     * 更新 content 行（供其他 Service 调用）
     */
    public void updateContentById(RuleDefinitionContent content) {
        contentMapper.updateById(content);
    }

    /**
     * 修改规则内容的省份归属和说明
     */
    @Transactional
    public void updateContentMeta(Long definitionId, String scopeCompId, String newCompId, String newDescription) {
        String scope = RuleCompIds.normalize(scopeCompId);
        RuleDefinitionContent content = getContent(definitionId, scope);
        if (content == null) {
            throw new IllegalArgumentException("规则内容不存在");
        }
        if (newCompId != null) {
            content.setCompId(newCompId);
        }
        if (newDescription != null) {
            content.setDescription(newDescription);
        }
        contentMapper.updateById(content);
    }

    /**
     * 将指定规则的全国（0）作用域内容复制到多个目标省份作用域。
     * <ul>
     *   <li>源内容取全国默认行（scopeCompId = 0）</li>
     *   <li>目标作用域行已存在时覆盖 modelJson，不存在时新建</li>
     * </ul>
     *
     * @param definitionId       规则定义 ID
     * @param targetScopeCompIds 目标省份 compId 列表
     */
    @Transactional
    public void copyToScopes(Long definitionId, List<String> targetScopeCompIds) {
        RuleDefinition definition = getById(definitionId);
        if (definition == null) {
            throw new IllegalArgumentException("规则定义不存在，id=" + definitionId);
        }
        // 取全国默认行作为复制源
        RuleDefinitionContent source = getContent(definitionId, RuleCompIds.NATIONAL);
        if (source == null) {
            throw new IllegalArgumentException("规则全国默认内容不存在，无法复制");
        }
        String modelJson = source.getModelJson();
        String modelType = source.getModelType();
        String compiledScript = source.getCompiledScript();
        String compiledType = source.getCompiledType();
        Integer compileStatus = source.getCompileStatus();
        String compileMessage = source.getCompileMessage();
        LocalDateTime compileTime = source.getCompileTime();
        String scriptMode = source.getScriptMode();

        for (String targetCompId : targetScopeCompIds) {
            String scope = RuleCompIds.normalize(targetCompId);
            if (RuleCompIds.NATIONAL.equals(scope)) {
                continue; // 跳过全国自身
            }
            RuleDefinitionContent existing = getContent(definitionId, scope);
            if (existing != null) {
                existing.setModelJson(modelJson);
                existing.setModelType(modelType);
                existing.setCompiledScript(compiledScript);
                existing.setCompiledType(compiledType);
                existing.setCompileStatus(compileStatus);
                existing.setCompileMessage(compileMessage);
                existing.setCompileTime(compileTime);
                existing.setScriptMode(scriptMode);
                contentMapper.updateById(existing);
            } else {
                RuleDefinitionContent c = new RuleDefinitionContent();
                c.setDefinitionId(definitionId);
                c.setScopeCompId(scope);
                c.setModelJson(modelJson);
                c.setModelType(modelType);
                c.setCompiledScript(compiledScript);
                c.setCompiledType(compiledType);
                c.setCompileStatus(compileStatus);
                c.setCompileMessage(compileMessage);
                c.setCompileTime(compileTime);
                c.setScriptMode(scriptMode);
                contentMapper.insert(c);
            }
        }
    }
}
