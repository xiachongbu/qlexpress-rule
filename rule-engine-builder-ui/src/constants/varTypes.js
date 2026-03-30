/**
 * 变量数据类型：与变量管理「新建/编辑变量」表单保持一致，供全站下拉与展示复用。
 */

/** 表单、设计器配置等：中文名 + 英文枚举（与 VariableList 弹窗一致） */
export const VAR_TYPE_FORM_OPTIONS = [
  { label: '字符串 (STRING)', value: 'STRING' },
  { label: '数值 (NUMBER)', value: 'NUMBER' },
  { label: '布尔 (BOOLEAN)', value: 'BOOLEAN' },
  { label: '日期 (DATE)', value: 'DATE' },
  { label: '枚举 (ENUM)', value: 'ENUM' },
  { label: '对象 (OBJECT)', value: 'OBJECT' },
  { label: '列表 (LIST)', value: 'LIST' },
  { label: '映射 (MAP)', value: 'MAP' }
]

/** 列表/常量 Tab 筛选等窄位：仅中文简称，value 与库表一致 */
export const VAR_TYPE_FILTER_OPTIONS = [
  { label: '字符串', value: 'STRING' },
  { label: '数值', value: 'NUMBER' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: '日期', value: 'DATE' },
  { label: '枚举', value: 'ENUM' },
  { label: '对象', value: 'OBJECT' },
  { label: '列表', value: 'LIST' },
  { label: '映射', value: 'MAP' }
]

const LABELS = {
  STRING: '字符串',
  NUMBER: '数值',
  BOOLEAN: '布尔',
  DATE: '日期',
  ENUM: '枚举',
  OBJECT: '对象',
  LIST: '列表',
  MAP: '映射'
}

/** 与 VariableList 表格中 el-tag 配色一致 */
const TAG_COLORS = {
  STRING: '',
  NUMBER: 'warning',
  BOOLEAN: 'success',
  DATE: 'info',
  ENUM: 'danger',
  OBJECT: '',
  LIST: 'warning',
  MAP: 'info'
}

/**
 * 将 varType 枚举转为中文简称（表头、标签等）。
 */
export function varTypeLabel(t) {
  return LABELS[t] || t
}

/**
 * 将 varType 转为 Element UI el-tag 的 type 属性值。
 */
export function varTypeTagColor(t) {
  return TAG_COLORS[t] || ''
}
