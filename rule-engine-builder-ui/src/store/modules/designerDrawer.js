/**
 * 从项目详情 / 规则管理等页打开设计器时，在 Layout 右侧 92% 抽屉中展示，不跳转全屏路由。
 */
export default {
  namespaced: true,
  state: {
    visible: false,
    /** 与路由段一致：table、tree、flow、cross、score、cross-adv、score-adv、script */
    segment: '',
    definitionId: null,
    /** 打开时合并到作用域初始化（如新建规则后的 scopeCompId） */
    routeQuery: {}
  },
  mutations: {
    /**
     * 打开设计器抽屉并传入定义 ID 与可选 query。
     */
    OPEN(state, { segment, definitionId, query }) {
      state.visible = true
      state.segment = segment
      state.definitionId = definitionId
      state.routeQuery = query && typeof query === 'object' ? { ...query } : {}
    },
    /**
     * 关闭抽屉并清空状态。
     */
    CLOSE(state) {
      state.visible = false
      state.segment = ''
      state.definitionId = null
      state.routeQuery = {}
    }
  }
}
