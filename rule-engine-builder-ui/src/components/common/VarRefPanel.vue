<template>
  <div class="var-ref-panel">
    <div class="panel-title">
      <i class="el-icon-connection" /> 可用变量
      <el-tooltip content="点击变量名可复制到剪贴板" placement="top" effect="light">
        <i class="el-icon-question tip-icon" />
      </el-tooltip>
    </div>
    <div v-if="loading" class="panel-loading">
      <i class="el-icon-loading" /> 加载中...
    </div>
    <div v-else-if="vars.length === 0" class="panel-empty">
      暂无变量，请先在变量管理中为该项目添加变量
    </div>
    <div v-else class="var-list">
      <div
        v-for="v in vars"
        :key="v.varCode"
        class="var-item"
        :title="v.description || v.varLabel"
        @click="copyCode(v.varCode)"
      >
        <code class="var-code">{{ v.varCode }}</code>
        <span class="var-label">{{ v.varLabel }}</span>
        <el-tag :type="typeColor(v.varType)" size="mini" class="var-tag">{{ typeLabel(v.varType) }}</el-tag>
        <span v-if="v.exampleValue" class="var-example">例：{{ v.exampleValue }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import { varTypeLabel, varTypeTagColor } from '@/constants/varTypes'

export default {
  name: 'VarRefPanel',
  props: {
    vars: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false }
  },
  methods: {
    /** 与变量管理一致的类型中文名 */
    typeLabel(t) {
      return varTypeLabel(t)
    },
    /** 与变量管理一致的标签配色 */
    typeColor(t) {
      return varTypeTagColor(t)
    },
    copyCode(code) {
      if (navigator.clipboard) {
        navigator.clipboard.writeText(code).then(() => {
          this.$message({ message: '已复制变量编码：' + code, type: 'success', duration: 1500 })
        })
      } else {
        this.$emit('insert', code)
      }
    }
  }
}
</script>

<style scoped>
.var-ref-panel {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  overflow: hidden;
  font-size: 12px;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  background: #f5f5f5;
  font-weight: bold;
  color: #555;
  border-bottom: 1px solid #e8e8e8;
}
.tip-icon {
  color: #bbb;
  cursor: pointer;
  margin-left: auto;
  &:hover { color: #1890ff; }
}
.panel-loading, .panel-empty {
  padding: 12px;
  text-align: center;
  color: #bbb;
}
.var-list {
  max-height: 220px;
  overflow-y: auto;
}
.var-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.15s;
  &:last-child { border-bottom: none; }
  &:hover { background: #f0f7ff; }
}
.var-code {
  font-family: 'Consolas', monospace;
  color: #c41d7f;
  background: #fff0f6;
  padding: 1px 4px;
  border-radius: 3px;
  flex-shrink: 0;
  font-size: 11px;
}
.var-label {
  color: #555;
  flex-shrink: 0;
}
.var-tag {
  flex-shrink: 0;
}
.var-example {
  color: #bbb;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}
</style>
