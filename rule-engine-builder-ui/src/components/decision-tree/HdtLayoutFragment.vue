<template>
  <div v-if="layout" class="hdt-frag">
    <!-- 开始 -->
    <template v-if="layout.kind === 'start'">
      <div class="hdt-cell">
        <decision-tree-node-card
          :node-id="layout.id"
          kind="start"
          :label="layout.name"
          @click="$emit('select-node', layout.id)"
          @contextmenu="openNodeMenu($event, layout.id, 'start')"
        />
      </div>
      <div v-if="layout.child" class="hdt-chain">
        <hdt-layout-fragment
          :layout="layout.child"
          :selected-node-id="selectedNodeId"
          :selected-edge-id="selectedEdgeId"
          @select-node="$emit('select-node', $event)"
          @select-edge="$emit('select-edge', $event)"
          @tree-command="$emit('tree-command', $event)"
          @node-contextmenu="openNodeMenu($event.event, $event.nodeId, $event.backendKind)"
          @edge-contextmenu="openEdgeMenu($event.event, $event.edgeId)"
        />
      </div>
    </template>

    <!-- 判断 -->
    <template v-else-if="layout.kind === 'decision'">
      <div class="hdt-cell">
        <decision-tree-node-card
          :node-id="layout.id"
          kind="decision"
          :label="layout.name"
          @click="$emit('select-node', layout.id)"
          @contextmenu="openNodeMenu($event, layout.id, 'decision')"
        />
      </div>
      <div class="hdt-branches">
        <div
          v-for="br in layout.branches"
          :key="br.edgeId"
          class="hdt-branch-row"
        >
          <decision-tree-branch-chip
            :edge-id="br.edgeId"
            :branch-name="br.edgeName"
            :condition-expression="br.conditionExpression"
            :selected="selectedEdgeId === br.edgeId"
            @click="$emit('select-edge', br.edgeId)"
            @contextmenu="openEdgeMenu($event, br.edgeId)"
          />
          <div v-if="br.child" class="hdt-branch-tail">
            <hdt-layout-fragment
              :layout="br.child"
              :selected-node-id="selectedNodeId"
              :selected-edge-id="selectedEdgeId"
              @select-node="$emit('select-node', $event)"
              @select-edge="$emit('select-edge', $event)"
              @tree-command="$emit('tree-command', $event)"
              @node-contextmenu="openNodeMenu($event.event, $event.nodeId, $event.backendKind)"
              @edge-contextmenu="openEdgeMenu($event.event, $event.edgeId)"
            />
          </div>
        </div>
      </div>
    </template>

    <!-- 任务 -->
    <template v-else-if="layout.kind === 'task'">
      <div class="hdt-cell">
        <decision-tree-node-card
          :node-id="layout.id"
          kind="task"
          :label="layout.name"
          @click="$emit('select-node', layout.id)"
          @remove="$emit('tree-command', { cmd: 'deleteNode', nodeId: layout.id })"
          @contextmenu="openNodeMenu($event, layout.id, 'task')"
        />
      </div>
      <div v-if="layout.next" class="hdt-chain">
        <hdt-layout-fragment
          :layout="layout.next"
          :selected-node-id="selectedNodeId"
          :selected-edge-id="selectedEdgeId"
          @select-node="$emit('select-node', $event)"
          @select-edge="$emit('select-edge', $event)"
          @tree-command="$emit('tree-command', $event)"
          @node-contextmenu="openNodeMenu($event.event, $event.nodeId, $event.backendKind)"
          @edge-contextmenu="openEdgeMenu($event.event, $event.edgeId)"
        />
      </div>
    </template>

    <!-- 环检测占位 -->
    <template v-else-if="layout.kind === 'cycle'">
      <div class="hdt-cycle">{{ layout.name }}</div>
    </template>
  </div>
</template>

<script>
import DecisionTreeNodeCard from './DecisionTreeNodeCard.vue'
import DecisionTreeBranchChip from './DecisionTreeBranchChip.vue'

/**
 * 横向决策树单段递归布局（与 HorizontalDecisionTree 配合）
 */
export default {
  name: 'HdtLayoutFragment',
  components: {
    DecisionTreeNodeCard,
    DecisionTreeBranchChip
  },
  props: {
    layout: { type: Object, default: null },
    selectedNodeId: { type: String, default: '' },
    selectedEdgeId: { type: String, default: '' }
  },
  methods: {
    /**
     * 打开节点右键菜单
     */
    openNodeMenu(e, nodeId, backendKind) {
      this.$emit('node-contextmenu', { event: e, nodeId, backendKind })
    },
    /**
     * 打开边右键菜单
     */
    openEdgeMenu(e, edgeId) {
      this.$emit('edge-contextmenu', { event: e, edgeId })
    }
  }
}
</script>

<style lang="scss" scoped>
.hdt-frag {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  flex-wrap: nowrap;
  gap: 12px;
}
.hdt-cell {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.hdt-chain {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 12px;
  flex-shrink: 0;
}
.hdt-branches {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;
  flex-shrink: 0;
}
.hdt-branch-row {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 12px;
  flex-shrink: 0;
}
.hdt-branch-tail {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 12px;
  flex-shrink: 0;
}
.hdt-cycle {
  padding: 8px 12px;
  font-size: 12px;
  color: #cf1322;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 6px;
  max-width: 240px;
}
</style>
