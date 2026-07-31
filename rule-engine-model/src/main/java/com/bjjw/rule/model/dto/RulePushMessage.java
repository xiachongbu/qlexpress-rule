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
    /** 高精度计算开关；旧快照无此字段时为 null，客户端按 false 处理 */
    private Boolean precise;
    private Long publishTime;
    /** 动作类型：PUBLISH / UNPUBLISH / DELETE / FUNC_UPDATE / FUNC_DELETE / SET_PUBLISH / SET_UNPUBLISH */
    private String action;
    /** 规则所属项目编码 */
    private String projectCode;
    /** 作用域省份：0 全国；UNPUBLISH 时指明下线的是哪一条发布快照 */
    private String compId;

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

    // ── 规则集推送（action = SET_PUBLISH / SET_UNPUBLISH） ──
    /** 规则集编码 */
    private String setCode;
    /** 成员 rule_code 有序 JSON 数组字符串 */
    private String memberRuleCodes;
    /** 规则集命中策略：ALL/FIRST/UNIQUE */
    private String hitPolicy;
}
