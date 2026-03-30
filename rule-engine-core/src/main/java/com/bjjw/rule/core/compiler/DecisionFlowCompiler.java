package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

/**
 * 决策流编译器 - 支持 DAG 结构（含聚合节点）
 *
 * 校验 nodes/edges 图结构的合法性，将图编译为单一 QLExpress 脚本。
 * 支持分支汇合（join 节点），适用于分叉后有公共后续逻辑的场景。
 */
public class DecisionFlowCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            JSONArray nodes = model.getJSONArray("nodes");
            JSONArray edges = model.getJSONArray("edges");

            if (nodes == null || nodes.isEmpty()) {
                return CompileResult.fail("决策流模型缺少 nodes");
            }
            if (edges == null) {
                return CompileResult.fail("决策流模型缺少 edges");
            }

            // --- 结构校验 ---
            Set<String> nodeIds = new HashSet<>();
            int startCount = 0;
            int endCount = 0;
            String startId = null;

            for (int i = 0; i < nodes.size(); i++) {
                JSONObject n = nodes.getJSONObject(i);
                String id = n.getString("id");
                String type = n.getString("type");

                if (id == null || id.isEmpty()) {
                    return CompileResult.fail("节点 #" + i + " 缺少 id");
                }
                if (!nodeIds.add(id)) {
                    return CompileResult.fail("节点 id 重复: " + id);
                }

                if ("start".equals(type)) {
                    startCount++;
                    startId = id;
                }
                if ("end".equals(type)) endCount++;
            }

            if (startCount == 0) return CompileResult.fail("缺少开始节点（start）");
            if (startCount > 1) return CompileResult.fail("开始节点（start）只能有一个，当前有 " + startCount + " 个");
            if (endCount == 0) return CompileResult.fail("缺少结束节点（end）");

            for (int i = 0; i < edges.size(); i++) {
                JSONObject e = edges.getJSONObject(i);
                String source = e.getString("source");
                String target = e.getString("target");
                if (!nodeIds.contains(source)) {
                    return CompileResult.fail("连线 source 指向不存在的节点: " + source);
                }
                if (!nodeIds.contains(target)) {
                    return CompileResult.fail("连线 target 指向不存在的节点: " + target);
                }
            }

            // --- 构建图结构并生成脚本 ---
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
            return CompileResult.fail("决策流编译失败: " + e.getMessage());
        }
    }

}
