package com.bjjw.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.Express4Runner;
import com.bjjw.rule.model.entity.RuleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 函数注册器 —— 将 rule_function 表中的自定义函数注册到 QLExpress 引擎。
 *
 * <ul>
 *   <li>SCRIPT 类型：包装为 QLExpress function 定义，拼接在编译脚本前面</li>
 *   <li>JAVA 类型：反射实例化 Java 类，通过 addFunctionOfServiceMethod 注册</li>
 *   <li>BEAN 类型：从 Spring 容器获取 Bean，通过 addFunctionOfServiceMethod 注册</li>
 * </ul>
 */
@Service
public class FunctionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(FunctionRegistrar.class);

    @Resource
    private ApplicationContext applicationContext;

    /** 缓存 JAVA 类型的实例，避免重复反射创建 */
    private final Map<String, Object> javaInstanceCache = new ConcurrentHashMap<>();

    /**
     * 将 SCRIPT 类型函数包装为 QLExpress function 定义脚本，用于拼接在编译脚本之前。
     *
     * @param functions SCRIPT 类型的函数列表
     * @return 拼接好的函数定义脚本（可能为空字符串）
     */
    public String buildScriptFunctionPrefix(List<RuleFunction> functions) {
        if (functions == null || functions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RuleFunction func : functions) {
            if (!"SCRIPT".equals(func.getImplType())) {
                continue;
            }
            String script = func.getImplScript();
            if (script == null || script.trim().isEmpty()) {
                continue;
            }
            List<String> paramNames = extractParamNames(func.getParamsJson());
            sb.append("function ").append(func.getFuncCode()).append("(");
            for (int i = 0; i < paramNames.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(paramNames.get(i));
            }
            sb.append(") {\n");
            sb.append("    ").append(script.trim().replace("\n", "\n    "));
            sb.append("\n}\n\n");
        }
        return sb.toString();
    }

    /**
     * 将 JAVA 类型函数注册到 Express4Runner。
     * 通过反射实例化 implClass 指定的类，将 implMethod 方法注册为 QL 函数。
     */
    public void registerJavaFunctions(List<RuleFunction> functions, Express4Runner runner) {
        if (functions == null) return;
        for (RuleFunction func : functions) {
            if (!"JAVA".equals(func.getImplType())) continue;
            try {
                String className = func.getImplClass();
                if (className == null || className.trim().isEmpty()) {
                    log.warn("[FunctionRegistrar] JAVA 函数 {} 未配置 implClass", func.getFuncCode());
                    continue;
                }
                String methodName = resolveMethodName(func);
                Object instance = javaInstanceCache.computeIfAbsent(className, k -> {
                    try {
                        Class<?> clazz = Class.forName(k);
                        return clazz.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("无法实例化 Java 类: " + k, e);
                    }
                });
                Class<?>[] paramTypes = resolveParamTypes(func.getParamsJson());
                runner.addFunctionOfServiceMethod(func.getFuncCode(), instance, methodName, paramTypes);
                log.debug("[FunctionRegistrar] 注册 JAVA 函数: {} -> {}.{}", func.getFuncCode(), className, methodName);
            } catch (Exception e) {
                log.error("[FunctionRegistrar] 注册 JAVA 函数 {} 失败: {}", func.getFuncCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * 将 BEAN 类型函数注册到 Express4Runner。
     * 从 Spring ApplicationContext 获取 Bean，将指定方法注册为 QL 函数。
     */
    public void registerBeanFunctions(List<RuleFunction> functions, Express4Runner runner) {
        if (functions == null) return;
        for (RuleFunction func : functions) {
            if (!"BEAN".equals(func.getImplType())) continue;
            try {
                String beanName = func.getImplBeanName();
                if (beanName == null || beanName.trim().isEmpty()) {
                    log.warn("[FunctionRegistrar] BEAN 函数 {} 未配置 implBeanName", func.getFuncCode());
                    continue;
                }
                String methodName = resolveMethodName(func);
                Object bean = applicationContext.getBean(beanName);
                Class<?>[] paramTypes = resolveParamTypes(func.getParamsJson());
                runner.addFunctionOfServiceMethod(func.getFuncCode(), bean, methodName, paramTypes);
                log.debug("[FunctionRegistrar] 注册 BEAN 函数: {} -> {}.{}", func.getFuncCode(), beanName, methodName);
            } catch (Exception e) {
                log.error("[FunctionRegistrar] 注册 BEAN 函数 {} 失败: {}", func.getFuncCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * 从 params_json 中提取参数名列表
     */
    private List<String> extractParamNames(String paramsJson) {
        List<String> names = new ArrayList<>();
        if (paramsJson == null || paramsJson.trim().isEmpty()) return names;
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject p = arr.getJSONObject(i);
                String name = p.getString("name");
                if (name != null && !name.trim().isEmpty()) {
                    names.add(name.trim());
                }
            }
        } catch (Exception e) {
            log.warn("[FunctionRegistrar] 解析 params_json 失败: {}", e.getMessage());
        }
        return names;
    }

    /**
     * 获取方法名：优先使用 implMethod，若未配置则使用 funcCode
     */
    private String resolveMethodName(RuleFunction func) {
        String method = func.getImplMethod();
        return (method != null && !method.trim().isEmpty()) ? method.trim() : func.getFuncCode();
    }

    /**
     * 根据 params_json 中的类型映射为 Java Class 数组。
     * 用于 addFunctionOfServiceMethod 的参数类型签名。
     */
    private Class<?>[] resolveParamTypes(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return new Class<?>[0];
        }
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            Class<?>[] types = new Class<?>[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                String type = arr.getJSONObject(i).getString("type");
                types[i] = mapParamType(type);
            }
            return types;
        } catch (Exception e) {
            log.warn("[FunctionRegistrar] 解析参数类型失败: {}", e.getMessage());
            return new Class<?>[0];
        }
    }

    /**
     * 规则参数类型 → Java 类型映射
     */
    private Class<?> mapParamType(String type) {
        if (type == null) return Object.class;
        switch (type.toUpperCase()) {
            case "NUMBER":  return double.class;
            case "STRING":  return String.class;
            case "BOOLEAN": return boolean.class;
            default:        return Object.class;
        }
    }
}
