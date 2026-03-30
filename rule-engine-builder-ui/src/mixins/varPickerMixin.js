/**
 * varPickerMixin
 * 在设计器页面中混入此 mixin，自动加载当前规则所属项目的变量/常量/对象字段。
 *
 * 提供分层联动选择：
 *   - 普通变量：如 taxAmount（rule_variable 且非 CONSTANT）
 *   - 常量：如 scriptName（var_source=CONSTANT，不再使用 组.常量）
 *   - 对象字段：如 对象 TaxRequest 中的 amount
 *
 * 提供：
 *   - projectRefs: { refCode, refLabel, varType, varObj, category }[] — 可选的引用列表
 *   - projectVars: RuleVariable[]  — 兼容旧逻辑，为 projectRefs 的扁平变量
 *   - loadingVars: boolean
 *   - getVarByCode(code)           — 按 refCode 查找
 *   - getVarOptions(refCode)       — ENUM 选项
 */

import { getDefinition } from '@/api/definition'
import { listVariablesByProject, getVariableOptions } from '@/api/variable'
import { getVariableTree, getDataObjectFieldOptions } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import { varTypeLabel as varTypeLabelFn, varTypeTagColor } from '@/constants/varTypes'

export default {
  data() {
    return {
      projectVars: [],
      projectRefs: [],
      projectFunctions: [],
      loadingVars: false,
      /** 变量加载是否失败 */
      varsLoadError: false,
      varOptionsByCode: {},
      _projectId: null
    }
  },

  computed: {
    /** VarPicker 使用的选项列表（分层：普通变量 / 常量 / 对象字段） */
    varPickerOptions() {
      return this.projectRefs.map(r => ({
        varCode: r.refCode,
        varLabel: r.refLabel,
        varType: r.varType,
        varObj: r.varObj,
        _ref: r
      }))
    },
    inputVars() {
      return this.projectVars.filter(v => v.varSource === 'INPUT' || !v.varSource)
    },
    computedVars() {
      return this.projectVars.filter(v => v.varSource === 'COMPUTED')
    },
    enumVars() {
      return this.projectVars.filter(v => v.varType === 'ENUM')
    }
  },

  created() {
    if (this.$route && this.$route.params && this.$route.params.id) {
      this.loadProjectVars(this.$route.params.id)
    }
  },

  methods: {
    /**
     * 根据定义 ID 拉取项目下变量树、常量、对象字段与函数列表，并组装 projectRefs。
     */
    async loadProjectVars(definitionId) {
      this.loadingVars = true
      this.varsLoadError = false
      try {
        const defRes = await getDefinition(definitionId)
        const def = defRes && defRes.data ? defRes.data : defRes
        if (!def || !def.projectId) {
          this.loadingVars = false
          return
        }
        this._projectId = def.projectId
        const pid = def.projectId

        const [varRes, objRes, funcRes] = await Promise.all([
          listVariablesByProject(pid).catch(() => ({ data: [] })),
          getVariableTree(pid).catch(() => ({ data: [] })),
          listAllFunctionsByProject(pid).catch(() => ({ data: [] }))
        ])
        const funcData = (funcRes && funcRes.data ? funcRes.data : funcRes) || []
        this.projectFunctions = Array.isArray(funcData) ? funcData : (funcData && Array.isArray(funcData.records) ? funcData.records : [])

        const allVars = (varRes && varRes.data ? varRes.data : varRes) || []
        const objectTree = (objRes && objRes.data ? objRes.data : objRes) || []

        this.projectVars = allVars

        const refs = []

        // 1. 普通变量（排除常量）
        allVars.filter(v => v.varSource !== 'CONSTANT').forEach(v => {
          refs.push({
            refCode: v.scriptName || v.varCode,
            refLabel: `${v.varLabel} (${v.scriptName || v.varCode})`,
            varType: v.varType,
            varObj: v,
            category: 'standalone'
          })
        })

        // 2. 常量：单段 scriptName（或 varCode）
        allVars.filter(v => v.varSource === 'CONSTANT').forEach(c => {
          const constScriptName = c.scriptName || c.varCode
          refs.push({
            refCode: constScriptName,
            refLabel: `${c.varLabel || c.varCode} (${constScriptName})`,
            varType: c.varType,
            varObj: c,
            category: 'constant'
          })
        })

        // 3. 对象字段：对象 scriptName.字段 scriptName
        objectTree.forEach(node => {
          const obj = node.object || node
          const objectCode = obj.objectCode || ''
          const objScriptName = obj.scriptName || objectCode
          const objectLabel = obj.objectLabel || objectCode
          ;(node.variables || []).forEach(v => {
            const varScriptName = v.scriptName || v.varCode
            const refCode = `${objScriptName}.${varScriptName}`
            refs.push({
              refCode,
              refLabel: `${v.varLabel || v.varCode} (${varScriptName})`,
              varType: v.varType,
              varObj: v,
              category: 'object',
              objectCode,
              objectLabel
            })
          })
        })

        this.projectRefs = refs

        this._trySyncModelVarRefs()

        const enumRefs = refs.filter(r => r.varType === 'ENUM')
        await Promise.all(enumRefs.map(r => this.loadVarOptionsForRef(r.refCode, r.varObj)))
      } catch (e) {
        this.projectVars = []
        this.projectRefs = []
        this.varsLoadError = true
      } finally {
        this.loadingVars = false
      }
    },

    /**
     * 拉取枚举选项：变量 ENUM 走 variable；数据对象字段 ENUM 走 dataobject field。
     */
    async loadVarOptionsForRef(refCode, varObj) {
      if (!varObj || !varObj.id) return
      try {
        const api = varObj.objectField ? getDataObjectFieldOptions : getVariableOptions
        const res = await api(varObj.id)
        const opts = (res && res.data ? res.data : res) || []
        this.$set(this.varOptionsByCode, refCode, opts)
      } catch (e) {
        this.$set(this.varOptionsByCode, refCode, [])
      }
    },

    getVarByCode(code) {
      const ref = this.projectRefs.find(r => r.refCode === code)
      return ref ? ref.varObj : (this.projectVars.find(v => v.varCode === code) || null)
    },

    getVarOptions(refCode) {
      const opts = this.varOptionsByCode[refCode] || []
      return opts.map(o => ({ value: o.optionValue, label: o.optionLabel || o.optionValue }))
    },

    /** 与变量管理表格中类型标签配色一致 */
    varTypeColor(varType) {
      return varTypeTagColor(varType)
    },

    /** 与变量管理中类型中文名一致 */
    varTypeLabel(varType) {
      return varTypeLabelFn(varType)
    },

    /**
     * 当 projectRefs 和 model 都加载完成后，尝试同步设计器 model 中的变量引用。
     * 各设计器可实现 _syncModelVarRefs() 来定义自身的同步逻辑。
     */
    _trySyncModelVarRefs() {
      if (this.projectRefs && this.projectRefs.length > 0 && this.contentLoaded && typeof this._syncModelVarRefs === 'function') {
        this._syncModelVarRefs()
      }
    },

    /**
     * 根据变量数据库 ID 在最新的 projectRefs 中查找对应引用
     */
    findRefByVarId(varId) {
      if (!varId) return null
      return this.projectRefs.find(r => r.varObj && String(r.varObj.id) === String(varId)) || null
    },

    /**
     * 同步单个变量引用项，根据 _varId 或 varLabel 回溯匹配，更新 varCode 和 varLabel。
     * @param {Object} item - 包含 {varCode, varLabel, _varId?} 的模型项
     * @returns {boolean} 是否有更新
     */
    syncVarItem(item) {
      if (!item || !item.varCode) return false
      // 1. 优先使用 _varId 精确匹配
      if (item._varId) {
        const ref = this.findRefByVarId(item._varId)
        if (ref) {
          let changed = false
          if (item.varCode !== ref.refCode) { item.varCode = ref.refCode; changed = true }
          const newLabel = (ref.varObj && ref.varObj.varLabel) || ref.refLabel
          if (item.varLabel !== newLabel) { item.varLabel = newLabel; changed = true }
          if (ref.varType && item.varType !== ref.varType) { item.varType = ref.varType; changed = true }
          return changed
        }
      }
      // 2. varCode 在当前 projectRefs 中能匹配，说明没变化
      if (this.projectRefs.some(r => r.refCode === item.varCode)) {
        return false
      }
      // 3. varCode 匹配不上（scriptName 可能已改），通过 varLabel 回溯
      if (item.varLabel) {
        const candidates = this.projectRefs.filter(r =>
          r.varObj && r.varObj.varLabel && r.varObj.varLabel === item.varLabel
        )
        if (candidates.length === 1) {
          const ref = candidates[0]
          item.varCode = ref.refCode
          item.varLabel = (ref.varObj && ref.varObj.varLabel) || ref.refLabel
          item._varId = ref.varObj && ref.varObj.id
          if (ref.varType) item.varType = ref.varType
          return true
        }
      }
      return false
    }
  }
}
