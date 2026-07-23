<template>
  <div
    class="dt-chip"
    :class="{ 'is-selected': selected }"
    @click.stop="$emit('click')"
    @contextmenu.stop.prevent="$emit('contextmenu', $event)"
  >
    <span :data-dt-anchor="'edge-in-' + edgeId" class="dt-chip__anchor dt-chip__anchor--in" />
    <div class="dt-chip__body">
      <span class="dt-chip__check"><i class="el-icon-check" /></span>
      <span class="dt-chip__text">{{ displayLine }}</span>
      <i class="el-icon-arrow-down dt-chip__caret" />
    </div>
    <span :data-dt-anchor="'edge-out-' + edgeId" class="dt-chip__anchor dt-chip__anchor--out" />
  </div>
</template>

<script>
/**
 * 决策树分支上的条件条（对应一条出边）
 */
export default {
  name: 'DecisionTreeBranchChip',
  props: {
    edgeId: { type: String, required: true },
    branchName: { type: String, default: '' },
    conditionExpression: { type: String, default: '' },
    selected: { type: Boolean, default: false }
  },
  computed: {
    /**
     * 条件条展示：分支名或表达式摘要
     */
    displayLine() {
      const n = (this.branchName || '').trim()
      const ex = (this.conditionExpression || '').trim()
      if (n) return n
      if (ex) return ex
      return '默认分支'
    }
  }
}
</script>

<style lang="scss" scoped>
.dt-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 8px;
  border: 2px solid #d9d9d9;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}
.dt-chip.is-selected {
  border-color: #1890ff;
  box-shadow: 0 0 0 1px #1890ff;
}
.dt-chip__anchor {
  width: 1px;
  height: 1px;
  opacity: 0;
  flex-shrink: 0;
}
.dt-chip__body {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
}
.dt-chip__check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.dt-chip__text {
  font-size: 13px;
  color: #595959;
  white-space: nowrap;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dt-chip__caret {
  color: #999;
  font-size: 12px;
}
</style>
