<template>
  <div class="node-panel" :class="{ collapsed: collapsed }">
    <div class="panel-toggle" @click="collapsed = !collapsed">
      <i :class="collapsed ? 'el-icon-arrow-right' : 'el-icon-arrow-left'" />
    </div>
    <template v-if="!collapsed">
      <div class="panel-header">节点面板</div>
      <div class="panel-search">
        <el-input v-model="searchKey" size="mini" placeholder="搜索节点" prefix-icon="el-icon-search" clearable />
      </div>
      <div class="node-groups">
        <div v-for="group in filteredGroups" :key="group.group" class="node-group">
          <div class="group-title">{{ group.group }}</div>
          <div class="group-items">
            <div
              v-for="item in group.items"
              :key="item.type"
              class="node-item"
              draggable="true"
              @mousedown="onDragStart(item)"
            >
              <span class="node-dot" :style="{ background: item.color }" />
              <span class="node-label">{{ item.label }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import { NODE_PANEL_LIST } from './nodes'

export default {
  name: 'NodePanel',
  props: {
    lf: { type: Object, default: null }
  },
  data() {
    return {
      collapsed: false,
      searchKey: '',
      groups: NODE_PANEL_LIST
    }
  },
  computed: {
    filteredGroups() {
      if (!this.searchKey) return this.groups
      const key = this.searchKey.toLowerCase()
      return this.groups
        .map(g => ({
          ...g,
          items: g.items.filter(i => i.label.toLowerCase().includes(key))
        }))
        .filter(g => g.items.length > 0)
    }
  },
  methods: {
    onDragStart(item) {
      if (!this.lf) return
      this.lf.dnd.startDrag({
        type: item.type,
        properties: {
          nodeName: item.label,
          nodeCode: item.type.toUpperCase().replace(/-/g, '_') + '_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase(),
          nodeDesc: ''
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.node-panel {
  width: 180px;
  height: 100%;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: relative;
  transition: width 0.2s;
  &.collapsed {
    width: 24px;
    overflow: hidden;
  }
}
.panel-toggle {
  position: absolute;
  right: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 48px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 0 12px 12px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  font-size: 12px;
  color: #999;
  &:hover { color: #333; background: #f5f5f5; }
}
.panel-header {
  padding: 12px 16px;
  font-weight: bold;
  font-size: 14px;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}
.panel-search {
  padding: 8px 12px;
  flex-shrink: 0;
}
.node-groups {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 12px;
}
.node-group {
  margin-bottom: 12px;
}
.group-title {
  font-size: 12px;
  color: #999;
  padding: 6px 0 4px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 4px;
}
.group-items {
  display: flex;
  flex-direction: column;
}
.node-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: grab;
  border-radius: 4px;
  transition: background 0.15s;
  &:hover {
    background: #f0f7ff;
  }
  &:active {
    cursor: grabbing;
    background: #e6f0ff;
  }
}
.node-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}
.node-label {
  font-size: 13px;
  color: #333;
}
</style>
