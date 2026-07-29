<template>
  <div class="tree-designer">
    <div class="tree-toolbar">
      <div class="toolbar-left">
        <i class="el-icon-set-up toolbar-icon" />
        <span class="toolbar-title">决策树设计器</span>
        <el-tag size="small" type="info" style="margin-left:8px;">可视化树形规则</el-tag>
        <span v-if="definitionId" class="toolbar-id">ID: {{ definitionId }}</span>
      </div>
      <div class="toolbar-center">
        <span class="toolbar-label">选中节点后添加：</span>
        <el-button size="mini" @click="toolbarAddDecision">
          <span class="node-dot" style="background:#fa8c16" />条件判断
        </el-button>
        <el-button size="mini" @click="toolbarAddTask">
          <span class="node-dot" style="background:#1890ff" />执行动作
        </el-button>
        <el-divider direction="vertical" />
        <el-button size="mini" icon="el-icon-delete" :disabled="!hasSelection" @click="deleteSelected">删除选中</el-button>
        <el-divider direction="vertical" />
        <el-button-group>
          <el-button size="mini" icon="el-icon-refresh-left" :disabled="!canUndoTree" @click="undoTree">撤销</el-button>
          <el-button size="mini" icon="el-icon-refresh-right" :disabled="!canRedoTree" @click="redoTree">重做</el-button>
        </el-button-group>
      </div>
      <div class="toolbar-right">
        <el-button size="mini" icon="el-icon-circle-check" @click="handleValidate">验证</el-button>
        <el-button size="mini" icon="el-icon-document" @click="handleSave">保存</el-button>
        <design-version-switcher
          select-size="mini"
          :definition-id="definitionId"
          :scope-comp-id="scopeCompId"
          @apply-model="onApplyDesignSnapshot"
        />
        <el-button size="mini" type="warning" icon="el-icon-cpu" @click="handleCompile">编译</el-button>
        <el-button size="mini" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <div v-loading="scopeContentLoading" class="tree-body">
      <div class="tree-canvas">
        <horizontal-decision-tree
          :nodes="treeNodes"
          :edges="treeEdges"
          :selected-node-id="selectedNodeIdForTree"
          :selected-edge-id="selectedEdgeIdForTree"
          @select-node="onTreeSelectNode"
          @select-edge="onTreeSelectEdge"
          @tree-command="onTreeCommand"
          @dismiss-selection="clearSelection"
        />
      </div>

      <transition name="panel-slide">
        <div v-if="activeElement" class="tree-property">
          <div class="prop-header">
            <span class="prop-title">
              <i :class="propIcon" />
              {{ isEdge ? '连线属性' : '节点属性配置' }}
            </span>
            <i class="el-icon-close prop-close" @click="clearSelection" />
          </div>

          <template v-if="isEdge">
            <div class="prop-section">
              <el-form size="small" label-width="70px" class="prop-form">
                <el-form-item label="分支标签">
                  <el-input v-model="edgeProps.conditionName" placeholder="如：是、否、金额>500" @input="onEdgeChange" />
                </el-form-item>
              </el-form>
            </div>

            <div v-if="edgeSourceIsDecision" class="prop-section">
              <div class="section-title">
                <span>条件表达式</span>
                <el-radio-group v-model="edgeCondMode" size="mini">
                  <el-radio-button label="visual">可视化</el-radio-button>
                  <el-radio-button label="script">脚本</el-radio-button>
                </el-radio-group>
              </div>

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
                    <el-option label="包含于 (in)" value="in" />
                    <el-option label="字符串包含 (contains)" value="contains" />
                    <el-option label="前匹配 (startsWith)" value="startsWith" />
                    <el-option label="后匹配 (endsWith)" value="endsWith" />
                  </el-select>
                </div>
                <div class="cond-row">
                  <span class="cond-label">值</span>
                  <el-input v-model="edgeCondVisual.rightValue" size="mini" placeholder="比较值，如：100000">
                    <el-select slot="prepend" v-model="edgeCondVisual.rightType" style="width:70px" size="mini">
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

            <!-- 非条件判断节点出边：不开放条件配置，存量失效条件提供清除入口 -->
            <div v-else class="prop-section">
              <div class="section-title"><span>条件表达式</span></div>
              <div class="hint-box">
                <i class="el-icon-info" /> 仅“条件判断”节点的出边支持配置条件表达式，如需按条件分流，请在该连线前插入条件判断节点
              </div>
              <template v-if="edgeProps.conditionExpr">
                <div class="generated-expr">
                  <code>{{ edgeProps.conditionExpr }}</code>
                </div>
                <div class="hint-box" style="margin-top:6px;">
                  <i class="el-icon-warning-outline" /> 该连线已配置的条件不会生效，且会导致编译失败，请清除
                </div>
                <el-button type="warning" size="mini" icon="el-icon-delete" style="width:100%;margin-top:8px;" @click="clearInvalidEdgeCond">
                  清除条件
                </el-button>
              </template>
            </div>
          </template>

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
                    <i class="el-icon-info" /> 右键画布节点可添加分支
                  </div>
                </div>
                <div class="hint-box" style="margin-top:6px;">
                  <i class="el-icon-info" /> 点击分支项可配置条件表达式
                </div>
              </div>
            </template>

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

            <div class="prop-section" style="padding-top:4px;">
              <el-button
                v-if="activeElement.type !== 'start-event'"
                type="danger"
                size="small"
                plain
                icon="el-icon-delete"
                style="width:100%;"
                @click="deleteCurrentNode"
              >
                删除此节点
              </el-button>
            </div>
          </template>
        </div>
      </transition>

      <div v-if="!activeElement" class="tree-hint">
        <i class="el-icon-share hint-icon" />
        <p>点击节点或分支配置属性；右键节点可添加判断、条件、动作</p>
      </div>
    </div>

    <div v-if="definitionId" class="tree-script-area">
      <script-panel
        ref="scriptPanel"
        :definition-id="definitionId"
        :scope-comp-id="scopeCompId"
        :on-before-compile="persistModelSilent"
        @mode-change="onScriptModeChange"
      />
    </div>

    <el-dialog title="测试执行" :visible.sync="testVisible" width="600px" append-to-body>
      <p class="test-hint"><i class="el-icon-info" /> 请输入测试参数（JSON 格式）</p>
      <el-input
        v-model="testParamsJson"
        type="textarea"
        :rows="6"
        placeholder="{&quot;income&quot;: 100000, &quot;taxRate&quot;: 0.13}"
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

    <design-save-version-dialog ref="designSaveVersionDialog" />
  </div>
</template>

<script>
import {compileRule, executeRule, getContent, saveContent} from '@/api/definition'
import {generateScript} from '@/utils/actionDataCodegen'
import {buildQlConditionExpr, inferConstVarType, parseQlConditionExpr} from '@/utils/conditionExpr'
import varPickerMixin from '@/mixins/varPickerMixin'
import VarPicker from '@/components/common/VarPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'
import designerDefinitionIdMixin from '@/mixins/designerDefinitionIdMixin'
import designerScopeMixin from '@/mixins/designerScopeMixin'
import ActionBlockEditor from '@/components/flow/ActionBlockEditor.vue'
import DesignSaveVersionDialog from '@/components/designer/DesignSaveVersionDialog.vue'
import DesignVersionSwitcher from '@/components/designer/DesignVersionSwitcher.vue'
import HorizontalDecisionTree from '@/components/decision-tree/HorizontalDecisionTree.vue'
import {
  addBranchToDecision,
  addBranchWithNestedDecision,
  addChildDecision,
  addChildTask,
  backendTypeToUi,
  deleteBranchByEdgeId,
  deleteSubtree,
  extractTreeGraphFromModel,
  getDefaultTreeGraph,
  getOutEdges,
  nodeById,
  patchEdge,
  patchNode,
  validateTreeModel
} from '@/components/decision-tree/treeGraphModel'

export default {
  name: 'DecisionTree',
  components: {
    VarPicker,
    ScriptPanel,
    ActionBlockEditor,
    DesignSaveVersionDialog,
    DesignVersionSwitcher,
    HorizontalDecisionTree
  },
  mixins: [varPickerMixin, designerScopeMixin, designerDefinitionIdMixin],
  data() {
    return {
      definitionId: null,
      treeNodes: [],
      treeEdges: [],
      activeElement: null,
      hasSelection: false,
      scriptMode: 'visual',
      nodeProps: {},
      edgeProps: {},
      edgeCondMode: 'visual',
      edgeCondVisual: { leftVar: '', leftLabel: '', operator: '==', rightValue: '', rightType: 'value', rightVar: '' },
      actionMode: 'visual',
      currentActionData: [],
      testVisible: false,
      testParamsJson: '{}',
      testResult: null,
      /** 撤销栈：每项为 { nodes, edges } 深拷贝快照 */
      undoStack: [],
      /** 重做栈 */
      redoStack: [],
      /** 最多保留的撤销步数 */
      treeHistoryMax: 50
    }
  },
  computed: {
    /**
     * 横向树高亮用节点 id
     */
    selectedNodeIdForTree() {
      return this.activeElement && this.activeElement.kind === 'node' ? this.activeElement.id : ''
    },
    /**
     * 横向树高亮用边 id
     */
    selectedEdgeIdForTree() {
      return this.activeElement && this.activeElement.kind === 'edge' ? this.activeElement.id : ''
    },
    isEdge() {
      return this.activeElement && this.activeElement.kind === 'edge'
    },
    /** 当前选中连线的起点是否为条件判断节点（仅其出边允许配置条件表达式） */
    edgeSourceIsDecision() {
      if (!this.isEdge || !this.activeElement) return false
      const src = nodeById(this.treeNodes, this.activeElement.sourceNodeId)
      return !!src && src.type === 'decision'
    },
    propIcon() {
      if (this.isEdge) return 'el-icon-connection'
      if (!this.activeElement) return 'el-icon-s-help'
      const map = {
        'start-event': 'el-icon-video-play',
        'exclusive-gateway': 'el-icon-sort',
        'script-task': 'el-icon-document',
        'join-gateway': 'el-icon-copy-document'
      }
      return map[this.activeElement.type] || 'el-icon-s-help'
    },
    /**
     * 当前选中判断节点的出边列表
     */
    outEdges() {
      if (!this.activeElement || this.activeElement.kind !== 'node') return []
      if (this.activeElement.type !== 'exclusive-gateway') return []
      return getOutEdges(this.treeEdges, this.activeElement.id)
    },
    scriptPreview() {
      return generateScript(this.currentActionData)
    },
    /**
     * 是否可撤销（与决策流工具栏一致）
     */
    canUndoTree() {
      return this.undoStack.length > 0
    },
    /**
     * 是否可重做
     */
    canRedoTree() {
      return this.redoStack.length > 0
    }
  },
  created() {
    this.definitionId = this.resolveDefinitionIdFromContext()
  },
  mounted() {
    const boot = async() => {
      try {
        await this.bootstrapDesignerWithScope()
      } catch (e) {
        this.$message.error('加载失败: ' + (e.message || '未知错误'))
        this.applyParsedFlowModel({})
      }
    }
    boot()
    this.bindTreeHistoryHotkeys()
  },
  beforeDestroy() {
    this.unbindTreeHistoryHotkeys()
  },
  methods: {
    /**
     * 注册全局快捷键：Ctrl/Cmd+Z 撤销、Ctrl/Cmd+Y 与 Ctrl/Cmd+Shift+Z 重做（与决策流一致；输入框内不拦截）
     */
    bindTreeHistoryHotkeys() {
      this._treeHistoryKeyHandler = e => {
        const tag = (e.target && e.target.tagName) || ''
        if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target && e.target.isContentEditable)) return
        const mod = e.ctrlKey || e.metaKey
        if (!mod) return
        if (e.key === 'z' || e.key === 'Z') {
          if (e.shiftKey) {
            e.preventDefault()
            this.redoTree()
          } else {
            e.preventDefault()
            this.undoTree()
          }
          return
        }
        if (e.key === 'y' || e.key === 'Y') {
          e.preventDefault()
          this.redoTree()
        }
      }
      window.addEventListener('keydown', this._treeHistoryKeyHandler, true)
    },
    /**
     * 移除决策树撤销快捷键监听
     */
    unbindTreeHistoryHotkeys() {
      if (this._treeHistoryKeyHandler) {
        window.removeEventListener('keydown', this._treeHistoryKeyHandler, true)
        this._treeHistoryKeyHandler = null
      }
    },
    /**
     * 深拷贝当前 nodes/edges 为历史快照
     * @returns {{ nodes: object[], edges: object[] }}
     */
    cloneTreeSnapshot() {
      return {
        nodes: JSON.parse(JSON.stringify(this.treeNodes || [])),
        edges: JSON.parse(JSON.stringify(this.treeEdges || []))
      }
    },
    /**
     * 图操作前压栈：保存当前图状态并清空重做栈
     */
    pushUndoSnapshot() {
      const snap = this.cloneTreeSnapshot()
      this.undoStack.push(snap)
      if (this.undoStack.length > this.treeHistoryMax) {
        this.undoStack.shift()
      }
      this.redoStack = []
    },
    /**
     * 判断 graph 操作是否未改变引用（用于撤销误压栈）
     * @param {{ nodes: object[], edges: object[] }} r
     * @returns {boolean}
     */
    isGraphUnchangedByResult(r) {
      return r && r.nodes === this.treeNodes && r.edges === this.treeEdges
    },
    /**
     * 清空撤销/重做（加载、切换版本时调用）
     */
    clearTreeHistory() {
      this.undoStack = []
      this.redoStack = []
    },
    /**
     * 撤销一步结构变更
     */
    undoTree() {
      if (!this.undoStack.length) return
      const current = this.cloneTreeSnapshot()
      const prev = this.undoStack.pop()
      this.redoStack.push(current)
      this.treeNodes = JSON.parse(JSON.stringify(prev.nodes || []))
      this.treeEdges = JSON.parse(JSON.stringify(prev.edges || []))
      this.$nextTick(() => this.syncSelectionAfterHistory())
    },
    /**
     * 重做一步
     */
    redoTree() {
      if (!this.redoStack.length) return
      const current = this.cloneTreeSnapshot()
      const next = this.redoStack.pop()
      this.undoStack.push(current)
      if (this.undoStack.length > this.treeHistoryMax) {
        this.undoStack.shift()
      }
      this.treeNodes = JSON.parse(JSON.stringify(next.nodes || []))
      this.treeEdges = JSON.parse(JSON.stringify(next.edges || []))
      this.$nextTick(() => this.syncSelectionAfterHistory())
    },
    /**
     * 撤销/重做后同步属性面板与选中 id
     */
    syncSelectionAfterHistory() {
      if (!this.activeElement) return
      if (this.activeElement.kind === 'node') {
        if (!nodeById(this.treeNodes, this.activeElement.id)) {
          this.clearSelection()
        } else {
          this.onTreeSelectNode(this.activeElement.id)
        }
      } else if (this.activeElement.kind === 'edge') {
        const e = (this.treeEdges || []).find(x => x.id === this.activeElement.id)
        if (!e) {
          this.clearSelection()
        } else {
          this.onTreeSelectEdge(this.activeElement.id)
        }
      }
    },
    /**
     * 取消选中
     */
    clearSelection() {
      this.activeElement = null
      this.hasSelection = false
    },
    /**
     * 从画布选中节点
     */
    onTreeSelectNode(nodeId) {
      const n = nodeById(this.treeNodes, nodeId)
      if (!n) return
      this.activeElement = { kind: 'node', id: n.id, type: backendTypeToUi(n.type) }
      this.nodeProps = { nodeName: n.name || '', nodeDesc: '', gatewayDirection: 'Diverging' }
      this.hasSelection = true
      if (n.type === 'task') {
        this.currentActionData = Array.isArray(n.actionData) ? [...n.actionData] : []
        this.actionMode = 'visual'
      }
    },
    /**
     * 从画布选中边
     */
    onTreeSelectEdge(edgeId) {
      const e = (this.treeEdges || []).find(x => x.id === edgeId)
      if (!e) return
      this.activeElement = {
        kind: 'edge',
        id: e.id,
        type: 'polyline',
        sourceNodeId: e.source,
        targetNodeId: e.target
      }
      this.edgeProps = {
        conditionName: e.name || '',
        conditionExpr: e.conditionExpression || ''
      }
      this.edgeCondVisual = this.syncCondVisualFromExpr(this.edgeProps.conditionExpr)
      this.edgeCondMode = this.resolveEdgeCondModeForPanel(this.edgeProps.conditionExpr)
      this.hasSelection = true
    },
    /**
     * 处理横向树右键菜单命令
     */
    onTreeCommand(cmd) {
      if (!cmd || !cmd.cmd) return
      switch (cmd.cmd) {
        case 'deleteNode':
          this.confirmDeleteNode(cmd.nodeId)
          break
        case 'deleteEdge':
          this.confirmDeleteEdge(cmd.edgeId)
          break
        case 'addChildDecision':
          this.runAddChildDecision(cmd.nodeId)
          break
        case 'addBranch':
          this.runAddBranch(cmd.nodeId)
          break
        case 'addNestedDecision':
          this.runAddNestedDecision(cmd.nodeId)
          break
        case 'addChildTask':
          this.runAddChildTask(cmd.nodeId)
          break
        default:
          break
      }
    },
    /**
     * 工具栏：添加判断/子树
     */
    toolbarAddDecision() {
      if (!this.requireNodeSelection()) return
      const id = this.activeElement.id
      const n = nodeById(this.treeNodes, id)
      if (!n) return
      if (n.type === 'decision') {
        this.pushUndoSnapshot()
        const r = addBranchWithNestedDecision(this.treeNodes, this.treeEdges, id)
        if (this.isGraphUnchangedByResult(r)) {
          this.undoStack.pop()
          return
        }
        this.treeNodes = r.nodes
        this.treeEdges = r.edges
        return
      }
      if (getOutEdges(this.treeEdges, id).length > 0) {
        this.$message.warning('该节点已有下级，请删除或调整后再插入判断')
        return
      }
      this.pushUndoSnapshot()
      const r = addChildDecision(this.treeNodes, this.treeEdges, id)
      if (this.isGraphUnchangedByResult(r)) {
        this.undoStack.pop()
        return
      }
      this.treeNodes = r.nodes
      this.treeEdges = r.edges
    },
    /**
     * 工具栏：添加执行动作分支或链
     */
    toolbarAddTask() {
      if (!this.requireNodeSelection()) return
      const id = this.activeElement.id
      const n = nodeById(this.treeNodes, id)
      if (!n) return
      if (n.type === 'decision') {
        this.pushUndoSnapshot()
        const r = addBranchToDecision(this.treeNodes, this.treeEdges, id)
        if (!r.newEdgeId) {
          this.undoStack.pop()
          return
        }
        this.treeNodes = r.nodes
        this.treeEdges = r.edges
        return
      }
      if (getOutEdges(this.treeEdges, id).length > 0) {
        this.$message.warning('该节点已有下级')
        return
      }
      this.pushUndoSnapshot()
      const r = addChildTask(this.treeNodes, this.treeEdges, id)
      this.treeNodes = r.nodes
      this.treeEdges = r.edges
    },
    /**
     * 要求已选中节点
     */
    requireNodeSelection() {
      if (!this.activeElement || this.activeElement.kind !== 'node') {
        this.$message.warning('请先选中一个节点')
        return false
      }
      return true
    },
    /**
     * 在父节点下挂子判断（父须无出边或已为判断时的处理在外部）
     */
    runAddChildDecision(parentId) {
      const n = nodeById(this.treeNodes, parentId)
      if (!n) return
      if (n.type === 'start' && getOutEdges(this.treeEdges, parentId).length > 0) {
        this.$message.warning('开始节点已有下级')
        return
      }
      if (n.type !== 'start' && n.type !== 'task') {
        if (n.type === 'decision') {
          this.runAddNestedDecision(parentId)
          return
        }
        this.$message.warning('该节点类型不支持此操作')
        return
      }
      if (getOutEdges(this.treeEdges, parentId).length > 0) {
        this.$message.warning('该节点已有下级')
        return
      }
      this.pushUndoSnapshot()
      const r = addChildDecision(this.treeNodes, this.treeEdges, parentId)
      if (this.isGraphUnchangedByResult(r)) {
        this.undoStack.pop()
        return
      }
      this.treeNodes = r.nodes
      this.treeEdges = r.edges
    },
    /**
     * 判断节点新增一条任务分支
     */
    runAddBranch(decisionId) {
      this.pushUndoSnapshot()
      const r = addBranchToDecision(this.treeNodes, this.treeEdges, decisionId)
      if (!r.newEdgeId) {
        this.undoStack.pop()
        return
      }
      this.treeNodes = r.nodes
      this.treeEdges = r.edges
    },
    /**
     * 判断节点新增一条「子判断」分支
     */
    runAddNestedDecision(decisionId) {
      this.pushUndoSnapshot()
      const r = addBranchWithNestedDecision(this.treeNodes, this.treeEdges, decisionId)
      if (this.isGraphUnchangedByResult(r)) {
        this.undoStack.pop()
        return
      }
      this.treeNodes = r.nodes
      this.treeEdges = r.edges
    },
    /**
     * 添加任务子节点
     */
    runAddChildTask(parentId) {
      const n = nodeById(this.treeNodes, parentId)
      if (!n) return
      if (n.type === 'decision') {
        this.runAddBranch(parentId)
        return
      }
      if (getOutEdges(this.treeEdges, parentId).length > 0) {
        this.$message.warning('该节点已有下级')
        return
      }
      this.pushUndoSnapshot()
      const r = addChildTask(this.treeNodes, this.treeEdges, parentId)
      this.treeNodes = r.nodes
      this.treeEdges = r.edges
    },
    /**
     * 确认删除节点子树
     */
    confirmDeleteNode(nodeId) {
      const n = nodeById(this.treeNodes, nodeId)
      if (n && n.type === 'start') {
        this.$message.warning('不能删除开始节点')
        return
      }
      this.$confirm('确认删除该节点及其子树？', '提示', { type: 'warning' })
        .then(() => {
          this.pushUndoSnapshot()
          const { nodes, edges } = deleteSubtree(this.treeNodes, this.treeEdges, nodeId)
          this.treeNodes = nodes
          this.treeEdges = edges
          if (this.activeElement && this.activeElement.id === nodeId) {
            this.clearSelection()
          }
        })
        .catch(() => {})
    },
    /**
     * 确认删除一条分支（边及目标子树）
     */
    confirmDeleteEdge(edgeId) {
      this.$confirm('确认删除该分支及其子树？', '提示', { type: 'warning' })
        .then(() => {
          this.pushUndoSnapshot()
          const { nodes, edges } = deleteBranchByEdgeId(this.treeNodes, this.treeEdges, edgeId)
          this.treeNodes = nodes
          this.treeEdges = edges
          if (this.activeElement && this.activeElement.kind === 'edge' && this.activeElement.id === edgeId) {
            this.clearSelection()
          }
        })
        .catch(() => {})
    },
    selectEdgeById(edgeId) {
      this.onTreeSelectEdge(edgeId)
    },
    /**
     * 同步节点名称等到 treeNodes
     */
    onNodeChange() {
      if (!this.activeElement || this.activeElement.kind !== 'node') return
      this.treeNodes = patchNode(this.treeNodes, this.activeElement.id, {
        name: this.nodeProps.nodeName || ''
      })
    },
    onActionDataUpdate(data) {
      this.currentActionData = data
      if (this.activeElement && this.activeElement.kind === 'node' && this.activeElement.type === 'script-task') {
        this.treeNodes = patchNode(this.treeNodes, this.activeElement.id, {
          actionData: Array.isArray(data) ? data : []
        })
      }
    },
    /**
     * 同步边属性
     */
    onEdgeChange() {
      if (!this.activeElement || this.activeElement.kind !== 'edge') return
      this.treeEdges = patchEdge(this.treeEdges, this.activeElement.id, {
        name: this.edgeProps.conditionName || '',
        conditionExpression: this.edgeProps.conditionExpr || ''
      })
    },
    /** 清除非条件判断节点出边上的存量失效条件，避免编译报错 */
    clearInvalidEdgeCond() {
      this.edgeProps.conditionExpr = ''
      this.edgeCondVisual = this.syncCondVisualFromExpr('')
      this.onEdgeChange()
      this.$message.success('已清除该连线的条件表达式')
    },
    deleteSelected() {
      if (!this.activeElement) return
      if (this.activeElement.kind === 'node') {
        this.confirmDeleteNode(this.activeElement.id)
      } else {
        this.confirmDeleteEdge(this.activeElement.id)
      }
    },
    deleteCurrentNode() {
      if (!this.activeElement || this.activeElement.kind !== 'node') return
      this.confirmDeleteNode(this.activeElement.id)
    },
    nodeTypeLabel(type) {
      const map = {
        'start-event': '开始节点',
        'exclusive-gateway': '条件判断（网关）',
        'script-task': '执行动作（脚本任务）',
        'join-gateway': '聚合节点'
      }
      return map[type] || type
    },
    nodeTypeTag(type) {
      const map = {
        'start-event': 'success',
        'exclusive-gateway': 'warning',
        'script-task': '',
        'join-gateway': 'info'
      }
      return map[type] || 'info'
    },
    /**
     * 分支列表展示文案
     */
    edgeLabel(edge) {
      if (edge.name) return edge.name
      if (edge.conditionExpression) return edge.conditionExpression
      return ''
    },
    handleValidate() {
      const errors = validateTreeModel(this.treeNodes, this.treeEdges)
      if (errors.length === 0) {
        this.$message.success('验证通过！')
      } else {
        this.$alert(errors.map((e, i) => (i + 1) + '. ' + e).join('\n'), '验证失败', { type: 'warning' })
      }
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId, this.scopeCompId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          const modelData = JSON.parse(content.modelJson)
          this.applyParsedFlowModel(modelData)
        } else {
          const g = getDefaultTreeGraph()
          this.treeNodes = g.nodes
          this.treeEdges = g.edges
          this.clearTreeHistory()
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
        const g = getDefaultTreeGraph()
        this.treeNodes = g.nodes
        this.treeEdges = g.edges
        this.clearTreeHistory()
      }
    },
    /**
     * 应用已解析 model（加载与版本回滚）
     */
    applyParsedFlowModel(modelData) {
      this.clearTreeHistory()
      const { nodes, edges } = extractTreeGraphFromModel(modelData)
      this.treeNodes = nodes
      this.treeEdges = edges
      this.clearSelection()
    },
    onApplyDesignSnapshot(parsed) {
      if (!parsed || typeof parsed !== 'object') return
      this.applyParsedFlowModel(parsed)
    },
    /**
     * 构建保存用 modelJson（仅 nodes + edges）
     */
    buildBackendModel() {
      if (this.activeElement && this.activeElement.kind === 'node' && this.activeElement.type === 'script-task') {
        this.treeNodes = patchNode(this.treeNodes, this.activeElement.id, {
          actionData: Array.isArray(this.currentActionData) ? this.currentActionData : []
        })
      }
      const nodes = (this.treeNodes || []).map(n => ({
        id: n.id,
        type: n.type,
        name: n.name || '',
        actionData: Array.isArray(n.actionData) ? n.actionData : [],
        gatewayDirection: n.gatewayDirection || ''
      }))
      const edges = (this.treeEdges || []).map(e => ({
        id: e.id,
        source: e.source,
        target: e.target,
        conditionExpression: e.conditionExpression || '',
        name: e.name || ''
      }))
      return { nodes, edges }
    },
    async persistModelSilent() {
      const modelJson = JSON.stringify(this.buildBackendModel())
      await saveContent({
        definitionId: this.definitionId,
        scopeCompId: this.scopeCompId,
        modelJson,
        recordHistory: false
      })
    },
    async handleSave() {
      if (!this.ensureVisualEditable()) return
      let changeLog = ''
      try {
        changeLog = await this.$refs.designSaveVersionDialog.prompt()
      } catch (e) {
        return
      }
      const modelJson = JSON.stringify(this.buildBackendModel())
      // 后端“保存即编译”：校验失败回滚不落库（错误由统一拦截器弹出）；拦截器不 reject，须判返回码
      const res = await saveContent({
        definitionId: this.definitionId,
        scopeCompId: this.scopeCompId,
        modelJson,
        changeLog: changeLog || undefined,
        recordHistory: true
      })
      if (res && res.code === 200) {
        this.$message.success('保存成功，规则已生效，可直接「发布」')
        if (this.$refs.scriptPanel) this.$refs.scriptPanel.refresh()
      }
    },
    async handleCompile() {
      if (!this.ensureVisualEditable()) return
      await this.persistModelSilent()
      const res = await compileRule(this.definitionId, this.scopeCompId)
      if (res && res.data && res.data.success) {
        this.$message.success('编译成功')
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
      try {
        params = JSON.parse(this.testParamsJson || '{}')
      } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await executeRule({ definitionId: this.definitionId, scopeCompId: this.scopeCompId, params })
      this.testResult = res && res.data ? res.data : res
    },
    onScriptModeChange(mode) {
      this.scriptMode = mode
    },
    varTypeTag(varType) {
      return this.varTypeColor(varType)
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
      if (!left) {
        this.$message.warning('请选择左侧变量')
        return null
      }
      const op = visual.operator
      const mode = visual.rightType === 'var' ? 'var' : 'value'
      const raw = mode === 'var' ? visual.rightVar : visual.rightValue
      if (raw == null || raw === '') {
        this.$message.warning('请填写比较值')
        return null
      }
      const constType = mode === 'value' ? inferConstVarType(raw) : undefined
      return buildQlConditionExpr(left, op, String(raw), mode, constType)
    },
    applyEdgeCondVisual() {
      const expr = this.buildCondExpr(this.edgeCondVisual)
      if (expr === null) return
      this.edgeProps.conditionExpr = expr
      if (!this.edgeProps.conditionName) {
        this.edgeProps.conditionName =
          (this.edgeCondVisual.leftLabel || this.edgeCondVisual.leftVar) +
          this.edgeCondVisual.operator +
          (this.edgeCondVisual.rightType === 'var' ? this.edgeCondVisual.rightVar : this.edgeCondVisual.rightValue)
      }
      this.onEdgeChange()
      this.$message.success('已生成: ' + expr)
    },
    /**
     * 选中连线时条件编辑区模式：默认可视化；占位字面量 true/false、空表达式、可单条解析的均走可视化
     */
    resolveEdgeCondModeForPanel(expr) {
      const ex = (expr || '').trim()
      if (!ex) return 'visual'
      if (/^(true|false)$/i.test(ex)) return 'visual'
      return parseQlConditionExpr(ex) ? 'visual' : 'script'
    },
    syncCondVisualFromExpr(expr) {
      const p = parseQlConditionExpr(expr)
      if (p) {
        return {
          leftVar: p.leftVar,
          leftLabel: '',
          operator: p.operator,
          rightValue: p.rightType === 'value' ? p.rightValue : '',
          rightType: p.rightType,
          rightVar: p.rightType === 'var' ? (p.rightVar || p.rightValue) : ''
        }
      }
      return { leftVar: '', leftLabel: '', operator: '==', rightValue: '', rightType: 'value', rightVar: '' }
    },
    formatJson(obj) {
      if (obj === null || obj === undefined) return '(空)'
      try {
        return JSON.stringify(typeof obj === 'string' ? JSON.parse(obj) : obj, null, 2)
      } catch (e) {
        return String(obj)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.tree-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 82px);
  background: #fff;
  position: relative;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
.tree-script-area {
  flex-shrink: 0;
  max-height: 60%;
  overflow-y: auto;
  border-top: 1px solid #e8e8e8;
  background: #fff;
}
.tree-toolbar {
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
.toolbar-left,
.toolbar-center,
.toolbar-right {
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
  border-color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.1);
  &:hover {
    background: rgba(255, 255, 255, 0.2);
    border-color: rgba(255, 255, 255, 0.7);
  }
  &.el-button--primary {
    background: #fff;
    color: #1890ff;
    border-color: #fff;
  }
  &.el-button--warning {
    background: rgba(250, 140, 22, 0.9);
    border-color: transparent;
  }
}
.node-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}
.tree-body {
  flex: 1;
  display: flex;
  min-height: 0;
  position: relative;
  background: #f7f8fa;
}
.tree-canvas {
  flex: 1;
  min-height: 0;
  min-width: 0;
  position: relative;
}
.tree-hint {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  color: #bbb;
  pointer-events: none;
  .hint-icon {
    font-size: 32px;
    display: block;
    margin-bottom: 6px;
  }
  p {
    margin: 2px 0;
    font-size: 13px;
    line-height: 1.6;
  }
}
.tree-property {
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
  &:hover {
    color: #333;
  }
}
.prop-section {
  padding: 10px 14px;
  border-bottom: 1px solid #f0f0f0;
  &:last-child {
    border-bottom: none;
  }
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
.cond-builder,
.cond-script {
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
  &:hover {
    background: #f0f7ff;
    border-color: #1890ff;
  }
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
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: width 0.2s ease, opacity 0.2s;
}
.panel-slide-enter,
.panel-slide-leave-to {
  width: 0;
  opacity: 0;
}
.test-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
.test-result {
  margin-top: 12px;
}
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
