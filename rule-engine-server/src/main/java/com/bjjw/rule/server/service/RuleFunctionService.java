package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjjw.rule.model.entity.RuleFunction;
import com.bjjw.rule.server.mapper.RuleFunctionMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class RuleFunctionService {

    @Resource
    private RuleFunctionMapper functionMapper;

    /**
     * 按项目查询全部启用函数（SDK 同步场景使用）
     */
    public List<RuleFunction> listByProject(Long projectId) {
        return functionMapper.selectList(
            new LambdaQueryWrapper<RuleFunction>()
                .eq(RuleFunction::getProjectId, projectId)
                .eq(RuleFunction::getStatus, 1)
                .orderByAsc(RuleFunction::getFuncCode)
        );
    }

    /**
     * 按项目分页查询函数（管理页面使用）
     */
    public IPage<RuleFunction> pageByProject(Long projectId, int pageNum, int pageSize) {
        LambdaQueryWrapper<RuleFunction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleFunction::getProjectId, projectId)
               .orderByAsc(RuleFunction::getFuncCode);
        return functionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public RuleFunction getById(Long id) {
        return functionMapper.selectById(id);
    }

    public void create(RuleFunction func) {
        functionMapper.insert(func);
    }

    public void update(RuleFunction func) {
        functionMapper.updateById(func);
    }

    public void delete(Long id) {
        functionMapper.deleteById(id);
    }
}
