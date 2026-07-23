package com.bjjw.rule.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已发布规则集快照（运行态，供 SDK 同步）
 */
@Data
@TableName("rule_published_set")
public class RulePublishedSet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String setCode;
    private String projectCode;
    private String compId;
    private Integer version;
    /** 成员 rule_code 有序 JSON 数组字符串 */
    private String memberRuleCodes;
    private Integer status;
    private String publishBy;
    private LocalDateTime publishTime;
    private LocalDateTime offlineTime;
}
