/**
 * actionData JSON → QLExpress 脚本生成器
 *
 * 块类型一览：
 *   assign:       赋值          { type:'assign', target, value }
 *   if-block:     条件分支      { type:'if-block', branches:[{type,condVar,condOp,condValue,actions}] }
 *   switch-block: switch匹配    { type:'switch-block', matchVar, cases:[{value,actions}], defaultActions:[] }
 *   func-call:    函数调用      { type:'func-call', target, funcName, args:[] }
 *   http-call:    HTTP调用       { type:'http-call', target, method, url, headers:[{key,value}], bodyMode:'none'|'text'|'json', body, connectTimeout, readTimeout }
 *   foreach:      for-each循环  { type:'foreach', itemVar, listExpr, actions:[] }
 *   ternary:      三元表达式    { type:'ternary', target, condVar, condOp, condValue, trueValue, falseValue }
 *   in-check:     in判断赋值    { type:'in-check', target, checkVar, inValues:[], trueValue, falseValue }
 *   template-str: 动态字符串    { type:'template-str', target, parts:[{type:'text'|'expr', content}] }
 */

import {buildQlConditionExpr, constTypeFromVarType, inferConstVarType} from './conditionExpr'

function wrapValue(val) {
  if (val === null || val === undefined || val === '') return '""'
  const s = String(val).trim()
  if (s === 'true' || s === 'false' || s === 'null') return s
  if (!isNaN(s) && s !== '') return s
  if (/^[a-zA-Z_]\w*(\.\w+)*$/.test(s)) return s
  if (s.startsWith('"') || s.startsWith("'")) return s
  if (/[+\-*/()><=!&|,[\]{}]/.test(s)) return s
  return '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

/**
 * 文本 → QL 字符串字面量（保留 ${} 插值）；与后端 ActionDataCompiler.qlStringLiteral 一致。
 */
function qlStringLiteral(s) {
  if (s == null) s = ''
  return '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

/**
 * headers 数组 [{key,value}] → QL map 字面量 {"k": "v"}；无有效项时返回空字符串。
 */
function buildHeaderMap(headers) {
  if (!Array.isArray(headers) || headers.length === 0) return ''
  const segs = []
  for (const h of headers) {
    if (!h || !h.key || !String(h.key).trim()) continue
    segs.push(qlStringLiteral(String(h.key).trim()) + ': ' + qlStringLiteral(h.value))
  }
  return segs.length ? '{' + segs.join(', ') + '}' : ''
}

function generateBlock(block, indent) {
  const pad = '    '.repeat(indent)
  if (!block || !block.type) return ''

  switch (block.type) {
    case 'assign': {
      if (!block.target || !block.value) return ''
      let code = pad + block.target + ' = ' + block.value
      // 精度舍入（与后端 ActionDataCompiler.compileAssign 一致）：结果保留 BigDecimal，不转 double
      if (block.enableRounding && block.decimalPlaces != null && block.decimalPlaces >= 0) {
        const rm = block.roundingMode || 'HALF_UP'
        code += '\n' + pad + block.target + ' = (new java.math.BigDecimal("" + ' + block.target +
          ')).setScale(' + block.decimalPlaces + ', java.math.RoundingMode.' + rm + ')'
      }
      return code
    }

    case 'if-block': {
      const branches = block.branches || []
      if (branches.length === 0) return ''
      const lines = []
      for (const b of branches) {
        const inner = (b.actions || []).map(a => generateBlock(a, indent + 1)).filter(Boolean)
        if (b.type === 'if') lines.push(pad + 'if (' + buildCondExpr(b) + ') {')
        else if (b.type === 'elseif') lines.push(pad + '} else if (' + buildCondExpr(b) + ') {')
        else lines.push(pad + '} else {')
        lines.push(...inner)
      }
      lines.push(pad + '}')
      return lines.join('\n')
    }

    case 'switch-block': {
      if (!block.matchVar) return ''
      const lines = [pad + 'switch (' + block.matchVar + ') {']
      for (const c of (block.cases || [])) {
        if (!c.value && c.value !== 0) continue
        lines.push(pad + '    case ' + wrapValue(c.value) + ' -> {')
        for (const a of (c.actions || [])) {
          const code = generateBlock(a, indent + 2)
          if (code) lines.push(code)
        }
        lines.push(pad + '    }')
      }
      if (block.defaultActions && block.defaultActions.length > 0) {
        lines.push(pad + '    default -> {')
        for (const a of block.defaultActions) {
          const code = generateBlock(a, indent + 2)
          if (code) lines.push(code)
        }
        lines.push(pad + '    }')
      }
      lines.push(pad + '}')
      return lines.join('\n')
    }

    case 'func-call': {
      if (!block.funcName) return ''
      const args = (block.args || []).join(', ')
      const call = block.funcName + '(' + args + ')'
      return block.target ? pad + block.target + ' = ' + call : pad + call
    }

    case 'http-call': {
      if (!block.url) return ''
      const parts = []
      parts.push('"url": ' + qlStringLiteral(block.url))
      const method = (block.method || 'GET').trim().toUpperCase()
      parts.push('"method": ' + qlStringLiteral(method))
      const headerMap = buildHeaderMap(block.headers)
      if (headerMap) parts.push('"headers": ' + headerMap)
      if (block.bodyMode === 'json') {
        if (block.body && String(block.body).trim()) parts.push('"body": ' + String(block.body).trim())
      } else if (block.bodyMode === 'text') {
        if (block.body && String(block.body).trim()) parts.push('"body": ' + qlStringLiteral(block.body))
      }
      if (block.connectTimeout != null && Number(block.connectTimeout) > 0) parts.push('"connectTimeout": ' + Number(block.connectTimeout))
      if (block.readTimeout != null && Number(block.readTimeout) > 0) parts.push('"readTimeout": ' + Number(block.readTimeout))
      const call = 'httpCall({' + parts.join(', ') + '})'
      return block.target ? pad + block.target + ' = ' + call : pad + call
    }

    case 'foreach': {
      if (!block.itemVar || !block.listExpr) return ''
      const lines = [pad + 'for (' + block.itemVar + ' : ' + block.listExpr + ') {']
      for (const a of (block.actions || [])) {
        const code = generateBlock(a, indent + 1)
        if (code) lines.push(code)
      }
      lines.push(pad + '}')
      return lines.join('\n')
    }

    case 'ternary': {
      if (!block.target || !block.condVar) return ''
      const cond = buildTernaryCond(block)
      return pad + block.target + ' = ' + cond + ' ? ' + (block.trueValue || '""') + ' : ' + (block.falseValue || '""')
    }

    case 'in-check': {
      if (!block.target || !block.checkVar) return ''
      const inVals = (block.inValues || []).filter(v => v != null && String(v).trim() !== '')
      const vals = inVals.map(v => wrapValue(v)).join(', ')
      return pad + block.target + ' = ' + block.checkVar + ' in [' + vals + '] ? ' + (block.trueValue || 'true') + ' : ' + (block.falseValue || 'false')
    }

    case 'template-str': {
      if (!block.target || !block.parts || block.parts.length === 0) return ''
      const segs = block.parts.map(p => p.type === 'expr' ? '${' + p.content + '}' : p.content).join('')
      return pad + block.target + ' = "' + segs.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
    }

    default:
      return ''
  }
}

/**
 * if-block / elseif 条件（与后端 ActionDataCompiler.buildCond 一致）：
 * 优先用选择条件变量时记录的 condVarType 决定常量是否加引号，
 * 避免纯数字形式的字符串（如税号）被误判为数值；缺失时回退启发式推断。
 */
function buildCondExpr(branch) {
  if (!branch.condVar) return 'true'
  const raw = branch.condValue
  if (raw == null || String(raw).trim() === '') return 'true'
  const expr = buildQlConditionExpr(
    branch.condVar,
    branch.condOp || '==',
    String(raw),
    'value',
    constTypeFromVarType(branch.condVarType) || inferConstVarType(raw)
  )
  return expr || 'true'
}

/**
 * 三元表达式块的条件子句。
 */
function buildTernaryCond(block) {
  if (!block.condVar) return 'true'
  const raw = block.condValue
  if (raw == null || String(raw).trim() === '') return 'true'
  const expr = buildQlConditionExpr(
    block.condVar,
    block.condOp || '==',
    String(raw),
    'value',
    constTypeFromVarType(block.condVarType) || inferConstVarType(raw)
  )
  return expr || 'true'
}

export function generateScript(actionData) {
  if (!actionData || !Array.isArray(actionData) || actionData.length === 0) return ''
  return actionData.map(b => generateBlock(b, 0)).filter(Boolean).join('\n')
}

export function actionDataToBlocks(actionData) {
  if (!actionData || !Array.isArray(actionData) || actionData.length === 0) return []
  return actionData.map(block => {
    if (block.type === 'if-block' && block.branches) {
      return {
        ...block,
        branches: block.branches.map(b => ({
          ...b,
          actions: b.actions || []
        }))
      }
    }
    if (block.type === 'switch-block') {
      return {
        ...block,
        cases: (block.cases || []).map(c => ({ ...c, actions: c.actions || [] })),
        defaultActions: block.defaultActions || []
      }
    }
    if (block.type === 'foreach') {
      return { ...block, actions: block.actions || [] }
    }
    if (block.type === 'in-check') {
      const inValues = Array.isArray(block.inValues) ? block.inValues.filter(v => v != null && String(v).trim() !== '') : []
      return { ...block, inValues }
    }
    if (block.type === 'http-call') {
      return { ...block, headers: Array.isArray(block.headers) ? block.headers.map(h => ({ ...h })) : [] }
    }
    return { ...block }
  })
}

export function blocksToActionData(blocks) {
  if (!blocks || blocks.length === 0) return []
  return blocks.map(b => JSON.parse(JSON.stringify(b)))
}

export function newBlock(type) {
  switch (type) {
    case 'assign':
      return { type: 'assign', target: '', value: '', enableRounding: false, decimalPlaces: 2, roundingMode: 'HALF_UP' }
    case 'if-block':
      return { type: 'if-block', branches: [{ type: 'if', condVar: '', condVarType: '', condOp: '==', condValue: '', actions: [{ type: 'assign', target: '', value: '' }] }] }
    case 'switch-block':
      return { type: 'switch-block', matchVar: '', cases: [{ value: '', actions: [{ type: 'assign', target: '', value: '' }] }], defaultActions: [{ type: 'assign', target: '', value: '' }] }
    case 'func-call':
      return { type: 'func-call', target: '', funcName: '', args: [''] }
    case 'http-call':
      return { type: 'http-call', target: 'httpResult', method: 'GET', url: '', headers: [], bodyMode: 'none', body: '', connectTimeout: 3000, readTimeout: 5000 }
    case 'foreach':
      return { type: 'foreach', itemVar: 'item', listExpr: '', actions: [{ type: 'assign', target: '', value: '' }] }
    case 'ternary':
      return { type: 'ternary', target: '', condVar: '', condVarType: '', condOp: '==', condValue: '', trueValue: '', falseValue: '' }
    case 'in-check':
      return { type: 'in-check', target: '', checkVar: '', inValues: [], trueValue: 'true', falseValue: 'false' }
    case 'template-str':
      return { type: 'template-str', target: '', parts: [{ type: 'text', content: '' }] }
    default:
      return { type: 'assign', target: '', value: '' }
  }
}

export const BLOCK_TYPES = [
  { type: 'assign', label: '赋值', icon: 'el-icon-edit', color: '#1890ff' },
  { type: 'if-block', label: '条件分支', icon: 'el-icon-s-operation', color: '#fa8c16' },
  { type: 'switch-block', label: 'Switch 匹配', icon: 'el-icon-menu', color: '#722ed1' },
  { type: 'func-call', label: '函数调用', icon: 'el-icon-phone-outline', color: '#13c2c2' },
  { type: 'http-call', label: 'HTTP 调用', icon: 'el-icon-connection', color: '#0958d9' },
  { type: 'foreach', label: 'ForEach 循环', icon: 'el-icon-refresh', color: '#52c41a' },
  { type: 'ternary', label: '三元表达式', icon: 'el-icon-question', color: '#eb2f96' },
  { type: 'in-check', label: 'IN 判断', icon: 'el-icon-finished', color: '#2f54eb' },
  { type: 'template-str', label: '动态字符串', icon: 'el-icon-document', color: '#8c8c8c' }
]

/**
 * 从 actionData 中递归收集所有输出变量（被赋值的目标变量）
 * @param {Array} actionData 动作块数组
 * @returns {Set<string>} 输出变量名集合
 */
export function collectOutputVars(actionData) {
  const result = new Set()
  if (!Array.isArray(actionData)) return result

  function collectFromBlock(block) {
    if (!block || !block.type) return
    switch (block.type) {
      case 'assign':
      case 'func-call':
      case 'http-call':
      case 'ternary':
      case 'in-check':
      case 'template-str':
        if (block.target) result.add(block.target)
        break
      case 'if-block':
        if (Array.isArray(block.branches)) {
          block.branches.forEach(b => {
            if (Array.isArray(b.actions)) b.actions.forEach(collectFromBlock)
          })
        }
        break
      case 'switch-block':
        if (Array.isArray(block.cases)) {
          block.cases.forEach(c => {
            if (Array.isArray(c.actions)) c.actions.forEach(collectFromBlock)
          })
        }
        if (Array.isArray(block.defaultActions)) block.defaultActions.forEach(collectFromBlock)
        break
      case 'foreach':
        if (Array.isArray(block.actions)) block.actions.forEach(collectFromBlock)
        break
    }
  }

  actionData.forEach(collectFromBlock)
  return result
}

/**
 * 从决策树/决策流的 nodes 中收集所有输出变量
 * @param {Array} nodes 节点数组
 * @returns {Set<string>} 输出变量名集合
 */
export function collectOutputVarsFromNodes(nodes) {
  const result = new Set()
  if (!Array.isArray(nodes)) return result
  nodes.forEach(node => {
    if (node.type === 'task' && Array.isArray(node.actionData)) {
      const vars = collectOutputVars(node.actionData)
      vars.forEach(v => result.add(v))
    }
  })
  return result
}
