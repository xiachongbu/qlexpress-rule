package com.bjjw.rule.example.functions;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * 定价 / 费用试算工具类 —— 同时满足 JAVA 类型（反射实例化）和 BEAN 类型（Spring Bean）函数注册。
 *
 * <p>包名与 server 端一致（com.bjjw.rule.example.functions.TaxFunctions），
 * 使得 data-example.sql 中 JAVA 类型函数的 implClass 在 server 和 example 两个项目中都能被反射找到。</p>
 *
 * <p>作为 @Component 注册到 Spring 容器（beanName = "taxFunctions"），
 * SDK 同步 BEAN 类型函数时通过 ApplicationContext.getBean("taxFunctions") 获取。</p>
 */
@Component("taxFunctions")
public class TaxFunctions {

    /**
     * 按金额与费率计算费用（与 data-example 中 calculateVAT / 费用试算(Java) 语义一致）。
     *
     * @param amount 含税金额
     * @param rate   费率
     * @return 费用（保留2位小数）
     */
    public double calculateVAT(double amount, double rate) {
        double excludingTax = amount / (1 + rate);
        double tax = excludingTax * rate;
        return new BigDecimal(String.valueOf(tax))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 计算不含税（或本金）拆分金额。
     *
     * @param amount 含税金额
     * @param rate   费率
     * @return 拆分后金额（保留2位小数）
     */
    public double excludingTax(double amount, double rate) {
        double result = amount / (1 + rate);
        return new BigDecimal(String.valueOf(result))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 金额格式化（带千分位）。
     *
     * @param amount 金额
     * @return 格式化字符串，如 "13,000.00"
     */
    public String formatAmount(double amount) {
        return new DecimalFormat("#,##0.00").format(amount);
    }
}
