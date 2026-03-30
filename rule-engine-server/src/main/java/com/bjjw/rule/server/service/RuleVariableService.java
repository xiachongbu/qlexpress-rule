package com.bjjw.rule.server.service;

import com.bjjw.rule.model.dto.ParsedConstant;
import com.bjjw.rule.model.dto.ParsedConstantGroup;
import com.bjjw.rule.model.entity.RuleVariable;
import com.bjjw.rule.model.entity.RuleVariableOption;
import com.bjjw.rule.core.util.ScriptNameUtil;
import com.bjjw.rule.server.mapper.RuleVariableMapper;
import com.bjjw.rule.server.mapper.RuleVariableOptionMapper;
import com.bjjw.rule.server.service.parser.JavaEntityParser;
import com.bjjw.rule.server.service.parser.JsonSchemaParser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目变量与常量（{@code var_source=CONSTANT}）的持久化与分页查询；常量要求非空默认值。
 */
@Service
public class RuleVariableService extends ServiceImpl<RuleVariableMapper, RuleVariable> {

    @Resource
    private RuleVariableOptionMapper optionMapper;

    @Resource
    private JavaEntityParser javaEntityParser;

    @Resource
    private JsonSchemaParser jsonSchemaParser;

    /**
     * 分页列表：可选按类型、关键字过滤；{@code standaloneOnly=true} 时排除常量（供「变量列表」Tab）。
     */
    public IPage<RuleVariable> pageList(int pageNum, int pageSize, Long projectId, String varType,
                                        String keyword, Boolean standaloneOnly, String varSource) {
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(RuleVariable::getProjectId, projectId);
        }
        if (varType != null && !varType.isEmpty()) {
            wrapper.eq(RuleVariable::getVarType, varType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(RuleVariable::getVarCode, keyword)
                    .or()
                    .like(RuleVariable::getVarLabel, keyword));
        }
        if (varSource != null && !varSource.isEmpty()) {
            wrapper.eq(RuleVariable::getVarSource, varSource);
        } else if (Boolean.TRUE.equals(standaloneOnly)) {
            wrapper.ne(RuleVariable::getVarSource, "CONSTANT");
        }
        wrapper.orderByAsc(RuleVariable::getSortOrder).orderByDesc(RuleVariable::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public List<RuleVariable> listByProject(Long projectId) {
        LambdaQueryWrapper<RuleVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariable::getProjectId, projectId)
               .eq(RuleVariable::getStatus, 1)
               .orderByAsc(RuleVariable::getSortOrder);
        return list(wrapper);
    }

    @Override
    public boolean save(RuleVariable entity) {
        assertConstantHasDefault(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(RuleVariable entity) {
        assertConstantHasDefault(mergeForConstantCheck(entity));
        return super.updateById(entity);
    }

    /**
     * 部分更新时合并库里的 varSource、defaultValue，再校验常量默认值。
     */
    private RuleVariable mergeForConstantCheck(RuleVariable patch) {
        if (patch == null || patch.getId() == null) {
            return patch;
        }
        RuleVariable db = getById(patch.getId());
        if (db == null) {
            return patch;
        }
        RuleVariable m = new RuleVariable();
        m.setVarSource(patch.getVarSource() != null ? patch.getVarSource() : db.getVarSource());
        m.setDefaultValue(patch.getDefaultValue() != null ? patch.getDefaultValue() : db.getDefaultValue());
        return m;
    }

    /**
     * 常量（{@code var_source=CONSTANT}）必须配置非空默认值。
     */
    private void assertConstantHasDefault(RuleVariable v) {
        if (v == null || !"CONSTANT".equals(v.getVarSource())) {
            return;
        }
        String d = v.getDefaultValue();
        if (d == null || d.trim().isEmpty()) {
            throw new IllegalArgumentException("常量的默认值不能为空");
        }
    }

    /**
     * 从 Java 源码解析 static final 常量并写入 {@code rule_variable}（按 var_code  upsert）。
     */
    @Transactional
    public Map<String, Object> importConstantsFromJava(Long projectId, String javaSource) {
        ParsedConstantGroup parsed = javaEntityParser.parseConstants(javaSource);
        int count = batchUpsertConstants(projectId, parsed);
        Map<String, Object> result = new HashMap<>();
        result.put("constantCount", count);
        return result;
    }

    /**
     * 从扁平 JSON 键值对导入常量（顶层仅基本类型键）。
     */
    @Transactional
    public Map<String, Object> importConstantsFromJson(Long projectId, String jsonContent) {
        ParsedConstantGroup parsed = jsonSchemaParser.parseConstants(jsonContent);
        int count = batchUpsertConstants(projectId, parsed);
        Map<String, Object> result = new HashMap<>();
        result.put("constantCount", count);
        return result;
    }

    /**
     * 批量插入或更新常量行，不依赖已删除的常量组表。
     */
    private int batchUpsertConstants(Long projectId, ParsedConstantGroup parsed) {
        int count = 0;
        int order = 0;
        for (ParsedConstant pc : parsed.getConstants()) {
            String val = pc.getConstValue();
            if (val == null || val.trim().isEmpty()) {
                throw new IllegalArgumentException("常量 [" + pc.getConstCode() + "] 缺少默认值");
            }
            RuleVariable existing = getBaseMapper().selectOne(
                    new LambdaQueryWrapper<RuleVariable>()
                            .eq(RuleVariable::getProjectId, projectId)
                            .eq(RuleVariable::getVarCode, pc.getConstCode()));
            if (existing != null) {
                existing.setVarType(pc.getConstType());
                existing.setVarSource("CONSTANT");
                existing.setDefaultValue(val);
                if (pc.getConstLabel() != null && !pc.getConstLabel().isEmpty()) {
                    existing.setVarLabel(pc.getConstLabel());
                }
                if (pc.getScriptName() != null && (existing.getScriptName() == null || existing.getScriptName().isEmpty())) {
                    existing.setScriptName(pc.getScriptName());
                }
                getBaseMapper().updateById(existing);
            } else {
                RuleVariable var = new RuleVariable();
                var.setProjectId(projectId);
                var.setVarCode(pc.getConstCode());
                var.setVarLabel(pc.getConstLabel() != null ? pc.getConstLabel() : pc.getConstCode());
                var.setScriptName(pc.getScriptName() != null ? pc.getScriptName() : ScriptNameUtil.toCamelCase(pc.getConstCode()));
                var.setVarType(pc.getConstType());
                var.setVarSource("CONSTANT");
                var.setDefaultValue(val);
                var.setSortOrder(order);
                var.setStatus(1);
                getBaseMapper().insert(var);
            }
            count++;
            order++;
        }
        return count;
    }

    @Transactional
    public void deleteWithOptions(Long id) {
        removeById(id);
        LambdaQueryWrapper<RuleVariableOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariableOption::getVariableId, id);
        optionMapper.delete(wrapper);
    }

    public List<RuleVariableOption> getOptions(Long variableId) {
        LambdaQueryWrapper<RuleVariableOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariableOption::getVariableId, variableId)
               .orderByAsc(RuleVariableOption::getSortOrder);
        return optionMapper.selectList(wrapper);
    }

    @Transactional
    public void saveOptions(Long variableId, List<RuleVariableOption> options) {
        LambdaQueryWrapper<RuleVariableOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleVariableOption::getVariableId, variableId);
        optionMapper.delete(wrapper);

        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                RuleVariableOption opt = options.get(i);
                opt.setId(null);
                opt.setVariableId(variableId);
                opt.setSortOrder(i);
                optionMapper.insert(opt);
            }
        }
    }
}
