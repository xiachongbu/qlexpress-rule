/**
 * 决策树图模型（与后端 DecisionTreeCompiler 的 nodes/edges 字段一致）
 */

/**
 * 生成唯一节点 id
 * @returns {string}
 */
export function newNodeId() {
  return 'n_' + Date.now() + '_' + Math.random().toString(36).slice(2, 9)
}

/**
 * 生成唯一边 id
 * @returns {string}
 */
export function newEdgeId() {
  return 'e_' + Date.now() + '_' + Math.random().toString(36).slice(2, 9)
}

/**
 * 默认可编译的决策树（开始 → 判断 → 两条条件分支占位，无「默认 else」空表达式边）
 * @returns {{ nodes: object[], edges: object[] }}
 */
export function getDefaultTreeGraph() {
  const nStart = newNodeId()
  const nDec = newNodeId()
  const nT1 = newNodeId()
  const nT2 = newNodeId()
  return {
    nodes: [
      { id: nStart, type: 'start', name: '开始', actionData: [] },
      { id: nDec, type: 'decision', name: '条件判断', actionData: [] },
      { id: nT1, type: 'task', name: '分支一', actionData: [] },
      { id: nT2, type: 'task', name: '分支二', actionData: [] }
    ],
    edges: [
      { id: newEdgeId(), source: nStart, target: nDec, conditionExpression: '', name: '' },
      { id: newEdgeId(), source: nDec, target: nT1, conditionExpression: 'true', name: '条件1' },
      { id: newEdgeId(), source: nDec, target: nT2, conditionExpression: 'false', name: '条件2' }
    ]
  }
}

/**
 * 按 id 查找节点
 * @param {object[]} nodes
 * @param {string} id
 * @returns {object|undefined}
 */
export function nodeById(nodes, id) {
  return (nodes || []).find(n => n.id === id)
}

/**
 * 获取从某节点出发的出边列表
 * @param {object[]} edges
 * @param {string} sourceId
 * @returns {object[]}
 */
export function getOutEdges(edges, sourceId) {
  return (edges || []).filter(e => e.source === sourceId)
}

/**
 * 统计指向某节点的入边数量
 * @param {object[]} edges
 * @param {string} targetId
 * @returns {number}
 */
export function countInEdges(edges, targetId) {
  return (edges || []).filter(e => e.target === targetId).length
}

/**
 * 获取指向该节点的唯一入边（决策树最多一条）
 * @param {object[]} edges
 * @param {string} targetId
 * @returns {object|undefined}
 */
export function getInEdge(edges, targetId) {
  const list = (edges || []).filter(e => e.target === targetId)
  return list.length ? list[0] : undefined
}

/**
 * 决策节点出边排序：有 conditionExpression 的在前，空表达式在后（兼容旧数据的 else 分支）
 * @param {object[]} outList
 * @returns {object[]}
 */
export function sortDecisionOutEdges(outList) {
  const arr = (outList || []).slice()
  arr.sort((a, b) => {
    const ae = (a.conditionExpression || '').trim()
    const be = (b.conditionExpression || '').trim()
    if (!ae && be) return 1
    if (ae && !be) return -1
    return 0
  })
  return arr
}

/**
 * 从某节点沿出边 BFS 收集所有可达节点 id（含起点）
 * @param {object[]} edges
 * @param {string} fromId
 * @returns {Set<string>}
 */
export function collectReachableFrom(edges, fromId) {
  const set = new Set()
  const q = [fromId]
  while (q.length) {
    const id = q.shift()
    if (set.has(id)) continue
    set.add(id)
    getOutEdges(edges, id).forEach(e => q.push(e.target))
  }
  return set
}

/**
 * 若从 source 连到 target 是否会在现有树结构上产生环（沿 target 能回到 source）
 * @param {object[]} edges
 * @param {string} sourceId
 * @param {string} targetId
 * @returns {boolean}
 */
export function wouldCreateCycle(edges, sourceId, targetId) {
  const reachable = collectReachableFrom(edges, targetId)
  return reachable.has(sourceId)
}

/**
 * 校验决策树模型，返回错误文案列表（空则通过）
 * @param {object[]} nodes
 * @param {object[]} edges
 * @returns {string[]}
 */
export function validateTreeModel(nodes, edges) {
  const errors = []
  const ns = nodes || []
  const es = edges || []
  const starts = ns.filter(n => n.type === 'start')
  if (starts.length === 0) errors.push('缺少开始节点')
  if (starts.length > 1) errors.push('开始节点只能有一个')

  ns.filter(n => n.type === 'join').forEach(jn => {
    errors.push('决策树不允许使用聚合节点「' + (jn.name || jn.id) + '」，请使用决策流')
  })

  ns.filter(n => n.type !== 'start').forEach(n => {
    if (countInEdges(es, n.id) > 1) {
      errors.push('节点「' + (n.name || n.id) + '」有多条入边，决策树不允许分支汇合')
    }
  })

  ns.filter(n => n.type === 'decision').forEach(gw => {
    const out = getOutEdges(es, gw.id)
    if (out.length < 2) {
      errors.push('条件判断节点「' + (gw.name || gw.id) + '」至少需要两个出口')
    }
  })

  if (starts.length === 1) {
    const startOut = getOutEdges(es, starts[0].id)
    if (startOut.length === 0) errors.push('开始节点没有出边')
  }

  return errors
}

/**
 * 删除节点及其所有后代节点、以及所有相关边
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {string} nodeId
 * @returns {{ nodes: object[], edges: object[] }}
 */
export function deleteSubtree(nodes, edges, nodeId) {
  const reachable = collectReachableFrom(edges, nodeId)
  const keepNodes = (nodes || []).filter(n => !reachable.has(n.id))
  const keepEdges = (edges || []).filter(
    e => !reachable.has(e.source) && !reachable.has(e.target)
  )
  return { nodes: keepNodes, edges: keepEdges }
}

/**
 * 删除单条边（不删节点）
 * @param {object[]} edges
 * @param {string} edgeId
 * @returns {object[]}
 */
export function removeEdgeById(edges, edgeId) {
  return (edges || []).filter(e => e.id !== edgeId)
}

/**
 * 查找开始节点 id
 * @param {object[]} nodes
 * @returns {string|null}
 */
export function findStartId(nodes) {
  const s = (nodes || []).find(n => n.type === 'start')
  return s ? s.id : null
}

/**
 * 在图中新增一条边（调用方需自行校验树约束）
 * @param {object[]} edges
 * @param {object} edge
 * @returns {object[]}
 */
export function addEdgeClone(edges, edge) {
  return [...(edges || []), edge]
}

/**
 * 在图中新增节点
 * @param {object[]} nodes
 * @param {object} node
 * @returns {object[]}
 */
export function addNodeClone(nodes, node) {
  return [...(nodes || []), node]
}

/**
 * 用 patch 更新节点（按 id）
 * @param {object[]} nodes
 * @param {string} id
 * @param {object} patch
 * @returns {object[]}
 */
export function patchNode(nodes, id, patch) {
  return (nodes || []).map(n => (n.id === id ? { ...n, ...patch } : n))
}

/**
 * 用 patch 更新边（按 id）
 * @param {object[]} edges
 * @param {string} id
 * @param {object} patch
 * @returns {object[]}
 */
export function patchEdge(edges, id, patch) {
  return (edges || []).map(e => (e.id === id ? { ...e, ...patch } : e))
}

/**
 * 解析后端 modelJson 为 { nodes, edges }（忽略 logicflow）
 * @param {object} modelData
 * @returns {{ nodes: object[], edges: object[] }}
 */
export function extractTreeGraphFromModel(modelData) {
  if (!modelData || typeof modelData !== 'object') {
    return getDefaultTreeGraph()
  }
  const nodes = Array.isArray(modelData.nodes) ? modelData.nodes.map(normalizeLoadedNode) : []
  const edges = Array.isArray(modelData.edges) ? modelData.edges.map(normalizeLoadedEdge) : []
  if (nodes.length === 0) return getDefaultTreeGraph()
  return { nodes, edges }
}

/**
 * 规范化加载的节点字段
 * @param {object} n
 * @returns {object}
 */
function normalizeLoadedNode(n) {
  /** 决策树不再使用 end 类型：历史数据中的结束节点按任务节点加载 */
  const type = n.type === 'end' ? 'task' : n.type
  return {
    id: n.id,
    type,
    name: n.name || '',
    actionData: Array.isArray(n.actionData) ? n.actionData : [],
    gatewayDirection: n.gatewayDirection || ''
  }
}

/**
 * 规范化加载的边字段
 * @param {object} e
 * @returns {object}
 */
function normalizeLoadedEdge(e) {
  return {
    id: e.id || newEdgeId(),
    source: e.source,
    target: e.target,
    conditionExpression: e.conditionExpression || '',
    name: e.name || ''
  }
}

/**
 * 在给定父节点下新增子「判断」节点，并自动补两条到占位任务的边（满足至少两出口）
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {string} parentNodeId
 * @returns {{ nodes: object[], edges: object[], newDecisionId: string }}
 */
export function addChildDecision(nodes, edges, parentNodeId) {
  const parent = nodeById(nodes, parentNodeId)
  if (!parent) return { nodes, edges, newDecisionId: null }
  const decId = newNodeId()
  const t1 = newNodeId()
  const t2 = newNodeId()
  const e0 = { id: newEdgeId(), source: parentNodeId, target: decId, conditionExpression: '', name: '' }
  const e1 = { id: newEdgeId(), source: decId, target: t1, conditionExpression: 'true', name: '条件1' }
  const e2 = { id: newEdgeId(), source: decId, target: t2, conditionExpression: 'false', name: '条件2' }
  const newNodes = [
    ...nodes,
    { id: decId, type: 'decision', name: '条件判断', actionData: [] },
    { id: t1, type: 'task', name: '分支一', actionData: [] },
    { id: t2, type: 'task', name: '分支二', actionData: [] }
  ]
  const newEdges = [...edges, e0, e1, e2]
  return { nodes: newNodes, edges: newEdges, newDecisionId: decId }
}

/**
 * 在判断节点上新增一条分支，子节点为新的「判断+双任务」子树
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {string} decisionId
 * @returns {{ nodes: object[], edges: object[] }}
 */
export function addBranchWithNestedDecision(nodes, edges, decisionId) {
  const d = nodeById(nodes, decisionId)
  if (!d || d.type !== 'decision') return { nodes, edges }
  const decId = newNodeId()
  const t1 = newNodeId()
  const t2 = newNodeId()
  const eNew = {
    id: newEdgeId(),
    source: decisionId,
    target: decId,
    conditionExpression: 'true',
    name: '新条件'
  }
  const e1 = { id: newEdgeId(), source: decId, target: t1, conditionExpression: 'true', name: '条件1' }
  const e2 = { id: newEdgeId(), source: decId, target: t2, conditionExpression: 'false', name: '条件2' }
  return {
    nodes: [
      ...nodes,
      { id: decId, type: 'decision', name: '条件判断', actionData: [] },
      { id: t1, type: 'task', name: '分支一', actionData: [] },
      { id: t2, type: 'task', name: '分支二', actionData: [] }
    ],
    edges: [...edges, eNew, e1, e2]
  }
}

/**
 * 在给定判断节点上新增一条分支（新边 + 新任务节点）
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {string} decisionId
 * @returns {{ nodes: object[], edges: object[], newEdgeId: string }}
 */
export function addBranchToDecision(nodes, edges, decisionId) {
  const d = nodeById(nodes, decisionId)
  if (!d || d.type !== 'decision') return { nodes, edges, newEdgeId: null }
  const tid = newNodeId()
  const eid = newEdgeId()
  const edge = {
    id: eid,
    source: decisionId,
    target: tid,
    conditionExpression: 'true',
    name: '新条件'
  }
  return {
    nodes: [...nodes, { id: tid, type: 'task', name: '新分支', actionData: [] }],
    edges: [...edges, edge],
    newEdgeId: eid
  }
}

/**
 * 在父节点下直接添加任务子节点（单出边）
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {string} parentNodeId
 * @returns {{ nodes: object[], edges: object[], newTaskId: string }}
 */
export function addChildTask(nodes, edges, parentNodeId) {
  const tid = newNodeId()
  const e = {
    id: newEdgeId(),
    source: parentNodeId,
    target: tid,
    conditionExpression: '',
    name: ''
  }
  return {
    nodes: [...nodes, { id: tid, type: 'task', name: '执行动作', actionData: [] }],
    edges: [...edges, e],
    newTaskId: tid
  }
}

/**
 * 按边删除该分支（删掉目标子树及关联边）
 * @param {object[]} nodes
 * @param {object[]} edges
 * @param {string} edgeId
 * @returns {{ nodes: object[], edges: object[] }}
 */
export function deleteBranchByEdgeId(nodes, edges, edgeId) {
  const e = (edges || []).find(x => x.id === edgeId)
  if (!e) return { nodes: [...(nodes || [])], edges: [...(edges || [])] }
  return deleteSubtree(nodes, edges, e.target)
}

/**
 * UI 类型（与旧 LogicFlow 属性面板 type 一致）
 * @param {string} backendType
 * @returns {string}
 */
export function backendTypeToUi(backendType) {
  const m = {
    start: 'start-event',
    decision: 'exclusive-gateway',
    task: 'script-task',
    join: 'join-gateway'
  }
  return m[backendType] || backendType
}

/**
 * UI 类型转回后端类型
 * @param {string} uiType
 * @returns {string}
 */
export function uiTypeToBackend(uiType) {
  const m = {
    'start-event': 'start',
    'script-task': 'task',
    'exclusive-gateway': 'decision',
    'join-gateway': 'join'
  }
  return m[uiType] || uiType
}
