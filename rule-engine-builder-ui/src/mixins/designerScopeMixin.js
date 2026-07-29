import { mapState } from 'vuex'
import { addContentScope, listContentScopes } from '@/api/definition'
import { COMP_SCOPE_OPTIONS } from '@/constants/compScopeOptions'
import { DICT_TYPE_COMP_SCOPE } from '@/constants/dictTypes'

/**
 * 设计器共用：作用域 compId、与后端 content/scope 同步
 */
export default {
  data() {
    return {
      scopeCompId: '0',
      scopeContentList: [],
      /** 切换作用域拉取内容时为 true，用于 v-loading，避免把整块设计区 v-if 卸掉导致重挂载卡顿 */
      scopeContentLoading: false
    }
  },
  computed: {
    ...mapState('ruleDict', {
      _dictCompScope(state) {
        const arr = state.byType[DICT_TYPE_COMP_SCOPE]
        return Array.isArray(arr) ? arr : []
      }
    }),
    /**
     * 优先使用字典接口 + 本地缓存；库中无数据时回退到前端静态列表。
     */
    compScopeOptions() {
      if (this._dictCompScope.length > 0) return this._dictCompScope
      return COMP_SCOPE_OPTIONS
    },
    /** 当前选中的作用域是否已有内容行 */
    scopeRowConfigured() {
      const sid = String(this.scopeCompId)
      return (this.scopeContentList || []).some(c => String(c.scopeCompId) === sid)
    }
  },
  methods: {
    /**
     * 脚本覆盖模式下拦截可视化“保存/编译”：
     * 这两个操作会写入 modelJson 并把 compileStatus 置回 0（或用可视化编译结果覆盖手写脚本），
     * 导致“保存即生效、可直接发布”的脚本模式行为被破坏。
     * 依赖宿主设计器 data 中的 scriptMode 字段（'visual' | 'script'）。
     */
    ensureVisualEditable() {
      if (this.scriptMode === 'script') {
        this.$message.warning('当前为脚本覆盖模式，请在脚本面板中「保存脚本」（保存即生效，可直接发布）；如需可视化编辑请先退出脚本模式')
        return false
      }
      return true
    },
    /**
     * 从抽屉 store 或路由 query 初始化 scopeCompId（新建规则打开抽屉时带 scopeCompId）
     * 抽屉内编辑规则时 OPEN 往往传 query:{}，若仍读取主页面 URL 上残留的 scopeCompId，会拉错作用域内容导致设计无法反显。
     */
    initDesignerScopeFromRoute() {
      let q
      try {
        const dq = this.$store.state.designerDrawer && this.$store.state.designerDrawer.routeQuery
        if (dq && dq.scopeCompId !== undefined && dq.scopeCompId !== null && String(dq.scopeCompId).trim() !== '') {
          q = dq.scopeCompId
        }
      } catch (e) {
        /* 无 store 时忽略 */
      }
      const inDrawer = this.$store.state.designerDrawer && this.$store.state.designerDrawer.visible
      if (q === undefined && !inDrawer) {
        q = this.$route.query.scopeCompId
      }
      if (q === undefined && inDrawer) {
        q = '0'
      }
      this.scopeCompId =
        q !== undefined && q !== null && String(q).trim() !== '' ? String(q).trim() : '0'
    },
    /**
     * 拉取当前规则下已存在的作用域行
     */
    async refreshScopeContentList() {
      if (!this.definitionId) return
      try {
        const res = await listContentScopes(this.definitionId)
        const body = res && res.data !== undefined ? res.data : res
        this.scopeContentList = Array.isArray(body) ? body : []
      } catch (e) {
        this.scopeContentList = []
      }
    },
    /**
     * 非通用作用域若无内容行则先创建空行；校验返回值，失败时重试一次。
     */
    async ensureScopeRowExists(scope) {
      if (!this.definitionId || String(scope) === '0') return
      await this.refreshScopeContentList()
      const has = (this.scopeContentList || []).some(c => String(c.scopeCompId) === String(scope))
      if (!has) {
        let res = await addContentScope({ definitionId: this.definitionId, scopeCompId: String(scope) })
        // HTTP 拦截器在业务异常时返回 false，网络异常时返回 undefined；检测并重试一次
        if (!res) {
          console.warn('[designerScopeMixin] addContentScope 首次调用失败，正在重试…')
          res = await addContentScope({ definitionId: this.definitionId, scopeCompId: String(scope) })
          if (!res) {
            console.error('[designerScopeMixin] addContentScope 重试仍失败，scope=' + scope)
          }
        }
      }
    },
    /**
     * 下拉切换作用域：同步路由、确保库中有行、重载模型与脚本面板。
     * 使用 scopeContentLoading + 父级 v-loading 遮罩，不再把 contentLoaded 置 false，避免大组件树销毁/重建造成的明显卡顿。
     */
    async onDesignerScopeInput(val) {
      const v = String(val != null ? val : '0')
      this.scopeCompId = v
      this.$router.replace({ path: this.$route.path, query: { ...this.$route.query, scopeCompId: v }}).catch(() => {})
      this.scopeContentLoading = true
      try {
        await this.ensureScopeRowExists(v)
        await this.refreshScopeContentList()
        await this.loadContent()
      } finally {
        this.scopeContentLoading = false
      }
      this.$nextTick(() => {
        if (this.$refs.scriptPanel && typeof this.$refs.scriptPanel.reloadForScope === 'function') {
          this.$refs.scriptPanel.reloadForScope()
        }
      })
    },
    /**
     * 进入设计器：初始化 query、补全库表行、加载内容
     */
    async bootstrapDesignerWithScope() {
      this.initDesignerScopeFromRoute()
      await this.refreshScopeContentList()
      await this.ensureScopeRowExists(this.scopeCompId)
      await this.refreshScopeContentList()
      await this.loadContent()
    }
  }
}
