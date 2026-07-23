<template>
  <div class="designer-drawer-host-wrap">
    <component
      :is="designerComponent"
      v-if="designerComponent && definitionId"
      :key="instanceKey"
      :definition-id-prop="definitionId"
    />
  </div>
</template>

<script>
import {mapState} from 'vuex'
import DecisionTable from '@/views/designer/DecisionTable.vue'
import DecisionTree from '@/views/designer/DecisionTree.vue'
import DecisionFlow from '@/views/designer/DecisionFlow.vue'
import CrossTable from '@/views/designer/CrossTable.vue'
import Scorecard from '@/views/designer/Scorecard.vue'
import AdvancedCrossTable from '@/views/designer/AdvancedCrossTable.vue'
import AdvancedScorecard from '@/views/designer/AdvancedScorecard.vue'
import ScriptEditor from '@/views/designer/ScriptEditor.vue'

/**
 * 将 segment（路由 path 段）映射为具体设计器组件，供 Layout 抽屉渲染。
 */
const SEGMENT_TO_COMPONENT = {
  table: 'DecisionTable',
  tree: 'DecisionTree',
  flow: 'DecisionFlow',
  cross: 'CrossTable',
  score: 'Scorecard',
  'cross-adv': 'AdvancedCrossTable',
  'score-adv': 'AdvancedScorecard',
  script: 'ScriptEditor'
}

export default {
  name: 'DesignerDrawerHost',
  components: {
    DecisionTable,
    DecisionTree,
    DecisionFlow,
    CrossTable,
    Scorecard,
    AdvancedCrossTable,
    AdvancedScorecard,
    ScriptEditor
  },
  computed: {
    ...mapState('designerDrawer', ['segment', 'definitionId']),
    /**
     * 当前抽屉实例 key，切换规则时强制重建子设计器。
     */
    instanceKey() {
      return `${this.segment}-${this.definitionId}`
    },
    /**
     * segment → 组件名
     */
    designerComponent() {
      const name = SEGMENT_TO_COMPONENT[this.segment]
      return name || null
    }
  }
}
</script>

<style lang="scss" scoped>
/**
 * 充满抽屉内容区（至少 100% 高），内容较高时可随抽屉体滚动。
 */
.designer-drawer-host-wrap {
  min-height: 100%;
  box-sizing: border-box;
}
</style>

<style lang="scss">
/**
 * 设计器根节点在抽屉内至少填满高度（消除底部空白），但不固定高度，
 * 以便内容超出时能通过抽屉体（.el-drawer__body）滚动，避免被裁切无法滚动。
 */
.designer-drawer-host-wrap > * {
  min-height: 100%;
  max-height: none !important;
}
</style>
