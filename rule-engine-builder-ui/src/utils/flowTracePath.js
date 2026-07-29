/**
 * 决策流(FLOW)执行轨迹路径推导
 *
 * 依据执行日志的表达式追踪树(traceInfo)与规则逻辑图(modelJson 的 nodes/edges)，
 * 复刻后端 GraphScriptGenerator 的遍历顺序（start/join 透传、task 顺序执行、
 * decision 分支 + 汇合点），推导实际执行经过的节点与连线，供只读画布高亮。
 *
 * 对齐策略：decision 网关的每条条件边按序消费追踪树中"实际求值过的 IF"序列，
 * 并用条件表达式的变量集做子集匹配（跳过 task 节点 actionData 编译出的 if-block）。
 * 任一环节无法对齐即整体返回 null，调用方回退为不高亮，保证无害。
 */

/** QL 关键字与字面量，提取条件表达式变量名时排除 */
const QL_NON_VAR_WORDS = new Set([
  'true', 'false', 'null', 'in', 'like', 'new', 'if', 'else', 'return',
  'and', 'or', 'not', 'instanceof'
])

/**
 * 解析 traceInfo JSON 为顶层语句列表（展开嵌套数组与 BLOCK/STATEMENT 包装，过滤函数/宏定义）
 * @param {string} traceInfo - 执行日志 trace_info 原始 JSON 字符串
 * @returns {Array<Object>} 顶层语句节点数组，解析失败返回空数组
 */
export function parseTraceStatements(traceInfo) {
  if (!traceInfo) return []
  let data
  try {
    data = JSON.parse(traceInfo)
  } catch (e) {
    return []
  }
  while (Array.isArray(data) && data.length > 0 && Array.isArray(data[0])) data = data.flat()
  let nodes = Array.isArray(data) ? data : [data]
  if (nodes.length === 1 && nodes[0] && nodes[0].type === 'BLOCK' && nodes[0].children) nodes = nodes[0].children
  if (nodes.length === 1 && nodes[0] && nodes[0].type === 'STATEMENT' && nodes[0].children) nodes = nodes[0].children
  const result = []
  for (const n of nodes) {
    if (!n || typeof n !== 'object') continue
    if (n.type === 'DEFINE_FUNCTION' || n.type === 'DEFINE_MACRO') continue
    if ((n.type === 'BLOCK' || n.type === 'STATEMENT') && Array.isArray(n.children)) {
      for (const c of n.children) {
        if (c && typeof c === 'object' && c.type !== 'DEFINE_FUNCTION' && c.type !== 'DEFINE_MACRO') result.push(c)
      }
    } else {
      result.push(n)
    }
  }
  return result
}

/** BLOCK/STATEMENT 包装节点展开为语句数组，普通节点包装为单元素数组 */
function unwrapBlock(node) {
  if (!node || typeof node !== 'object') return []
  if ((node.type === 'BLOCK' || node.type === 'STATEMENT') && Array.isArray(node.children)) {
    return node.children.filter(c => c && typeof c === 'object')
  }
  return [node]
}

/** 收集表达式子树中出现的根变量名（type=VARIABLE 的 token） */
function collectVarTokens(node, out) {
  if (!node || typeof node !== 'object') return out
  if (node.type === 'VARIABLE' && node.token) out.add(node.token)
  if (Array.isArray(node.children)) {
    for (const c of node.children) collectVarTokens(c, out)
  }
  return out
}

/**
 * 深度优先（只沿实际执行分支）收集追踪树中被求值过的 IF 节点，
 * 生成 { hit, varSet } 扁平序列。else-if 链在 QL AST 中是嵌套 IF，
 * 仅当前置条件为 false 时其 else 分支内的 IF 才会被求值并进入序列，
 * 与生成脚本的实际求值顺序一致。
 */
function collectExecutedIfs(stmts, out) {
  for (const s of stmts) {
    if (!s || typeof s !== 'object') continue
    if (s.type !== 'IF') continue
    const cond = s.children && s.children[0]
    const hit = !!(cond && cond.value === true)
    out.push({ hit, varSet: collectVarTokens(cond, new Set()) })
    const branch = s.children && (hit ? s.children[1] : s.children[2])
    if (branch) collectExecutedIfs(unwrapBlock(branch), out)
  }
}

/**
 * 从边 conditionExpression 中提取根变量标识符集合：
 * 剔除字符串字面量后匹配标识符，排除属性访问段（点号后）与关键字/函数调用名
 */
function extractConditionIdentifiers(expr) {
  const out = new Set()
  if (expr == null) return out
  const cleaned = String(expr)
    .replace(/"(?:\\.|[^"\\])*"/g, ' ')
    .replace(/'(?:\\.|[^'\\])*'/g, ' ')
  const re = /(^|[^.\w$])([A-Za-z_$][\w$]*)\s*(\()?/g
  let m
  while ((m = re.exec(cleaned)) !== null) {
    const word = m[2]
    const isCall = m[3] === '('
    if (!isCall && !QL_NON_VAR_WORDS.has(word)) out.add(word)
  }
  return out
}

/** 条件表达式变量集是否与追踪 IF 的变量集匹配（表达式变量为追踪变量的子集；表达式无变量时视为匹配） */
function varSetMatches(wanted, traceVarSet) {
  if (wanted.size === 0) return true
  for (const w of wanted) {
    if (!traceVarSet.has(w)) return false
  }
  return true
}

/**
 * 推导决策流执行路径
 * @param {{nodes: Array, edges: Array}} model - 规则 modelJson 的逻辑图（node.id 与画布 logicflow 节点同 id）
 * @param {string} traceInfo - 执行日志追踪树 JSON 字符串
 * @returns {{executedNodeIds: string[], executedEdgeIds: string[]}|null} 推导失败返回 null（调用方回退不高亮）
 */
export function deriveFlowTracePath(model, traceInfo) {
  if (!model || !Array.isArray(model.nodes) || !Array.isArray(model.edges) || model.nodes.length === 0) return null
  const stmts = parseTraceStatements(traceInfo)
  if (stmts.length === 0) return null
  const ifSeq = []
  collectExecutedIfs(stmts, ifSeq)

  const nodeMap = {}
  const outEdges = {}
  let startId = null
  for (const n of model.nodes) {
    if (!n || !n.id) return null
    nodeMap[n.id] = n
    outEdges[n.id] = []
    if (n.type === 'start') startId = n.id
  }
  for (const e of model.edges) {
    if (!e || !outEdges[e.source] || !nodeMap[e.target]) return null
    outEdges[e.source].push(e)
  }
  if (!startId) return null

  const executedNodeIds = new Set()
  const executedEdgeIds = new Set()
  const state = { idx: 0, failed: false }

  /** 复刻 GraphScriptGenerator.findMergeNode：各分支可达集交集，优先 join 节点 */
  function findMergeNode(decisionId) {
    const edges = outEdges[decisionId] || []
    if (edges.length < 2) return null
    const branchReachable = []
    for (const edge of edges) {
      const reachable = new Set()
      const queue = [edge.target]
      while (queue.length) {
        const nid = queue.shift()
        if (reachable.has(nid)) continue
        reachable.add(nid)
        for (const oe of (outEdges[nid] || [])) queue.push(oe.target)
      }
      branchReachable.push(reachable)
    }
    const common = [...branchReachable[0]].filter(id => branchReachable.every(s => s.has(id)))
    if (common.length === 0) return null
    for (const nid of common) {
      const n = nodeMap[nid]
      if (n && n.type === 'join') return nid
    }
    return common[0]
  }

  /** 自游标起向后找第一个变量集匹配的已求值 IF 并消费；找不到视为推导失败 */
  function consumeIf(condExpr) {
    const wanted = extractConditionIdentifiers(condExpr)
    for (let j = state.idx; j < ifSeq.length; j++) {
      if (varSetMatches(wanted, ifSeq[j].varSet)) {
        state.idx = j + 1
        return ifSeq[j]
      }
    }
    return null
  }

  function walk(nodeId, stopAt, visited) {
    if (state.failed || nodeId == null || nodeId === stopAt) return
    if (visited.has(nodeId)) return
    visited.add(nodeId)
    const node = nodeMap[nodeId]
    if (!node) return
    executedNodeIds.add(nodeId)
    const out = outEdges[nodeId] || []

    if (node.type !== 'decision') {
      // start/task/join/end：沿第一条出边（与脚本生成一致）
      if (out.length > 0) {
        executedEdgeIds.add(out[0].id)
        walk(out[0].target, stopAt, visited)
      }
      return
    }

    if (out.length === 0) return
    const condEdges = []
    let defaultEdge = null
    for (const e of out) {
      const ce = e.conditionExpression
      if (ce == null || String(ce).trim() === '') defaultEdge = e
      else condEdges.push(e)
    }
    const mergeId = findMergeNode(nodeId)

    if (condEdges.length === 0) {
      if (defaultEdge) {
        executedEdgeIds.add(defaultEdge.id)
        walk(defaultEdge.target, stopAt, visited)
      }
    } else {
      let hitEdge = null
      for (const edge of condEdges) {
        const evaluated = consumeIf(edge.conditionExpression)
        if (evaluated == null) {
          state.failed = true
          return
        }
        if (evaluated.hit) {
          hitEdge = edge
          break
        }
      }
      if (!hitEdge && defaultEdge) hitEdge = defaultEdge
      if (hitEdge) {
        executedEdgeIds.add(hitEdge.id)
        walk(hitEdge.target, mergeId, new Set(visited))
      }
    }

    if (mergeId != null) walk(mergeId, stopAt, visited)
  }

  walk(startId, null, new Set())
  if (state.failed || executedNodeIds.size === 0) return null
  return {
    executedNodeIds: Array.from(executedNodeIds),
    executedEdgeIds: Array.from(executedEdgeIds)
  }
}
