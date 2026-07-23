package com.bjjw.rule.model.dto;

import lombok.Data;

/**
 * 字典项（前端下拉选项），由 {@code rule_sys_dict} 映射。
 */
@Data
public class RuleSysDictItemDTO {

    private String dictCode;
    private String dictLabel;
    private Integer sortOrder;
}
