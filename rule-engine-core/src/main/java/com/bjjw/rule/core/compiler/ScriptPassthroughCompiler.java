package com.bjjw.rule.core.compiler;

import java.util.Collections;
import java.util.Set;

/**
 * QL脚本直通编译器：不做模型转换，仅将脚本原文存入编译结果。
 * 适用于技术人员直接编写 QLExpress 脚本的场景。
 * <p>常量固化：脚本引用的项目常量以「带界定注释的序言块」前置到产物开头；
 * 因 compiledScript 会回流为脚本模式的编辑源，先剥离已存在的序言块再重建，保证幂等。</p>
 */
public class ScriptPassthroughCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, Collections.emptySet(), null);
    }

    @Override
    public CompileResult compile(String modelJson, Set<String> constantNames, String constantPrefix) {
        if (modelJson == null || modelJson.trim().isEmpty() || "{}".equals(modelJson.trim())) {
            return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
        }

        String script;
        try {
            com.alibaba.fastjson.JSONObject model = com.alibaba.fastjson.JSON.parseObject(modelJson);
            script = model.getString("script");
            if (script == null || script.trim().isEmpty()) {
                return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
            }
        } catch (Exception e) {
            script = modelJson;
        }

        // 剥离历史序言块后按最新常量重建，避免编辑回流导致序言叠加或值过期
        script = ConstantPrefixBuilder.stripMarkedPrefix(script);
        String wrapped = ConstantPrefixBuilder.wrapWithMarkers(constantPrefix);
        if (!wrapped.isEmpty()) {
            script = wrapped + script;
        }
        return CompileResult.ok(script, "QLEXPRESS");
    }
}
