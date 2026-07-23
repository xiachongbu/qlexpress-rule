import {listDictItems} from '@/api/dict'
import {DICT_TYPE_COMP_SCOPE} from '@/constants/dictTypes'

const STORAGE_PREFIX = 'rule_engine_dict_'
const DEFAULT_TTL_MS = 30 * 60 * 1000

/**
 * sessionStorage 键名
 */
function storageKey(type) {
  return STORAGE_PREFIX + type
}

/**
 * 读取未过期的本地缓存
 */
function readCache(type, ttlMs) {
  try {
    const raw = sessionStorage.getItem(storageKey(type))
    if (!raw) return null
    const o = JSON.parse(raw)
    if (!o || typeof o.t !== 'number' || !Array.isArray(o.data)) return null
    if (Date.now() - o.t > ttlMs) return null
    return o.data
  } catch (e) {
    return null
  }
}

/**
 * 写入本地缓存
 */
function writeCache(type, items) {
  try {
    sessionStorage.setItem(storageKey(type), JSON.stringify({ t: Date.now(), data: items }))
  } catch (e) {
    /* 配额或其它异常忽略 */
  }
}

export default {
  namespaced: true,
  state: {
    byType: {},
    ttlMs: DEFAULT_TTL_MS
  },
  mutations: {
    SET_ITEMS(state, { type, items }) {
      state.byType = { ...state.byType, [type]: items }
    }
  },
  actions: {
    /**
     * 确保指定类型已加载：Vuex → sessionStorage → 接口。
     */
    async ensureType({ commit, state }, type) {
      if (!type) return
      const mem = state.byType[type]
      if (mem && mem.length) return

      const cached = readCache(type, state.ttlMs)
      if (cached && cached.length) {
        commit('SET_ITEMS', { type, items: cached })
        return
      }

      const res = await listDictItems(type)
      const body = res && res.data !== undefined ? res.data : res
      const rows = Array.isArray(body) ? body : []
      const items = rows
        .map(r => ({
          value: String(r.dictCode != null ? r.dictCode : ''),
          label: r.dictLabel || String(r.dictCode),
          sortOrder: r.sortOrder
        }))
        .filter(x => x.value !== '')
      commit('SET_ITEMS', { type, items })
      writeCache(type, items)
    },

    /**
     * 启动时预加载常用字典（当前含作用域 compId）。
     */
    async init({ dispatch }) {
      await dispatch('ensureType', DICT_TYPE_COMP_SCOPE)
    }
  }
}
