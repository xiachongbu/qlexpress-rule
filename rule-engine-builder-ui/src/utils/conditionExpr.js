/**
 * 与后端 QlCompareExpression 对齐：前端设计器生成/解析 QLExpress 比较表达式。
 */

/** 二元运算符（长的在前，避免切分错误） */
const BINARY_OPS = ['>=', '<=', '!=', '==', '>', '<', 'in']

/**
 * 根据输入推断常量类型，用于字面量格式化。
 */
export function inferConstVarType(raw) {
  if (raw == null || raw === '') return 'STRING'
  const s = String(raw).trim()
  if (s === 'true' || s === 'false') return 'BOOLEAN'
  if (!isNaN(Number(s)) && s !== '' && !/^0\d/.test(s)) return 'NUMBER'
  return 'STRING'
}

/**
 * 将变量元数据类型映射为常量格式化类型，与后端 QlCompareExpression.formatConstantRhs
 * 对齐：仅 STRING/ENUM/DATE 加引号，其余（NUMBER/BOOLEAN 等）保持原值。
 * 未知类型返回空串，由调用方回退到 inferConstVarType 启发式判断。
 */
export function constTypeFromVarType(varType) {
  if (!varType) return ''
  return ['STRING', 'ENUM', 'DATE'].indexOf(varType) >= 0 ? 'STRING' : 'NUMBER'
}

/**
 * 双引号字符串内的转义（与 Java 字面量一致）。
 */
function escapeQlDoubleQuotedSegment(s) {
  return String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

/**
 * 将常量格式化为 QL/Java 字面量。
 */
export function formatQlConstant(raw, varType) {
  const t = varType || 'STRING'
  if (t === 'NUMBER' || t === 'BOOLEAN') {
    return String(raw).trim()
  }
  return '"' + escapeQlDoubleQuotedSegment(raw) + '"'
}

/**
 * 生成单条条件表达式（决策树边、决策节点、评分卡等共用）。
 *
 * @param {string} leftVar - 左侧变量
 * @param {string} operator - 运算符
 * @param {string} rightRaw - 右侧原始值（常量未加引号）
 * @param {'value'|'var'} rightMode
 * @param {string} [constVarType] - 常量模式下的类型
 * @returns {string|null}
 */
export function buildQlConditionExpr(leftVar, operator, rightRaw, rightMode, constVarType) {
  if (!leftVar) return null
  if (rightRaw == null || rightRaw === '') return null
  const op = operator || '=='

  if (op === 'contains' || op === 'startsWith' || op === 'endsWith') {
    if (rightMode === 'var') {
      const x = String(rightRaw).trim()
      if (op === 'contains') {
        return '(' + leftVar + ' != null && ' + leftVar + '.toString().contains(String.valueOf(' + x + ')))'
      }
      if (op === 'startsWith') {
        return '(' + leftVar + ' != null && ' + leftVar + '.toString().startsWith(String.valueOf(' + x + ')))'
      }
      return '(' + leftVar + ' != null && ' + leftVar + '.toString().endsWith(String.valueOf(' + x + ')))'
    }
    const strLit = '"' + escapeQlDoubleQuotedSegment(String(rightRaw)) + '"'
    if (op === 'contains') {
      return '(' + leftVar + ' != null && ' + leftVar + '.toString().contains(' + strLit + '))'
    }
    if (op === 'startsWith') {
      return '(' + leftVar + ' != null && ' + leftVar + '.toString().startsWith(' + strLit + '))'
    }
    return '(' + leftVar + ' != null && ' + leftVar + '.toString().endsWith(' + strLit + '))'
  }

  if (rightMode === 'var') {
    return leftVar + ' ' + op + ' ' + String(rightRaw).trim()
  }
  const vt = constVarType || inferConstVarType(rightRaw)
  const rhs = formatQlConstant(rightRaw, vt)
  return leftVar + ' ' + op + ' ' + rhs
}

/**
 * 从表达式反解可视化字段（与 buildQlConditionExpr 可逆的部分）。
 *
 * @returns {{ leftVar: string, leftLabel: string, operator: string, rightValue: string, rightType: 'value'|'var', rightVar: string, rightConstType?: string }|null}
 */
export function parseQlConditionExpr(expr) {
  if (!expr || typeof expr !== 'string') return null
  const t = expr.trim()
  if (!t) return null

  const method = /^\(([\w.$]+)\s*!=\s*null\s*&&\s*\1\.toString\(\)\.(contains|startsWith|endsWith)\(([^)]+)\)\)$/.exec(t)
  if (method) {
    const left = method[1]
    const op = method[2]
    const inner = method[3].trim()
    const vo = /^String\.valueOf\(([\w.$]+)\)$/.exec(inner)
    if (vo) {
      return { leftVar: left, leftLabel: '', operator: op, rightValue: vo[1], rightType: 'var', rightVar: vo[1] }
    }
    const dq = /^"((?:\\.|[^"\\])*)"$/.exec(inner)
    if (dq) {
      const unesc = dq[1].replace(/\\"/g, '"').replace(/\\\\/g, '\\')
      return { leftVar: left, leftLabel: '', operator: op, rightValue: unesc, rightType: 'value', rightVar: '', rightConstType: 'STRING' }
    }
    return null
  }

  for (let i = 0; i < BINARY_OPS.length; i++) {
    const op = BINARY_OPS[i]
    const needle = ' ' + op + ' '
    const idx = t.indexOf(needle)
    if (idx < 0) continue
    const left = t.substring(0, idx).trim()
    const right = t.substring(idx + needle.length).trim()
    if (!/^[\w.$]+$/.test(left)) continue

    if (op === 'in') {
      const isVar = /^[\w.$]+$/.test(right) && !right.startsWith('[')
      return {
        leftVar: left,
        leftLabel: '',
        operator: op,
        rightValue: right,
        rightType: isVar ? 'var' : 'value',
        rightVar: isVar ? right : ''
      }
    }

    if (right.startsWith('"') && right.endsWith('"')) {
      const body = right.slice(1, -1).replace(/\\"/g, '"').replace(/\\\\/g, '\\')
      return { leftVar: left, leftLabel: '', operator: op, rightValue: body, rightType: 'value', rightVar: '', rightConstType: 'STRING' }
    }
    if (/^[\w.$]+$/.test(right)) {
      return { leftVar: left, leftLabel: '', operator: op, rightValue: right, rightType: 'var', rightVar: right }
    }
    if (!isNaN(Number(right)) && right !== '') {
      return { leftVar: left, leftLabel: '', operator: op, rightValue: right, rightType: 'value', rightVar: '', rightConstType: 'NUMBER' }
    }
    return { leftVar: left, leftLabel: '', operator: op, rightValue: right, rightType: 'value', rightVar: '' }
  }
  return null
}
