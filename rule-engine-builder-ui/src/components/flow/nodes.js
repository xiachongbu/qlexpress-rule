/**
 * LogicFlow 自定义节点注册
 * 节点类型：开始事件、结束事件、脚本任务、排他网关、聚合节点
 */

import {
  h,
  CircleNode, CircleNodeModel,
  RectNode, RectNodeModel,
  DiamondNode, DiamondNodeModel
} from '@logicflow/core'
import { wouldCreateCycleFromNewEdge } from '@/utils/flowGraphCycle'

// ============================================================
// 工具函数
// ============================================================
function uuid() {
  return 'node_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
}

/**
 * LogicFlow 源端连线规则：禁止新增/调整后的边构成有向环（与决策流 DAG 编译一致）。
 */
function createDagSourceRule() {
  return {
    message: '流程图不支持环路（仅支持 DAG）：该连线会形成回到上游节点的循环。若有「重算直到满足」类需求，请在脚本任务节点内用循环实现。',
    validate(sourceNode, targetNode, _sourceAnchor, _targetAnchor, edgeId) {
      if (!sourceNode || !targetNode) return true
      const gm = sourceNode.graphModel
      return !wouldCreateCycleFromNewEdge(gm, sourceNode.id, targetNode.id, edgeId)
    }
  }
}

// ============================================================
// 1. 开始事件 - 绿色圆形
// ============================================================
function StartEventFactory(CircleNode, CircleNodeModel) {
  class StartEventView extends CircleNode {
    getShape() {
      const { x, y, r } = this.props.model
      return h('g', {}, [
        h('circle', {
          cx: x,
          cy: y,
          r,
          fill: '#52c41a',
          stroke: '#389e0d',
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: '#fff',
          fontSize: 12,
          fontWeight: 'bold'
        }, '开始')
      ])
    }
  }

  class StartEventModel extends CircleNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.r = 25
      this.text.editable = false
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      style.stroke = '#389e0d'
      style.fill = '#52c41a'
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      const notAsTarget = {
        message: '开始节点只能作为连线的起点',
        validate: () => true
      }
      rules.push(notAsTarget)
      rules.push(createDagSourceRule())
      return rules
    }
    getConnectedTargetRules() {
      const rules = super.getConnectedTargetRules()
      rules.push({
        message: '开始节点不能作为连线的终点',
        validate: () => false
      })
      return rules
    }
  }

  return { type: 'start-event', view: StartEventView, model: StartEventModel }
}

// ============================================================
// 2. 结束事件 - 红色圆形
// ============================================================
function EndEventFactory(CircleNode, CircleNodeModel) {
  class EndEventView extends CircleNode {
    getShape() {
      const { x, y, r } = this.props.model
      return h('g', {}, [
        h('circle', {
          cx: x,
          cy: y,
          r,
          fill: '#ff4d4f',
          stroke: '#cf1322',
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: '#fff',
          fontSize: 12,
          fontWeight: 'bold'
        }, '结束')
      ])
    }
  }

  class EndEventModel extends CircleNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.r = 25
      this.text.editable = false
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      style.stroke = '#cf1322'
      style.fill = '#ff4d4f'
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push({
        message: '结束节点不能作为连线的起点',
        validate: () => false
      })
      return rules
    }
  }

  return { type: 'end-event', view: EndEventView, model: EndEventModel }
}

// ============================================================
// 3. 脚本任务 - 蓝色圆角矩形
// ============================================================
function ScriptTaskFactory(RectNode, RectNodeModel) {
  class ScriptTaskView extends RectNode {
    getShape() {
      const { x, y, width, height, radius, properties } = this.props.model
      const name = properties.nodeName || '脚本任务'
      return h('g', {}, [
        h('rect', {
          x: x - width / 2,
          y: y - height / 2,
          width,
          height,
          rx: radius,
          ry: radius,
          fill: '#1890ff',
          stroke: '#096dd9',
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: '#fff',
          fontSize: 13,
          fontWeight: 'bold'
        }, name.length > 10 ? name.substr(0, 10) + '...' : name)
      ])
    }
  }

  class ScriptTaskModel extends RectNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.width = 160
      this.height = 42
      this.radius = 6
      this.text.editable = false
      if (!data.properties) data.properties = {}
      if (!data.properties.nodeName) data.properties.nodeName = '脚本任务'
      if (!data.properties.nodeCode) data.properties.nodeCode = 'TASK_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
      if (!data.properties.nodeDesc) data.properties.nodeDesc = ''
      if (!data.properties.scriptMode) data.properties.scriptMode = 'visual'
      if (!data.properties.asyncExec) data.properties.asyncExec = false
      if (!data.properties.scriptContent) data.properties.scriptContent = ''
      if (!data.properties.actions) data.properties.actions = []
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      style.stroke = '#096dd9'
      style.fill = '#1890ff'
      style.radius = 6
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push(createDagSourceRule())
      return rules
    }
  }

  return { type: 'script-task', view: ScriptTaskView, model: ScriptTaskModel }
}

// ============================================================
// 4. 排他网关 - 橙色菱形
// ============================================================
function ExclusiveGatewayFactory(DiamondNode, DiamondNodeModel) {
  class ExclusiveGatewayView extends DiamondNode {
    getShape() {
      const { x, y, rx, ry, properties } = this.props.model
      const nodeName = (properties && properties.nodeName) || '条件判断'
      const points = [
        [x, y - ry],
        [x + rx, y],
        [x, y + ry],
        [x - rx, y]
      ].map(p => p.join(',')).join(' ')
      const shortName = nodeName.length > 4 ? nodeName.substr(0, 4) : nodeName
      return h('g', {}, [
        h('polygon', {
          points,
          fill: '#fa8c16',
          stroke: '#d46b08',
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: '#fff',
          fontSize: 11,
          fontWeight: 'bold'
        }, shortName),
        h('text', {
          x,
          y: y + ry + 14,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: '#666',
          fontSize: 12
        }, nodeName.length > 8 ? nodeName.substr(0, 8) + '…' : nodeName)
      ])
    }
  }

  class ExclusiveGatewayModel extends DiamondNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.rx = 28
      this.ry = 28
      this.text.editable = false
      if (!data.properties) data.properties = {}
      if (!data.properties.nodeName) data.properties.nodeName = '条件判断'
      if (!data.properties.nodeCode) data.properties.nodeCode = 'DECISION_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
      if (!data.properties.nodeDesc) data.properties.nodeDesc = ''
      if (!data.properties.gatewayDirection) data.properties.gatewayDirection = 'Diverging'
      if (!data.properties.defaultBranch) data.properties.defaultBranch = ''
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      style.stroke = '#d46b08'
      style.fill = '#fa8c16'
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push(createDagSourceRule())
      return rules
    }
  }

  return { type: 'exclusive-gateway', view: ExclusiveGatewayView, model: ExclusiveGatewayModel }
}

// ============================================================
// 5. 聚合节点 - 灰色菱形
// ============================================================
function JoinGatewayFactory(DiamondNode, DiamondNodeModel) {
  class JoinGatewayView extends DiamondNode {
    getShape() {
      const { x, y, rx, ry } = this.props.model
      const points = [
        [x, y - ry],
        [x + rx, y],
        [x, y + ry],
        [x - rx, y]
      ].map(p => p.join(',')).join(' ')
      return h('g', {}, [
        h('polygon', {
          points,
          fill: '#8c8c8c',
          stroke: '#595959',
          strokeWidth: 2
        }),
        h('text', {
          x,
          y: y + 1,
          textAnchor: 'middle',
          dominantBaseline: 'central',
          fill: '#fff',
          fontSize: 12,
          fontWeight: 'bold'
        }, '聚合')
      ])
    }
  }

  class JoinGatewayModel extends DiamondNodeModel {
    initNodeData(data) {
      super.initNodeData(data)
      this.rx = 28
      this.ry = 28
      this.text.editable = false
      if (!data.properties) data.properties = {}
      if (!data.properties.nodeName) data.properties.nodeName = '聚合'
      if (!data.properties.nodeCode) data.properties.nodeCode = 'JOIN_' + Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
      if (!data.properties.nodeDesc) data.properties.nodeDesc = ''
    }
    setAttributes() {
      this.text.value = ''
    }
    getNodeStyle() {
      const style = super.getNodeStyle()
      style.stroke = '#595959'
      style.fill = '#8c8c8c'
      return style
    }
    getConnectedSourceRules() {
      const rules = super.getConnectedSourceRules()
      rules.push(createDagSourceRule())
      return rules
    }
  }

  return { type: 'join-gateway', view: JoinGatewayView, model: JoinGatewayModel }
}

// ============================================================
// 注册所有自定义节点
// ============================================================
export function registerCustomNodes(lf) {
  const nodes = [
    StartEventFactory(CircleNode, CircleNodeModel),
    EndEventFactory(CircleNode, CircleNodeModel),
    ScriptTaskFactory(RectNode, RectNodeModel),
    ExclusiveGatewayFactory(DiamondNode, DiamondNodeModel),
    JoinGatewayFactory(DiamondNode, DiamondNodeModel)
  ]

  nodes.forEach(n => {
    lf.register(n)
  })
}

// 节点面板配置
export const NODE_PANEL_LIST = [
  {
    group: '事件节点',
    items: [
      { type: 'start-event', label: '开始事件', icon: 'el-icon-video-play', color: '#52c41a' },
      { type: 'end-event', label: '结束事件', icon: 'el-icon-remove', color: '#ff4d4f' }
    ]
  },
  {
    group: '任务节点',
    items: [
      { type: 'script-task', label: '脚本任务', icon: 'el-icon-document', color: '#1890ff' }
    ]
  },
  {
    group: '网关节点',
    items: [
      { type: 'exclusive-gateway', label: '排他网关', icon: 'el-icon-sort', color: '#fa8c16' },
      { type: 'join-gateway', label: '聚合节点', icon: 'el-icon-copy-document', color: '#8c8c8c' }
    ]
  }
]

// 默认流程数据
export function getDefaultFlowData() {
  return {
    nodes: [
      {
        id: uuid(),
        type: 'start-event',
        x: 160,
        y: 300,
        properties: { nodeName: '开始', nodeCode: 'START_' + Date.now(), nodeDesc: '流程开始节点' }
      },
      {
        id: uuid(),
        type: 'end-event',
        x: 800,
        y: 300,
        properties: { nodeName: '结束', nodeCode: 'END_' + Date.now(), nodeDesc: '流程结束节点' }
      }
    ],
    edges: []
  }
}
