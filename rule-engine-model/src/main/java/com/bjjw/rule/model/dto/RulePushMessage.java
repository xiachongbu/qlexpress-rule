package com.bjjw.rule.model.dto;

import lombok.Data;

@Data
public class RulePushMessage {
    private String ruleCode;
    private Integer version;
    private String modelType;
    private String compiledScript;
    private String compiledType;
    private String modelJson;
    private Long publishTime;
    /** 动作类型：PUBLISH / UNPUBLISH / DELETE / FUNC_UPDATE / FUNC_DELETE */
    private String action;
    /** 规则所属项目编码 */
    private String projectCode;

    // ── 函数推送字段（action = FUNC_UPDATE / FUNC_DELETE 时使用） ──
    /** 函数编码 */
    private String funcCode;
    /** 函数名称 */
    private String funcName;
    /** 实现类型：SCRIPT / JAVA / BEAN */
    private String funcImplType;
    /** SCRIPT 类型的脚本内容 */
    private String funcImplScript;
    /** JAVA 类型的全限定类名 */
    private String funcImplClass;
    /** 方法名（JAVA/BEAN） */
    private String funcImplMethod;
    /** Spring Bean 名称（BEAN） */
    private String funcImplBeanName;
    /** 函数参数 JSON */
    private String funcParamsJson;
}
