import request from './request'

export function listProjects(params) {
  return request({ url: '/rule/project/list', method: 'get', params })
}

export function getProject(id) {
  return request({ url: `/rule/project/${id}`, method: 'get' })
}

export function createProject(data) {
  return request({ url: '/rule/project', method: 'post', data })
}

export function updateProject(data) {
  return request({ url: '/rule/project', method: 'put', data })
}

export function deleteProject(id) {
  return request({ url: `/rule/project/${id}`, method: 'delete' })
}

export function getMaskedToken(id) {
  return request({ url: `/rule/project/${id}/token/masked`, method: 'get' })
}
