<template>
  <div class="hdt-wrap">
    <div
      ref="scrollRoot"
      class="hdt-scroll"
      data-dt-scroll-root
      @scroll="bumpConnector"
      @click="onScrollAreaClick"
    >
      <decision-tree-connector-layer
        class="hdt-svg-layer"
        :container-el="scrollEl"
        :segments="connectorSegments"
        :tick="connectorTick"
      />
      <div class="hdt-inner">
        <hdt-layout-fragment
          v-if="layoutRoot"
          :layout="layoutRoot"
          :selected-node-id="selectedNodeId"
          :selected-edge-id="selectedEdgeId"
          @select-node="$emit('select-node', $event)"
          @select-edge="$emit('select-edge', $event)"
          @tree-command="$emit('tree-command', $event)"
          @node-contextmenu="onNodeContextmenu"
          @edge-contextmenu="onEdgeContextmenu"
        />
        <div v-else class="hdt-empty">无开始节点，请重置或加载有效模型</div>
      </div>
    </div>

    <ul
      v-show="ctxMenu.visible"
      class="hdt-ctx-menu"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
    >
      <li
        v-for="(it, idx) in ctxMenu.items"
        :key="idx"
        @click="runCtxItem(it)"
      >
        {{ it.label }}
      </li>
    </ul>
  </div>
</template>

<script>
import HdtLayoutFragment from './HdtLayoutFragment.vue'
import DecisionTreeConnectorLayer from './DecisionTreeConnectorLayer.vue'
import {buildTreeLayout} from './treeLayout'
import {collectLayoutSegments} from './segmentCollector'
import {findStartId} from './treeGraphModel'

/**
 * 横向决策树：滚动区 + 连线层 + 右键菜单壳层
 */
export default {
  name: 'HorizontalDecisionTree',
  components: { HdtLayoutFragment, DecisionTreeConnectorLayer },
  props: {
    nodes: { type: Array, default: () => [] },
    edges: { type: Array, default: () => [] },
    selectedNodeId: { type: String, default: '' },
    selectedEdgeId: { type: String, default: '' }
  },
  data() {
    return {
      connectorTick: 0,
      scrollEl: null,
      ctxMenu: {
        visible: false,
        x: 0,
        y: 0,
        items: [],
        payload: null
      }
    }
  },
  computed: {
    /**
     * 从开始节点构建布局根
     */
    layoutRoot() {
      const sid = findStartId(this.nodes)
      if (!sid) return null
      return buildTreeLayout(this.nodes, this.edges, sid)
    },
    /**
     * 连线锚点线段
     */
    connectorSegments() {
      if (!this.layoutRoot) return []
      return collectLayoutSegments(this.layoutRoot)
    }
  },
  watch: {
    nodes: { deep: true, handler: 'bumpConnector' },
    edges: { deep: true, handler: 'bumpConnector' }
  },
  mounted() {
    this.scrollEl = this.$refs.scrollRoot
    this.bumpConnector()
    document.addEventListener('click', this.closeCtxMenu)
  },
  beforeDestroy() {
    document.removeEventListener('click', this.closeCtxMenu)
  },
  methods: {
    /**
     * 点击画布空白处（未点在节点卡片或条件条上）时收起右侧属性面板
     */
    onScrollAreaClick(e) {
      const t = e.target
      if (!t || !t.closest) return
      if (t.closest('.dt-card') || t.closest('.dt-chip')) return
      this.$emit('dismiss-selection')
    },
    /**
     * 触发连线层重算
     */
    bumpConnector() {
      this.connectorTick++
    },
    /**
     * 关闭右键菜单
     */
    closeCtxMenu() {
      this.ctxMenu.visible = false
      this.ctxMenu.items = []
      this.ctxMenu.payload = null
    },
    /**
     * 节点右键：组装菜单项
     */
    onNodeContextmenu({ event, nodeId, backendKind }) {
      event.preventDefault()
      const items = []
      if (backendKind === 'start') {
        items.push({ label: '添加判断', cmd: 'addChildDecision', nodeId })
      } else if (backendKind === 'decision') {
        items.push({ label: '添加条件', cmd: 'addBranch', nodeId })
        items.push({ label: '添加变量', cmd: 'addNestedDecision', nodeId })
        items.push({ label: '添加动作', cmd: 'addChildTask', nodeId })
        items.push({ label: '删除', cmd: 'deleteNode', nodeId })
      } else if (backendKind === 'task') {
        items.push({ label: '添加判断', cmd: 'addChildDecision', nodeId })
        items.push({ label: '添加动作', cmd: 'addChildTask', nodeId })
        items.push({ label: '删除', cmd: 'deleteNode', nodeId })
      }
      if (!items.length) return
      this.openCtx(event, items, { nodeId, backendKind })
    },
    /**
     * 边右键
     */
    onEdgeContextmenu({ event, edgeId }) {
      event.preventDefault()
      const items = [
        { label: '删除分支', cmd: 'deleteEdge', edgeId }
      ]
      this.openCtx(event, items, { edgeId })
    },
    /**
     * 显示菜单
     */
    openCtx(event, items, payload) {
      this.ctxMenu.visible = true
      this.ctxMenu.x = event.clientX
      this.ctxMenu.y = event.clientY
      this.ctxMenu.items = items
      this.ctxMenu.payload = payload
    },
    /**
     * 执行菜单项并转发 tree-command
     */
    runCtxItem(it) {
      this.closeCtxMenu()
      if (!it || !it.cmd) return
      const p = {}
      if (it.nodeId) p.nodeId = it.nodeId
      if (it.edgeId) p.edgeId = it.edgeId
      this.$emit('tree-command', Object.assign({ cmd: it.cmd }, p))
    }
  }
}
</script>

<style lang="scss" scoped>
.hdt-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 200px;
}
.hdt-scroll {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: auto;
  background: #f7f8fa;
  z-index: 1;
}
.hdt-scroll .dt-connect-svg {
  position: absolute;
  left: 0;
  top: 0;
}
.hdt-inner {
  position: relative;
  z-index: 2;
  display: inline-block;
  min-width: 100%;
  min-height: 100%;
  padding: 24px 32px 48px;
  box-sizing: border-box;
}
.hdt-svg-layer {
  z-index: 0;
}
.hdt-empty {
  padding: 40px;
  color: #999;
  font-size: 14px;
}
.hdt-ctx-menu {
  position: fixed;
  z-index: 9999;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  min-width: 120px;
}
.hdt-ctx-menu li {
  padding: 8px 16px;
  font-size: 13px;
  color: #333;
  cursor: pointer;
}
.hdt-ctx-menu li:hover {
  background: #f0f7ff;
  color: #1890ff;
}
</style>
