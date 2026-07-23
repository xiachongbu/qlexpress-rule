/**
 * 规则作用域 compId 与展示名称（与客户端 execute(..., compId) 传入值一致）
 * value 使用字符串，与后端 scope_comp_id / comp_id 存储一致。
 * 管理端优先从 rule_sys_dict（dict_type=COMP_SCOPE）拉取并缓存；本列表仅作离线/接口失败兜底。
 */
export const COMP_SCOPE_OPTIONS = [
  { value: '0', label: '通用' },
  { value: '10', label: '集团' },
  { value: '11', label: '北京' },
  { value: '12', label: '天津' },
  { value: '13', label: '河北省' },
  { value: '14', label: '山西省' },
  { value: '15', label: '内蒙古自治区' },
  { value: '21', label: '辽宁省' },
  { value: '22', label: '吉林省' },
  { value: '23', label: '黑龙江省' },
  { value: '31', label: '上海市' },
  { value: '32', label: '江苏省' },
  { value: '33', label: '浙江省' },
  { value: '34', label: '安徽省' },
  { value: '35', label: '福建省' },
  { value: '36', label: '江西省' },
  { value: '37', label: '山东省' },
  { value: '41', label: '河南省' },
  { value: '42', label: '湖北省' },
  { value: '43', label: '湖南省' },
  { value: '44', label: '广东省' },
  { value: '45', label: '广西壮族自治区' },
  { value: '46', label: '海南省' },
  { value: '50', label: '重庆市' },
  { value: '51', label: '四川省' },
  { value: '52', label: '贵州省' },
  { value: '53', label: '云南省' },
  { value: '54', label: '西藏自治区' },
  { value: '61', label: '陕西省' },
  { value: '62', label: '甘肃省' },
  { value: '63', label: '青海省' },
  { value: '64', label: '宁夏回族自治区' },
  { value: '65', label: '新疆维吾尔自治区' }
]
