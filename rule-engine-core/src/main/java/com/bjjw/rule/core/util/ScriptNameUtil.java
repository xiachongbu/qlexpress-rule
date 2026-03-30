package com.bjjw.rule.core.util;

import java.util.Locale;

/**
 * 脚本名称转换工具
 *
 * 将各种命名形式统一转为 lowerCamelCase，用于 QLExpress 脚本中的标识符。
 * 支持：PascalCase、snake_case、UPPER_SNAKE_CASE、已有 camelCase。
 */
public class ScriptNameUtil {

    /**
     * 将输入字符串转为 lowerCamelCase
     *
     * <ul>
     *   <li>TaxRequest → taxRequest（PascalCase 首字母小写）</li>
     *   <li>total_amount → totalAmount（下划线转驼峰）</li>
     *   <li>TAX_RATE → taxRate（全大写下划线转驼峰）</li>
     *   <li>totalAmount → totalAmount（已是驼峰不变）</li>
     *   <li>ID、THEME、TYPE（无下划线且字母全大写，常见于 DDL 列名）→ id、theme、type</li>
     * </ul>
     */
    public static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;

        if (input.contains("_")) {
            return underscoreToCamel(input);
        }

        if (isAllUpperLettersWord(input)) {
            return input.toLowerCase(Locale.ROOT);
        }

        return Character.toLowerCase(input.charAt(0)) + input.substring(1);
    }

    /**
     * 是否为「无下划线的全大写单词」：含至少一个字母且所有字母均为大写（可含数字，如 UUID、T2）。
     */
    private static boolean isAllUpperLettersWord(String input) {
        boolean hasLetter = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (Character.isLowerCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    private static String underscoreToCamel(String input) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = false;
        String lower = input.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else {
                sb.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }
        return sb.toString();
    }
}
