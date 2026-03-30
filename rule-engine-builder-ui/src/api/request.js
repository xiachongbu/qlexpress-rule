import axios from 'axios'
import { Message } from 'element-ui'
import router from '@/router'

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true
})

service.interceptors.response.use(
  response => {
    const res = response.data
    const reqUrl = (response.config && response.config.url) || ''
    if (res.code === 401) {
      if (reqUrl.includes('/auth/console/login')) {
        Message.error(res.message || '登录失败')
        return Promise.reject(new Error(res.message || '登录失败'))
      }
      if (router.currentRoute.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: router.currentRoute.fullPath } })
      }
      Message.error(res.message || '未登录')
      return Promise.reject(new Error(res.message || '未登录'))
    }
    if (res.code !== 200) {
      Message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    const status = error.response && error.response.status
    const url = (error.config && error.config.url) || ''
    const data = error.response && error.response.data
    const msg = (data && data.message) || error.message || '网络异常'
    if (status === 401 && !url.includes('/auth/console/login')) {
      if (router.currentRoute.path !== '/login') {
        router.replace({ path: '/login', query: { redirect: router.currentRoute.fullPath } })
      }
    }
    Message.error(msg)
    return Promise.reject(error)
  }
)

export default service
