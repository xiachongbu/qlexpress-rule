package com.bjjw.rule.client.function;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.qlexpress4.QLOptions;
import com.bjjw.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.bjjw.rule.core.engine.QLExpressEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端函数注册器 —— 根据服务端同步的函数元数据，自动注册到本地 QLExpress 引擎。
 *
 * <p>支持三种函数类型：
 * <ul>
 *   <li>SCRIPT：通过 addVarArgsFunction 注册 QLExpress 脚本函数</li>
 *   <li>JAVA：反射实例化类，通过 addFunctionOfServiceMethod 注册</li>
 *   <li>BEAN：从 Spring 容器获取 Bean，通过 addFunctionOfServiceMethod 注册</li>
 * </ul>
 * 所有注册方法均使用 addOrReplace 语义，同名函数会被覆盖以支持热更新。
 * </p>
 */
public class ClientFunctionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ClientFunctionRegistrar.class);

    private final QLExpressEngine engine;
    private final ApplicationContext applicationContext;

    public ClientFunctionRegistrar(QLExpressEngine engine, ApplicationContext applicationContext) {
        this.engine = engine;
        this.applicationContext = applicationContext;
    }

    /**
     * 批量注册函数（从 HTTP 同步接口返回的 JSON 列表）
     */
    public void registerAll(List<JSONObject> functions) {
        if (functions == null || functions.isEmpty()) return;
        for (JSONObject func : functions) {
            registerOne(func);
        }
        AggregateBuiltinFunctionRegistry.register(engine.getRunner());
    }

    /**
     * 注册单个函数
     */
    public void registerOne(JSONObject func) {
        String funcCode = func.getString("funcCode");
        String implType = func.getString("implType");
        if (funcCode == null || implType == null) return;

        try {
            switch (implType) {
                case "SCRIPT":
                    registerScript(func);
                    break;
                case "JAVA":
                    registerJava(func);
                    break;
                case "BEAN":
                    registerBean(func);
                    break;
                default:
                    log.warn("[ClientFuncReg] 未知函数类型: {} for {}", implType, funcCode);
            }
        } catch (Exception e) {
            log.error("[ClientFuncReg] 注册函数 {} ({}) 失败: {}", funcCode, implType, e.getMessage(), e);
        }
    }

    /**
     * 通过 RulePushMessage 字段注册单个函数（Redis 推送场景）
     */
    public void registerFromPush(String funcCode, String implType, String implScript,
                                 String implClass, String implMethod, String implBeanName,
                                 String paramsJson) {
        JSONObject func = new JSONObject();
        func.put("funcCode", funcCode);
        func.put("implType", implType);
        func.put("implScript", implScript);
        func.put("implClass", implClass);
        func.put("implMethod", implMethod);
        func.put("implBeanName", implBeanName);
        func.put("paramsJson", paramsJson);
        registerOne(func);
        AggregateBuiltinFunctionRegistry.register(engine.getRunner());
    }

    private void registerScript(JSONObject func) {
        String funcCode = func.getString("funcCode");
        String script = func.getString("implScript");
        if (script == null || script.trim().isEmpty()) return;

        List<String> paramNames = extractParamNames(func.getString("paramsJson"));
        String trimmedScript = script.trim();

        engine.getRunner().addVarArgsFunction(funcCode, params -> {
            Map<String, Object> context = new HashMap<>();
            for (int i = 0; i < paramNames.size(); i++) {
                context.put(paramNames.get(i), i < params.length ? params[i] : null);
            }
            return engine.getRunner().execute(trimmedScript, context,
                    QLOptions.builder().cache(true).build()).getResult();
        });
        log.info("[ClientFuncReg] 注册/更新 SCRIPT 函数: {}", funcCode);
    }

    private void registerJava(JSONObject func) throws Exception {
        String funcCode = func.getString("funcCode");
        String className = func.getString("implClass");
        String methodName = func.getString("implMethod");
        if (className == null || className.isEmpty()) {
            log.warn("[ClientFuncReg] JAVA 函数 {} 未配置 implClass", funcCode);
            return;
        }
        if (methodName == null || methodName.isEmpty()) {
            methodName = funcCode;
        }
        Class<?> clazz = Class.forName(className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Class<?>[] paramTypes = resolveParamTypes(func.getString("paramsJson"));
        engine.getRunner().addFunctionOfServiceMethod(funcCode, instance, methodName, paramTypes);
        log.info("[ClientFuncReg] 注册/更新 JAVA 函数: {} -> {}.{}", funcCode, className, methodName);
    }

    private void registerBean(JSONObject func) {
        String funcCode = func.getString("funcCode");
        String beanName = func.getString("implBeanName");
        String methodName = func.getString("implMethod");
        if (beanName == null || beanName.isEmpty()) {
            log.warn("[ClientFuncReg] BEAN 函数 {} 未配置 implBeanName", funcCode);
            return;
        }
        if (applicationContext == null) {
            log.warn("[ClientFuncReg] BEAN 函数 {} 无法注册：ApplicationContext 未提供", funcCode);
            return;
        }
        if (methodName == null || methodName.isEmpty()) {
            methodName = funcCode;
        }
        try {
            Object bean = applicationContext.getBean(beanName);
            Class<?>[] paramTypes = resolveParamTypes(func.getString("paramsJson"));
            engine.getRunner().addFunctionOfServiceMethod(funcCode, bean, methodName, paramTypes);
            log.info("[ClientFuncReg] 注册/更新 BEAN 函数: {} -> {}.{}", funcCode, beanName, methodName);
        } catch (Exception e) {
            log.warn("[ClientFuncReg] BEAN 函数 {} 注册失败（Bean '{}' 可能不存在）: {}", funcCode, beanName, e.getMessage());
        }
    }

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
            log.warn("[ClientFuncReg] 解析 paramsJson 失败: {}", e.getMessage());
        }
        return names;
    }

    private Class<?>[] resolveParamTypes(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) return new Class<?>[0];
        try {
            JSONArray arr = JSON.parseArray(paramsJson);
            Class<?>[] types = new Class<?>[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                String type = arr.getJSONObject(i).getString("type");
                types[i] = mapParamType(type);
            }
            return types;
        } catch (Exception e) {
            return new Class<?>[0];
        }
    }

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
