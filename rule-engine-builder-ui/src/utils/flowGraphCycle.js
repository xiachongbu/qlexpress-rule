/**
 * 决策流 / 决策树画布：有向环检测工具（与后端 DAG 编译语义一致）
 */

/**
 * 从 target 沿现有出边做 BFS；若可达 source，则新增边 source→target 会与已有路径构成有向环。
 *
 * @param {(nodeId: string) => Array<{ id?: string, targetNodeId: string }>} getOutgoing 当前图中某节点的出边（含 id 以便排除正在调整的边）
 * @param {string} sourceId 拟连线的起点节点 id
 * @param {string} targetId 拟连线的终点节点 id
 * @param {string} [excludeEdgeId] 重连/调整边时排除的旧边 id
 * @returns {boolean} 是否会形成环
 */
export function edgeWouldCompleteCycle (getOutgoing, sourceId, targetId, excludeEdgeId) {
  if (!sourceId || !targetId) return false
  if (sourceId === targetId) return true
  const visited = new Set()
  const queue = [targetId]
  while (queue.length > 0) {
    const cur = queue.shift()
    if (cur === sourceId) return true
    if (visited.has(cur)) continue
    visited.add(cur)
    const outs = getOutgoing(cur) || []
    for (let i = 0; i < outs.length; i++) {
      const e = outs[i]
      if (excludeEdgeId && e.id === excludeEdgeId) continue
      const next = e.targetNodeId
      if (next != null) queue.push(next)
    }
  }
  return false
}

/**
 * 使用 LogicFlow GraphModel 判断拟增/拟改的边是否成环。
 *
 * @param {object} graphModel LogicFlow 图模型（需含 getNodeOutgoingEdge）
 * @param {string} sourceId 连线起点
 * @param {string} targetId 连线终点
 * @param {string} [excludeEdgeId] 调整边时排除的边 id
 * @returns {boolean}
 */
export function wouldCreateCycleFromNewEdge (graphModel, sourceId, targetId, excludeEdgeId) {
  if (!graphModel || typeof graphModel.getNodeOutgoingEdge !== 'function') return false
  return edgeWouldCompleteCycle(
    (nodeId) => {
      const list = graphModel.getNodeOutgoingEdge(nodeId) || []
      return list.map((e) => ({ id: e.id, targetNodeId: e.targetNodeId }))
    },
    sourceId,
    targetId,
    excludeEdgeId
  )
}

/**
 * 根据边列表判断整张图是否存在有向环（用于加载数据 / 手工改库后的校验）。
 *
 * @param {Array<{ sourceNodeId: string, targetNodeId: string }>} edges
 * @param {string[]} nodeIds 图中全部节点 id
 * @returns {boolean}
 */
export function graphContainsDirectedCycle (edges, nodeIds) {
  const ids = nodeIds && nodeIds.length ? [...new Set(nodeIds)] : []
  if (ids.length === 0) return false
  const idSet = new Set(ids)
  const adj = new Map()
  const indegree = new Map()
  ids.forEach((id) => {
    adj.set(id, [])
    indegree.set(id, 0)
  })
  const list = edges || []
  for (let i = 0; i < list.length; i++) {
    const e = list[i]
    const u = e.sourceNodeId
    const v = e.targetNodeId
    if (!idSet.has(u) || !idSet.has(v)) continue
    adj.get(u).push(v)
    indegree.set(v, (indegree.get(v) || 0) + 1)
  }
  const queue = []
  indegree.forEach((deg, id) => {
    if (deg === 0) queue.push(id)
  })
  let visited = 0
  while (queue.length > 0) {
    const u = queue.shift()
    visited++
    const outs = adj.get(u) || []
    for (let j = 0; j < outs.length; j++) {
      const v = outs[j]
      const next = (indegree.get(v) || 0) - 1
      indegree.set(v, next)
      if (next === 0) queue.push(v)
    }
  }
  return visited < ids.length
}
