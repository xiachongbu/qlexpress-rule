package com.bjjw.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bjjw.rule.model.dto.RuleSysDictItemDTO;
import com.bjjw.rule.model.entity.RuleSysDict;
import com.bjjw.rule.server.mapper.RuleSysDictMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统字典查询（管理端下拉等）。
 */
@Service
public class RuleSysDictService extends ServiceImpl<RuleSysDictMapper, RuleSysDict> {

    /**
     * 按类型查询已启用的字典项，按 sort_order、id 升序。
     *
     * @param dictType 字典类型，如 COMP_SCOPE
     * @return DTO 列表
     */
    public List<RuleSysDictItemDTO> listEnabledByType(String dictType) {
        if (dictType == null || dictType.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String type = dictType.trim();
        List<RuleSysDict> list = list(new LambdaQueryWrapper<RuleSysDict>()
                .eq(RuleSysDict::getDictType, type)
                .eq(RuleSysDict::getStatus, 1)
                .orderByAsc(RuleSysDict::getSortOrder)
                .orderByAsc(RuleSysDict::getId));
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * 实体转前端用 DTO。
     */
    private RuleSysDictItemDTO toDto(RuleSysDict e) {
        RuleSysDictItemDTO d = new RuleSysDictItemDTO();
        d.setDictCode(e.getDictCode());
        d.setDictLabel(e.getDictLabel());
        d.setSortOrder(e.getSortOrder());
        return d;
    }
}
