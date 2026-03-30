/**
 * actionData JSON → QLExpress 脚本生成器
 *
 * 块类型一览：
 *   assign:       赋值          { type:'assign', target, value }
 *   if-block:     条件分支      { type:'if-block', branches:[{type,condVar,condOp,condValue,actions}] }
 *   switch-block: switch匹配    { type:'switch-block', matchVar, cases:[{value,actions}], defaultActions:[] }
 *   func-call:    函数调用      { type:'func-call', target, funcName, args:[] }
 *   foreach:      for-each循环  { type:'foreach', itemVar, listExpr, actions:[] }
 *   ternary:      三元表达式    { type:'ternary', target, condVar, condOp, condValue, trueValue, falseValue }
 *   in-check:     in判断赋值    { type:'in-check', target, checkVar, inValues:[], trueValue, falseValue }
 *   template-str: 动态字符串    { type:'template-str', target, parts:[{type:'text'|'expr', content}] }
 */

function wrapValue(val) {
  if (val === null || val === undefined || val === '') return '""'
  const s = String(val).trim()
  if (s === 'true' || s === 'false' || s === 'null') return s
  if (!isNaN(s) && s !== '') return s
  if (/^[a-zA-Z_]\w*(\.\w+)*$/.test(s)) return s
  if (s.startsWith('"') || s.startsWith("'")) return s
  if (/[+\-*/()><=!&|,\[\]{}]/.test(s)) return s
  return '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
}

function generateBlock(block, indent) {
  const pad = '    '.repeat(indent)
  if (!block || !block.type) return ''

  switch (block.type) {
    case 'assign':
      return (!block.target || !block.value) ? '' : pad + block.target + ' = ' + block.value

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
      const cond = block.condVar + ' ' + (block.condOp || '==') + ' ' + wrapValue(block.condValue)
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

function buildCondExpr(branch) {
  if (!branch.condVar) return 'true'
  return branch.condVar + ' ' + (branch.condOp || '==') + ' ' + wrapValue(branch.condValue)
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
      return { type: 'assign', target: '', value: '' }
    case 'if-block':
      return { type: 'if-block', branches: [{ type: 'if', condVar: '', condOp: '==', condValue: '', actions: [{ type: 'assign', target: '', value: '' }] }] }
    case 'switch-block':
      return { type: 'switch-block', matchVar: '', cases: [{ value: '', actions: [{ type: 'assign', target: '', value: '' }] }], defaultActions: [{ type: 'assign', target: '', value: '' }] }
    case 'func-call':
      return { type: 'func-call', target: '', funcName: '', args: [''] }
    case 'foreach':
      return { type: 'foreach', itemVar: 'item', listExpr: '', actions: [{ type: 'assign', target: '', value: '' }] }
    case 'ternary':
      return { type: 'ternary', target: '', condVar: '', condOp: '==', condValue: '', trueValue: '', falseValue: '' }
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
  { type: 'foreach', label: 'ForEach 循环', icon: 'el-icon-refresh', color: '#52c41a' },
  { type: 'ternary', label: '三元表达式', icon: 'el-icon-question', color: '#eb2f96' },
  { type: 'in-check', label: 'IN 判断', icon: 'el-icon-finished', color: '#2f54eb' },
  { type: 'template-str', label: '动态字符串', icon: 'el-icon-document', color: '#8c8c8c' }
]
