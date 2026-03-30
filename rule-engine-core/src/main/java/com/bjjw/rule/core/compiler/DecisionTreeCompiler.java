package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

/**
 * 决策树编译器 - 严格树形结构
 *
 * 校验图必须是树形结构（禁止聚合节点、禁止多入边汇合），
 * 然后将 nodes/edges 编译为单一 QLExpress 脚本。
 */
public class DecisionTreeCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            JSONArray nodes = model.getJSONArray("nodes");
            JSONArray edges = model.getJSONArray("edges");

            if (nodes == null || edges == null) {
                return CompileResult.fail("决策树模型缺少 nodes 或 edges");
            }

            Map<String, JSONObject> nodeMap = new LinkedHashMap<>();
            Map<String, List<JSONObject>> outEdgeMap = new LinkedHashMap<>();

            for (int i = 0; i < nodes.size(); i++) {
                JSONObject n = nodes.getJSONObject(i);
                String id = n.getString("id");
                nodeMap.put(id, n);
                outEdgeMap.put(id, new ArrayList<>());
            }

            for (int i = 0; i < edges.size(); i++) {
                JSONObject e = edges.getJSONObject(i);
                String src = e.getString("source");
                outEdgeMap.computeIfAbsent(src, k -> new ArrayList<>()).add(e);
            }

            // --- 树形结构校验 ---

            // 禁止聚合（join）节点
            for (JSONObject n : nodeMap.values()) {
                if ("join".equals(n.getString("type"))) {
                    String name = n.getString("name") != null ? n.getString("name") : n.getString("id");
                    return CompileResult.fail("决策树不允许使用聚合节点 [" + name + "]，请使用决策流");
                }
            }

            // 禁止多入边（除 start 外每个节点入边数 <= 1）
            Map<String, Integer> inEdgeCount = new HashMap<>();
            for (int i = 0; i < edges.size(); i++) {
                String target = edges.getJSONObject(i).getString("target");
                inEdgeCount.merge(target, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : inEdgeCount.entrySet()) {
                if (entry.getValue() > 1) {
                    JSONObject n = nodeMap.get(entry.getKey());
                    if (n != null && !"start".equals(n.getString("type"))) {
                        String name = n.getString("name") != null ? n.getString("name") : entry.getKey();
                        return CompileResult.fail("决策树中节点 [" + name + "] 有多条入边，不允许分支汇合");
                    }
                }
            }

            // --- 查找起始节点 ---
            String startId = null;
            for (Map.Entry<String, JSONObject> entry : nodeMap.entrySet()) {
                if ("start".equals(entry.getValue().getString("type"))) {
                    startId = entry.getKey();
                    break;
                }
            }
            if (startId == null) {
                return CompileResult.fail("缺少开始节点");
            }

            String script = GraphScriptGenerator.generate(nodeMap, outEdgeMap, startId);

            LinkedHashSet<String> outputVars = new LinkedHashSet<>();
            ActionDataOutputVarCollector.collectFromGraphTaskNodes(nodes, outputVars);
            StringBuilder sb = new StringBuilder(script);
            if (!outputVars.isEmpty()) {
                RuleScriptResultCollector.prependOutputNullInits(sb, outputVars);
                RuleScriptResultCollector.appendResultMapReturn(sb, outputVars);
            }

            return CompileResult.ok(sb.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("决策树编译失败: " + e.getMessage());
        }
    }

}
