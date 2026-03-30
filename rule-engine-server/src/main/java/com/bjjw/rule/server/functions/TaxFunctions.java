package com.bjjw.rule.server.functions;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 税务计算工具类 —— 同时作为 JAVA 类型和 BEAN 类型函数的示例。
 *
 * <p>作为 @Component 注册到 Spring 容器（beanName = "taxFunctions"），
 * 可在函数管理中通过 BEAN 类型引用。也可通过 JAVA 类型以全限定类名反射调用。</p>
 */
@Component("taxFunctions")
public class TaxFunctions {

    /**
     * 计算增值税额
     *
     * @param amount 含税金额
     * @param rate   税率
     * @return 税额（保留2位小数）
     */
    public double calculateVAT(double amount, double rate) {
        double excludingTax = amount / (1 + rate);
        double tax = excludingTax * rate;
        return new BigDecimal(String.valueOf(tax))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 计算不含税金额
     *
     * @param amount 含税金额
     * @param rate   税率
     * @return 不含税金额（保留2位小数）
     */
    public double excludingTax(double amount, double rate) {
        double result = amount / (1 + rate);
        return new BigDecimal(String.valueOf(result))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
