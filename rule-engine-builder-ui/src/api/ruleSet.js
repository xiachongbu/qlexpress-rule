import request from './request'

/** 规则集分页列表（POST + JSON） */
export function listRuleSets(data) {
  return request({ url: '/rule/set/list', method: 'post', data })
}

export function getRuleSet(id) {
  return request({ url: `/rule/set/${id}`, method: 'get' })
}

export function createRuleSet(data) {
  return request({ url: '/rule/set', method: 'post', data })
}

export function updateRuleSet(data) {
  return request({ url: '/rule/set', method: 'put', data })
}

export function deleteRuleSet(id) {
  return request({ url: `/rule/set/${id}`, method: 'delete' })
}

/** 全量保存成员顺序 */
export function saveRuleSetMembers(data) {
  return request({ url: '/rule/set/members/save', method: 'post', data })
}

/** 有序成员规则列表 */
export function listRuleSetMembers(data) {
  return request({ url: '/rule/set/members/list', method: 'post', data })
}

/** 发布规则集；body 可含 scopeCompIds，省略则由服务端取交集 */
export function publishRuleSet(setId, data) {
  return request({ url: `/rule/set/publish/${setId}`, method: 'post', data: data || {}})
}

export function unpublishRuleSet(setId) {
  return request({ url: `/rule/set/unpublish/${setId}`, method: 'post', data: {}})
}

/** 链式试跑 */
export function executeRuleSet(data) {
  return request({ url: '/rule/set/execute', method: 'post', data })
}
