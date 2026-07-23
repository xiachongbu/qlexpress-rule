import request from './request'

/**
 * 按类型拉取字典项（POST + JSON）
 * @param {string} type 字典类型
 */
export function listDictItems(type) {
  return request({ url: '/rule/dict/items', method: 'post', data: { type }})
}
