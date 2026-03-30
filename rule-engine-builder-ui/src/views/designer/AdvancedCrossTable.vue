<template>
  <div class="act-designer">
    <!-- 顶部工具栏 -->
    <div class="act-header">
      <div class="act-title-area">
        <i class="el-icon-data-analysis act-title-icon" />
        <span class="act-title">复杂交叉表设计器</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">
          {{ totalRowCount }} 行 × {{ totalColCount }} 列
        </el-tag>
      </div>
      <div class="act-toolbar">
        <el-button size="small" icon="el-icon-document" @click="handleSave">保存</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <!-- 维度配置区：行维度 + 列维度 并排 -->
    <div class="act-dim-row">
      <!-- 行维度 -->
      <div class="act-dim-panel">
        <div class="dim-panel-header">
          <i class="el-icon-s-unfold" style="color:#1890ff;" /> 行维度
          <el-button size="mini" icon="el-icon-plus" @click="addDimension('row')">添加行维度</el-button>
        </div>
        <div v-for="(dim, di) in model.rowDimensions" :key="'rd-' + di" class="dim-config-card">
          <div class="dim-config-header">
            <var-picker
              v-if="varPickerOptions.length"
              :vars="varPickerOptions"
              :value="dim.varCode"
              placeholder="选择变量..."
              width="100%"
              class="dim-field-var"
              @select="v => applyVarToDim(v, 'rowDimensions', di)"
            />
            <el-input v-else v-model="dim.varCode" size="mini" placeholder="变量编码" class="dim-field-var" />
            <el-input v-model="dim.varLabel" size="mini" placeholder="维度名称" class="dim-field-label" />
            <el-select v-model="dim.varType" size="mini" class="dim-field-type" popper-append-to-body>
              <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C;" @click="removeDimension('row', di)" />
          </div>
          <div class="segments-area">
            <div v-for="(seg, si) in dim.segments" :key="si" class="segment-row">
              <el-select v-model="seg.operator" size="mini" class="seg-op">
                <el-option label="等于" value="==" /><el-option label="不等于" value="!=" />
                <el-option label="大于" value=">" /><el-option label="大于等于" value=">=" />
                <el-option label="小于" value="<" /><el-option label="小于等于" value="<=" />
                <el-option label="区间" value="range" />
              </el-select>
              <template v-if="seg.operator === 'range'">
                <el-input v-model="seg.min" size="mini" placeholder="最小值(含)" class="seg-val" />
                <span class="seg-sep">~</span>
                <el-input v-model="seg.max" size="mini" placeholder="最大值(不含)" class="seg-val" />
              </template>
              <el-input v-else v-model="seg.value" size="mini" placeholder="值" class="seg-val" />
              <el-input v-model="seg.label" size="mini" placeholder="标签" class="seg-label" />
              <el-button type="text" size="mini" icon="el-icon-close" style="color:#ccc;" @click="dim.segments.splice(si, 1)" />
            </div>
            <el-button type="text" size="mini" icon="el-icon-plus" @click="addSegment(dim)">添加分段</el-button>
          </div>
        </div>
      </div>

      <!-- 列维度 -->
      <div class="act-dim-panel">
        <div class="dim-panel-header">
          <i class="el-icon-s-fold" style="color:#52c41a;" /> 列维度
          <el-button size="mini" icon="el-icon-plus" @click="addDimension('col')">添加列维度</el-button>
        </div>
        <div v-for="(dim, di) in model.colDimensions" :key="'cd-' + di" class="dim-config-card">
          <div class="dim-config-header">
            <var-picker
              v-if="varPickerOptions.length"
              :vars="varPickerOptions"
              :value="dim.varCode"
              placeholder="选择变量..."
              width="100%"
              class="dim-field-var"
              @select="v => applyVarToDim(v, 'colDimensions', di)"
            />
            <el-input v-else v-model="dim.varCode" size="mini" placeholder="变量编码" class="dim-field-var" />
            <el-input v-model="dim.varLabel" size="mini" placeholder="维度名称" class="dim-field-label" />
            <el-select v-model="dim.varType" size="mini" class="dim-field-type" popper-append-to-body>
              <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C;" @click="removeDimension('col', di)" />
          </div>
          <div class="segments-area">
            <div v-for="(seg, si) in dim.segments" :key="si" class="segment-row">
              <el-select v-model="seg.operator" size="mini" class="seg-op">
                <el-option label="等于" value="==" /><el-option label="不等于" value="!=" />
                <el-option label="大于" value=">" /><el-option label="大于等于" value=">=" />
                <el-option label="小于" value="<" /><el-option label="小于等于" value="<=" />
                <el-option label="区间" value="range" />
              </el-select>
              <template v-if="seg.operator === 'range'">
                <el-input v-model="seg.min" size="mini" placeholder="最小值(含)" class="seg-val" />
                <span class="seg-sep">~</span>
                <el-input v-model="seg.max" size="mini" placeholder="最大值(不含)" class="seg-val" />
              </template>
              <el-input v-else v-model="seg.value" size="mini" placeholder="值" class="seg-val" />
              <el-input v-model="seg.label" size="mini" placeholder="标签" class="seg-label" />
              <el-button type="text" size="mini" icon="el-icon-close" style="color:#ccc;" @click="dim.segments.splice(si, 1)" />
            </div>
            <el-button type="text" size="mini" icon="el-icon-plus" @click="addSegment(dim)">添加分段</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 结果变量：独立一行 -->
    <div class="act-result-row">
      <div class="dim-panel-header">
        <i class="el-icon-finished" style="color:#fa8c16;" /> 结果变量
      </div>
      <div class="result-config">
        <var-picker
          v-if="varPickerOptions.length"
          :vars="varPickerOptions"
          :value="model.resultVar.varCode"
          placeholder="选择结果变量..."
          width="100%"
          class="result-field-var"
          @select="v => { model.resultVar.varCode = v.varCode; model.resultVar.varLabel = (v.varObj && v.varObj.varLabel) || v.varCode }"
        />
        <el-input v-else v-model="model.resultVar.varCode" size="mini" placeholder="变量编码" class="result-field-var" />
        <el-input v-model="model.resultVar.varLabel" size="mini" placeholder="结果名称" class="result-field-label" />
        <el-select v-model="model.resultVar.varType" size="mini" class="result-field-type" popper-append-to-body>
          <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>

    <!-- 交叉矩阵 -->
    <div class="act-card" v-if="totalRowCount > 0 && totalColCount > 0">
      <div class="act-card-title"><i class="el-icon-s-grid" /> 交叉矩阵（{{ totalRowCount }} × {{ totalColCount }}）</div>
      <div class="act-matrix-wrap">
        <table class="act-matrix">
          <thead>
            <!-- 多级列表头：每个列维度一行 -->
            <tr v-for="(headerRow, level) in colHeaderRows" :key="'ch-level-' + level">
              <!-- 左上角单元格仅在第一行显示 -->
              <th
                v-if="level === 0"
                class="corner-cell"
                :rowspan="colDimLevels"
                :colspan="rowDimLevels"
              >
                <div class="corner-inner">
                  <div class="corner-row-label">{{ rowDimLabel }}</div>
                  <div class="corner-divider" />
                  <div class="corner-col-label">{{ colDimLabel }}</div>
                </div>
              </th>
              <th
                v-for="(cell, ci) in headerRow"
                :key="'ch-' + level + '-' + ci"
                :colspan="cell.colspan"
                class="col-header-cell"
                :class="{ 'col-header-top': level === 0, 'col-header-bottom': level === colDimLevels - 1 }"
              >
                {{ cell.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(rowCombo, ri) in rowCombinations" :key="'row-' + ri">
              <!-- 多级行表头：每个行维度一列，使用 rowspan 合并 -->
              <td
                v-for="cell in rowHeaderCells[ri]"
                :key="'rh-' + ri + '-' + cell.level"
                :rowspan="cell.rowspan"
                class="row-header-cell"
                :class="{ 'row-header-first': cell.level === 0 }"
              >
                {{ cell.label }}
              </td>
              <td
                v-for="(colCombo, ci) in colCombinations"
                :key="'cell-' + ri + '-' + ci"
                class="data-cell"
              >
                <el-input
                  v-model="cellData[ri][ci]"
                  size="mini"
                  :placeholder="model.resultVar.varType === 'NUMBER' ? '0' : ''"
                  class="cell-input"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 脚本预览 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="mode => scriptMode = mode"
    />

    <!-- 测试弹窗 -->
    <el-dialog title="测试执行" :visible.sync="testVisible" width="600px" append-to-body>
      <p class="test-hint"><i class="el-icon-info" /> 输入测试参数（JSON 格式）</p>
      <el-input v-model="testParamsJson" type="textarea" :rows="6" placeholder='{}' />
      <template slot="footer">
        <el-button size="small" @click="testVisible = false">取消</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="doTest">执行</el-button>
      </template>
      <div v-if="testResult" class="test-result">
        <el-alert
          :title="testResult.success ? '执行成功' : '执行失败'"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false" show-icon style="margin-bottom:10px;"
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
  name: 'AdvancedCrossTable',
  components: { VarPicker, ScriptPanel },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        rowDimensions: [],
        colDimensions: [],
        resultVar: { varCode: '', varLabel: '', varType: 'NUMBER' },
        cells: []
      },
      cellData: [],
      scriptMode: 'visual',
      testVisible: false,
      testParamsJson: '{}',
      testResult: null,
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS
    }
  },
  computed: {
    rowCombinations() { return this.cartesianProduct(this.model.rowDimensions) },
    colCombinations() { return this.cartesianProduct(this.model.colDimensions) },
    totalRowCount() { return this.rowCombinations.length },
    totalColCount() { return this.colCombinations.length },
    /** 列维度层级数 */
    colDimLevels() { return Math.max(1, (this.model.colDimensions || []).length) },
    /** 行维度层级数 */
    rowDimLevels() { return Math.max(1, (this.model.rowDimensions || []).length) },
    /** 行维度名称（拼接所有维度标签） */
    rowDimLabel() {
      return (this.model.rowDimensions || []).map(d => d.varLabel || d.varCode || '').join(' / ') || '行'
    },
    /** 列维度名称 */
    colDimLabel() {
      return (this.model.colDimensions || []).map(d => d.varLabel || d.varCode || '').join(' / ') || '列'
    },
    /**
     * 多级列表头行数组，每一级是一个 [{label, colspan}] 数组。
     * 例如 2 个列维度（客户类型3项 × 纳税人2项），生成2行：
     *   Level 0: [{label:'企业',colspan:2},{label:'个人',colspan:2},{label:'超企',colspan:2}]
     *   Level 1: [{label:'一般',colspan:1},{label:'小规模',colspan:1},...重复3次]
     */
    colHeaderRows() {
      const dims = this.model.colDimensions || []
      if (dims.length === 0) return []
      const rows = []
      for (let level = 0; level < dims.length; level++) {
        const cells = []
        let colspan = 1
        for (let l = level + 1; l < dims.length; l++) {
          colspan *= (dims[l].segments || []).length || 1
        }
        let repeat = 1
        for (let l = 0; l < level; l++) {
          repeat *= (dims[l].segments || []).length || 1
        }
        const segs = dims[level].segments || []
        for (let r = 0; r < repeat; r++) {
          for (const seg of segs) {
            cells.push({ label: seg.label || seg.value || '-', colspan })
          }
        }
        rows.push(cells)
      }
      return rows
    },
    /**
     * 多级行表头。为每个数据行返回需要渲染的 td 列表（含 rowspan）。
     * 仅在某分组首行显示该维度的 td，其余行省略（靠 rowspan 合并）。
     */
    rowHeaderCells() {
      const dims = this.model.rowDimensions || []
      if (dims.length === 0) return []
      const totalRows = this.totalRowCount
      const result = []
      for (let ri = 0; ri < totalRows; ri++) {
        const cells = []
        for (let level = 0; level < dims.length; level++) {
          let rowspan = 1
          for (let l = level + 1; l < dims.length; l++) {
            rowspan *= (dims[l].segments || []).length || 1
          }
          if (ri % rowspan === 0) {
            const combo = this.rowCombinations[ri]
            cells.push({
              label: combo && combo[level] ? (combo[level].label || combo[level].value || '-') : '-',
              rowspan,
              level
            })
          }
        }
        result.push(cells)
      }
      return result
    }
  },
  watch: {
    totalRowCount() { this.syncCellData() },
    totalColCount() { this.syncCellData() }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
    cartesianProduct(dimensions) {
      if (!dimensions || dimensions.length === 0) return []
      let result = [[]]
      for (const dim of dimensions) {
        const segs = dim.segments || []
        if (segs.length === 0) return []
        const newResult = []
        for (const existing of result) {
          for (const seg of segs) {
            newResult.push([...existing, seg])
          }
        }
        result = newResult
      }
      return result
    },
    syncCellData() {
      const rows = this.totalRowCount
      const cols = this.totalColCount
      if (rows === 0 || cols === 0) { this.cellData = []; return }
      const newData = []
      for (let r = 0; r < rows; r++) {
        const row = []
        for (let c = 0; c < cols; c++) {
          row.push(this.cellData[r] && this.cellData[r][c] != null ? this.cellData[r][c] : '')
        }
        newData.push(row)
      }
      this.cellData = newData
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          const parsed = JSON.parse(content.modelJson)
          this.model = parsed
          if (parsed.cells) {
            this.cellData = this.flattenCells(parsed.cells)
          }
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.normalizeModel()
        this.syncCellData()
        this.contentLoaded = true
      }
    },
    flattenCells(cells) {
      if (!Array.isArray(cells)) return []
      return cells.map(row => {
        if (!Array.isArray(row)) return []
        return row.map(cell => {
          if (Array.isArray(cell)) return cell[0] != null ? String(cell[0]) : ''
          return cell != null ? String(cell) : ''
        })
      })
    },
    normalizeModel() {
      if (!this.model.rowDimensions) this.$set(this.model, 'rowDimensions', [])
      if (!this.model.colDimensions) this.$set(this.model, 'colDimensions', [])
      if (!this.model.resultVar) this.$set(this.model, 'resultVar', { varCode: '', varLabel: '', varType: 'NUMBER' })
    },
    applyVarToDim(variable, dimKey, di) {
      if (!variable) return
      const dim = this.model[dimKey][di]
      dim.varCode = variable.varCode
      dim.varLabel = (variable.varObj && variable.varObj.varLabel) || variable.varLabel || variable.varCode
      dim.varType = variable.varType || 'STRING'
      if (variable.varType === 'ENUM') {
        const options = this.getVarOptions(variable.varCode)
        if (options.length > 0) {
          dim.segments = options.map(o => ({ label: o.label || o.value, operator: '==', value: o.value }))
        }
      }
    },
    addDimension(type) {
      const dims = type === 'row' ? this.model.rowDimensions : this.model.colDimensions
      dims.push({ varCode: '', varLabel: '', varType: 'STRING', segments: [{ label: '', operator: '==', value: '' }] })
    },
    removeDimension(type, index) {
      const dims = type === 'row' ? this.model.rowDimensions : this.model.colDimensions
      dims.splice(index, 1)
    },
    addSegment(dim) {
      dim.segments.push({ label: '', operator: '==', value: '' })
    },
    buildSaveModel() {
      const saveModel = JSON.parse(JSON.stringify(this.model))
      saveModel.cells = JSON.parse(JSON.stringify(this.cellData))
      return saveModel
    },
    async handleSave() {
      const saveModel = this.buildSaveModel()
      await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(saveModel) })
      this.$message.success('保存成功')
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (res && res.data && res.data.success) {
        this.$message.success('编译成功')
        if (this.$refs.scriptPanel) this.$refs.scriptPanel.refresh()
      } else {
        this.$message.error('编译失败: ' + (res && res.data ? res.data.errorMessage : '未知错误'))
      }
    },
    handleTest() {
      this.testParamsJson = '{}'
      this.testResult = null
      this.testVisible = true
    },
    async doTest() {
      let params = {}
      try { params = JSON.parse(this.testParamsJson || '{}') } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await executeRule({ definitionId: this.definitionId, params })
      this.testResult = res && res.data ? res.data : res
    }
  }
}
</script>

<style lang="scss" scoped>
.act-designer {
  background: #f3f3f3;
  padding: 20px;
  min-height: 100%;
}
.act-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-radius: 4px; padding: 14px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 16px;
  flex-wrap: wrap; gap: 8px;
}
.act-title-area { display: flex; align-items: center; }
.act-title-icon { font-size: 18px; color: #722ed1; margin-right: 8px; }
.act-title { font-size: 16px; font-weight: bold; color: #282828; }
.act-toolbar { display: flex; align-items: center; gap: 6px; }

/* 维度配置：行+列并排 */
.act-dim-row {
  display: flex; gap: 16px; margin-bottom: 16px;
}
.act-dim-panel {
  flex: 1; min-width: 0; background: #fff; border-radius: 6px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

/* 结果变量：独立一行 */
.act-result-row {
  background: #fff; border-radius: 6px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  margin-bottom: 16px;
}
.result-config {
  display: flex; align-items: center; gap: 8px; padding: 8px 0;
}
.result-field-var { flex: 3; min-width: 0; }
.result-field-label { flex: 2; min-width: 0; }
.result-field-type { flex: 1; min-width: 100px; }

.dim-panel-header {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; font-weight: 600; color: #555; margin-bottom: 10px;
  gap: 8px;
}

/* 维度卡片内：表头自适应 */
.dim-config-card {
  border: 1px solid #e8e8e8; border-radius: 4px; margin-bottom: 10px; padding: 8px;
}
.dim-config-header {
  display: flex; align-items: center; gap: 6px; margin-bottom: 6px;
}
.dim-field-var { flex: 3; min-width: 0; }
.dim-field-label { flex: 2; min-width: 0; }
.dim-field-type { flex: 1; min-width: 100px; }

/* 分段条件行自适应 */
.segments-area { padding-left: 4px; }
.segment-row {
  display: flex; align-items: center; gap: 6px; margin-bottom: 6px;
}
.seg-op { flex: 2; min-width: 80px; }
.seg-val { flex: 2; min-width: 0; }
.seg-label { flex: 3; min-width: 0; }
.seg-sep { color: #999; flex-shrink: 0; }

/* 矩阵 */
.act-card {
  background: #fff; border-radius: 4px; padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 16px;
}
.act-card-title {
  font-size: 14px; font-weight: bold; color: #333; margin-bottom: 14px;
  display: flex; align-items: center; gap: 6px;
  i { color: #722ed1; }
}
.act-matrix-wrap { overflow-x: auto; border-radius: 6px; border: 1px solid #e8e8e8; }
.act-matrix {
  border-collapse: collapse; width: 100%;
  th, td { border: 1px solid #e8e8e8; padding: 6px; vertical-align: middle; text-align: center; }
}
.corner-cell {
  background: #f5f5f5; min-width: 100px; position: relative; padding: 0; min-height: 56px;
}
.corner-inner { position: relative; width: 100%; height: 56px; }
.corner-row-label {
  position: absolute; bottom: 6px; left: 8px; font-size: 11px; color: #888;
}
.corner-col-label {
  position: absolute; top: 6px; right: 8px; font-size: 11px; color: #888;
}
.corner-divider {
  position: absolute; top: 0; left: 0; right: 0; bottom: 0;
  background: linear-gradient(to bottom right, transparent calc(50% - 0.5px), #d0d0d0, transparent calc(50% + 0.5px));
  pointer-events: none;
}
.col-header-cell {
  background: #e8f3ff; min-width: 90px; font-size: 12px; font-weight: 600; color: #333;
  white-space: nowrap;
}
.col-header-top { background: #daeeff; }
.row-header-cell {
  background: #f0fff4; min-width: 80px; font-size: 12px; font-weight: 500; color: #333;
  white-space: nowrap;
}
.row-header-first { background: #e0f5e9; font-weight: 600; }
.data-cell { background: #fff; min-width: 90px; }
.cell-input ::v-deep input { text-align: center; font-weight: 500; }

.test-hint { font-size: 12px; color: #909399; margin-bottom: 8px; }
.test-result { margin-top: 16px; }
</style>
