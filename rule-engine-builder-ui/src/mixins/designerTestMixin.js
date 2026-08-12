/**
 * designerTestMixin
 * 为各设计器提供统一的“测试执行”能力：自动带出规则中引用的输入变量、按类型生成默认值表单，
 * 并支持表单 / JSON 两种入参编辑模式。与 TestExecuteDialog.vue 搭配使用。
 *
 * 依赖：
 *   - varPickerMixin 提供的 projectRefs / getVarOptions
 *   - 组件自身的 definitionId / scopeCompId
 *
 * 可覆盖钩子：
 *   - getTestSourceText()：返回用于扫描变量引用的“设计源”文本，默认序列化 this.model；
 *     图类设计器可返回 buildBackendModel() 的序列化结果，脚本设计器返回脚本文本。
 *   - collectTestVarCodes()：返回测试需录入的变量编码列表，默认按 getTestSourceText 扫描；
 *     结构化条件的设计器（如决策表）可覆盖为精确收集。
 */

import {executeRule} from '@/api/definition'
import {collectReferencedVarCodes, isTestInputRef} from '@/utils/testParamCollector'

export default {
  data() {
    return {
      testVisible: false,
      /** 入参编辑模式：form 表单 / json 文本 */
      testMode: 'form',
      /** 表单模式入参对象 */
      testParams: {},
      /** JSON 模式入参文本 */
      testParamsJson: '{}',
      testResult: null
    }
  },

  computed: {
    /** 可作为测试入参的候选引用（排除常量与派生变量） */
    testCandidateRefs() {
      return (this.projectRefs || []).filter(isTestInputRef)
    },

    /** 测试需录入的变量编码列表 */
    testVarCodeList() {
      return this.collectTestVarCodes()
    },

    /** 供 TestExecuteDialog 渲染的字段描述列表 */
    testFields() {
      return this.testVarCodeList.map(code => {
        const meta = this.testVarMeta(code)
        return {
          code,
          label: this.testVarLabel(code),
          varType: meta.varType,
          enumOptions: meta.enumOptions
            ? meta.enumOptions.split(',').map(s => s.trim()).filter(Boolean)
            : []
        }
      })
    }
  },

  watch: {
    /** 切换编辑模式时在表单对象与 JSON 文本之间同步入参 */
    testMode(mode) {
      if (mode === 'json') {
        this.testParamsJson = JSON.stringify(this.testParams || {}, null, 2)
      } else {
        try {
          const obj = JSON.parse(this.testParamsJson || '{}')
          if (obj && typeof obj === 'object') this.testParams = obj
        } catch (e) {
          // JSON 非法时保留表单原值，不做覆盖
        }
      }
    }
  },

  methods: {
    /** 默认扫描设计源文本收集变量；结构化设计器可覆盖 */
    collectTestVarCodes() {
      return collectReferencedVarCodes(this.getTestSourceText(), this.testCandidateRefs)
    },

    /** 默认以 this.model 作为设计源；图 / 脚本设计器按需覆盖 */
    getTestSourceText() {
      try {
        return JSON.stringify(this.model || {})
      } catch (e) {
        return ''
      }
    },

    /** 变量中文标签 */
    testVarLabel(code) {
      const ref = (this.projectRefs || []).find(r => r.refCode === code)
      if (ref && ref.varObj && ref.varObj.varLabel) return ref.varObj.varLabel
      if (ref && ref.refLabel) return ref.refLabel
      return code
    },

    /** 从变量库解析类型与枚举串（用于表单控件） */
    testVarMeta(code) {
      const ref = (this.projectRefs || []).find(r => r.refCode === code)
      const vt = (ref && ref.varType) || 'STRING'
      let enumOptions = ''
      if (vt === 'ENUM' && ref && ref.varObj && typeof this.getVarOptions === 'function') {
        const opts = this.getVarOptions(code) || []
        enumOptions = opts.map(o => o.value || o.optionValue).filter(Boolean).join(',')
      }
      return {varType: vt, enumOptions}
    },

    /** 将后端存储值按变量类型转换为表单初值 */
    _coerceTestValue(raw, varType) {
      if (varType === 'NUMBER') {
        const n = Number(raw)
        return isNaN(n) ? raw : n
      }
      if (varType === 'BOOLEAN') {
        return raw === true || raw === 'true'
      }
      return raw
    },

    /** 依据引用变量的默认值 / 类型构造测试入参模板 */
    buildTestParamsTemplate() {
      const template = {}
      this.testVarCodeList.forEach(code => {
        const ref = (this.projectRefs || []).find(r => r.refCode === code)
        const dv = ref && ref.varObj ? ref.varObj.defaultValue : undefined
        if (dv !== undefined && dv !== null && dv !== '') {
          template[code] = this._coerceTestValue(dv, ref.varType)
        } else {
          const meta = this.testVarMeta(code)
          if (meta.varType === 'NUMBER') template[code] = 0
          else if (meta.varType === 'BOOLEAN') template[code] = false
          else template[code] = ''
        }
      })
      return template
    },

    handleTest() {
      const tpl = this.buildTestParamsTemplate()
      this.testParams = tpl
      this.testParamsJson = JSON.stringify(tpl, null, 2)
      this.testResult = null
      this.testVisible = true
    },

    async doTest() {
      let params = this.testParams || {}
      if (this.testMode === 'json') {
        try {
          params = JSON.parse(this.testParamsJson || '{}')
        } catch (e) {
          this.$message.error('参数 JSON 格式错误')
          return
        }
      }
      const res = await executeRule({
        definitionId: this.definitionId,
        scopeCompId: this.scopeCompId,
        params
      })
      this.testResult = res && res.data ? res.data : res
    }
  }
}
