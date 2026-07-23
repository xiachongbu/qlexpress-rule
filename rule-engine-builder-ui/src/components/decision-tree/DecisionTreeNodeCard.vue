<template>
  <div
    class="dt-card"
    :class="cardClass"
    @click.stop="$emit('click')"
    @contextmenu.stop.prevent="$emit('contextmenu', $event)"
  >
    <span
      v-if="kind === 'task'"
      class="dt-card__minus"
      title="删除节点"
      @click.stop="$emit('remove')"
    >−</span>
    <div class="dt-card__anchors">
      <span :data-dt-anchor="'in-' + nodeId" class="dt-card__anchor dt-card__anchor--in" />
      <div class="dt-card__body">
        <span class="dt-card__check"><i class="el-icon-check" /></span>
        <span class="dt-card__text">{{ displayText }}</span>
        <i class="el-icon-arrow-down dt-card__caret" />
      </div>
      <span :data-dt-anchor="'out-' + nodeId" class="dt-card__anchor dt-card__anchor--out" />
    </div>
  </div>
</template>

<script>
/**
 * 决策树横向视图中的圆角节点卡片（开始/判断/任务）
 */
export default {
  name: 'DecisionTreeNodeCard',
  props: {
    /** 节点 id，用于连线锚点 */
    nodeId: { type: String, required: true },
    /** start | decision | task */
    kind: { type: String, required: true },
    /** 主文案 */
    label: { type: String, default: '' }
  },
  computed: {
    /**
     * 卡片展示文案
     */
    displayText() {
      return this.label || this.defaultLabel
    },
    /**
     * 无传入 label 时的占位
     */
    defaultLabel() {
      const m = { start: '开始', decision: '条件判断', task: '执行动作' }
      return m[this.kind] || ''
    },
    /**
     * 修饰类名
     */
    cardClass() {
      return 'dt-card--' + this.kind
    }
  }
}
</script>

<style lang="scss" scoped>
.dt-card {
  position: relative;
  display: inline-flex;
  align-items: center;
  vertical-align: top;
  min-height: 40px;
  border-radius: 8px;
  border: 2px solid #91caff;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}
.dt-card--start {
  border-color: #52c41a;
  min-width: 72px;
}
.dt-card--decision {
  border-color: #1890ff;
}
.dt-card--task {
  border-color: #ff4d4f;
  padding-left: 28px;
}
.dt-card__minus {
  position: absolute;
  left: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #ff4d4f;
  color: #fff;
  font-size: 14px;
  line-height: 16px;
  text-align: center;
  cursor: pointer;
  flex-shrink: 0;
  z-index: 1;
}
.dt-card__anchors {
  display: flex;
  align-items: center;
  gap: 0;
  width: 100%;
}
.dt-card__anchor {
  width: 1px;
  height: 1px;
  flex-shrink: 0;
  pointer-events: none;
  opacity: 0;
}
.dt-card__body {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  min-width: 0;
}
.dt-card__check {
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
.dt-card__text {
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dt-card__caret {
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
}
</style>
