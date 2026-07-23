import request from './request'

/** 分页查询项目下函数（POST + JSON） */
export function listFunctionsByProject(projectId, data) {
  return request({ url: '/rule/function/project/' + projectId + '/list', method: 'post', data: data || {}})
}

/** 查询项目下全部启用函数（非分页，设计器变量面板使用） */
export function listAllFunctionsByProject(projectId) {
  return request({ url: '/rule/function/project/all', method: 'post', data: { projectId }})
}

export function getFunctionById(id) {
  return request({ url: '/rule/function/' + id, method: 'get' })
}

export function createFunction(data) {
  return request({ url: '/rule/function', method: 'post', data })
}

export function updateFunction(data) {
  return request({ url: '/rule/function', method: 'put', data })
}

export function deleteFunction(id) {
  return request({ url: '/rule/function/' + id, method: 'delete' })
}
