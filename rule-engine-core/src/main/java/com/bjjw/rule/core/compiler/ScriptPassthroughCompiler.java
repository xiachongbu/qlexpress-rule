package com.bjjw.rule.core.compiler;

/**
 * QL脚本直通编译器：不做模型转换，仅将脚本原文存入编译结果。
 * 适用于技术人员直接编写 QLExpress 脚本的场景。
 */
public class ScriptPassthroughCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        if (modelJson == null || modelJson.trim().isEmpty() || "{}".equals(modelJson.trim())) {
            return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
        }

        try {
            com.alibaba.fastjson.JSONObject model = com.alibaba.fastjson.JSON.parseObject(modelJson);
            String script = model.getString("script");
            if (script == null || script.trim().isEmpty()) {
                return CompileResult.fail("脚本内容为空，请先编写脚本再编译");
            }
            return CompileResult.ok(script, "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.ok(modelJson, "QLEXPRESS");
        }
    }
}
