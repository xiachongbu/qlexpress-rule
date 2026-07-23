/**
 * 设计器可在全页路由 /designer/:type/:id 或 Layout 抽屉内打开；
 * 抽屉模式通过 definitionIdProp 传入定义 ID，避免误用外层路由 params（如 /project/:id）。
 */
export default {
  props: {
    definitionIdProp: { type: [String, Number], default: null }
  },
  methods: {
    /**
     * 优先 props，否则使用当前路由中的规则定义 id。
     */
    resolveDefinitionIdFromContext() {
      const p = this.definitionIdProp
      if (p != null && p !== '') return p
      const id = this.$route && this.$route.params && this.$route.params.id
      return id
    }
  }
}
