package com.bjjw.rule.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设计快照列表项（不含 model_json，减轻列表接口体积）。
 */
@Data
public class RuleDefinitionDesignSnapshotListVO {
    private Long id;
    private Long definitionId;
    private String scopeCompId;
    private String changeLog;
    private Integer designVersion;
    private String createBy;
    private LocalDateTime createTime;
}
