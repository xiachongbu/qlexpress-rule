package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;

/**
 * 从可视化动作块（actionData）中收集「会被赋值」的变量名，供决策树/决策流统一返回 Map 使用。
 * 仅解析结构化 actionData；纯文本 qlExpressScript 需自行保证返回值或后续扩展解析。
 */
public final class ActionDataOutputVarCollector {

    private ActionDataOutputVarCollector() {
    }

    /**
     * 遍历图中全部 task 节点，汇总其 actionData 中的赋值目标变量名。
     *
     * @param nodes 决策树/决策流的 nodes 数组
     * @param out   收集结果（去重、顺序大致为节点与块出现顺序）
     */
    public static void collectFromGraphTaskNodes(JSONArray nodes, LinkedHashSet<String> out) {
        if (nodes == null) {
            return;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            if (n == null || !"task".equals(n.getString("type"))) {
                continue;
            }
            collectFromActionData(n.getJSONArray("actionData"), out);
        }
    }

    /**
     * 递归遍历 actionData 数组，收集 assign/ternary/func-call 等块的目标变量。
     */
    public static void collectFromActionData(JSONArray actionData, LinkedHashSet<String> out) {
        if (actionData == null) {
            return;
        }
        for (int i = 0; i < actionData.size(); i++) {
            collectBlock(actionData.getJSONObject(i), out);
        }
    }

    /**
     * 根据块类型递归收集子动作中的输出变量。
     */
    private static void collectBlock(JSONObject block, LinkedHashSet<String> out) {
        if (block == null) {
            return;
        }
        String type = block.getString("type");
        if (type == null) {
            return;
        }
        switch (type) {
            case "assign":
                addTarget(out, block.getString("target"));
                break;
            case "if-block":
                JSONArray branches = block.getJSONArray("branches");
                if (branches != null) {
                    for (int i = 0; i < branches.size(); i++) {
                        collectFromActionData(branches.getJSONObject(i).getJSONArray("actions"), out);
                    }
                }
                break;
            case "switch-block":
                JSONArray cases = block.getJSONArray("cases");
                if (cases != null) {
                    for (int i = 0; i < cases.size(); i++) {
                        collectFromActionData(cases.getJSONObject(i).getJSONArray("actions"), out);
                    }
                }
                collectFromActionData(block.getJSONArray("defaultActions"), out);
                break;
            case "func-call":
                addTarget(out, block.getString("target"));
                break;
            case "foreach":
                collectFromActionData(block.getJSONArray("actions"), out);
                break;
            case "ternary":
                addTarget(out, block.getString("target"));
                break;
            case "in-check":
                addTarget(out, block.getString("target"));
                break;
            case "template-str":
                addTarget(out, block.getString("target"));
                break;
            default:
                break;
        }
    }

    /**
     * 将非空的赋值目标加入集合。
     */
    private static void addTarget(LinkedHashSet<String> out, String target) {
        if (target != null && !target.trim().isEmpty()) {
            out.add(target.trim());
        }
    }
}
