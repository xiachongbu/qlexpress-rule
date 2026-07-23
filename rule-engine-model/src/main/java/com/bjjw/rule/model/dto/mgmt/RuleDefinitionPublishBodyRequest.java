package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

import java.util.List;

/**
 * 发布规则请求体（变更说明、可选作用域列表）。
 */
@Data
public class RuleDefinitionPublishBodyRequest {
    /** 发布说明 */
    private String changeLog;
    /**
     * 要发布的作用域 compId 列表；未传或 null 表示发布全部已编译作用域。
     */
    private List<String> scopeCompIds;
}
