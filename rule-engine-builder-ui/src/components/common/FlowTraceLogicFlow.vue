<template>
  <div class="flow-trace-lf">
    <div v-if="!hasLogicflow" class="flow-trace-lf__empty">
      <i class="el-icon-warning-outline" />
      <span>当前规则未保存画布数据（logicflow），无法展示流程图。可在决策流设计器保存后重新查看。</span>
    </div>
    <template v-else>
      <div class="flow-trace-lf__bar">
        <el-button type="text" size="small" @click="fitViewToContainer"><i class="el-icon-full-screen" /> 适应画布</el-button>
      </div>
      <div ref="canvasContainer" class="flow-trace-lf__canvas" />
    </template>
  </div>
</template>

<script>
import LogicFlow, {
  BezierEdge,
  BezierEdgeModel,
  LineEdge,
  LineEdgeModel,
  PolylineEdge,
  PolylineEdgeModel
} from '@logicflow/core'
import '@logicflow/core/dist/style/index.css'
import { registerCustomNodes } from '@/components/flow/nodes'
import {
  migrateModelJsonForEdgeLineTypes,
  normalizeDefaultEdgeLineType,
  prepareLogicFlowDataForRender
} from '@/components/flow/edgeLineType'

/**
 * 按 properties.traceStatus 调整边样式：executed=绿色加粗虚线，skipped=灰色淡化；无状态时保持原样
 */
function applyTraceEdgeStyle(style, properties) {
  var status = properties && properties.traceStatus
  if (status === 'executed') {
    style.stroke = '#52c41a'
    style.strokeWidth = 2.5
    style.strokeDasharray = '8,5'
  } else if (status === 'skipped') {
    style.stroke = '#d9d9d9'
    style.strokeWidth = 1.5
    style.opacity = 0.7
  }
  return style
}

/**
 * 仅在追踪画布实例上覆盖注册三种内置边类型（model 读 traceStatus 改样式），不影响设计器
 */
function registerTraceEdges(lf) {
  var defs = [
    { type: 'polyline', view: PolylineEdge, base: PolylineEdgeModel },
    { type: 'line', view: LineEdge, base: LineEdgeModel },
    { type: 'bezier', view: BezierEdge, base: BezierEdgeModel }
  ]
  defs.forEach(function(def) {
    class TraceEdgeModel extends def.base {
      getEdgeStyle() {
        return applyTraceEdgeStyle(super.getEdgeStyle(), this.properties)
      }
    }
    lf.register({ type: def.type, view: def.view, model: TraceEdgeModel })
  })
}

export default {
  name: 'FlowTraceLogicFlow',
  props: {
    /** 规则完整 modelJson（需含 logicflow、defaultEdgeLineType） */
    definitionModel: { type: Object, default: null },
    /** 执行轨迹（{ executedNodeIds, executedEdgeIds }），为空时不高亮 */
    tracePath: { type: Object, default: null }
  },
  data: function() {
    return {
      lf: null,
      containerResizeObserver: null,
      layoutDebounceTimerId: null,
      redrawDebounceTimerId: null
    }
  },
  computed: {
    /**
     * 是否存在可渲染的 logicflow 图数据
     */
    hasLogicflow: function() {
      var m = this.definitionModel
      var g = m && m.logicflow
      return !!(g && Array.isArray(g.nodes) && g.nodes.length > 0)
    },
    /**
     * 规范化后的默认边类型
     */
    edgeLineDefault: function() {
      return normalizeDefaultEdgeLineType(this.definitionModel && this.definitionModel.defaultEdgeLineType)
    }
  },
  watch: {
    definitionModel: { deep: true, handler: 'scheduleRedraw' },
    tracePath: { deep: true, handler: 'scheduleRedraw' }
  },
  mounted: function() {
    this.scheduleRedraw()
  },
  beforeDestroy: function() {
    this.unbindResizeObserver()
    this.teardownLogicFlow()
  },
  methods: {
    /**
     * 供父级在抽屉打开、切换 Tab 后调用：按容器尺寸 resize 并多次 fitView（解决隐藏 Tab 内初始宽高为 0 的问题）
     */
    requestRelayout: function() {
      this.schedulePostRenderFit()
    },
    /**
     * 防抖合并多次 prop 更新后重绘
     */
    scheduleRedraw: function() {
      var self = this
      if (this.redrawDebounceTimerId) clearTimeout(this.redrawDebounceTimerId)
      this.redrawDebounceTimerId = setTimeout(function() {
        self.redrawDebounceTimerId = null
        self.redraw()
      }, 80)
    },
    /**
     * 断开容器尺寸监听
     */
    unbindResizeObserver: function() {
      if (this.containerResizeObserver) {
        try {
          this.containerResizeObserver.disconnect()
        } catch (e) { /* ignore */ }
        this.containerResizeObserver = null
      }
      if (this.layoutDebounceTimerId) {
        clearTimeout(this.layoutDebounceTimerId)
        this.layoutDebounceTimerId = null
      }
    },
    /**
     * 监听画布容器尺寸变化并重新适应视图
     */
    bindResizeObserver: function() {
      this.unbindResizeObserver()
      var el = this.$refs.canvasContainer
      if (!el || typeof ResizeObserver === 'undefined') return
      var self = this
      this.containerResizeObserver = new ResizeObserver(function() {
        if (self.layoutDebounceTimerId) clearTimeout(self.layoutDebounceTimerId)
        self.layoutDebounceTimerId = setTimeout(function() {
          self.layoutDebounceTimerId = null
          self.fitViewToContainer()
        }, 100)
      })
      this.containerResizeObserver.observe(el)
    },
    /**
     * 按容器 client 尺寸调用 LogicFlow resize + fitView
     */
    fitViewToContainer: function() {
      if (!this.lf || !this.$refs.canvasContainer) return
      var el = this.$refs.canvasContainer
      var w = el.clientWidth
      var h = el.clientHeight
      if (w < 16 || h < 16) return
      try {
        this.lf.resize(w, h)
        this.lf.fitView(24, 24)
      } catch (e) { /* ignore */ }
    },
    /**
     * 渲染后多时刻补偿 fit（抽屉动画、Tab 显示、首帧布局）
     */
    schedulePostRenderFit: function() {
      var self = this
      var run = function() { self.fitViewToContainer() }
      run()
      if (typeof requestAnimationFrame !== 'undefined') {
        requestAnimationFrame(function() {
          run()
          requestAnimationFrame(run)
        })
      }
      setTimeout(run, 120)
      setTimeout(run, 320)
      setTimeout(run, 600)
    },
    /**
     * 销毁 LogicFlow 实例
     */
    teardownLogicFlow: function() {
      if (this.redrawDebounceTimerId) {
        clearTimeout(this.redrawDebounceTimerId)
        this.redrawDebounceTimerId = null
      }
      this.lf = null
    },
    /**
     * 克隆并迁移 model 上的边类型字段，避免污染父级引用；并按 tracePath 注入节点/边的 traceStatus
     */
    prepareGraphData: function() {
      if (!this.definitionModel || !this.definitionModel.logicflow) return { nodes: [], edges: [] }
      var clone = JSON.parse(JSON.stringify(this.definitionModel))
      migrateModelJsonForEdgeLineTypes(clone)
      var g = clone.logicflow || { nodes: [], edges: [] }
      var data = prepareLogicFlowDataForRender(g, normalizeDefaultEdgeLineType(clone.defaultEdgeLineType))
      this.applyTraceStatus(data)
      return data
    },
    /**
     * 画布节点/边与逻辑图同 id：命中 executedNodeIds/executedEdgeIds 标 executed，其余标 skipped；无轨迹时不打标（完全保持现状）
     */
    applyTraceStatus: function(data) {
      var tp = this.tracePath
      if (!tp || !Array.isArray(tp.executedNodeIds) || tp.executedNodeIds.length === 0) return
      var nodeSet = {}
      var edgeSet = {}
      tp.executedNodeIds.forEach(function(id) { nodeSet[id] = true })
      ;(tp.executedEdgeIds || []).forEach(function(id) { edgeSet[id] = true })
      ;(data.nodes || []).forEach(function(n) {
        n.properties = Object.assign({}, n.properties, { traceStatus: nodeSet[n.id] ? 'executed' : 'skipped' })
      })
      ;(data.edges || []).forEach(function(e) {
        e.properties = Object.assign({}, e.properties, { traceStatus: edgeSet[e.id] ? 'executed' : 'skipped' })
      })
    },
    /**
     * 初始化只读 LogicFlow（仅调用一次）
     */
    initLogicFlow: function() {
      var el = this.$refs.canvasContainer
      if (!el) return
      var w = Math.max(320, el.clientWidth || el.offsetWidth || 800)
      var h = Math.max(200, el.clientHeight || el.offsetHeight || 360)
      this.lf = new LogicFlow({
        container: el,
        width: w,
        height: h,
        grid: { size: 20, visible: true },
        keyboard: { enabled: false },
        edgeType: this.edgeLineDefault,
        snapline: false,
        history: false,
        style: {
          nodeText: { overflowMode: 'ellipsis', fontSize: 12 },
          edgeText: { fontSize: 12, background: { fill: '#fff' }},
          polyline: { stroke: '#999', strokeWidth: 1.5 },
          line: { stroke: '#999', strokeWidth: 1.5 },
          bezier: { stroke: '#999', strokeWidth: 1.5 },
          anchor: { stroke: '#1890ff', fill: '#fff', r: 4 },
          anchorHover: { stroke: '#1890ff', fill: '#1890ff', r: 5 }
        },
        guards: {
          beforeClone: function() { return false },
          beforeDelete: function() { return false }
        }
      })
      registerCustomNodes(this.lf)
      registerTraceEdges(this.lf)
      this.lf.updateEditConfig({
        isSilentMode: true,
        adjustEdge: false,
        adjustEdgeStartAndEnd: false,
        adjustNodePosition: false,
        nodeTextEdit: false,
        edgeTextEdit: false,
        textEdit: false,
        hideAnchors: true
      })
    },
    /**
     * 重绘整张图并适应容器
     */
    redraw: function() {
      var self = this
      if (!this.hasLogicflow) {
        this.unbindResizeObserver()
        this.teardownLogicFlow()
        return
      }
      this.$nextTick(function() {
        if (!self.$refs.canvasContainer) return
        if (!self.lf) self.initLogicFlow()
        if (!self.lf) return
        var data = self.prepareGraphData()
        try {
          self.lf.render(data)
        } catch (e) {
          return
        }
        self.lf.setDefaultEdgeType(self.edgeLineDefault)
        self.bindResizeObserver()
        self.schedulePostRenderFit()
      })
    }
  }
}
</script>

<style scoped>
.flow-trace-lf {
  margin-bottom: 16px;
}
.flow-trace-lf__empty {
  padding: 16px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 4px;
  color: #ad6800;
  font-size: 13px;
  line-height: 1.5;
}
.flow-trace-lf__empty i {
  margin-right: 8px;
}
.flow-trace-lf__bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.flow-trace-lf__canvas {
  height: 360px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  background: #fafafa;
}
</style>
