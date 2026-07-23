package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;
import java.util.List;

/**
 * 复制规则到目标省份（作用域）请求。
 */
@Data
public class RuleDefinitionCopyRequest {
    /** 源规则定义 ID */
    private Long definitionId;
    /** 目标作用域 compId 列表 */
    private List<String> targetScopeCompIds;
}
