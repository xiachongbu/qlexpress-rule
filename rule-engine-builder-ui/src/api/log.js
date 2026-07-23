import request from './request'

/** 执行日志分页查询（POST + JSON） */
export function listExecutionLogs(data) {
  return request({ url: '/rule/log/list', method: 'post', data })
}

/** 根据 ID 获取执行日志完整详情（含 inputParams、outputResult、traceInfo） */
export function getExecutionLogDetail(id) {
  return request({ url: '/rule/log/detail/' + id, method: 'get' })
}
