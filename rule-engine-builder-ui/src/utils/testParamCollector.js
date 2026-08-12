/**
 * 测试入参收集：从设计器的“设计源”（模型 JSON 或脚本文本）中扫描出被引用的输入变量，
 * 供各设计器测试弹窗自动带出条件变量并生成默认值表单。
 *
 * 与决策表 collectVarCodesFromConditionTree 的结构化收集不同，这里面向条件以 QL 表达式
 * / 脚本文本存储的设计器（决策树、决策流、评分卡、脚本等），采用整词匹配的通用方式。
 */

/** 正则元字符转义 */
function escapeRegExp(s) {
  return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * 判断一个 projectRefs 引用项是否为“可作为测试入参”的变量。
 * 常量与派生（COMPUTED）变量不需要用户输入，予以排除。
 * @param {Object} ref - projectRefs 中的引用项
 * @returns {boolean}
 */
export function isTestInputRef(ref) {
  if (!ref) return false
  if (ref.category === 'constant') return false
  const src = ref.varObj && ref.varObj.varSource
  if (src === 'CONSTANT' || src === 'COMPUTED') return false
  return true
}

/**
 * 从设计源文本中扫描出被引用到的变量编码（整词匹配，保持 refs 原有顺序、去重）。
 *
 * 匹配规则：编码前不能紧跟标识符字符或点号（避免作为其它标识符的后半段或字段名被误取），
 * 编码后不能紧跟标识符字符（允许紧跟点号，兼容 `code.toString()` 之类的方法/字段访问）。
 *
 * @param {string} sourceText - 序列化后的模型 JSON 或脚本文本
 * @param {Array} refs - 候选引用项列表（通常为经 isTestInputRef 过滤后的 projectRefs）
 * @returns {string[]} 被引用的变量编码列表
 */
export function collectReferencedVarCodes(sourceText, refs) {
  const src = sourceText == null ? '' : String(sourceText)
  const out = []
  if (!src || !Array.isArray(refs)) return out
  const seen = new Set()
  refs.forEach(ref => {
    const code = ref && ref.refCode
    if (!code || seen.has(code)) return
    const re = new RegExp('(^|[^\\w$.])' + escapeRegExp(code) + '(?![\\w$])')
    if (re.test(src)) {
      out.push(code)
      seen.add(code)
    }
  })
  return out
}
