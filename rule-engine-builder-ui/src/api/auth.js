import request from './request'

/**
 * 是否启用控制台用户名密码登录（与后端 rule-engine.console-login.enabled 一致）。
 */
export function getConsoleAuthConfig() {
  return request.get('/auth/console/config')
}

/**
 * 提交用户名密码建立会话。
 */
export function consoleLogin(data) {
  return request.post('/auth/console/login', data)
}

/**
 * 注销当前会话。
 */
export function consoleLogout() {
  return request.post('/auth/console/logout')
}

/**
 * 查询当前登录用户；未登录时请求会失败（401）。
 */
export function getConsoleMe() {
  return request.get('/auth/console/me')
}
