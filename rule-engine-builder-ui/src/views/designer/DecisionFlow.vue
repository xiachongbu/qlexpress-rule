<template>
  <div class="flow-designer">
    <!-- 顶部工具栏 -->
    <div class="flow-toolbar">
      <div class="toolbar-left">
        <i class="el-icon-share toolbar-icon" />
        <span class="toolbar-title">决策流设计器</span>
        <el-tag size="small" type="info" style="margin-left:8px;">可视化流程规则</el-tag>
        <span class="toolbar-id" v-if="definitionId">ID: {{ definitionId }}</span>
      </div>
      <div class="toolbar-center">
        <span class="toolbar-label">添加节点：</span>
        <el-button size="mini" @click="addNode('start-event')">
          <span class="node-dot" style="background:#52c41a" />开始
        </el-button>
        <el-button size="mini" @click="addNode('exclusive-gateway')">
          <span class="node-dot" style="background:#fa8c16" />条件判断
        </el-button>
        <el-button size="mini" @click="addNode('script-task')">
          <span class="node-dot" style="background:#1890ff" />执行动作
        </el-button>
        <el-button size="mini" @click="addNode('join-gateway')">
          <span class="node-dot" style="background:#8c8c8c" />聚合
        </el-button>
        <el-button size="mini" @click="addNode('end-event')">
          <span class="node-dot" style="background:#ff4d4f" />结束
        </el-button>
        <el-divider direction="vertical" />
        <el-button-group>
          <el-button size="mini" icon="el-icon-delete" :disabled="!hasSelection" @click="deleteSelected">删除</el-button>
          <el-button size="mini" icon="el-icon-refresh-left" @click="undo">撤销</el-button>
          <el-button size="mini" icon="el-icon-refresh-right" @click="redo">重做</el-button>
        </el-button-group>
        <el-divider direction="vertical" />
        <el-button-group>
          <el-button size="mini" icon="el-icon-zoom-in" @click="zoomIn" />
          <el-button size="mini" icon="el-icon-zoom-out" @click="zoomOut" />
          <el-button size="mini" icon="el-icon-rank" @click="resetZoom" />
        </el-button-group>
        <span class="zoom-text">{{ zoomPercent }}%</span>
        <el-divider direction="vertical" />
        <span class="toolbar-label">连线：</span>
        <el-select
          v-model="globalEdgeLineType"
          size="mini"
          style="width:110px"
          @change="onGlobalEdgeLineTypeChange"
        >
          <el-option label="折线" value="polyline" />
          <el-option label="直线" value="line" />
          <el-option label="弧线" value="bezier" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button size="mini" icon="el-icon-circle-check" @click="handleValidate">验证</el-button>
        <el-button size="mini" icon="el-icon-document" @click="handleSave">保存</el-button>
        <el-button size="mini" type="warning" icon="el-icon-cpu" @click="handleCompile">编译</el-button>
        <el-button size="mini" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <!-- 主体：画布 + 属性面板 -->
    <div class="flow-body">
      <!-- LogicFlow 画布 -->
      <div ref="canvasContainer" class="flow-canvas" />

      <!-- 右侧属性面板 -->
      <transition name="panel-slide">
        <div v-if="activeElement" class="flow-property">
          <div class="prop-header">
            <span class="prop-title">
              <i :class="propIcon" />
              {{ isEdge ? '连线属性' : '节点属性配置' }}
            </span>
            <i class="el-icon-close prop-close" @click="activeElement = null" />
          </div>

          <!-- ========== 连线属性（可视化+脚本双模式） ========== -->
          <template v-if="isEdge">
            <div class="prop-section">
              <el-form size="small" label-width="80px" class="prop-form">
                <el-form-item label="连接线类型">
                  <el-select v-model="edgeProps.edgeLineType" size="mini" style="width:95%" @change="onEdgeLineShapeChange">
                    <el-option label="跟随全局" value="" />
                    <el-option label="折线" value="polyline" />
                    <el-option label="直线" value="line" />
                    <el-option label="弧线" value="bezier" />
                  </el-select>
                </el-form-item>
                <el-form-item label="分支标签">
                  <el-input v-model="edgeProps.conditionName" placeholder="如：是、否、金额>500" @input="onEdgeChange" />
                </el-form-item>
              </el-form>
            </div>

            <div class="prop-section">
              <div class="section-title">
                <span>条件表达式</span>
                <el-radio-group v-model="edgeCondMode" size="mini">
                  <el-radio-button label="visual">可视化</el-radio-button>
                  <el-radio-button label="script">脚本</el-radio-button>
                </el-radio-group>
              </div>

              <!-- 可视化条件构建 -->
              <div v-if="edgeCondMode === 'visual'" class="cond-builder">
                <div class="cond-row">
                  <span class="cond-label">变量</span>
                  <var-picker
                    :vars="varPickerOptions"
                    :value="edgeCondVisual.leftVar"
                    placeholder="选择或输入变量..."
                    size="mini"
                    @select="v => onEdgeCondVarSelect(v, 'left')"
                  />
                </div>
                <div class="cond-row">
                  <span class="cond-label">运算符</span>
                  <el-select v-model="edgeCondVisual.operator" size="mini" style="width:100%">
                    <el-option label="等于 (==)" value="==" />
                    <el-option label="不等于 (!=)" value="!=" />
                    <el-option label="大于 (>)" value=">" />
                    <el-option label="大于等于 (>=)" value=">=" />
                    <el-option label="小于 (<)" value="<" />
                    <el-option label="小于等于 (<=)" value="<=" />
                    <el-option label="包含 (in)" value="in" />
                  </el-select>
                </div>
                <div class="cond-row">
                  <span class="cond-label">值</span>
                  <el-input v-model="edgeCondVisual.rightValue" size="mini" placeholder="比较值，如：100000">
                    <el-select v-model="edgeCondVisual.rightType" slot="prepend" style="width:70px" size="mini">
                      <el-option label="值" value="value" />
                      <el-option label="变量" value="var" />
                    </el-select>
                  </el-input>
                </div>
                <div v-if="edgeCondVisual.rightType === 'var'" class="cond-row">
                  <span class="cond-label">右变量</span>
                  <var-picker
                    :vars="varPickerOptions"
                    :value="edgeCondVisual.rightVar"
                    placeholder="选择或输入比较变量..."
                    size="mini"
                    @select="v => onEdgeCondVarSelect(v, 'right')"
                  />
                </div>
                <el-button type="primary" size="mini" icon="el-icon-check" style="width:100%;margin-top:8px;" @click="applyEdgeCondVisual">
                  生成表达式
                </el-button>
                <div v-if="edgeProps.conditionExpr" class="generated-expr">
                  <code>{{ edgeProps.conditionExpr }}</code>
                </div>
              </div>

              <!-- 脚本模式 -->
              <div v-else class="cond-script">
                <el-input
                  v-model="edgeProps.conditionExpr"
                  type="textarea"
                  :rows="3"
                  placeholder="QLExpress 表达式，如：amount > 100000"
                  class="mono-input"
                  @input="onEdgeChange"
                />
              </div>

              <div class="hint-box" style="margin-top:6px;">
                <i class="el-icon-info" /> 条件为空表示默认分支（else）
              </div>
            </div>
          </template>

          <!-- ========== 节点属性 ========== -->
          <template v-else>
            <div class="prop-section">
              <el-form size="small" label-width="70px" class="prop-form">
                <el-form-item label="节点名称">
                  <el-input v-model="nodeProps.nodeName" placeholder="节点名称" @input="onNodeChange" />
                </el-form-item>
                <el-form-item label="节点类型">
                  <el-tag :type="nodeTypeTag(activeElement.type)" size="small">{{ nodeTypeLabel(activeElement.type) }}</el-tag>
                </el-form-item>
              </el-form>
            </div>

            <!-- ===== 条件节点：只显示分支出口 ===== -->
            <template v-if="activeElement.type === 'exclusive-gateway'">
              <div class="prop-section">
                <div class="section-title"><span>分支出口</span></div>
                <div class="out-edges">
                  <div
                    v-for="(edge, ei) in outEdges"
                    :key="edge.id"
                    class="out-edge-item"
                    @click="selectEdgeById(edge.id)"
                  >
                    <span class="edge-idx">{{ ei + 1 }}</span>
                    <i class="el-icon-right edge-arrow" />
                    <span class="edge-name">{{ edgeLabel(edge) || '（点击配置条件）' }}</span>
                    <el-tag v-if="!edgeLabel(edge)" size="mini" type="warning">未设置</el-tag>
                  </div>
                  <div v-if="outEdges.length === 0" class="hint-box">
                    <i class="el-icon-info" /> 从节点锚点拖拽到目标节点创建分支
                  </div>
                </div>
                <div class="hint-box" style="margin-top:6px;">
                  <i class="el-icon-info" /> 点击分支连线可配置条件表达式（可视化或脚本）
                </div>
              </div>
            </template>

            <!-- ===== 聚合节点 ===== -->
            <template v-if="activeElement.type === 'join-gateway'">
              <div class="prop-section">
                <div class="section-title"><span>聚合配置</span></div>
                <div class="hint-box">
                  <i class="el-icon-info" /> 聚合节点用于多条分支汇合，所有入边的分支都到达后，继续向下执行
                </div>
              </div>
            </template>

            <!-- ===== 执行动作节点 ===== -->
            <template v-if="activeElement.type === 'script-task'">
              <div class="prop-section">
                <div class="section-title">
                  <span>动作配置</span>
                  <el-radio-group v-model="actionMode" size="mini">
                    <el-radio-button label="visual">可视化</el-radio-button>
                    <el-radio-button label="script">脚本预览</el-radio-button>
                  </el-radio-group>
                </div>

                <div v-if="actionMode === 'visual'">
                  <action-block-editor
                    :action-data="currentActionData"
                    :vars="varPickerOptions"
                    :functions="projectFunctions"
                    @update="onActionDataUpdate"
                  />
                </div>

                <div v-else class="cond-script">
                  <pre class="script-readonly">{{ scriptPreview || '（请在可视化模式配置动作）' }}</pre>
                </div>

                <div class="hint-box" style="margin-top:6px;">
                  <i class="el-icon-info" /> 支持赋值、条件分支、Switch、函数调用、循环、三元、IN判断、动态字符串
                </div>
              </div>
            </template>

            <!-- 操作按钮 -->
            <div class="prop-section" style="padding-top:4px;">
              <el-button type="danger" size="small" plain icon="el-icon-delete" style="width:100%;" @click="deleteCurrentNode">
                删除此节点
              </el-button>
            </div>
          </template>
        </div>
      </transition>

      <!-- 无选中提示 -->
      <div v-if="!activeElement" class="flow-hint">
        <i class="el-icon-set-up hint-icon" />
        <p>从工具栏添加节点，拖拽锚点连线</p>
        <p>单击节点/连线可编辑属性</p>
      </div>
    </div>

    <!-- 脚本预览/编辑面板 -->
    <div class="flow-script-area" v-if="definitionId">
      <script-panel
        ref="scriptPanel"
        :definitionId="definitionId"
        :onBeforeCompile="handleSave"
        @mode-change="onScriptModeChange"
      />
    </div>

    <!-- 测试执行弹窗 -->
    <el-dialog title="测试执行" :visible.sync="testVisible" width="600px" append-to-body>
      <p class="test-hint"><i class="el-icon-info" /> 请输入测试参数（JSON 格式）</p>
      <el-input
        v-model="testParamsJson"
        type="textarea"
        :rows="6"
        placeholder='{"taxpayerType": "一般纳税人", "amount": 100000}'
      />
      <template slot="footer">
        <el-button size="small" @click="testVisible = false">取消</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="doTest">执行</el-button>
      </template>
      <div v-if="testResult" class="test-result">
        <el-alert
          :title="testResult.success ? '执行成功' : '执行失败'"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="margin-bottom:10px;"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="返回值">
            <pre class="result-pre">{{ formatJson(testResult.result) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ testResult.executeTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item v-if="testResult.errorMessage" label="错误">
            <span style="color:#F56C6C">{{ testResult.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import LogicFlow from '@logicflow/core'
import { SelectionSelect, Menu, Snapshot } from '@logicflow/extension'
import '@logicflow/core/dist/style/index.css'
import '@logicflow/extension/lib/style/index.css'

import { registerCustomNodes, getDefaultFlowData } from '@/components/flow/nodes'
import {
  normalizeDefaultEdgeLineType,
  migrateModelJsonForEdgeLineTypes,
  prepareLogicFlowDataForRender,
  applyGlobalEdgeTypeToInheritedEdges,
  mergeEdgePropertiesFromForm
} from '@/components/flow/edgeLineType'
import { saveContent, compileRule, executeRule, getContent } from '@/api/definition'
import { generateScript } from '@/utils/actionDataCodegen'
import { graphContainsDirectedCycle } from '@/utils/flowGraphCycle'
import varPickerMixin from '@/mixins/varPickerMixin'
import VarPicker from '@/components/common/VarPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import ActionBlockEditor from '@/components/flow/ActionBlockEditor.vue'

export default {
  name: 'DecisionFlow',
  components: { VarPicker, ScriptPanel, ActionBlockEditor },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      lf: null,
      activeElement: null,
      hasSelection: false,
      zoomPercent: 100,
      scriptMode: 'visual',
      nodeProps: {},
      edgeProps: {},
      nodeCondMode: 'visual',
      nodeCondVisual: { leftVar: '', leftLabel: '', operator: '>', rightValue: '', rightType: 'value', rightVar: '' },
      edgeCondMode: 'visual',
      edgeCondVisual: { leftVar: '', leftLabel: '', operator: '==', rightValue: '', rightType: 'value', rightVar: '' },
      actionMode: 'visual',
      currentActionData: [],
      testVisible: false,
      testParamsJson: '{}',
      testResult: null,
      /** 工具栏全局默认连线类型（折线/直线/贝塞尔），新连线与「跟随全局」的边使用 */
      globalEdgeLineType: 'polyline'
    }
  },
  computed: {
    isEdge() {
      return this.activeElement && this.activeElement.baseType === 'edge'
    },
    propIcon() {
      if (this.isEdge) return 'el-icon-connection'
      if (!this.activeElement) return 'el-icon-s-help'
      const map = {
        'start-event': 'el-icon-video-play',
        'end-event': 'el-icon-remove',
        'exclusive-gateway': 'el-icon-sort',
        'script-task': 'el-icon-document',
        'join-gateway': 'el-icon-copy-document'
      }
      return map[this.activeElement.type] || 'el-icon-s-help'
    },
    outEdges() {
      if (!this.lf || !this.activeElement || this.isEdge) return []
      try {
        const edges = this.lf.getNodeEdges(this.activeElement.id)
        return (edges || []).filter(e => e.sourceNodeId === this.activeElement.id)
      } catch (e) {
        return []
      }
    },
    scriptPreview() {
      return generateScript(this.currentActionData)
    }
  },
  created() {
    this.definitionId = this.$route.params.id
  },
  mounted() {
    this.initLogicFlow()
    this.loadContent()
  },
  beforeDestroy() {
    if (this.lf) {
      this.lf.off('node:click', this.onNodeClick)
      this.lf.off('edge:click', this.onEdgeClick)
      this.lf.off('blank:click', this.onBlankClick)
      this.lf.off('node:dnd-add', this.onDndAdd)
      if (this._connectionNotAllowedHandler) {
        this.lf.off('connection:not-allowed', this._connectionNotAllowedHandler)
      }
    }
  },
  methods: {
    initLogicFlow() {
      LogicFlow.use(SelectionSelect)
      LogicFlow.use(Menu)
      LogicFlow.use(Snapshot)

      this.lf = new LogicFlow({
        container: this.$refs.canvasContainer,
        grid: { size: 20, visible: true },
        keyboard: {
          enabled: true,
          shortcuts: [
            { keys: ['ctrl+z', 'cmd+z'], callback: () => this.undo() },
            { keys: ['ctrl+y', 'cmd+y'], callback: () => this.redo() },
            { keys: ['backspace', 'delete'], callback: () => this.deleteSelected() }
          ]
        },
        edgeType: this.globalEdgeLineType,
        snapline: true,
        history: true,
        style: {
          nodeText: { overflowMode: 'ellipsis', fontSize: 12 },
          edgeText: { fontSize: 12, background: { fill: '#fff' } },
          polyline: { stroke: '#999', strokeWidth: 1.5 },
          line: { stroke: '#999', strokeWidth: 1.5 },
          bezier: { stroke: '#999', strokeWidth: 1.5 },
          anchor: { stroke: '#1890ff', fill: '#fff', r: 4 },
          anchorHover: { stroke: '#1890ff', fill: '#1890ff', r: 5 }
        },
        guards: { beforeClone: () => true, beforeDelete: () => true }
      })

      registerCustomNodes(this.lf)
      this.setupContextMenu()
      this.bindEvents()
    },

    setupContextMenu() {
      this.lf.addMenuConfig({
        nodeMenu: [
          { text: '编辑属性', callback: node => this.selectNodeData(node) },
          { text: '删除节点', callback: node => {
            this.lf.deleteNode(node.id)
            if (this.activeElement && this.activeElement.id === node.id) this.activeElement = null
          }}
        ],
        edgeMenu: [
          { text: '编辑条件', callback: edge => this.selectEdgeById(edge.id) },
          { text: '删除连线', callback: edge => {
            this.lf.deleteEdge(edge.id)
            if (this.activeElement && this.activeElement.id === edge.id) this.activeElement = null
          }}
        ],
        graphMenu: [
          { text: '适应画布', callback: () => this.resetZoom() }
        ]
      })
    },

    bindEvents() {
      this.lf.on('node:click', ({ data }) => this.selectNodeData(data))
      this.lf.on('edge:click', ({ data }) => this.selectEdgeData(data))
      this.lf.on('blank:click', () => {
        this.activeElement = null
        this.hasSelection = false
      })
      this.lf.on('node:dnd-add', ({ data }) => {
        this.$nextTick(() => this.selectNodeData(data))
      })
      this.lf.on('node:dbclick', ({ data }) => this.selectNodeData(data))
      this.lf.on('edge:dbclick', ({ data }) => this.selectEdgeData(data))
      this.lf.on('history:change', () => this.updateZoom())
      this._connectionNotAllowedHandler = (payload) => this.handleConnectionNotAllowed(payload)
      this.lf.on('connection:not-allowed', this._connectionNotAllowedHandler)
    },

    /**
     * LogicFlow 连线规则校验失败时提示用户（如禁止构成有向环）
     */
    handleConnectionNotAllowed(payload) {
      const msg =
        (payload && (payload.msg || payload.message)) ||
        (payload && payload.data && (payload.data.msg || payload.data.message)) ||
        '该连线不被允许'
      this.$message.warning(msg)
    },

    selectNodeData(data) {
      const model = this.lf.getNodeModelById(data.id)
      if (!model) return
      this.activeElement = {
        id: data.id,
        type: model.type,
        baseType: 'node',
        properties: model.properties ? JSON.parse(JSON.stringify(model.properties)) : {}
      }
      this.nodeProps = {
        nodeName: (this.activeElement.properties.nodeName) || '',
        nodeDesc: (this.activeElement.properties.nodeDesc) || '',
        gatewayDirection: (this.activeElement.properties.gatewayDirection) || 'Diverging'
      }
      this.hasSelection = true
      if (model.type === 'exclusive-gateway') {
        const scriptContent = (this.activeElement.properties.scriptContent) || ''
        this.nodeCondVisual = this.syncCondVisualFromExpr(scriptContent)
        this.nodeCondMode = scriptContent && !this.nodeCondVisual.leftVar ? 'script' : 'visual'
      }
      if (model.type === 'script-task') {
        this.currentActionData = (this.activeElement.properties.actionData) || []
        this.actionMode = 'visual'
      }
    },

    selectEdgeData(data) {
      this.activeElement = {
        id: data.id,
        type: data.type,
        baseType: 'edge',
        sourceNodeId: data.sourceNodeId,
        targetNodeId: data.targetNodeId,
        properties: data.properties ? JSON.parse(JSON.stringify(data.properties)) : {}
      }
      this.edgeProps = {
        conditionName: (this.activeElement.properties.conditionName) || '',
        conditionExpr: (this.activeElement.properties.conditionExpr) || '',
        edgeLineType: (this.activeElement.properties.edgeLineType) || ''
      }
      this.edgeCondVisual = this.syncCondVisualFromExpr(this.edgeProps.conditionExpr)
      this.edgeCondMode = this.edgeProps.conditionExpr && !this.edgeCondVisual.leftVar ? 'script' : 'visual'
      this.hasSelection = true
    },

    selectEdgeById(edgeId) {
      try {
        const model = this.lf.getEdgeModelById(edgeId)
        if (model) {
          this.selectEdgeData({
            id: model.id,
            type: model.type,
            sourceNodeId: model.sourceNodeId,
            targetNodeId: model.targetNodeId,
            properties: model.properties || {}
          })
        }
      } catch (e) { /* ignore */ }
    },

    onNodeChange() {
      if (!this.lf || !this.activeElement) return
      this.lf.setProperties(this.activeElement.id, { ...this.nodeProps })
    },
    onActionDataUpdate(data) {
      this.currentActionData = data
      if (this.lf && this.activeElement) {
        this.lf.setProperties(this.activeElement.id, { actionData: data })
      }
    },

    /**
     * 同步连线分支文案与条件等到模型，并保持与连接线类型相关的 properties 一致
     */
    onEdgeChange() {
      if (!this.lf || !this.activeElement || this.activeElement.baseType !== 'edge') return
      const curProps = this.lf.getProperties(this.activeElement.id) || {}
      const props = mergeEdgePropertiesFromForm(curProps, this.edgeProps)
      this.lf.setProperties(this.activeElement.id, props)
      const effectiveType = this.edgeProps.edgeLineType
        ? normalizeDefaultEdgeLineType(this.edgeProps.edgeLineType)
        : this.globalEdgeLineType
      try {
        this.lf.changeEdgeType(this.activeElement.id, effectiveType)
      } catch (e) { /* ignore */ }
      try {
        this.lf.updateText(this.activeElement.id, this.edgeProps.conditionName || '')
      } catch (e) { /* ignore */ }
    },

    /**
     * 仅修改当前连线的连接线类型（跟随全局或单独指定）
     */
    onEdgeLineShapeChange() {
      this.onEdgeChange()
    },

    /**
     * 修改工具栏全局连线类型：更新默认类型及所有未单独指定的边
     */
    onGlobalEdgeLineTypeChange(val) {
      this.globalEdgeLineType = normalizeDefaultEdgeLineType(val)
      if (this.lf) applyGlobalEdgeTypeToInheritedEdges(this.lf, this.globalEdgeLineType)
    },

    addNode(type) {
      const labelMap = {
        'start-event': '开始',
        'end-event': '结束',
        'exclusive-gateway': '条件判断',
        'script-task': '执行动作',
        'join-gateway': '聚合'
      }
      const idSuffix = Date.now() + '_' + Math.random().toString(36).substr(2, 4).toUpperCase()
      const xPos = 300 + Math.random() * 200
      const yPos = 200 + Math.random() * 150
      const nodeData = {
        type,
        x: xPos,
        y: yPos,
        properties: {
          nodeName: labelMap[type] || type,
          nodeCode: type.toUpperCase().replace(/-/g, '_') + '_' + idSuffix,
          nodeDesc: '',
          actionData: [],
          gatewayDirection: 'Diverging'
        }
      }
      this.lf.addNode(nodeData)
    },

    deleteSelected() {
      if (!this.activeElement) return
      if (this.activeElement.baseType === 'node') {
        this.lf.deleteNode(this.activeElement.id)
      } else {
        this.lf.deleteEdge(this.activeElement.id)
      }
      this.activeElement = null
      this.hasSelection = false
    },

    deleteCurrentNode() {
      if (!this.activeElement || this.activeElement.baseType !== 'node') return
      this.$confirm('确认删除该节点及其关联连线？', '提示', { type: 'warning' }).then(() => {
        this.lf.deleteNode(this.activeElement.id)
        this.activeElement = null
        this.hasSelection = false
      }).catch(() => {})
    },

    nodeTypeLabel(type) {
      const map = {
        'start-event': '开始节点',
        'end-event': '结束节点',
        'exclusive-gateway': '条件判断（网关）',
        'script-task': '执行动作（脚本任务）',
        'join-gateway': '聚合节点'
      }
      return map[type] || type
    },

    nodeTypeTag(type) {
      const map = {
        'start-event': 'success',
        'end-event': 'danger',
        'exclusive-gateway': 'warning',
        'script-task': '',
        'join-gateway': 'info'
      }
      return map[type] || 'info'
    },

    edgeLabel(edge) {
      if (edge.properties && edge.properties.conditionName) return edge.properties.conditionName
      if (edge.properties && edge.properties.conditionExpr) return edge.properties.conditionExpr
      return ''
    },

    zoomIn() { this.lf.zoom(true); this.updateZoom() },
    zoomOut() { this.lf.zoom(false); this.updateZoom() },
    resetZoom() { this.lf.resetZoom(); this.lf.resetTranslate(); this.updateZoom() },
    updateZoom() {
      this.$nextTick(() => {
        try {
          const t = this.lf.getTransform()
          this.zoomPercent = Math.round((t.SCALE_X || 1) * 100)
        } catch (e) { this.zoomPercent = 100 }
      })
    },
    undo() { this.lf.undo() },
    redo() { this.lf.redo() },

    handleValidate() {
      const errors = []
      const graphData = this.lf.getGraphData()
      const nodes = graphData.nodes || []
      const edges = graphData.edges || []

      const starts = nodes.filter(n => n.type === 'start-event')
      if (starts.length === 0) errors.push('缺少开始节点')
      if (starts.length > 1) errors.push('开始节点只能有一个')
      const ends = nodes.filter(n => n.type === 'end-event')
      if (ends.length === 0) errors.push('缺少结束节点')
      nodes.filter(n => n.type === 'exclusive-gateway').forEach(gw => {
        const outEdges = edges.filter(e => e.sourceNodeId === gw.id)
        if (outEdges.length < 2) {
          errors.push('条件判断节点「' + ((gw.properties && gw.properties.nodeName) || gw.id) + '」至少需要两个出口')
        }
      })

      nodes.filter(n => n.type === 'join-gateway').forEach(jn => {
        const inEdges = edges.filter(e => e.targetNodeId === jn.id)
        if (inEdges.length < 2) {
          errors.push('聚合节点「' + ((jn.properties && jn.properties.nodeName) || jn.id) + '」至少需要两个入边')
        }
      })

      const nodeIds = nodes.map(n => n.id)
      if (graphContainsDirectedCycle(edges, nodeIds)) {
        errors.push('存在有向环路：决策流仅支持 DAG，请删除或调整形成回路的连线')
      }

      if (errors.length === 0) {
        this.$message.success('验证通过！')
      } else {
        this.$alert(errors.map((e, i) => (i + 1) + '. ' + e).join('\n'), '验证失败', { type: 'warning' })
      }
    },

    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          const modelData = JSON.parse(content.modelJson)
          migrateModelJsonForEdgeLineTypes(modelData)
          this.globalEdgeLineType = modelData.defaultEdgeLineType
          this.lf.setDefaultEdgeType(this.globalEdgeLineType)
          if (modelData.logicflow) {
            const prepared = prepareLogicFlowDataForRender(modelData.logicflow, this.globalEdgeLineType)
            this.lf.render(prepared)
          } else if (modelData.nodes && modelData.nodes.length > 0) {
            const converted = this.convertLegacyModel(modelData)
            const prepared = prepareLogicFlowDataForRender(converted, this.globalEdgeLineType)
            this.lf.render(prepared)
          } else {
            this.lf.render(getDefaultFlowData())
          }
        } else {
          this.globalEdgeLineType = 'polyline'
          this.lf.setDefaultEdgeType('polyline')
          this.lf.render(getDefaultFlowData())
        }
        this.updateZoom()
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
        this.globalEdgeLineType = 'polyline'
        this.lf.setDefaultEdgeType('polyline')
        this.lf.render(getDefaultFlowData())
      }
    },

    convertLegacyModel(old) {
      const lfNodes = (old.nodes || []).map((n, i) => {
        let type = 'script-task'
        if (n.type === 'start') type = 'start-event'
        else if (n.type === 'end') type = 'end-event'
        else if (n.type === 'decision') type = 'exclusive-gateway'
        else if (n.type === 'join') type = 'join-gateway'
        return {
          id: n.id,
          type,
          x: n.x || 160 + i * 200,
          y: n.y || 300,
          properties: {
            nodeName: n.name || '',
            nodeCode: n.id,
            nodeDesc: '',
            actionData: n.actionData || []
          }
        }
      })
      const lfEdges = (old.edges || []).map((e, i) => ({
        id: 'edge_' + Date.now() + '_' + i,
        type: 'polyline',
        sourceNodeId: e.source,
        targetNodeId: e.target,
        properties: {
          conditionName: e.conditionExpression || '',
          conditionExpr: e.conditionExpression || ''
        }
      }))
      return { nodes: lfNodes, edges: lfEdges }
    },

    lfTypeToBackend(lfType) {
      const map = {
        'start-event': 'start',
        'end-event': 'end',
        'script-task': 'task',
        'exclusive-gateway': 'decision',
        'join-gateway': 'join'
      }
      return map[lfType] || lfType
    },

    buildBackendModel() {
      // 保存前将当前编辑中的 actionData 同步到 LogicFlow 模型，确保配置不丢失
      if (this.activeElement && this.activeElement.baseType === 'node' && this.activeElement.type === 'script-task') {
        const model = this.lf.getNodeModelById(this.activeElement.id)
        const currentProps = model ? (model.properties || {}) : {}
        this.lf.setProperties(this.activeElement.id, { ...currentProps, actionData: this.currentActionData || [] })
      }
      const graphData = this.lf.getGraphData()

      const nodes = (graphData.nodes || []).map(n => {
        const actionData = (n.properties && n.properties.actionData) || []
        return {
          id: n.id,
          type: this.lfTypeToBackend(n.type),
          name: (n.properties && n.properties.nodeName) || '',
          x: Math.round(n.x),
          y: Math.round(n.y),
          actionData: Array.isArray(actionData) ? actionData : [],
          gatewayDirection: (n.properties && n.properties.gatewayDirection) || ''
        }
      })

      const edges = (graphData.edges || []).map(e => ({
        id: e.id,
        source: e.sourceNodeId,
        target: e.targetNodeId,
        conditionExpression: (e.properties && e.properties.conditionExpr) || '',
        name: (e.properties && e.properties.conditionName) || ''
      }))

      // 确保 logicflow 中的节点包含完整的 actionData（与 nodes 一致）
      const logicflowNodes = (graphData.nodes || []).map(n => {
        const base = { ...n }
        const actionData = (n.properties && n.properties.actionData) || []
        base.properties = { ...(n.properties || {}), actionData: Array.isArray(actionData) ? actionData : [] }
        return base
      })
      return {
        nodes,
        edges,
        defaultEdgeLineType: this.globalEdgeLineType,
        logicflow: { nodes: logicflowNodes, edges: graphData.edges || [] }
      }
    },

    async handleSave() {
      const modelJson = JSON.stringify(this.buildBackendModel())
      await saveContent({ definitionId: this.definitionId, modelJson })
      this.$message.success('保存成功')
    },

    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (res && res.data && res.data.success) {
        this.$message.success('编译成功')
        // 异步刷新变量映射和脚本面板
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) {
          this.$refs.scriptPanel.refresh()
        }
      } else {
        this.$message.error('编译失败: ' + (res && res.data ? res.data.errorMessage : '未知错误'))
      }
    },

    handleTest() {
      this.testParamsJson = '{}'
      this.testResult = null
      this.testVisible = true
    },
    async doTest() {
      let params = {}
      try { params = JSON.parse(this.testParamsJson || '{}') } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await executeRule({ definitionId: this.definitionId, params })
      this.testResult = res && res.data ? res.data : res
    },
    onScriptModeChange(mode) {
      this.scriptMode = mode
    },
    varTypeTag(varType) {
      return this.varTypeColor(varType)
    },
    // === 条件可视化构建 ===
    onNodeCondVarSelect(v, side) {
      if (!v) return
      const label = (v.varObj && v.varObj.varLabel) || v.varLabel || v.varCode
      if (side === 'left') {
        this.nodeCondVisual.leftVar = v.varCode
        this.nodeCondVisual.leftLabel = label
      } else {
        this.nodeCondVisual.rightVar = v.varCode
      }
    },
    onEdgeCondVarSelect(v, side) {
      if (!v) return
      const label = (v.varObj && v.varObj.varLabel) || v.varLabel || v.varCode
      if (side === 'left') {
        this.edgeCondVisual.leftVar = v.varCode
        this.edgeCondVisual.leftLabel = label
      } else {
        this.edgeCondVisual.rightVar = v.varCode
      }
    },
    buildCondExpr(visual) {
      const left = visual.leftVar
      if (!left) { this.$message.warning('请选择左侧变量'); return null }
      const op = visual.operator
      let right = visual.rightType === 'var' ? visual.rightVar : visual.rightValue
      if (!right && right !== 0) { this.$message.warning('请填写比较值'); return null }
      if (visual.rightType === 'value' && isNaN(right)) {
        right = "'" + right.replace(/'/g, "\\'") + "'"
      }
      return left + ' ' + op + ' ' + right
    },
    applyNodeCondVisual() {
      const expr = this.buildCondExpr(this.nodeCondVisual)
      if (expr === null) return
      this.nodeProps.scriptContent = expr
      this.onNodeChange()
      this.$message.success('已生成: ' + expr)
    },
    applyEdgeCondVisual() {
      const expr = this.buildCondExpr(this.edgeCondVisual)
      if (expr === null) return
      this.edgeProps.conditionExpr = expr
      if (!this.edgeProps.conditionName) {
        this.edgeProps.conditionName = (this.edgeCondVisual.leftLabel || this.edgeCondVisual.leftVar) + this.edgeCondVisual.operator + (this.edgeCondVisual.rightType === 'var' ? this.edgeCondVisual.rightVar : this.edgeCondVisual.rightValue)
      }
      this.onEdgeChange()
      this.$message.success('已生成: ' + expr)
    },

    syncCondVisualFromExpr(expr) {
      const m = (expr || '').match(/^(\S+)\s*(==|!=|>=|<=|>|<|in)\s*(.+)$/)
      if (m) {
        return { leftVar: m[1], leftLabel: '', operator: m[2], rightValue: m[3].replace(/^'|'$/g, ''), rightType: 'value', rightVar: '' }
      }
      return { leftVar: '', leftLabel: '', operator: '==', rightValue: '', rightType: 'value', rightVar: '' }
    },

    insertVarCode(code) {
      if (navigator.clipboard) {
        navigator.clipboard.writeText(code).then(() => {
          this.$message({ message: '已复制：' + code, type: 'success', duration: 1200 })
        })
      }
    },
    formatJson(obj) {
      if (obj === null || obj === undefined) return '(空)'
      try { return JSON.stringify(typeof obj === 'string' ? JSON.parse(obj) : obj, null, 2) } catch (e) { return String(obj) }
    }
  }
}
</script>

<style lang="scss" scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 82px);
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}
/* 脚本面板区域 */
.flow-script-area {
  flex-shrink: 0;
  max-height: 60%;
  overflow-y: auto;
  border-top: 1px solid #e8e8e8;
  background: #fff;
}

/* 工具栏 */
.flow-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 14px;
  background: linear-gradient(135deg, #1a6fc4 0%, #1890ff 100%);
  color: #fff;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 6px;
}
.toolbar-icon {
  font-size: 18px;
  margin-right: 6px;
}
.toolbar-title {
  font-weight: bold;
  font-size: 15px;
}
.toolbar-id {
  font-size: 12px;
  opacity: 0.7;
  margin-left: 4px;
}
.toolbar-left, .toolbar-center, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.toolbar-label {
  font-size: 12px;
  opacity: 0.85;
}
.toolbar-right .el-button,
.toolbar-center .el-button {
  color: #fff;
  border-color: rgba(255,255,255,0.4);
  background: rgba(255,255,255,0.1);
  &:hover { background: rgba(255,255,255,0.2); border-color: rgba(255,255,255,0.7); }
  &.el-button--primary { background: #fff; color: #1890ff; border-color: #fff; }
  &.el-button--warning { background: rgba(250,140,22,0.9); border-color: transparent; }
}
.node-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}
.zoom-text {
  font-size: 12px;
  opacity: 0.85;
  min-width: 40px;
  text-align: center;
}

/* 主体 */
.flow-body {
  flex: 1;
  display: flex;
  min-height: 0;
  position: relative;
  background: #f7f8fa;
}

/* 画布 */
.flow-canvas {
  flex: 1;
  min-height: 0;
  min-width: 0;
}

/* 提示 */
.flow-hint {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  color: #bbb;
  pointer-events: none;
  .hint-icon { font-size: 32px; display: block; margin-bottom: 6px; }
  p { margin: 2px 0; font-size: 13px; line-height: 1.6; }
}

/* 属性面板 - 右侧 */
.flow-property {
  width: 640px;
  height: 100%;
  background: #fff;
  border-left: 1px solid #e8e8e8;
  overflow-y: auto;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.prop-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
  flex-shrink: 0;
}
.prop-title {
  font-weight: bold;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.prop-close {
  cursor: pointer;
  color: #999;
  &:hover { color: #333; }
}

/* 面板分区 */
.prop-section {
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f0;
  &:last-child { border-bottom: none; }
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.prop-form {
  padding: 0;
}

/* 条件构建器 */
.cond-builder, .cond-script {
  padding: 0;
}
.cond-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.cond-label {
  font-size: 12px;
  color: #888;
  width: 42px;
  flex-shrink: 0;
  text-align: right;
}
.generated-expr {
  margin-top: 8px;
  padding: 6px 8px;
  background: #f0f7ff;
  border: 1px solid #d6e4ff;
  border-radius: 4px;
  code {
    font-family: 'Consolas', monospace;
    font-size: 12px;
    color: #1890ff;
    word-break: break-all;
  }
}

/* 动作构建器 */
.action-builder {
  padding: 0;
}
.branch-card {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  margin-bottom: 8px;
  background: #fafafa;
  overflow: hidden;
  &.branch-else {
    border-color: #d9d9d9;
    background: #f5f5f5;
  }
}
.branch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  background: #f0f0f0;
  border-bottom: 1px solid #e8e8e8;
}
.branch-tag {
  font-size: 11px;
  font-weight: bold;
  padding: 1px 8px;
  border-radius: 3px;
  color: #fff;
  &.tag-if { background: #1890ff; }
  &.tag-elseif { background: #fa8c16; }
  &.tag-else { background: #8c8c8c; }
}
.branch-cond {
  padding: 6px 8px;
  border-bottom: 1px dashed #e8e8e8;
}
.branch-assigns {
  padding: 6px 8px;
}
.branch-actions {
  display: flex;
  gap: 6px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.assign-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}
.assign-target {
  flex: 1;
  min-width: 0;
}
.assign-eq {
  font-weight: bold;
  color: #999;
  flex-shrink: 0;
  font-size: 14px;
}
.assign-value {
  flex: 1;
  min-width: 0;
}

/* 分支出口 */
.out-edges {
  padding: 0;
}
.out-edge-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: #f0f7ff; border-color: #1890ff; }
}
.edge-idx {
  font-size: 11px;
  font-weight: bold;
  color: #fff;
  background: #1890ff;
  padding: 1px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}
.edge-arrow {
  color: #1890ff;
  font-size: 13px;
}
.edge-name {
  flex: 1;
  font-size: 12px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 通用 */
.hint-box {
  font-size: 12px;
  color: #999;
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
  line-height: 1.6;
}
.mono-input ::v-deep textarea {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}
.script-readonly {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px 12px;
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
  margin: 0;
}
.el-divider {
  margin: 10px 0 4px;
}

/* 面板动画 */
.panel-slide-enter-active, .panel-slide-leave-active {
  transition: width 0.2s ease, opacity 0.2s;
}
.panel-slide-enter, .panel-slide-leave-to {
  width: 0;
  opacity: 0;
}

/* 测试相关 */
.test-hint { font-size: 12px; color: #909399; margin-bottom: 8px; }
.test-result { margin-top: 12px; }
.result-pre {
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow: auto;
  margin: 0;
}
</style>

<style>
/* LogicFlow 全局覆盖 */
.lf-menu {
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  border: 1px solid #e8e8e8;
  padding: 4px 0;
  min-width: 110px;
}
.lf-menu-item {
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
  color: #333;
}
.lf-menu-item:hover {
  background: #f0f7ff;
  color: #1890ff;
}
</style>
