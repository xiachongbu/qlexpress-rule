/**
 * 输出变量初始值配置 mixin
 * 提供 data / methods，宿主组件须实现 allOutputVars computed（返回 Set<string>）
 */
export default {
  data() {
    return {
      outputVarInits: [],
      outputVarInitDialogVisible: false
    }
  },
  methods: {
    openOutputVarInitDialog() {
      this.outputVarInitDialogVisible = true
    },
    /**
     * 从 modelJson 解析对象中提取 outputVarInits 并赋值到组件状态，
     * 同时从 parsed 中移除该字段避免冗余存储。
     */
    loadOutputVarInits(parsed) {
      this.outputVarInits = Array.isArray(parsed.outputVarInits)
        ? JSON.parse(JSON.stringify(parsed.outputVarInits))
        : []
      delete parsed.outputVarInits
    },
    /**
     * 将有效的 outputVarInits 合并到待保存的 model 对象上
     */
    mergeOutputVarInitsToModel(model) {
      const validInits = (this.outputVarInits || []).filter(
        item => item && item.varCode && item.initValue !== null && item.initValue !== undefined && String(item.initValue).trim() !== ''
      )
      if (validInits.length > 0) {
        model.outputVarInits = validInits.map(item => ({ varCode: item.varCode, initValue: item.initValue }))
      }
      return model
    }
  }
}
