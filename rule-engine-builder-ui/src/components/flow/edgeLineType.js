/**
 * LogicFlow 内置连线类型：折线、直线、贝塞尔（产品「弧线」）
 */
export const EDGE_LINE_TYPES = ['polyline', 'line', 'bezier']

/**
 * 将任意值规范为合法的默认连线类型，非法或缺失时回退为 polyline
 * @param {string|undefined|null} v - 来自 modelJson 或 UI 的值
 * @returns {'polyline'|'line'|'bezier'}
 */
export function normalizeDefaultEdgeLineType(v) {
  if (v != null && v !== '' && EDGE_LINE_TYPES.includes(v)) return v
  return 'polyline'
}

/**
 * 将解析后的规则 model 规范为新版连线字段（不保留旧 JSON 特殊分支；缺省补 defaultEdgeLineType）
 * @param {Record<string, unknown>} modelData - JSON.parse(modelJson) 得到的对象
 */
export function migrateModelJsonForEdgeLineTypes(modelData) {
  if (!modelData || typeof modelData !== 'object') return
  modelData.defaultEdgeLineType = normalizeDefaultEdgeLineType(modelData.defaultEdgeLineType)
}

/**
 * 计算单条边渲染时应采用的 LogicFlow type：单边 properties.edgeLineType 优先，否则使用全局默认
 * @param {{ type?: string, properties?: Record<string, unknown> }} edge - LogicFlow 边数据
 * @param {'polyline'|'line'|'bezier'} globalDefault - 已规范化的 model 级默认连线类型
 * @returns {'polyline'|'line'|'bezier'}
 */
export function resolveEdgeLineType(edge, globalDefault) {
  const p = edge.properties && edge.properties.edgeLineType
  if (p && EDGE_LINE_TYPES.includes(p)) return p
  return normalizeDefaultEdgeLineType(globalDefault)
}

/**
 * 克隆 logicflow 图并规范化每条边的 type，供 render 前使用
 * @param {{ nodes?: unknown[], edges?: unknown[] }} lfGraph - LogicFlow 图数据
 * @param {'polyline'|'line'|'bezier'} globalDefault - 已规范化的全局默认连线类型
 * @returns {{ nodes: unknown[], edges: unknown[] }}
 */
export function prepareLogicFlowDataForRender(lfGraph, globalDefault) {
  const g = normalizeDefaultEdgeLineType(globalDefault)
  const nodes = lfGraph && Array.isArray(lfGraph.nodes) ? lfGraph.nodes.map(n => ({ ...n })) : []
  const edges = (lfGraph && Array.isArray(lfGraph.edges) ? lfGraph.edges : []).map(e => ({
    ...e,
    type: resolveEdgeLineType(e, g)
  }))
  return { ...lfGraph, nodes, edges }
}

/**
 * 更新 LogicFlow 实例的默认边类型，并将「跟随全局」的边（无 edgeLineType）改为该类型
 * @param {*} lf - LogicFlow 实例
 * @param {'polyline'|'line'|'bezier'} globalType - 新的全局类型
 */
export function applyGlobalEdgeTypeToInheritedEdges(lf, globalType) {
  const g = normalizeDefaultEdgeLineType(globalType)
  lf.setDefaultEdgeType(g)
  const edges = lf.getGraphData().edges || []
  edges.forEach(e => {
    const explicit = e.properties && e.properties.edgeLineType
    if (explicit && EDGE_LINE_TYPES.includes(explicit)) return
    try {
      // LogicFlow 1.2 需用 changeEdgeType；setEdgeData 改 type 往往不触发重绘
      lf.changeEdgeType(e.id, g)
    } catch (err) {
      /* ignore */
    }
  })
}

/**
 * 从表单状态合并连线业务属性，并在「跟随全局」时移除 properties.edgeLineType
 * @param {Record<string, unknown>} currentProps - 当前边上的 properties
 * @param {{ conditionName?: string, conditionExpr?: string, edgeLineType?: string }} form - 属性面板绑定值
 * @returns {Record<string, unknown>}
 */
export function mergeEdgePropertiesFromForm(currentProps, form) {
  const next = { ...(currentProps || {}) }
  next.conditionName = form.conditionName != null ? form.conditionName : ''
  next.conditionExpr = form.conditionExpr != null ? form.conditionExpr : ''
  if (form.edgeLineType) next.edgeLineType = form.edgeLineType
  else delete next.edgeLineType
  return next
}
