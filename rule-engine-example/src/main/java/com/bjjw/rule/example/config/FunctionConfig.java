package com.bjjw.rule.example.config;

import com.bjjw.rule.client.RuleEngineClient;
import com.bjjw.rule.example.functions.TaxFunctions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 自定义函数补充注册配置。
 *
 * <p>SDK 启动时会根据 project-id 自动从服务端同步函数定义并注册：
 * <ul>
 *   <li>SCRIPT 函数：发布时已拼进 compiledScript，无需额外注册</li>
 *   <li>JAVA 函数：SDK 通过反射实例化 implClass 自动注册</li>
 *   <li>BEAN 函数：SDK 通过 ApplicationContext.getBean(implBeanName) 自动注册</li>
 * </ul>
 * </p>
 *
 * <p>本配置仅做补充说明和验证，实际注册由 SDK 自动完成。
 * 业务项目只需确保 classpath 上有 JAVA 函数引用的类、
 * Spring 容器中有 BEAN 函数引用的 Bean 即可。</p>
 *
 * <p>示例：TaxFunctions 类（{@code @Component("taxFunctions")}）同时满足：
 * <ul>
 *   <li>JAVA 类型：data-example.sql 中 calculateVAT 的 implClass 指向此类</li>
 *   <li>BEAN 类型：data-example.sql 中 calcTaxByBean 的 implBeanName = "taxFunctions"</li>
 * </ul>
 * </p>
 */
@Slf4j
@Configuration
public class FunctionConfig {

    @Resource
    private RuleEngineClient ruleClient;

    @Resource
    private TaxFunctions taxFunctions;

    @PostConstruct
    public void verify() {
        log.info("[FunctionConfig] TaxFunctions Bean 已加载 (beanName=taxFunctions), SDK 自动同步时 BEAN 函数可正常注册");
        log.info("[FunctionConfig] TaxFunctions 类: {}，SDK 自动同步时 JAVA 函数可正常反射实例化",
                TaxFunctions.class.getName());
    }
}
