<template>
  <el-dialog :visible.sync="dialogVisible" title="输出变量初始值配置" width="600px" :close-on-click-modal="false" append-to-body>
    <div v-if="allOutputVars.length === 0" class="hint-box">
      <i class="el-icon-info" /> 当前没有输出变量，请先配置赋值操作
    </div>
    <el-table v-else :data="tableData" size="mini" border style="width: 100%">
      <el-table-column prop="varCode" label="变量名" width="160" />
      <el-table-column label="类型" width="120">
        <template slot-scope="scope">
          <el-select v-model="scope.row.initType" size="mini" @change="onTypeChange(scope.row)">
            <el-option label="数字" value="number" />
            <el-option label="字符串" value="string" />
            <el-option label="布尔" value="boolean" />
            <el-option label="null" value="null" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="初始值" min-width="200">
        <template slot-scope="scope">
          <el-input
            v-if="scope.row.initType === 'number'"
            v-model="scope.row.initValue"
            size="mini"
            placeholder="如 0"
            @input="emitUpdate"
          />
          <el-input
            v-else-if="scope.row.initType === 'string'"
            v-model="scope.row.initValue"
            size="mini"
            placeholder="如 未匹配（无需加引号）"
            @input="emitUpdate"
          />
          <el-select
            v-else-if="scope.row.initType === 'boolean'"
            v-model="scope.row.initValue"
            size="mini"
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
      <i class="el-icon-info" /> 初始值会生成如 <code>taxRate = 0</code> 或 <code>result = ""</code> 的语句
    </div>
    <template slot="footer">
      <el-button size="small" @click="dialogVisible = false">关闭</el-button>
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
          let val = item.initValue
          const type = item.initType || this.inferType(val)
          if (type === 'string' && typeof val === 'string' && val.length >= 2 && val.startsWith('"') && val.endsWith('"')) {
            val = val.slice(1, -1).replace(/\\"/g, '"').replace(/\\\\/g, '\\')
          }
          initMap[item.varCode] = val
          typeMap[item.varCode] = type
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
        .filter(row => {
          if (row.initType === 'null') return false
          if (row.initValue === null || row.initValue === undefined) return false
          if (row.initValue === '' && row.initType !== 'string') return false
          return true
        })
        .map(row => ({
          varCode: row.varCode,
          initValue: this.formatInitValue(row),
          initType: row.initType
        }))
      this.$emit('update:output-var-inits', inits)
    },
    formatInitValue(row) {
      if (row.initType === 'null') return ''
      if (row.initType === 'string') {
        const val = String(row.initValue || '')
        return '"' + val.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'
      }
      if (row.initValue === '') return ''
      if (row.initType === 'number') return String(row.initValue)
      if (row.initType === 'boolean') return row.initValue === 'true' ? 'true' : 'false'
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
