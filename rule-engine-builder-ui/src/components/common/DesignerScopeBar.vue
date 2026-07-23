<template>
  <div class="designer-scope-bar">
    <span class="scope-label">作用域</span>
    <el-select
      :value="value"
      filterable
      placeholder="选择省份 / 组织"
      size="small"
      class="scope-select"
      popper-class="designer-scope-popper"
      @change="onChange"
    >
      <el-option
        v-for="o in options"
        :key="o.value"
        :label="optionLabel(o)"
        :value="o.value"
      />
    </el-select>
    <el-tag v-if="configured" size="mini" type="success" class="scope-tag">已配置</el-tag>
    <el-tag v-else size="mini" type="warning" class="scope-tag">首次保存将创建该作用域</el-tag>
  </div>
</template>

<script>
/**
 * 设计器顶部：选择规则作用域 compId（与后端 scope_comp_id 对应）
 */
export default {
  name: 'DesignerScopeBar',
  props: {
    value: { type: String, default: '0' },
    options: { type: Array, default: () => [] },
    /** 当前作用域是否已有 rule_definition_content 行 */
    configured: { type: Boolean, default: true }
  },
  methods: {
    /**
     * 下拉仅展示中文名称，value 仍为 compId
     */
    optionLabel(o) {
      return o.label
    },
    onChange(v) {
      this.$emit('input', v)
    }
  }
}
</script>

<style lang="scss" scoped>
.designer-scope-bar {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 8px;
  margin-right: 12px;
}
.scope-label {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
}
.scope-select {
  width: 280px;
  max-width: 42vw;
}
.scope-tag {
  margin-left: 2px;
}
</style>
