import request from './request'

/** 分页查询规则定义（POST + JSON） */
export function listDefinitions(data) {
  return request({ url: '/rule/definition/list', method: 'post', data })
}

export function getDefinition(id) {
  return request({ url: `/rule/definition/${id}`, method: 'get' })
}

/**
 * 查询单作用域设计内容
 * @param scopeCompId 作用域，默认 0 通用
 */
export function getContent(definitionId, scopeCompId) {
  return request({
    url: '/rule/definition/content/query',
    method: 'post',
    data: {
      definitionId,
      scopeCompId: scopeCompId != null && scopeCompId !== '' ? String(scopeCompId) : '0'
    }
  })
}

/** 某规则下全部作用域内容行 */
export function listContentScopes(definitionId) {
  return request({
    url: '/rule/definition/content/list',
    method: 'post',
    data: { definitionId }
  })
}

export function addContentScope(data) {
  return request({ url: '/rule/definition/content/scope', method: 'post', data })
}

export function deleteContentScope(definitionId, scopeCompId) {
  return request({
    url: '/rule/definition/content/scope/remove',
    method: 'post',
    data: { definitionId, scopeCompId: String(scopeCompId) }
  })
}

export function createDefinition(data) {
  return request({ url: '/rule/definition', method: 'post', data })
}

export function updateDefinition(data) {
  return request({ url: '/rule/definition', method: 'put', data })
}

export function deleteDefinition(id) {
  return request({ url: `/rule/definition/${id}`, method: 'delete' })
}

/** 按省份删除规则内容（已发布状态不允许删除） */
export function deleteDefinitionScope(definitionId, scopeCompId) {
  return request({
    url: '/rule/definition/content/delete',
    method: 'post',
    data: { definitionId, scopeCompId: String(scopeCompId) }
  })
}

/**
 * 保存设计内容；可选 changeLog（版本说明）、recordHistory（默认 true，编译前隐式保存传 false）
 */
export function saveContent(data) {
  return request({ url: '/rule/definition/save', method: 'post', data })
}

/**
 * 分页查询设计保存快照列表（无 model_json）
 */
export function listDesignSnapshots(data) {
  return request({ url: '/rule/definition/content/snapshot/list', method: 'post', data })
}

/**
 * 获取单条快照的 modelJson
 */
export function getDesignSnapshot(id, data) {
  return request({
    url: '/rule/definition/content/snapshot/get',
    method: 'post',
    data: { id, ...data }
  })
}

/**
 * @param scopeCompId 作用域
 */
export function compileRule(id, scopeCompId) {
  return request({
    url: `/rule/definition/compile/${id}`,
    method: 'post',
    data: {
      scopeCompId: scopeCompId != null && scopeCompId !== '' ? String(scopeCompId) : '0'
    }
  })
}

export function publishRule(id, data) {
  return request({ url: `/rule/definition/publish/${id}`, method: 'post', data })
}

export function unpublishRule(id, scopeCompId) {
  return request({
    url: '/rule/definition/unpublish',
    method: 'post',
    data: { definitionId: id, scopeCompId: scopeCompId || null }
  })
}

export function executeRule(data) {
  return request({ url: '/rule/definition/execute', method: 'post', data })
}

/** 技术人员直接保存脚本（脚本模式） */
export function saveScript(definitionId, script, scopeCompId) {
  return request({
    url: `/rule/definition/script/${definitionId}`,
    method: 'post',
    data: {
      script,
      scopeCompId: scopeCompId != null && scopeCompId !== '' ? String(scopeCompId) : '0'
    }
  })
}

/** 更新编辑模式（visual/script） */
export function updateScriptMode(definitionId, scriptMode, scopeCompId) {
  return request({
    url: `/rule/definition/scriptMode/${definitionId}`,
    method: 'post',
    data: {
      scriptMode,
      scopeCompId: scopeCompId != null && scopeCompId !== '' ? String(scopeCompId) : '0'
    }
  })
}

export function validateScript(definitionId, script) {
  return request({ url: `/rule/definition/validateScript/${definitionId}`, method: 'post', data: { script }})
}

/** 复制规则到目标省份（作用域） */
export function copyDefinitionToScope(data) {
  return request({ url: '/rule/definition/copy', method: 'post', data })
}

/** 修改规则内容的省份归属和说明 */
export function updateContentMeta(data) {
  return request({ url: '/rule/definition/content/updateMeta', method: 'post', data })
}
