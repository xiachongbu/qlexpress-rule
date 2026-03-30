package com.bjjw.rule.core.compiler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

/**
 * 图结构 → QLExpress 脚本生成器（共享工具类）
 *
 * 将 nodes/edges 图递归展开为单一 QLExpress 脚本。
 * 支持节点类型：start、end、task（含 actionData）、decision（if/else）、join（汇合透传）。
 * 被 DecisionTreeCompiler 和 DecisionFlowCompiler 共用。
 */
public class GraphScriptGenerator {

    /**
     * 根据图结构生成 QLExpress 脚本
     *
     * @param nodeMap    节点 id → 节点 JSON
     * @param outEdgeMap 节点 id → 出边列表
     * @param startId    开始节点 id
     * @return 生成的 QLExpress 脚本
     */
    public static String generate(Map<String, JSONObject> nodeMap,
                                  Map<String, List<JSONObject>> outEdgeMap,
                                  String startId) {
        StringBuilder script = new StringBuilder();
        Set<String> visited = new HashSet<>();
        generateScript(startId, null, nodeMap, outEdgeMap, script, visited, 0);
        return script.toString().trim();
    }

    private static void generateScript(String nodeId, String stopAt,
                                       Map<String, JSONObject> nodeMap,
                                       Map<String, List<JSONObject>> outEdgeMap,
                                       StringBuilder script, Set<String> visited, int indent) {
        if (nodeId == null || nodeId.equals(stopAt)) return;
        if (visited.contains(nodeId)) return;
        visited.add(nodeId);

        JSONObject node = nodeMap.get(nodeId);
        if (node == null) return;
        String type = node.getString("type");
        String name = node.getString("name");

        if ("start".equals(type) || "join".equals(type)) {
            List<JSONObject> out = outEdgeMap.getOrDefault(nodeId, Collections.emptyList());
            if (!out.isEmpty()) {
                generateScript(out.get(0).getString("target"), stopAt, nodeMap, outEdgeMap, script, visited, indent);
            }
        } else if ("task".equals(type)) {
            JSONArray actionData = node.getJSONArray("actionData");
            String qlScript;
            if (actionData != null && !actionData.isEmpty()) {
                qlScript = ActionDataCompiler.compile(actionData);
            } else {
                qlScript = node.getString("qlExpressScript");
            }
            if (qlScript != null && !qlScript.trim().isEmpty()) {
                appendIndent(script, indent);
                script.append("// ").append(name != null ? name : "脚本任务").append("\n");
                for (String line : qlScript.trim().split("\n")) {
                    appendIndent(script, indent);
                    script.append(line).append("\n");
                }
                script.append("\n");
            }
            List<JSONObject> out = outEdgeMap.getOrDefault(nodeId, Collections.emptyList());
            if (!out.isEmpty()) {
                generateScript(out.get(0).getString("target"), stopAt, nodeMap, outEdgeMap, script, visited, indent);
            }
        } else if ("decision".equals(type)) {
            List<JSONObject> out = outEdgeMap.getOrDefault(nodeId, Collections.emptyList());
            if (out.isEmpty()) return;

            String mergeNode = findMergeNode(nodeId, outEdgeMap, nodeMap);

            JSONObject defaultEdge = null;
            List<JSONObject> condEdges = new ArrayList<>();
            for (JSONObject edge : out) {
                String condExpr = edge.getString("conditionExpression");
                if (condExpr == null || condExpr.trim().isEmpty()) {
                    defaultEdge = edge;
                } else {
                    condEdges.add(edge);
                }
            }

            if (condEdges.isEmpty() && defaultEdge != null) {
                generateScript(defaultEdge.getString("target"), stopAt, nodeMap, outEdgeMap, script, visited, indent);
            } else {
                for (int i = 0; i < condEdges.size(); i++) {
                    JSONObject edge = condEdges.get(i);
                    String condExpr = edge.getString("conditionExpression");
                    appendIndent(script, indent);
                    if (i == 0) {
                        script.append("if (").append(condExpr).append(") {\n");
                    } else {
                        script.append("} else if (").append(condExpr).append(") {\n");
                    }
                    Set<String> branchVisited = new HashSet<>(visited);
                    generateScript(edge.getString("target"), mergeNode, nodeMap, outEdgeMap, script, branchVisited, indent + 1);
                }
                if (defaultEdge != null) {
                    appendIndent(script, indent);
                    script.append("} else {\n");
                    Set<String> branchVisited = new HashSet<>(visited);
                    generateScript(defaultEdge.getString("target"), mergeNode, nodeMap, outEdgeMap, script, branchVisited, indent + 1);
                }
                appendIndent(script, indent);
                script.append("}\n\n");
            }

            if (mergeNode != null) {
                generateScript(mergeNode, stopAt, nodeMap, outEdgeMap, script, visited, indent);
            }
        }
    }

    /**
     * 找到决策节点的汇合点（后续所有分支的第一个公共节点，优先选择 join 类型）
     */
    static String findMergeNode(String decisionNodeId,
                                Map<String, List<JSONObject>> outEdgeMap,
                                Map<String, JSONObject> nodeMap) {
        List<JSONObject> edges = outEdgeMap.getOrDefault(decisionNodeId, Collections.emptyList());
        if (edges.size() < 2) return null;

        List<Set<String>> branchReachable = new ArrayList<>();
        for (JSONObject edge : edges) {
            Set<String> reachable = new LinkedHashSet<>();
            Queue<String> queue = new LinkedList<>();
            queue.add(edge.getString("target"));
            while (!queue.isEmpty()) {
                String nid = queue.poll();
                if (reachable.contains(nid)) continue;
                reachable.add(nid);
                for (JSONObject e : outEdgeMap.getOrDefault(nid, Collections.emptyList())) {
                    queue.add(e.getString("target"));
                }
            }
            branchReachable.add(reachable);
        }

        if (branchReachable.isEmpty()) return null;

        Set<String> common = new LinkedHashSet<>(branchReachable.get(0));
        for (int i = 1; i < branchReachable.size(); i++) {
            common.retainAll(branchReachable.get(i));
        }
        if (common.isEmpty()) return null;

        for (String nid : common) {
            JSONObject n = nodeMap.get(nid);
            if (n != null && "join".equals(n.getString("type"))) {
                return nid;
            }
        }
        return common.iterator().next();
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("    ");
    }
}
