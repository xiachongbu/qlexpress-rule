<template>
  <div class="ct-designer">
    <!-- 顶部工具栏 -->
    <div class="ct-header">
      <div class="ct-title-area">
        <i class="el-icon-data-analysis ct-title-icon" />
        <span class="ct-title">交叉表设计器</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">
          {{ model.rowHeaders.length }} 行 × {{ model.colHeaders.length }} 列
        </el-tag>
      </div>
      <div class="ct-toolbar">
        <el-button-group>
          <el-button size="small" icon="el-icon-plus" @click="addRow">添加行</el-button>
          <el-button size="small" icon="el-icon-plus" @click="addColumn">添加列</el-button>
        </el-button-group>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-document" @click="handleSave">保存</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <!-- 维度变量定义 -->
    <div class="ct-dim-panel">
      <div class="ct-dim-card">
        <div class="dim-label"><i class="el-icon-s-unfold dim-icon row-icon" /> 行维度</div>
        <var-picker
          v-if="varPickerOptions.length"
          :vars="varPickerOptions"
          :value="model.rowVar.varCode"
          placeholder="选择变量、常量或对象字段..."
          style="margin-bottom:6px;"
          @select="v => applyVarToDim(v, 'rowVar')"
        />
        <el-input v-model="model.rowVar.varLabel" size="small" placeholder="中文名称（如 纳税人类型）" style="margin-bottom:6px;" />
        <el-input v-model="model.rowVar.varCode" size="small" placeholder="变量编码（如 taxpayerType）" />
      </div>
      <div class="ct-dim-cross">
        <div class="cross-label">×</div>
        <div class="cross-desc">{{ model.resultVar.varLabel || '结果值' }}</div>
      </div>
      <div class="ct-dim-card">
        <div class="dim-label"><i class="el-icon-s-fold dim-icon col-icon" /> 列维度</div>
        <var-picker
          v-if="varPickerOptions.length"
          :vars="varPickerOptions"
          :value="model.colVar.varCode"
          placeholder="选择变量、常量或对象字段..."
          style="margin-bottom:6px;"
          @select="v => applyVarToDim(v, 'colVar')"
        />
        <el-input v-model="model.colVar.varLabel" size="small" placeholder="中文名称（如 货物类别）" style="margin-bottom:6px;" />
        <el-input v-model="model.colVar.varCode" size="small" placeholder="变量编码（如 goodsCategory）" />
      </div>
      <div class="ct-dim-card">
        <div class="dim-label"><i class="el-icon-finished dim-icon result-icon" /> 结果变量</div>
        <var-picker
          v-if="varPickerOptions.length"
          :vars="varPickerOptions"
          :value="model.resultVar.varCode"
          placeholder="选择变量、常量或对象字段..."
          style="margin-bottom:6px;"
          @select="v => applyVarToDim(v, 'resultVar')"
        />
        <el-input v-model="model.resultVar.varLabel" size="small" placeholder="中文名称（如 适用税率）" style="margin-bottom:6px;" />
        <el-input v-model="model.resultVar.varCode" size="small" placeholder="变量编码（如 taxRate）" />
        <el-select v-model="model.resultVar.varType" size="small" style="width:100%;margin-top:6px;" popper-append-to-body>
          <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>

    <!-- 交叉矩阵 -->
    <div class="ct-matrix-wrap">
      <table class="ct-matrix">
        <colgroup>
          <col class="col-row-header" />
          <col v-for="(col, ci) in model.colHeaders" :key="'col-' + ci" class="col-data" />
          <col class="col-action" />
        </colgroup>
        <thead>
          <tr>
            <!-- 左上角交叉单元格 -->
            <th class="corner-cell">
              <div class="corner-row">{{ model.rowVar.varLabel || '行' }}</div>
              <div class="corner-divider" />
              <div class="corner-col">{{ model.colVar.varLabel || '列' }}</div>
            </th>
            <!-- 列头单元格 -->
            <th v-for="(col, ci) in model.colHeaders" :key="'ch-' + ci" class="col-header-cell">
              <div class="header-cell-inner">
                <el-input
                  v-model="model.colHeaders[ci]"
                  size="mini"
                  placeholder="列值"
                  class="header-input"
                />
                <el-tooltip content="删除此列" placement="top">
                  <el-button
                    type="text"
                    size="mini"
                    icon="el-icon-close"
                    class="delete-col-btn"
                    @click="removeColumn(ci)"
                  />
                </el-tooltip>
              </div>
            </th>
            <!-- 添加列按钮 -->
            <th class="add-col-cell">
              <el-button type="text" size="mini" icon="el-icon-plus" @click="addColumn" style="color:#1890ff;" />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in model.rowHeaders" :key="'row-' + ri">
            <!-- 行头单元格 -->
            <td class="row-header-cell">
              <div class="row-header-inner">
                <el-input
                  v-model="model.rowHeaders[ri]"
                  size="mini"
                  placeholder="行值"
                  class="header-input"
                />
                <el-tooltip content="删除此行" placement="right">
                  <el-button
                    type="text"
                    size="mini"
                    icon="el-icon-close"
                    class="delete-row-btn"
                    @click="removeRow(ri)"
                  />
                </el-tooltip>
              </div>
            </td>
            <!-- 数据单元格 -->
            <td
              v-for="(col, ci) in model.colHeaders"
              :key="'cell-' + ri + '-' + ci"
              :class="['data-cell', { 'cell-filled': isCellFilled(ri, ci), 'cell-focused': focusedCell === ri + '_' + ci }]"
            >
              <el-input
                v-model="model.cells[ri][ci]"
                size="mini"
                :placeholder="model.resultVar.varType === 'NUMBER' ? '0' : ''"
                class="cell-input"
                @focus="focusedCell = ri + '_' + ci"
                @blur="focusedCell = null"
              />
            </td>
            <!-- 行操作 -->
            <td class="add-row-cell" />
          </tr>
          <!-- 添加行按钮行 -->
          <tr>
            <td class="add-row-trigger" @click="addRow">
              <el-button type="text" size="mini" icon="el-icon-plus" style="color:#1890ff;">添加行</el-button>
            </td>
            <td v-for="(col, ci) in model.colHeaders" :key="'add-' + ci" class="add-row-trigger" @click="addRow" />
            <td class="add-row-trigger" />
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 说明预览 -->
    <div class="ct-preview">
      <i class="el-icon-info preview-icon" />
      <span>查询逻辑：当 <strong>{{ model.rowVar.varCode || '行变量' }}</strong> = [行值] 且 <strong>{{ model.colVar.varCode || '列变量' }}</strong> = [列值] 时，输出 <strong>{{ model.resultVar.varCode || '结果变量' }}</strong> = [对应单元格值]</span>
    </div>

    <!-- 脚本预览/编辑面板 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="mode => scriptMode = mode"
    />
    <div v-if="scriptMode === 'script'" class="script-override-banner">
      <i class="el-icon-warning" /> 脚本覆盖模式已激活，可视化编辑暂停。
    </div>

    <!-- 测试执行弹窗 -->
    <el-dialog title="测试执行" :visible.sync="testVisible" width="500px" append-to-body>
      <el-form label-width="130px" size="small">
        <el-form-item :label="model.rowVar.varLabel || '行变量'">
          <el-select v-if="model.rowHeaders.length" v-model="testParams[model.rowVar.varCode]" style="width:100%" clearable>
            <el-option v-for="r in model.rowHeaders" :key="r" :label="r" :value="r" />
          </el-select>
          <el-input v-else v-model="testParams[model.rowVar.varCode]" :placeholder="model.rowVar.varCode" />
        </el-form-item>
        <el-form-item :label="model.colVar.varLabel || '列变量'">
          <el-select v-if="model.colHeaders.length" v-model="testParams[model.colVar.varCode]" style="width:100%" clearable>
            <el-option v-for="c in model.colHeaders" :key="c" :label="c" :value="c" />
          </el-select>
          <el-input v-else v-model="testParams[model.colVar.varCode]" :placeholder="model.colVar.varCode" />
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
          <el-descriptions-item :label="model.resultVar.varLabel || '返回值'">
            <strong>{{ testResult.result }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ testResult.executeTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item v-if="testResult.errorMessage" label="错误">
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

export default {
  name: 'CrossTable',
  components: { VarPicker, ScriptPanel },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        rowVar: { varCode: '', varLabel: '', varType: 'STRING' },
        colVar: { varCode: '', varLabel: '', varType: 'STRING' },
        resultVar: { varCode: '', varLabel: '', varType: 'NUMBER' },
        rowHeaders: [''],
        colHeaders: [''],
        cells: [['']]
      },
      focusedCell: null,
      scriptMode: 'visual',
      testVisible: false,
      testParams: {},
      testResult: null,
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS
    }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
    applyVarToDim(variable, dimKey) {
      if (!variable) return
      const varLabel = (variable.varObj && variable.varObj.varLabel) || variable.varLabel || variable.varCode
      const _varId = variable.varObj && variable.varObj.id ? variable.varObj.id : null
      this.model[dimKey] = {
        ...this.model[dimKey],
        varCode: variable.varCode,
        varLabel,
        _varId,
        varType: variable.varType
      }
      // 如果是枚举行/列变量，自动填充表头
      if (variable.varType === 'ENUM') {
        const options = this.getVarOptions(variable.varCode)
        if (options.length > 0) {
          const vals = options.map(o => o.optionValue)
          if (dimKey === 'rowVar') {
            const oldLen = this.model.rowHeaders.length
            this.model.rowHeaders = vals
            const colCount = this.model.colHeaders.length
            this.model.cells = vals.map((_, ri) =>
              ri < oldLen ? [...(this.model.cells[ri] || []), ...Array(Math.max(0, colCount - (this.model.cells[ri] || []).length)).fill('')]
                          : Array(colCount).fill('')
            )
          } else if (dimKey === 'colVar') {
            const oldLen = this.model.colHeaders.length
            this.model.colHeaders = vals
            this.model.cells = this.model.cells.map(row => {
              const newRow = [...row]
              while (newRow.length < vals.length) newRow.push('')
              return newRow.slice(0, vals.length)
            })
          }
        }
      }
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          this.model = JSON.parse(content.modelJson)
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.normalizeModel()
        this.contentLoaded = true
        this._trySyncModelVarRefs()
      }
    },
    /** 加载最新变量后，同步 model 中行/列/结果变量的 varCode 和 varLabel */
    _syncModelVarRefs() {
      let changed = false
      ;['rowVar', 'colVar', 'resultVar'].forEach(key => {
        if (this.model[key] && this.syncVarItem(this.model[key])) changed = true
      })
      if (changed) this.$forceUpdate()
    },
    normalizeModel() {
      if (!this.model.rowVar) this.$set(this.model, 'rowVar', { varCode: '', varLabel: '', varType: 'STRING' })
      if (!this.model.colVar) this.$set(this.model, 'colVar', { varCode: '', varLabel: '', varType: 'STRING' })
      if (!this.model.resultVar) this.$set(this.model, 'resultVar', { varCode: '', varLabel: '', varType: 'NUMBER' })
      if (!this.model.rowHeaders) this.$set(this.model, 'rowHeaders', [''])
      if (!this.model.colHeaders) this.$set(this.model, 'colHeaders', [''])
      if (!this.model.cells) this.$set(this.model, 'cells', [['']])
      const rows = this.model.rowHeaders.length
      const cols = this.model.colHeaders.length
      while (this.model.cells.length < rows) {
        this.model.cells.push(Array(cols).fill(''))
      }
      this.model.cells.forEach(row => {
        while (row.length < cols) row.push('')
      })
    },
    isCellFilled(ri, ci) {
      return !!(this.model.cells[ri] && this.model.cells[ri][ci] !== '' && this.model.cells[ri][ci] !== null && this.model.cells[ri][ci] !== undefined)
    },
    addRow() {
      const colCount = Math.max(1, (this.model.colHeaders || []).length)
      this.model.rowHeaders.push('')
      this.model.cells.push(Array(colCount).fill(''))
    },
    removeRow(ri) {
      if (this.model.rowHeaders.length <= 1) {
        this.$message.warning('至少保留一行')
        return
      }
      this.model.rowHeaders.splice(ri, 1)
      this.model.cells.splice(ri, 1)
    },
    addColumn() {
      this.model.colHeaders.push('')
      this.model.cells.forEach(row => row.push(''))
    },
    removeColumn(ci) {
      if (this.model.colHeaders.length <= 1) {
        this.$message.warning('至少保留一列')
        return
      }
      this.model.colHeaders.splice(ci, 1)
      this.model.cells.forEach(row => row.splice(ci, 1))
    },
    async handleSave() {
      await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(this.model) })
      this.$message.success('保存成功')
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (res && res.data && res.data.success) {
        this.$message.success('编译成功')
        // 异步刷新变量映射和脚本面板
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) {
          this.$refs.scriptPanel.refresh()
        }
      } else {
        this.$message.error('编译失败: ' + (res && res.data ? res.data.errorMessage : '未知错误'))
      }
    },
    handleTest() {
      this.testParams = {
        [this.model.rowVar.varCode]: '',
        [this.model.colVar.varCode]: ''
      }
      this.testResult = null
      this.testVisible = true
    },
    async doTest() {
      const res = await executeRule({ definitionId: this.definitionId, params: this.testParams })
      this.testResult = res && res.data ? res.data : res
    }
  }
}
</script>

<style lang="scss" scoped>
.ct-designer {
  background: #fff;
  border-radius: 4px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  min-height: 100%;
}

/* 顶部标题栏 */
.ct-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.ct-title-area {
  display: flex;
  align-items: center;
}
.ct-title-icon {
  font-size: 18px;
  color: #722ed1;
  margin-right: 8px;
}
.ct-title {
  font-size: 16px;
  font-weight: bold;
  color: #282828;
}
.ct-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 维度定义面板 */
.ct-dim-panel {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #eeeeee;
}
.ct-dim-card {
  flex: 1;
  min-width: 160px;
}
.dim-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #555;
  margin-bottom: 8px;
}
.dim-icon {
  font-size: 15px;
}
.row-icon { color: #1890ff; }
.col-icon { color: #52c41a; }
.result-icon { color: #fa8c16; }
.ct-dim-cross {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 32px;
}
.cross-label {
  font-size: 28px;
  color: #bbb;
  line-height: 1;
}
.cross-desc {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  white-space: nowrap;
}

/* 矩阵表格 */
.ct-matrix-wrap {
  overflow-x: auto;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
  margin-bottom: 16px;
}
.ct-matrix {
  border-collapse: collapse;
  width: 100%;
  table-layout: auto;

  th, td {
    border: 1px solid #e8e8e8;
    padding: 0;
    vertical-align: middle;
  }

  .col-row-header { width: 140px; min-width: 120px; }
  .col-data { width: 130px; min-width: 110px; }
  .col-action { width: 50px; }
}

/* 左上角交叉单元格 */
.corner-cell {
  background: #f5f5f5;
  position: relative;
  overflow: hidden;
  padding: 0;
  min-height: 56px;
}
.corner-row {
  position: absolute;
  bottom: 6px;
  left: 8px;
  font-size: 11px;
  color: #888;
}
.corner-col {
  position: absolute;
  top: 6px;
  right: 8px;
  font-size: 11px;
  color: #888;
}
.corner-divider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to bottom right, transparent calc(50% - 0.5px), #d0d0d0, transparent calc(50% + 0.5px));
  pointer-events: none;
}

/* 列头单元格 */
.col-header-cell {
  background: #e8f3ff;
  padding: 6px;
  text-align: center;
}
.header-cell-inner {
  display: flex;
  align-items: center;
  gap: 4px;
}
.header-input {
  flex: 1;
}
.delete-col-btn {
  flex-shrink: 0;
  color: #ccc !important;
  padding: 0 !important;
  font-size: 13px;
  &:hover { color: #F56C6C !important; }
}
.add-col-cell {
  background: #f9f9f9;
  text-align: center;
  padding: 6px;
}

/* 行头单元格 */
.row-header-cell {
  background: #f0fff4;
  padding: 6px;
}
.row-header-inner {
  display: flex;
  align-items: center;
  gap: 4px;
}
.delete-row-btn {
  flex-shrink: 0;
  color: #ccc !important;
  padding: 0 !important;
  font-size: 13px;
  &:hover { color: #F56C6C !important; }
}

/* 数据单元格 */
.data-cell {
  background: #fff;
  padding: 5px;
  transition: background 0.15s;
  &.cell-filled {
    background: #fafffe;
  }
  &.cell-focused {
    background: #e6f0ff !important;
    outline: none;
  }
  &:hover { background: #f5f5f5; }
}
.cell-input ::v-deep input {
  text-align: center;
  font-weight: 500;
}

/* 添加行触发行 */
.add-row-trigger {
  background: #fafafa;
  text-align: center;
  cursor: pointer;
  height: 32px;
  padding: 4px;
  &:hover { background: #e6f7ff; }
}
.add-row-cell {
  background: #fafafa;
}

/* 查询逻辑预览 */
.ct-preview {
  padding: 10px 14px;
  background: #f0f7ff;
  border-radius: 4px;
  font-size: 13px;
  color: #555;
  display: flex;
  align-items: center;
  gap: 8px;
}
.preview-icon {
  color: #1890ff;
  font-size: 14px;
  flex-shrink: 0;
}

/* 测试结果 */
.test-result {
  margin-top: 16px;
}
.script-override-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff1f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: #cf1322;
  i { color: #f5222d; font-size: 14px; }
}
</style>
