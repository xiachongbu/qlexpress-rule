package com.bjjw.rule.core.util;

import java.util.Map;

/**
 * 规则集命中策略常量与命中判定（server 试跑与 client SDK 共用，保证两端语义一致）。
 * <p>策略语义：ALL-按序全部执行并做上下文累积（管道）；FIRST-成员为独立候选，
 * 首个命中即返回；UNIQUE-成员为独立候选，要求有且仅有一个命中，否则失败。
 * FIRST/UNIQUE 下各成员均以原始入参独立执行，不做上下文合并，
 * 避免未命中成员的全 null 输出覆盖同名入参。</p>
 */
public final class RuleSetHitPolicies {

    public static final String ALL = "ALL";
    public static final String FIRST = "FIRST";
    public static final String UNIQUE = "UNIQUE";

    private RuleSetHitPolicies() {
    }

    /** 空值回落 ALL（兼容存量数据与旧版本快照） */
    public static String normalize(String hitPolicy) {
        if (hitPolicy == null || hitPolicy.trim().isEmpty()) {
            return ALL;
        }
        return hitPolicy.trim().toUpperCase();
    }

    /** 是否为合法取值 */
    public static boolean isValid(String hitPolicy) {
        String p = normalize(hitPolicy);
        return ALL.equals(p) || FIRST.equals(p) || UNIQUE.equals(p);
    }

    /**
     * 成员结果是否视为「命中」：
     * 结果 Map 中存在至少一个非 null 值即命中（与编译产物对输出变量的 null 初始化自洽——
     * 可视化规则未命中任何分支时输出变量全为 null）；非 Map 的标量结果非 null 即命中。
     */
    public static boolean isHit(Object stepResult) {
        if (stepResult == null) {
            return false;
        }
        if (stepResult instanceof Map) {
            for (Object v : ((Map<?, ?>) stepResult).values()) {
                if (v != null) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
