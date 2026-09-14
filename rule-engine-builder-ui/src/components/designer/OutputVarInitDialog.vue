<template>
  <el-dialog v-model="dialogVisible" title="输出变量初始值配置" width="600px" :close-on-click-modal="false">
    <div v-if="allOutputVars.length === 0" class="hint-box">
      <i class="el-icon-info" /> 当前没有输出变量，请先配置赋值操作
    </div>
    <el-table v-else :data="tableData" size="small" border style="width: 100%">
      <el-table-column prop="varCode" label="变量名" width="160" />
      <el-table-column label="类型" width="120">
        <template #default="scope">
          <el-select v-model="scope.row.initType" size="small" @change="onTypeChange(scope.row)">
            <el-option label="数字" value="number" />
            <el-option label="字符串" value="string" />
            <el-option label="布尔" value="boolean" />
            <el-option label="null" value="null" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="初始值" min-width="200">
        <template #default="scope">
          <el-input
            v-if="scope.row.initType === 'number'"
            v-model="scope.row.initValue"
            size="small"
            placeholder="如 0"
            @input="emitUpdate"
          />
          <el-input
            v-else-if="scope.row.initType === 'string'"
            v-model="scope.row.initValue"
            size="small"
            placeholder="如 未匹配（无需加引号）"
            @input="emitUpdate"
          />
          <el-select
            v-else-if="scope.row.initType === 'boolean'"
            v-model="scope.row.initValue"
            size="small"
            @change="emitUpdate"
          >
            <el-option label="true" value="true" />
            <el-option label="false" value="false" />
          </el-select>
          <span v-else class="text-muted">null</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="hint-box" style="margin-top:10px;">
      <i class="el-icon-info" /> 初始值会生成如 <code>taxRate = 0</code> 或 <code>result = "未匹配"</code> 的语句，留空则默认为 null
    </div>
    <template #footer>
      <el-button size="small" @click="$emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: 'OutputVarInitDialog',
  props: {
    visible: { type: Boolean, default: false },
    allOutputVars: { type: Array, default: () => [] },
    outputVarInits: { type: Array, default: () => [] }
  },
  data() {
    return {
      tableData: []
    }
  },
  computed: {
    dialogVisible: {
      get() { return this.visible },
      set(val) { this.$emit('update:visible', val) }
    }
  },
  watch: {
    visible(val) {
      if (val) this.buildTableData()
    },
    allOutputVars: {
      deep: true,
      handler() {
        if (this.visible) this.buildTableData()
      }
    }
  },
  methods: {
    buildTableData() {
      const initMap = {}
      const typeMap = {}
      ;(this.outputVarInits || []).forEach(item => {
        if (item && item.varCode) {
          initMap[item.varCode] = item.initValue
          typeMap[item.varCode] = item.initType || this.inferType(item.initValue)
        }
      })
      this.tableData = this.allOutputVars.map(varCode => ({
        varCode,
        initType: typeMap[varCode] || 'null',
        initValue: initMap[varCode] || ''
      }))
    },
    inferType(value) {
      if (value === null || value === undefined || value === '' || value === 'null') return 'null'
      if (value === 'true' || value === 'false') return 'boolean'
      if (isFinite(Number(value))) return 'number'
      return 'string'
    },
    onTypeChange(row) {
      if (row.initType === 'null') {
        row.initValue = ''
      } else if (row.initType === 'boolean' && !row.initValue) {
        row.initValue = 'true'
      }
      this.emitUpdate()
    },
    emitUpdate() {
      const inits = this.tableData
        .filter(row => row.initType !== 'null' && row.initValue !== '' && row.initValue !== null && row.initValue !== undefined)
        .map(row => ({
          varCode: row.varCode,
          initValue: this.formatInitValue(row),
          initType: row.initType
        }))
      this.$emit('update:outputVarInits', inits)
    },
    formatInitValue(row) {
      if (row.initType === 'null' || row.initValue === '') return ''
      if (row.initType === 'number') return String(row.initValue)
      if (row.initType === 'boolean') return row.initValue === 'true' ? 'true' : 'false'
      if (row.initType === 'string') {
        const val = String(row.initValue)
        return '"' + val.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
      }
      return String(row.initValue)
    }
  }
}
</script>

<style scoped>
.hint-box {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.hint-box code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 2px;
  font-size: 12px;
}
.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}
</style>
