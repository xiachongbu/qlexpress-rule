package com.bjjw.rule.model.constant;

/**
 * 规则按省份（compId）作用域时的约定：{@value #NATIONAL} 表示全国默认，非 0 为具体省份业务 ID。
 */
public final class RuleCompIds {

    /** 全国默认作用域，与库表 {@code scope_comp_id} / {@code comp_id} 默认值一致 */
    public static final String NATIONAL = "0";

    private RuleCompIds() {
    }

    /**
     * 将请求或配置中的 compId 规范为存储用字符串；null、空串视为全国。
     *
     * @param compId 原始值
     * @return 永不为 null，全国为 {@link #NATIONAL}
     */
    public static String normalize(String compId) {
        if (compId == null) {
            return NATIONAL;
        }
        String t = compId.trim();
        return t.isEmpty() ? NATIONAL : t;
    }
}
