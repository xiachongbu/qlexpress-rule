<template>
  <div class="property-panel" v-if="activeElement">
    <div class="panel-header">
      <span class="panel-title">{{ isEdge ? '连线属性' : '节点属性' }}</span>
      <i class="el-icon-close panel-close" @click="$emit('close')" />
    </div>

    <!-- 连线属性 -->
    <template v-if="isEdge">
      <el-form size="mini" label-width="70px" class="prop-form">
        <el-form-item label="连线ID">
          <el-input :value="activeElement.id" disabled />
        </el-form-item>
        <el-form-item label="条件名称">
          <el-input v-model="edgeProps.conditionName" placeholder="如：金额大于500" @change="onEdgeChange" />
        </el-form-item>
        <el-form-item label="条件表达式">
          <el-input
            v-model="edgeProps.conditionExpr"
            type="textarea"
            :rows="3"
            placeholder="如：amount > 500"
            @change="onEdgeChange"
          />
        </el-form-item>
      </el-form>
    </template>

    <!-- 节点属性 -->
    <template v-else>
      <el-form size="mini" label-width="70px" class="prop-form">
        <el-form-item label="节点ID">
          <el-input :value="activeElement.id" disabled />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="nodeProps.nodeName" placeholder="节点名称" @change="onNodeChange" />
        </el-form-item>
        <el-form-item label="节点编码">
          <el-input v-model="nodeProps.nodeCode" placeholder="节点编码" @change="onNodeChange" />
        </el-form-item>
        <el-form-item label="节点描述">
          <el-input v-model="nodeProps.nodeDesc" type="textarea" :rows="2" placeholder="节点描述" @change="onNodeChange" />
        </el-form-item>
      </el-form>

      <!-- 排他网关额外属性 -->
      <template v-if="activeElement.type === 'exclusive-gateway'">
        <el-divider content-position="left">排他网关配置</el-divider>
        <el-form size="mini" label-width="70px" class="prop-form">
          <el-form-item label="网关方向">
            <el-select v-model="nodeProps.gatewayDirection" style="width:100%;" @change="onNodeChange">
              <el-option label="Diverging（分支）" value="Diverging" />
              <el-option label="Converging（汇聚）" value="Converging" />
            </el-select>
          </el-form-item>
          <el-form-item label="默认分支">
            <el-select v-model="nodeProps.defaultBranch" clearable style="width:100%;" placeholder="无" @change="onNodeChange">
              <el-option
                v-for="edge in outEdges"
                :key="edge.id"
                :label="(edge.properties && edge.properties.conditionName) || edge.id"
                :value="edge.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <div class="hint-text">
              <i class="el-icon-info" /> 排他网关用于条件分支，根据条件走其中一条路径执行
            </div>
          </el-form-item>
        </el-form>

        <el-divider content-position="left">出口列表</el-divider>
        <div class="edge-list">
          <div v-for="edge in outEdges" :key="edge.id" class="edge-item" @click="$emit('select-edge', edge.id)">
            <span class="edge-arrow">→</span>
            <span class="edge-label">{{ edgeLabel(edge) }}</span>
            <el-tag v-if="edge.id === nodeProps.defaultBranch" size="mini" type="warning">默认</el-tag>
          </div>
          <div v-if="outEdges.length === 0" class="hint-text" style="padding: 8px 0;">暂无出口连线</div>
        </div>
      </template>

      <!-- 聚合节点额外属性 -->
      <template v-if="activeElement.type === 'join-gateway'">
        <el-divider content-position="left">聚合配置</el-divider>
        <el-form size="mini" label-width="70px" class="prop-form">
          <el-form-item>
            <div class="hint-text">
              <i class="el-icon-info" /> 聚合节点用于多条分支汇合，所有入边的分支都到达后，继续向下执行
            </div>
          </el-form-item>
        </el-form>
      </template>

      <!-- 脚本任务额外属性 -->
      <template v-if="activeElement.type === 'script-task'">
        <el-divider content-position="left">脚本任务配置</el-divider>
        <el-form size="mini" label-width="70px" class="prop-form">
          <el-form-item label="脚本模式">
            <el-radio-group v-model="nodeProps.scriptMode" size="mini" @change="onNodeChange">
              <el-radio-button label="visual">可视化配置</el-radio-button>
              <el-radio-button label="script">脚本模式</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="异步执行">
            <el-switch v-model="nodeProps.asyncExec" active-text="是" inactive-text="否" @change="onNodeChange" />
          </el-form-item>
          <el-form-item>
            <div class="hint-text">
              <i class="el-icon-info" /> 开启后，任务将在后台异步执行，不阻断流程
            </div>
          </el-form-item>
        </el-form>

        <!-- 可视化模式 - 动作配置 -->
        <template v-if="nodeProps.scriptMode === 'visual'">
          <el-divider content-position="left">动作配置</el-divider>
          <div class="action-list">
            <div v-for="(action, idx) in nodeProps.actions" :key="idx" class="action-item">
              <el-select v-model="action.variable" size="mini" placeholder="变量" style="width: 140px;" @change="onNodeChange" filterable>
                <!-- 项目变量库（优先） -->
                <el-option-group v-if="projectVars.length" label="项目变量">
                  <el-option
                    v-for="v in projectVars"
                    :key="'pv_' + v.varCode"
                    :label="v.varLabel + ' (' + v.varCode + ')'"
                    :value="v.varCode"
                  />
                </el-option-group>
                <!-- 图中已定义的变量 -->
                <el-option-group v-if="variables.length" label="图中变量">
                  <el-option v-for="v in variables" :key="v" :label="v" :value="v" />
                </el-option-group>
              </el-select>
              <span class="action-eq">=</span>
              <el-input v-model="action.value" size="mini" placeholder="值" style="flex:1;" @change="onNodeChange" />
              <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C;" @click="removeAction(idx)" />
            </div>
            <el-button type="primary" size="mini" plain icon="el-icon-plus" @click="addAction" style="width:100%;margin-top:8px;">
              添加动作
            </el-button>
          </div>
        </template>

        <!-- 脚本模式 -->
        <template v-if="nodeProps.scriptMode === 'script'">
          <el-divider content-position="left">动作配置</el-divider>
          <div class="script-editor">
            <!-- 可用变量提示 -->
            <div v-if="projectVars.length" class="var-chips">
              <span class="var-chips-label">可用变量：</span>
              <el-tag
                v-for="v in projectVars"
                :key="v.varCode"
                size="mini"
                :type="varTagType(v.varType)"
                style="cursor:pointer;margin:2px;"
                :title="v.varLabel + ' [' + v.varType + ']'"
                @click.native="copyVarCode(v.varCode)"
              >{{ v.varCode }}</el-tag>
            </div>
            <div class="script-lang">QLExpress 脚本</div>
            <el-input
              v-model="nodeProps.scriptContent"
              type="textarea"
              :rows="8"
              placeholder="// QLExpress 脚本&#10;taxRate = 0.13;&#10;result = &quot;一般税率&quot;;"
              class="mono-textarea"
              @change="onNodeChange"
            />
          </div>
        </template>
      </template>
    </template>
  </div>
</template>

<script>
import { varTypeTagColor } from '@/constants/varTypes'

export default {
  name: 'PropertyPanel',
  props: {
    activeElement: { type: Object, default: null },
    lf: { type: Object, default: null },
    /** 从 varPickerMixin 传入的项目变量列表 */
    projectVars: { type: Array, default: () => [] }
  },
  data() {
    return {
      nodeProps: {},
      edgeProps: {},
      variables: []
    }
  },
  computed: {
    isEdge() {
      return this.activeElement && this.activeElement.baseType === 'edge'
    },
    outEdges() {
      if (!this.lf || !this.activeElement || this.isEdge) return []
      try {
        const model = this.lf.getNodeModelById(this.activeElement.id)
        if (!model) return []
        const edges = this.lf.getNodeEdges(this.activeElement.id)
        return (edges || []).filter(e => e.sourceNodeId === this.activeElement.id)
      } catch (e) {
        return []
      }
    }
  },
  watch: {
    activeElement: {
      handler(val) {
        if (!val) return
        if (this.isEdge) {
          this.edgeProps = {
            conditionName: (val.properties && val.properties.conditionName) || '',
            conditionExpr: (val.properties && val.properties.conditionExpr) || ''
          }
        } else {
          this.nodeProps = {
            nodeName: (val.properties && val.properties.nodeName) || '',
            nodeCode: (val.properties && val.properties.nodeCode) || '',
            nodeDesc: (val.properties && val.properties.nodeDesc) || '',
            gatewayDirection: (val.properties && val.properties.gatewayDirection) || 'Diverging',
            defaultBranch: (val.properties && val.properties.defaultBranch) || '',
            scriptMode: (val.properties && val.properties.scriptMode) || 'visual',
            asyncExec: (val.properties && val.properties.asyncExec) || false,
            scriptContent: (val.properties && val.properties.scriptContent) || '',
            actions: (val.properties && val.properties.actions) ? JSON.parse(JSON.stringify(val.properties.actions)) : []
          }
        }
        this.loadVariables()
      },
      immediate: true,
      deep: true
    }
  },
  methods: {
    loadVariables() {
      try {
        const vars = new Set()
        // 先加入项目变量库中的变量
        ;(this.projectVars || []).forEach(v => vars.add(v.varCode))
        // 再补充画布中已使用的变量
        if (this.lf) {
          const graphData = this.lf.getGraphData()
          ;(graphData.nodes || []).forEach(n => {
            if (n.properties && n.properties.actions) {
              n.properties.actions.forEach(a => {
                if (a.variable) vars.add(a.variable)
              })
            }
          })
        }
        this.variables = Array.from(vars)
      } catch (e) {
        this.variables = []
      }
    },
    onNodeChange() {
      if (!this.lf || !this.activeElement) return
      this.lf.setProperties(this.activeElement.id, { ...this.nodeProps })
      this.$emit('changed')
    },
    onEdgeChange() {
      if (!this.lf || !this.activeElement) return
      this.lf.setProperties(this.activeElement.id, { ...this.edgeProps })
      this.updateEdgeText()
      this.$emit('changed')
    },
    updateEdgeText() {
      if (!this.lf || !this.activeElement) return
      const text = this.edgeProps.conditionName || ''
      try {
        this.lf.updateText(this.activeElement.id, text)
      } catch (e) { /* ignore */ }
    },
    addAction() {
      this.nodeProps.actions.push({ variable: '', value: '' })
      this.onNodeChange()
    },
    removeAction(idx) {
      this.nodeProps.actions.splice(idx, 1)
      this.onNodeChange()
    },
    edgeLabel(edge) {
      if (edge.properties && edge.properties.conditionName) return edge.properties.conditionName
      const targetNode = this.lf.getNodeModelById(edge.targetNodeId)
      if (targetNode && targetNode.properties && targetNode.properties.nodeName) {
        return '目标: ' + targetNode.properties.nodeName
      }
      return edge.id.substr(0, 16)
    },
    /** 与变量管理表格类型标签配色一致 */
    varTagType(varType) {
      return varTypeTagColor(varType)
    },
    copyVarCode(code) {
      if (navigator.clipboard) {
        navigator.clipboard.writeText(code).then(() => {
          this.$message({ message: '已复制变量：' + code, type: 'success', duration: 1200 })
        })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.property-panel {
  width: 320px;
  height: 100%;
  background: #fff;
  border-left: 1px solid #e8e8e8;
  overflow-y: auto;
  flex-shrink: 0;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
}
.panel-title {
  font-weight: bold;
  font-size: 14px;
}
.panel-close {
  cursor: pointer;
  color: #999;
  &:hover { color: #333; }
}
.prop-form {
  padding: 12px 16px 0;
}
.hint-text {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
}
.edge-list {
  padding: 0 16px 12px;
}
.edge-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin-bottom: 6px;
  cursor: pointer;
  &:hover { background: #f5f5f5; }
}
.edge-arrow {
  color: #1890ff;
  font-weight: bold;
}
.edge-label {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.action-list {
  padding: 0 16px 12px;
}
.action-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.action-eq {
  color: #999;
  font-weight: bold;
}
.script-editor {
  padding: 0 16px 12px;
}
.script-lang {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}
.var-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
  margin-bottom: 6px;
  border: 1px solid #f0f0f0;
}
.var-chips-label {
  font-size: 11px;
  color: #999;
  flex-shrink: 0;
}
.mono-textarea ::v-deep textarea {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}
.el-divider {
  margin: 12px 0 4px;
}
.el-divider ::v-deep .el-divider__text {
  font-size: 13px;
  font-weight: bold;
  color: #333;
}
</style>
