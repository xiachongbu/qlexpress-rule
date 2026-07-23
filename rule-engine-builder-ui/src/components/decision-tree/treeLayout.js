import {getOutEdges, nodeById, sortDecisionOutEdges} from './treeGraphModel'

/**
 * 自某节点起构建横向渲染用的递归布局树
 * @param {string} nodeId 当前节点 id
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {Set<string>} [visited] 防环（异常图避免栈溢出卡死）
 * @returns {object|null} layout 节点
 */
export function buildLayoutFrom(nodeId, nodes, edges, visited) {
  if (!visited) visited = new Set()
  const node = nodeById(nodes, nodeId)
  if (!node) return null

  if (visited.has(nodeId)) {
    return { kind: 'cycle', id: nodeId, name: '（检测到环，请检查连线）' }
  }
  visited.add(nodeId)

  if (node.type === 'start') {
    const outs = getOutEdges(edges, nodeId)
    const child = outs.length === 1 ? buildLayoutFrom(outs[0].target, nodes, edges, visited) : null
    return { kind: 'start', id: nodeId, name: node.name, child }
  }

  if (node.type === 'decision') {
    const outs = sortDecisionOutEdges(getOutEdges(edges, nodeId))
    const branches = outs.map(e => ({
      edgeId: e.id,
      edgeName: e.name,
      conditionExpression: e.conditionExpression || '',
      child: buildLayoutFrom(e.target, nodes, edges, visited)
    }))
    return { kind: 'decision', id: nodeId, name: node.name, branches }
  }

  if (node.type === 'task') {
    const outs = getOutEdges(edges, nodeId)
    const next = outs.length === 1 ? buildLayoutFrom(outs[0].target, nodes, edges, visited) : null
    return {
      kind: 'task',
      id: nodeId,
      name: node.name,
      next
    }
  }

  return { kind: 'unknown', id: nodeId, name: node.name || nodeId }
}

/**
 * 从开始节点构建整棵树布局
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {string|null} startId
 * @returns {object|null}
 */
export function buildTreeLayout(nodes, edges, startId) {
  if (!startId) return null
  return buildLayoutFrom(startId, nodes, edges, new Set())
}
