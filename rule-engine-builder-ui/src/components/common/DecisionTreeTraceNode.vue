<template>
  <div class="dtt">
    <!-- 决策节点标题 -->
    <div class="dtt-decision" :class="{ 'dtt-decision--root': isRoot }">
      <span class="dtt-decision-icon">判</span>
      <span class="dtt-decision-label">{{ node.label }}？</span>
    </div>
    <!-- 分支列表 -->
    <div class="dtt-branches" v-if="node.children && node.children.length">
      <div
        v-for="(child, idx) in node.children"
        :key="idx"
        class="dtt-branch"
        :class="'dtt-branch--' + child.status"
      >
        <!-- 分支头：状态圆点 + 标签 + 条件文本 + 状态标记 -->
        <div class="dtt-branch-head" @click="toggle(idx)">
          <span class="dtt-dot" :class="'dtt-dot--' + child.status"></span>
          <span class="dtt-branch-label">{{ child.branchLabel || '其他' }}</span>
          <span class="dtt-cond" v-if="child.conditionText">({{ child.conditionText }})</span>
          <span class="dtt-status-tag" :class="'dtt-tag--' + child.status">{{ statusLabel(child.status) }}</span>
          <i v-if="child.children && child.children.length" class="dtt-toggle el-icon-arrow-right" :class="{ 'is-open': isOpen(idx) }" />
        </div>
        <!-- 分支体：叶子 或 子决策树 -->
        <div class="dtt-branch-body" v-show="isOpen(idx)">
          <!-- 子决策树 -->
          <decision-tree-trace-node
            v-if="child.children && child.children.length"
            :node="child"
            :is-root="false"
            :var-map="varMap"
          />
          <!-- 叶子节点 -->
          <div v-else class="dtt-leaf" :class="{ 'dtt-leaf--hit': child.status === 'hit' }">
            <span class="dtt-leaf-icon">{{ child.status === 'hit' ? '\u2705' : '\u25CB' }}</span>
            <span class="dtt-leaf-task" v-if="child.taskName">{{ child.taskName }}：</span>
            <span class="dtt-leaf-var" v-if="child.resultVar">{{ child.resultVarLabel || varMap[child.resultVar] || child.resultVar }} = </span>
            <span class="dtt-leaf-val">{{ child.label }}</span>
          </div>
          <!-- 叶子节点关联的函数调用 -->
          <div v-if="!child.children && child.funcCalls && child.funcCalls.length" class="dtt-func-calls">
            <div v-for="(fc, fci) in child.funcCalls" :key="fci" class="dtt-func-item" :class="{ 'dtt-func-item--hit': child.status === 'hit' }">
              <span class="dtt-func-icon">ƒ</span>
              <code class="dtt-func-name">{{ funcDisplayName(fc.name) }}</code>
              <span class="dtt-func-expr">({{ fc.args.map(function(a){ return a.label + '=' + a.value }).join(', ') }})</span>
              <span class="dtt-func-result" v-if="fc.value !== undefined && fc.value !== null">→ {{ typeof fc.value === 'object' ? JSON.stringify(fc.value) : String(fc.value) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
/**
 * 决策树追踪递归节点组件（垂直缩进层级树）
 * 支持三态图标(命中/阻断/跳过)、条件文本展示、折叠能力
 */
export default {
  name: 'DecisionTreeTraceNode',
  props: {
    /** 树节点：{ label, children: [{ branchLabel, hit, status, conditionText, resultVar, resultVarLabel, label, children }] } */
    node: { type: Object, required: true },
    /** 是否为根节点 */
    isRoot: { type: Boolean, default: false },
    /** 变量码→中文标签映射 */
    varMap: { type: Object, default: function () { return {} } },
    /** 函数名→中文展示（含内置映射与项目配置，由 TraceTree 传入） */
    functionNameMap: { type: Object, default: function () { return {} } }
  },
  data: function () {
    return {
      collapsed: {}
    }
  },
  created: function () {
    if (!this.node || !this.node.children) return
    for (var i = 0; i < this.node.children.length; i++) {
      var ch = this.node.children[i]
      if (ch.status !== 'hit' && ch.status !== 'blocked') {
        this.$set(this.collapsed, i, true)
      }
    }
  },
  methods: {
    /**
     * 将追踪中的函数标识转为中文展示名
     */
    funcDisplayName: function (code) {
      if (code === undefined || code === null || code === '') return '?'
      var s = String(code)
      var m = this.functionNameMap
      return (m && m[s]) || s
    },
    statusLabel: function (status) {
      if (status === 'hit') return '命中'
      if (status === 'blocked') return '不满足'
      return '跳过'
    },
    isOpen: function (idx) {
      return !this.collapsed[idx]
    },
    toggle: function (idx) {
      this.$set(this.collapsed, idx, !this.collapsed[idx])
    }
  }
}
</script>

<style scoped>
.dtt {
  font-size: 13px;
  color: #303133;
}

/* 决策节点标题 */
.dtt-decision {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 6px;
  margin-bottom: 4px;
}
.dtt-decision--root {
  background: #e6f0ff;
  border-color: #85a5ff;
}
.dtt-decision-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: #597ef7;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.dtt-decision-label {
  font-weight: 600;
  font-size: 13px;
  color: #1d39c4;
}
.dtt-decision--root .dtt-decision-label {
  font-size: 14px;
}

/* 分支列表 */
.dtt-branches {
  padding-left: 16px;
  border-left: 2px solid #e4e7ed;
  margin-left: 10px;
}

/* 分支项 */
.dtt-branch {
  position: relative;
  padding: 4px 0;
}
.dtt-branch::before {
  content: '';
  position: absolute;
  top: 16px;
  left: -16px;
  width: 14px;
  height: 0;
  border-top: 2px solid #e4e7ed;
}
.dtt-branch--hit::before {
  border-top-color: #52c41a;
  border-top-width: 2px;
}
.dtt-branch--hit {
  border-left: none;
}

/* 命中分支的父级连线变绿 */
.dtt-branches:has(> .dtt-branch--hit) {
  border-left-color: #52c41a;
}

/* 分支头 */
.dtt-branch-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s;
  user-select: none;
}
.dtt-branch-head:hover {
  background: #f5f7fa;
}

/* 状态圆点 */
.dtt-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 2px solid;
}
.dtt-dot--hit {
  background: #52c41a;
  border-color: #52c41a;
}
.dtt-dot--blocked {
  background: #ff4d4f;
  border-color: #ff4d4f;
}
.dtt-dot--skipped {
  background: #d9d9d9;
  border-color: #d9d9d9;
}

/* 分支标签 */
.dtt-branch-label {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}
.dtt-branch--hit > .dtt-branch-head > .dtt-branch-label {
  color: #389e0d;
}

/* 条件文本 */
.dtt-cond {
  font-size: 12px;
  color: #8c8c8c;
  font-family: 'Consolas', 'Monaco', monospace;
}

/* 状态标签 */
.dtt-status-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  font-weight: 500;
  white-space: nowrap;
}
.dtt-tag--hit {
  background: #f6ffed;
  color: #52c41a;
  border: 1px solid #b7eb8f;
}
.dtt-tag--blocked {
  background: #fff2f0;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
}
.dtt-tag--skipped {
  background: #fafafa;
  color: #bfbfbf;
  border: 1px solid #e8e8e8;
}

/* 折叠箭头 */
.dtt-toggle {
  font-size: 12px;
  color: #bfbfbf;
  transition: transform 0.2s;
  margin-left: 2px;
}
.dtt-toggle.is-open {
  transform: rotate(90deg);
}

/* 分支体 */
.dtt-branch-body {
  padding-left: 16px;
  margin-left: 4px;
}

/* 叶子节点 */
.dtt-leaf {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  margin: 4px 0 2px;
  border-radius: 4px;
  font-size: 12px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  color: #595959;
}
.dtt-leaf--hit {
  background: #f6ffed;
  border-color: #b7eb8f;
  color: #389e0d;
  font-weight: 600;
}
.dtt-leaf-icon {
  font-size: 13px;
  flex-shrink: 0;
}
.dtt-leaf-task {
  color: #595959;
  font-weight: 500;
}
.dtt-leaf--hit .dtt-leaf-task {
  color: #389e0d;
}
.dtt-leaf-var {
  color: #8c8c8c;
  font-family: 'Consolas', 'Monaco', monospace;
}
.dtt-leaf-val {
  font-family: 'Consolas', 'Monaco', monospace;
}
.dtt-leaf--hit .dtt-leaf-val {
  color: #237804;
}

/* 函数调用展示 */
.dtt-func-calls {
  margin-top: 4px;
  margin-left: 4px;
}
.dtt-func-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  margin: 2px 0;
  border-radius: 4px;
  font-size: 11px;
  background: #f9f0ff;
  border: 1px solid #d3adf7;
  color: #531dab;
}
.dtt-func-item--hit {
  background: #f0f5ff;
  border-color: #adc6ff;
  color: #1d39c4;
}
.dtt-func-icon {
  font-weight: 700;
  font-size: 13px;
  font-style: italic;
  color: #722ed1;
  flex-shrink: 0;
}
.dtt-func-item--hit .dtt-func-icon {
  color: #2f54eb;
}
.dtt-func-name {
  font-weight: 600;
  font-family: 'Consolas', 'Monaco', monospace;
}
.dtt-func-expr {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #8c8c8c;
}
.dtt-func-item--hit .dtt-func-expr {
  color: #597ef7;
}
.dtt-func-result {
  font-weight: 600;
  color: #389e0d;
  margin-left: 4px;
}
</style>
