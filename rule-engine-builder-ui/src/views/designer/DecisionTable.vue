<template>
  <div class="dt-designer">
    <!-- 顶部工具栏 -->
    <div class="dt-header">
      <div class="dt-title-area">
        <i class="el-icon-s-grid dt-title-icon" />
        <span class="dt-title">决策表配置</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">共 {{ model.rules.length }} 条规则</el-tag>
      </div>
      <div class="dt-toolbar">
        <el-button size="small" icon="el-icon-plus" @click="addRule">添加行</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-document" @click="handleSave">保存</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
        <el-divider direction="vertical" />
        <span class="toolbar-label">命中策略</span>
        <el-select v-model="model.hitPolicy" size="small" style="width:110px;">
          <el-option label="首次命中" value="FIRST" />
          <el-option label="全部执行" value="ALL" />
          <el-option label="唯一命中" value="UNIQUE" />
        </el-select>
        <el-tooltip :content="hitPolicyDesc" placement="bottom" effect="light">
          <i class="el-icon-question tip-icon" />
        </el-tooltip>
      </div>
    </div>

    <!-- 变量加载状态 -->
    <div v-if="loadingVars || varPickerOptions.length" class="dt-var-status">
      <span v-if="loadingVars" style="font-size:12px;color:#999;"><i class="el-icon-loading" /> 加载变量库...</span>
      <span v-else style="font-size:12px;color:#52c41a;">
        <i class="el-icon-s-custom" /> 已加载 {{ varPickerOptions.length }} 个变量/常量/对象字段
      </span>
    </div>

    <!-- 规则列表：每条含条件树 + 动作 -->
    <div class="dt-rules-wrap">
      <template v-if="contentLoaded && model.rules.length > 0">
        <div
          v-for="(row, ri) in model.rules"
          :key="'rule-' + ri"
          class="dt-rule-card"
        >
          <div class="dt-rule-toolbar">
            <span class="dt-rule-no">#{{ ri + 1 }}</span>
            <el-button type="text" size="mini" @click="copyRule(ri)">复制</el-button>
            <el-button type="text" size="mini" class="btn-del" @click="removeRule(ri)">删除</el-button>
          </div>
          <div class="dt-rule-grid">
            <div class="dt-cond-panel">
              <condition-group-editor
                v-if="row.conditionRoot"
                :group="row.conditionRoot"
                :vars="varPickerOptions"
                :get-var-options-fn="getVarOptions"
              />
            </div>
            <div class="dt-act-panel">
              <div class="dt-act-panel-head">
                <span class="dt-act-panel-title">动作 (THEN)</span>
                <span class="dt-act-panel-hint">本条规则独立配置，可与其它行不同</span>
                <el-button type="primary" size="mini" plain icon="el-icon-plus" @click="addRuleAction(ri)">添加动作</el-button>
              </div>
              <div class="dt-act-rows">
                <div
                  v-for="(act, ai) in row.actions"
                  :key="'r' + ri + '-act-' + ai"
                  class="dt-act-field"
                >
                  <div class="dt-act-head">
                    <span class="col-tag act-tag">THEN</span>
                    <span class="dt-act-title">{{ act.varLabel || act.varCode || '未选变量' }}</span>
                    <span class="th-actions">
                      <i class="el-icon-setting" title="配置变量与类型" @click.stop="openActionConfig(ri, ai)" />
                      <i
                        class="el-icon-delete"
                        title="删除此动作"
                        @click.stop="removeRuleAction(ri, ai)"
                      />
                    </span>
                  </div>
                  <div class="dt-act-body">
                    <el-select
                      v-if="act.varType === 'ENUM' && getEnumOptions(act).length"
                      v-model="act.value"
                      size="mini"
                      class="dt-act-value-ctl"
                      clearable
                    >
                      <el-option v-for="opt in getEnumOptions(act)" :key="opt" :label="opt" :value="opt" />
                    </el-select>
                    <el-select
                      v-else-if="act.varType === 'BOOLEAN'"
                      v-model="act.value"
                      size="mini"
                      class="dt-act-value-ctl"
                    >
                      <el-option label="true" value="true" />
                      <el-option label="false" value="false" />
                    </el-select>
                    <el-input v-else v-model="act.value" size="mini" class="dt-act-value-ctl" placeholder="赋值" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- 加载中 -->
      <div v-if="!contentLoaded" class="dt-loading">
        <i class="el-icon-loading" /> 加载决策表数据...
      </div>

      <!-- 空状态 -->
      <div v-else-if="contentLoaded && model.rules.length === 0" class="dt-empty">
        <i class="el-icon-s-grid dt-empty-icon" />
        <p>暂无规则行，请点击「添加行」；每条规则内可单独「添加动作」并选择要赋值的变量</p>
      </div>
    </div>

    <!-- 列配置弹窗（仅动作列） -->
    <el-dialog
      :title="colConfigTitle"
      :visible.sync="colConfigVisible"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form v-if="activeColDef" label-width="90px" size="small">
        <el-form-item label="选择变量">
          <var-picker
            v-if="varPickerOptions.length"
            :vars="varPickerOptions"
            :value="activeColDef.varCode"
            placeholder="选择变量、常量或对象字段..."
            size="small"
            @select="onColConfigVarSelect"
          />
          <span v-else style="color:#999;font-size:12px;">暂无变量库数据</span>
        </el-form-item>
        <el-form-item label="中文名称">
          <el-input v-model="activeColDef.varLabel" placeholder="如：业务类型" />
        </el-form-item>
        <el-form-item label="变量编码">
          <el-input v-model="activeColDef.varCode" placeholder="如：bizType" />
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select
            v-model="activeColDef.varType"
            style="width:100%"
            popper-append-to-body
            @change="onColDefVarTypeChange"
          >
            <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="activeColDef.varType === 'ENUM'" label="枚举值">
          <el-input v-model="activeColDef.enumOptions" placeholder="逗号分隔，如：普通,免税,优惠" />
        </el-form-item>
      </el-form>
      <template slot="footer">
        <el-button size="small" @click="colConfigVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 脚本预览/编辑面板 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="onScriptModeChange"
    />

    <!-- 脚本覆盖模式横幅 -->
    <div v-if="scriptMode === 'script'" class="script-override-banner">
      <i class="el-icon-warning" />
      <span>脚本覆盖模式已激活，可视化编辑暂停。如需恢复请在下方脚本面板切换回「可视化模式」。</span>
    </div>

    <!-- 测试执行弹窗 -->
    <el-dialog title="测试执行" :visible.sync="testVisible" width="600px" append-to-body>
      <el-form label-width="130px" size="small">
        <el-form-item
          v-for="code in testVarCodeList"
          :key="'tc-' + code"
          :label="testVarLabel(code)"
        >
          <el-select
            v-if="testVarMeta(code).varType === 'ENUM' && testVarMeta(code).enumOptions"
            v-model="testParams[code]"
            style="width:100%"
            clearable
          >
            <el-option v-for="opt in testEnumOpts(code)" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-select
            v-else-if="testVarMeta(code).varType === 'BOOLEAN'"
            v-model="testParams[code]"
            style="width:100%"
          >
            <el-option label="true" :value="true" />
            <el-option label="false" :value="false" />
          </el-select>
          <el-input-number
            v-else-if="testVarMeta(code).varType === 'NUMBER'"
            v-model="testParams[code]"
            style="width:100%"
          />
          <el-input v-else v-model="testParams[code]" />
        </el-form-item>
      </el-form>
      <template slot="footer">
        <el-button size="small" @click="testVisible = false">取消</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="doTest">执行</el-button>
      </template>
      <div v-if="testResult" class="test-result">
        <el-alert
          :title="testResult.success ? '执行成功' : '执行失败'"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="margin-bottom:10px;"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="返回值">
            <pre class="result-pre">{{ formatResult(testResult.result) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ testResult.executeTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item v-if="testResult.errorMessage" label="错误信息">
            <span style="color:#F56C6C">{{ testResult.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { saveContent, compileRule, executeRule, getContent } from '@/api/definition'
import { VAR_TYPE_FORM_OPTIONS } from '@/constants/varTypes'
import varPickerMixin from '@/mixins/varPickerMixin'
import VarPicker from '@/components/common/VarPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import {
  createEmptyLeaf,
  createEmptyActionItem,
  migrateRuleConditionsToTree,
  collectVarCodesFromConditionTree,
  walkConditionLeaves
} from '@/utils/decisionConditionTree'

export default {
  name: 'DecisionTable',
  components: { VarPicker, ScriptPanel, ConditionGroupEditor },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      model: {
        hitPolicy: 'FIRST',
        conditions: [],
        actions: [],
        rules: []
      },
      scriptMode: 'visual',
      testVisible: false,
      testParams: {},
      testResult: null,
      contentLoaded: false,
      colConfigVisible: false,
      colConfigRuleIndex: -1,
      colConfigActionIndex: -1,
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS
    }
  },
  computed: {
    hitPolicyDesc() {
      const map = {
        FIRST: '首次命中：从上到下匹配规则，返回第一条满足条件的规则结果',
        ALL: '全部执行：匹配所有满足条件的规则并依次执行，结果为最后一条',
        UNIQUE: '唯一命中：期望有且仅有一条规则满足，否则报错'
      }
      return map[this.model.hitPolicy] || ''
    },
    colConfigTitle() {
      if (this.colConfigRuleIndex < 0 || this.colConfigActionIndex < 0) return '动作配置'
      return '动作配置 - 规则 #' + (this.colConfigRuleIndex + 1) + ' / 第 ' + (this.colConfigActionIndex + 1) + ' 条'
    },
    activeColDef() {
      const r = this.model.rules[this.colConfigRuleIndex]
      if (!r || !r.actions || this.colConfigActionIndex < 0) return null
      return r.actions[this.colConfigActionIndex]
    },
    /** 测试弹窗中需要录入的变量编码列表（条件树 DFS 去重） */
    testVarCodeList() {
      const s = new Set()
      ;(this.model.rules || []).forEach(r => {
        collectVarCodesFromConditionTree(r.conditionRoot, s)
      })
      return Array.from(s)
    }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          this.model = JSON.parse(content.modelJson)
          this.normalizeModel()
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.contentLoaded = true
        this._trySyncModelVarRefs()
      }
    },

    /**
     * 同步条件叶与「变量比较」右侧引用到最新 projectRefs。
     */
    _syncModelVarRefs() {
      let changed = false
      ;(this.model.rules || []).forEach(rule => {
        walkConditionLeaves(rule.conditionRoot, leaf => {
          if (leaf.varCode && this.syncVarItem(leaf)) changed = true
          if (leaf.valueKind === 'VAR' && leaf.value) {
            const fake = {
              varCode: leaf.value,
              varLabel: leaf.rightVarLabel,
              _varId: leaf._rightVarId,
              varType: leaf.rightVarType
            }
            if (this.syncVarItem(fake)) {
              leaf.value = fake.varCode
              leaf.rightVarLabel = fake.varLabel
              leaf._rightVarId = fake._varId
              leaf.rightVarType = fake.varType
              changed = true
            }
          }
        })
      })
      ;(this.model.rules || []).forEach(rule => {
        ;(rule.actions || []).forEach(item => { if (item && item.varCode && this.syncVarItem(item)) changed = true })
      })
      if (changed) this.$forceUpdate()
    },

    /**
     * 判断规则动作是否已是「每行自带 varCode」的新结构（旧数据仅为 { value }）。
     */
    _ruleActionsAreFullShape(actions) {
      if (!actions || !actions.length) return false
      const a0 = actions[0]
      return a0 && (a0.varCode !== undefined || a0.varType !== undefined)
    },

    /**
     * 将单条规则动作列表规范为完整字段；支持旧版「全局 actions + 行上仅 value」合并。
     */
    ensureRuleActionsShape(rule, legacyGlobalActions) {
      let acts = rule.actions || []
      const full = this._ruleActionsAreFullShape(acts)
      if (!full && legacyGlobalActions && legacyGlobalActions.length) {
        const merged = legacyGlobalActions.map((def, i) => ({
          varCode: def.varCode || '',
          varLabel: def.varLabel || '',
          varType: def.varType || 'STRING',
          enumOptions: def.enumOptions !== undefined ? def.enumOptions : '',
          _varId: def._varId,
          value: acts[i] && acts[i].value !== undefined ? acts[i].value : ''
        }))
        this.$set(rule, 'actions', merged)
      } else if (!full) {
        this.$set(rule, 'actions', (acts.length ? acts : [{}]).map(a => ({
          varCode: '',
          varLabel: '',
          varType: 'STRING',
          enumOptions: '',
          value: a && a.value !== undefined ? a.value : ''
        })))
      }
      acts = rule.actions || []
      if (!acts.length) {
        this.$set(rule, 'actions', [createEmptyActionItem()])
        acts = rule.actions
      }
      acts.forEach(a => {
        if (a.enumOptions === undefined) this.$set(a, 'enumOptions', '')
      })
    },

    /**
     * 规范化模型：废弃顶层 conditions/actions 列定义；条件树 + 每规则独立动作列表。
     */
    normalizeModel() {
      this.model.rules = this.model.rules || []
      const legacyCols = Array.isArray(this.model.conditions) ? [...this.model.conditions] : []
      const legacyGlobalActions = Array.isArray(this.model.actions) && this.model.actions.length
        ? JSON.parse(JSON.stringify(this.model.actions))
        : null

      this.model.rules.forEach(r => {
        this.ensureRuleActionsShape(r, legacyGlobalActions)

        const hasTree = r.conditionRoot && r.conditionRoot.type === 'group' && Array.isArray(r.conditionRoot.children)
        if (!hasTree) {
          const migrated = migrateRuleConditionsToTree(r.conditions || [], legacyCols)
          this.$set(r, 'conditionRoot', migrated)
        }
        if (r.conditions !== undefined) delete r.conditions
      })
      this.model.conditions = []
      this.model.actions = []
    },

    getEnumOptions(cond) {
      if (!cond.enumOptions) return []
      return cond.enumOptions.split(',').map(s => s.trim()).filter(Boolean)
    },

    /**
     * 测试弹窗：变量中文标签。
     */
    testVarLabel(code) {
      const ref = this.projectRefs.find(r => r.refCode === code)
      if (ref && ref.varObj && ref.varObj.varLabel) return ref.varObj.varLabel
      if (ref && ref.refLabel) return ref.refLabel
      return code
    },

    /**
     * 测试弹窗：从变量库解析类型与枚举串（用于表单控件）。
     */
    testVarMeta(code) {
      const ref = this.projectRefs.find(r => r.refCode === code)
      const vt = (ref && ref.varType) || 'STRING'
      let enumOptions = ''
      if (vt === 'ENUM' && ref && ref.varObj) {
        const opts = this.getVarOptions(code) || []
        enumOptions = opts.map(o => o.value || o.optionValue).filter(Boolean).join(',')
      }
      return { varType: vt, enumOptions }
    },

    /**
     * 测试弹窗：枚举选项列表。
     */
    testEnumOpts(code) {
      const m = this.testVarMeta(code)
      if (!m.enumOptions) return []
      return m.enumOptions.split(',').map(s => s.trim()).filter(Boolean)
    },

    /**
     * 打开本条规则内某一动作的变量/类型配置弹窗。
     */
    openActionConfig(ruleIndex, actionIndex) {
      this.colConfigRuleIndex = ruleIndex
      this.colConfigActionIndex = actionIndex
      this.colConfigVisible = true
    },

    onColConfigVarSelect(variable) {
      if (!variable) return
      const act = this.activeColDef
      if (!act) return
      const varLabel = (variable.varObj && variable.varObj.varLabel) || variable.varLabel || variable.varCode
      const _varId = variable.varObj && variable.varObj.id ? variable.varObj.id : null
      this.$set(act, 'varCode', variable.varCode)
      this.$set(act, 'varLabel', varLabel)
      this.$set(act, '_varId', _varId)
      this.$set(act, 'varType', variable.varType)
      this.$set(act, 'enumOptions', variable.varType === 'ENUM'
        ? this.getVarOptions(variable.varCode).map(o => o.value || o.optionValue).join(',')
        : '')
    },

    /**
     * 列配置中切换数据类型时，非枚举类型清空枚举值配置，避免残留。
     */
    onColDefVarTypeChange(type) {
      if (!this.activeColDef || type === 'ENUM') return
      this.$set(this.activeColDef, 'enumOptions', '')
    },

    /**
     * 在本条规则末尾增加一条可独立配置变量的动作。
     */
    addRuleAction(ruleIndex) {
      const r = this.model.rules[ruleIndex]
      if (!r) return
      if (!r.actions) this.$set(r, 'actions', [])
      r.actions.push(createEmptyActionItem())
    },

    /**
     * 删除本条规则内的一条动作（至少保留一条以免结构为空）。
     */
    removeRuleAction(ruleIndex, actionIndex) {
      const r = this.model.rules[ruleIndex]
      if (!r || !r.actions) return
      if (r.actions.length <= 1) {
        this.$message.warning('每条规则至少保留一条动作')
        return
      }
      this.$confirm('确认删除该动作？', '提示', { type: 'warning' }).then(() => {
        r.actions.splice(actionIndex, 1)
      }).catch(() => {})
    },

    /**
     * 新增规则行：默认条件树 + 一条空动作。
     */
    addRule() {
      this.model.rules.push({
        conditionRoot: { type: 'group', op: 'AND', children: [createEmptyLeaf()] },
        actions: [createEmptyActionItem()]
      })
    },

    copyRule(index) {
      const orig = this.model.rules[index]
      const copy = JSON.parse(JSON.stringify(orig))
      this.model.rules.splice(index + 1, 0, copy)
    },

    removeRule(index) {
      this.model.rules.splice(index, 1)
    },

    async handleSave() {
      this.normalizeModel()
      await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(this.model) })
      this.$message.success('保存成功')
    },

    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (res && res.data && res.data.success) {
        this.$message.success('编译成功')
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) {
          this.$refs.scriptPanel.refresh()
        }
      } else {
        this.$message.error('编译失败: ' + (res && res.data ? res.data.errorMessage : '未知错误'))
      }
    },

    /**
     * 根据条件树涉及的变量构造测试默认值模板。
     */
    buildTestParamsTemplate() {
      const template = {}
      this.testVarCodeList.forEach(code => {
        const ref = this.projectRefs.find(r => r.refCode === code)
        if (ref && ref.varObj && ref.varObj.defaultValue !== undefined && ref.varObj.defaultValue !== null) {
          template[code] = ref.varObj.defaultValue
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
      const template = this.buildTestParamsTemplate()
      const params = {}
      this.testVarCodeList.forEach(code => {
        params[code] = template[code] !== undefined ? template[code] : ''
      })
      this.testParams = params
      this.testResult = null
      this.testVisible = true
    },

    async doTest() {
      const res = await executeRule({ definitionId: this.definitionId, params: this.testParams })
      this.testResult = res && res.data ? res.data : res
    },

    formatResult(val) {
      if (val === null || val === undefined) return '(空)'
      try {
        return JSON.stringify(typeof val === 'string' ? JSON.parse(val) : val, null, 2)
      } catch (e) {
        return String(val)
      }
    },

    onScriptModeChange(mode) {
      this.scriptMode = mode
    }
  }
}
</script>

<style lang="scss" scoped>
.dt-designer {
  background: #fff;
  border-radius: 4px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  min-height: 100%;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.dt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: 8px;
}
.dt-title-area {
  display: flex;
  align-items: center;
}
.dt-title-icon {
  font-size: 18px;
  color: #1890ff;
  margin-right: 8px;
}
.dt-title {
  font-size: 16px;
  font-weight: bold;
  color: #282828;
}
.dt-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.toolbar-label {
  font-size: 13px;
  color: #666;
}
.tip-icon {
  color: #999;
  cursor: pointer;
  font-size: 14px;
  &:hover { color: #1890ff; }
}

.dt-var-status {
  margin-bottom: 8px;
}

.dt-rules-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.dt-rule-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
  background: #fafafa;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}
.dt-rule-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.dt-rule-no {
  font-weight: 600;
  color: #666;
  margin-right: 8px;
}
.dt-rule-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: flex-start;
  width: 100%;
  box-sizing: border-box;
}
.dt-cond-panel {
  flex: 1 1 100%;
  width: 100%;
  min-width: 0;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 12px 14px;
  box-sizing: border-box;
}
.dt-act-panel {
  flex: 1 1 100%;
  width: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-sizing: border-box;
}
.dt-act-panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}
.dt-act-panel-title {
  font-weight: 600;
  color: #333;
}
.dt-act-panel-hint {
  font-size: 12px;
  color: #999;
  flex: 1 1 auto;
  min-width: 160px;
}
.dt-act-rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}
.dt-act-field {
  background: #fafffe;
  border: 1px solid #d9f7f0;
  border-radius: 6px;
  padding: 10px 12px;
  width: 100%;
  box-sizing: border-box;
}
.dt-act-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.dt-act-title {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  flex: 1;
  min-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dt-act-body {
  width: 100%;
  min-width: 0;
}
.dt-act-value-ctl {
  width: 100%;
  max-width: 100%;
}
@media (min-width: 1100px) {
  .dt-rule-grid {
    flex-wrap: nowrap;
    align-items: stretch;
  }
  /* 条件区占主要横向空间，避免控件挤在左侧 */
  .dt-cond-panel {
    flex: 3 1 0;
    min-width: 0;
    max-width: none;
  }
  .dt-act-panel {
    flex: 1 1 300px;
    min-width: 260px;
    max-width: 400px;
  }
}
.col-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: bold;
  padding: 0 4px;
  border-radius: 2px;
  letter-spacing: 0.5px;
  line-height: 16px;
  flex-shrink: 0;
}
.act-tag {
  background: #e6fffb;
  color: #13c2c2;
}
.th-actions {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
  i {
    cursor: pointer;
    color: #c0c0c0;
    font-size: 13px;
    transition: color .2s;
    &:hover { color: #1890ff; }
    &.el-icon-delete:hover { color: #F56C6C; }
  }
}

.btn-del {
  color: #F56C6C !important;
}

.dt-loading {
  text-align: center;
  padding: 40px;
  color: #999;
  font-size: 13px;
  i { margin-right: 4px; }
}

.dt-empty {
  text-align: center;
  padding: 30px;
  color: #999;
  font-size: 13px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.dt-empty-icon {
  font-size: 36px;
  color: #ddd;
  display: block;
  margin-bottom: 6px;
}

.test-result {
  margin-top: 16px;
}
.result-pre {
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow: auto;
  margin: 0;
}

.script-override-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff1f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  margin-top: 12px;
  font-size: 12px;
  color: #cf1322;
  i { color: #f5222d; font-size: 14px; }
}
</style>
