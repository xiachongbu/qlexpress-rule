package com.bjjw.rule.model.dto.mgmt;

import lombok.Data;

/**
 * 保存可视化设计模型 JSON 请求。
 */
@Data
public class RuleDefinitionSaveContentRequest {
    /** 规则定义 ID */
    private Long definitionId;
    /** 模型 JSON 字符串 */
    private String modelJson;
    /** 作用域 compId，可空则默认全国 */
    private String scopeCompId;
    /** 版本说明 */
    private String changeLog;
    /** 是否记录设计快照，默认可由服务端视为 true */
    private Boolean recordHistory;
}
