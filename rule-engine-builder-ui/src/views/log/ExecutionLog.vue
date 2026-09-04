<template>
  <div class="uiue-list-page">
    <div class="uiue-search-container">
      <el-form :inline="true" size="small" style="display:flex;flex-wrap:wrap;align-items:flex-start;" class="searchBtnVerticalForm">
        <el-form-item label="来源">
          <el-select v-model="qp.source" clearable placeholder="全部" @change="onSourceChange">
            <el-option label="服务端" value="SERVER" />
            <el-option label="客户端" value="CLIENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="qp.projectCode" clearable filterable placeholder="全部项目" @change="onProjectChange">
            <el-option v-for="p in projectList" :key="p.projectCode" :label="p.projectName" :value="p.projectCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则/规则集">
          <el-select v-model="qp.ruleCode" clearable filterable placeholder="全部">
            <el-option-group label="规则">
              <el-option v-for="r in filteredRules" :key="r.ruleCode" :label="r.ruleName" :value="r.ruleCode" />
            </el-option-group>
            <el-option-group v-if="filteredRuleSets.length" label="规则集">
              <el-option v-for="s in filteredRuleSets" :key="'set_'+s.setCode" :label="s.setName" :value="s.setCode" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="业务主键ID">
          <el-input v-model="qp.businessId" clearable placeholder="请输入业务主键ID" style="width:200px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <div class="date-picker-area">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              :unlink-panels="true"
              range-separator=""
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="yyyy-MM-dd HH:mm:ss"
              :default-time="['00:00:00','23:59:59']"
              :picker-options="pickerOptions"
            >
              <template v-slot:range-separator>
                <Icon name="switch2-line" />
              </template>
            </el-date-picker>
            <Icon class="suffixDateIcon" name="schedule-line" />
          </div>
        </el-form-item>
      </el-form>
      <div class="search-VerticalBtn">
        <el-button v-preventMousedown type="success" class="search" @click="qp.pageNum=1;load()">查询</el-button>
        <el-button v-preventMousedown type="primary" class="reset" @click="resetQuery">重置</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="list" border row-class-name="uiueTable" header-row-class-name="uiueTableHeader" style="width: 100%;margin-top: 8px">
      <el-table-column prop="projectCode" label="项目" min-width="100" show-overflow-tooltip sortable>
        <template #default="{row}">{{ projectMap[row.projectCode] || row.projectCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="ruleCode" label="规则编码" min-width="140" show-overflow-tooltip sortable>
        <template #default="{row}">
          <template v-if="row.modelType === 'RULE_SET'"><el-tag size="small" type="warning" style="margin-right:4px">集</el-tag>{{ ruleSetMap[row.ruleCode] || row.ruleCode }}</template>
          <template v-else>{{ ruleMap[row.ruleCode] || row.ruleCode }}</template>
        </template>
      </el-table-column>
      <el-table-column prop="modelType" label="模型类型" min-width="80" align="center" sortable>
        <template #default="{row}">{{ modelTypeMap[row.modelType] || row.modelType }}</template>
      </el-table-column>
      <el-table-column prop="source" label="来源" min-width="80" align="center" sortable>
        <template #default="{row}"><el-tag :type="row.source==='SERVER'?'':'success'" size="small">{{ row.source==='SERVER'?'服务端':'客户端' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="success" label="结果" min-width="70" align="center" sortable>
        <template #default="{row}"><el-tag :type="row.success===1?'success':'danger'" size="small">{{ row.success===1?'成功':'失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="executeTimeMs" label="耗时(ms)" min-width="80" align="center" sortable />
      <el-table-column prop="clientAppName" label="客户端" min-width="110" show-overflow-tooltip sortable />
      <el-table-column prop="createTime" label="执行时间" min-width="150" sortable>
        <template #default="{row}">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{row}">
          <span class="tableOperateBtn" @click="showDetail(row)">详情</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top:16px;text-align:right;"
      :current-page="qp.pageNum"
      :page-size="qp.pageSize"
      :total="total"
      layout="total,sizes,prev,pager,next"
      :page-sizes="[10,30,50,100,200,500]"
      @current-change="p=>{qp.pageNum=p;load()}"
      @size-change="s=>{qp.pageSize=s;qp.pageNum=1;load()}"
    />
    <el-drawer title="日志详情" v-model="detailVis" size="92%" @opened="onDetailDrawerOpened">
      <div v-if="detail" style="padding:16px">
        <!-- 汇总条：结论前置（成功/失败、耗时、模型、版本、来源、业务ID、执行时间） -->
        <div class="log-summary-bar">
          <el-tag :type="detail.success === 1 ? 'success' : 'danger'" size="small">{{ detail.success === 1 ? '执行成功' : '执行失败' }}</el-tag>
          <span class="log-summary-cost"><i class="el-icon-timer" /> {{ detail.executeTimeMs != null ? detail.executeTimeMs : '-' }}<small> ms</small></span>
          <span class="log-summary-item"><span class="lbl">模型</span><el-tag size="small">{{ modelTypeMap[detail.modelType] || detail.modelType || '-' }}</el-tag></span>
          <span v-if="detail.ruleVersion != null" class="log-summary-item"><span class="lbl">版本</span><span class="val">v{{ detail.ruleVersion }}</span></span>
          <span class="log-summary-item">
            <span class="lbl">来源</span>
            <el-tag :type="detail.source === 'SERVER' ? '' : 'success'" size="small">{{ detail.source === 'SERVER' ? '服务端' : '客户端' }}</el-tag>
            <span v-if="detail.clientAppName" class="val mono">{{ detail.clientAppName }}</span>
          </span>
          <span v-if="detail.businessId" class="log-summary-item">
            <span class="lbl">业务ID</span><span class="val mono">{{ detail.businessId }}</span>
            <i class="el-icon-document-copy copy-icon" title="复制业务ID" @click="copyText(detail.businessId, '业务ID')" />
          </span>
          <span class="log-summary-item"><span class="lbl">执行时间</span><span class="val mono">{{ formatTime(detail.createTime) }}</span></span>
        </div>
        <el-tabs v-model="detailTab" @tab-click="onDetailTabClick">
          <el-tab-pane label="基本信息" name="basic">
            <div class="uiue-card">
              <div class="uiue-card-title copyable-title">
                <span>输入参数</span>
                <i class="el-icon-document-copy copy-icon" title="复制输入参数" @click="copyText(fj(detail.inputParams), '输入参数')" />
              </div>
              <pre class="log-pre">{{ fj(detail.inputParams) }}</pre>
            </div>
            <div class="uiue-card" style="margin-top:12px">
              <div class="uiue-card-title copyable-title">
                <span>输出结果</span>
                <i class="el-icon-document-copy copy-icon" title="复制输出结果" @click="copyText(fj(detail.outputResult), '输出结果')" />
              </div>
              <pre class="log-pre">{{ fj(detail.outputResult) }}</pre>
            </div>
            <div v-if="detail.errorMessage" class="uiue-card" style="margin-top:12px">
              <div class="uiue-card-title" style="color:#F56C6C">错误信息</div>
              <pre class="log-pre error">{{ detail.errorMessage }}</pre>
            </div>
          </el-tab-pane>
          <el-tab-pane name="trace" :disabled="!detail.traceInfo">
            <template #label><span>
              <i class="el-icon-connection" /> 表达式追踪树
              <el-badge v-if="detail.traceInfo" is-dot class="trace-badge" />
            </span></template>
            <template v-if="detail.modelType === 'FLOW' && detail.traceInfo">
              <flow-trace-logic-flow
                ref="flowTraceLf"
                :definition-model="definitionModel"
                :trace-path="flowTracePath"
              />
              <trace-tree
                :trace-info="detail.traceInfo"
                :var-map="varMap"
                :function-name-map="functionNameMap"
                :model-type="detail.modelType"
                :input-params="detail.inputParams"
                :output-result="detail.outputResult"
                :rule-name="ruleMap[detail.ruleCode] || detail.ruleCode"
                :rule-version="detail.ruleVersion"
                :execute-time-ms="detail.executeTimeMs"
                :model-data="modelData"
                :definition-model="definitionModel"
                :show-flow-cards="true"
              />
            </template>
            <trace-tree
              v-else
              :trace-info="detail.traceInfo"
              :var-map="varMap"
              :function-name-map="functionNameMap"
              :model-type="detail.modelType"
              :input-params="detail.inputParams"
              :output-result="detail.outputResult"
              :rule-name="ruleMap[detail.ruleCode] || detail.ruleCode"
              :rule-version="detail.ruleVersion"
              :execute-time-ms="detail.executeTimeMs"
              :model-data="modelData"
              :definition-model="definitionModel"
            />
          </el-tab-pane>
          <el-tab-pane v-if="detail && detail.modelType === 'RULE_SET'" name="setTrace">
            <template #label><span><i class="el-icon-sort" /> 规则集追踪</span></template>
            <div v-if="ruleSetSteps.length" class="rs-trace">
              <div v-for="(step, idx) in ruleSetSteps" :key="idx" class="rs-step">
                <div v-if="idx > 0" class="rs-connector"><div class="rs-conn-line" /></div>
                <div class="rs-step-row">
                  <div class="rs-step-num" :class="step.success ? 'rs-step-num--ok' : 'rs-step-num--fail'">{{ idx + 1 }}</div>
                  <div class="rs-step-card" :class="step.success ? 'rs-step-card--ok' : 'rs-step-card--fail'">
                    <div class="rs-step-head">
                      <span class="rs-step-title">{{ ruleMap[step.ruleCode] || step.ruleCode }}</span>
                      <code class="rs-step-code">{{ step.ruleCode }}</code>
                      <span v-if="step.executeTimeMs != null" class="rs-step-cost" style="margin-left:auto">{{ step.executeTimeMs }} ms</span>
                      <el-tag :type="step.success ? 'success' : 'danger'" size="small" :style="step.executeTimeMs != null ? 'margin-left:8px' : 'margin-left:auto'">{{ step.success ? '成功' : '失败' }}</el-tag>
                    </div>
                    <div v-if="step.result !== undefined && step.result !== null" class="rs-step-body">
                      <pre class="log-pre" style="max-height:120px">{{ formatStepResult(step.result) }}</pre>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="rs-empty">无追踪步骤数据</div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>
<script>
import { listExecutionLogs, getExecutionLogDetail } from '@/api/log'
import { listRuleSets } from '@/api/ruleSet'
import { listVariables } from '@/api/variable'
import { getContent, listDefinitions as listRules } from '@/api/definition'
import { listProjects } from '@/api/project'
import { listAllFunctionsByProject } from '@/api/function'
import TraceTree from '@/components/common/TraceTree.vue'
import FlowTraceLogicFlow from '@/components/common/FlowTraceLogicFlow.vue'
import { deriveFlowTracePath } from '@/utils/flowTracePath'

export default {
  name: 'ExecutionLog',
  components: { TraceTree, FlowTraceLogicFlow },
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10, ruleCode: '', projectCode: '', source: '', businessId: '' },
      /** 时间范围，默认最近三个月 */
      timeRange: null,
      /** 日期快捷选项 */
      pickerOptions: {
        shortcuts: [
          { text: '最近一周', onClick: function(picker) { var end = new Date(); var start = new Date(); start.setTime(start.getTime() - 7 * 24 * 3600 * 1000); picker.$emit('pick', [start, end]) } },
          { text: '最近一个月', onClick: function(picker) { var end = new Date(); var start = new Date(); start.setMonth(start.getMonth() - 1); picker.$emit('pick', [start, end]) } },
          { text: '最近三个月', onClick: function(picker) { var end = new Date(); var start = new Date(); start.setMonth(start.getMonth() - 3); picker.$emit('pick', [start, end]) } },
          { text: '最近半年', onClick: function(picker) { var end = new Date(); var start = new Date(); start.setMonth(start.getMonth() - 6); picker.$emit('pick', [start, end]) } },
          { text: '最近一年', onClick: function(picker) { var end = new Date(); var start = new Date(); start.setFullYear(start.getFullYear() - 1); picker.$emit('pick', [start, end]) } }
        ]
      },
      detailVis: false,
      detail: null,
      detailTab: 'basic',
      varMap: {},
      ruleMap: {},
      ruleSetMap: {},
      projectMap: {},
      projectList: [],
      ruleList: [],
      ruleSetList: [],
      modelTypeMap: {
        'TABLE': '决策表',
        'TREE': '决策树',
        'FLOW': '决策流',
        'CROSS': '交叉表',
        'SCORE': '评分卡',
        'CROSS_ADV': '复杂交叉表',
        'SCORE_ADV': '复杂评分卡',
        'SCRIPT': '脚本',
        'RULE_SET': '规则集'
      },
      /** 当前查看日志对应的规则模型数据（nodes + edges，决策树/决策流） */
      modelData: null,
      /** 规则设计器完整 modelJson 解析结果（交叉表/复杂交叉表矩阵展示依赖） */
      definitionModel: null,
      /** 当前日志项目下函数编码→中文名，供追踪树展示调用函数 */
      functionNameMap: {}
    }
  },
  computed: {
    filteredRules: function() {
      if (!this.qp.projectCode) return this.ruleList
      var pc = this.qp.projectCode
      var pid = null
      for (var i = 0; i < this.projectList.length; i++) {
        if (this.projectList[i].projectCode === pc) {
          pid = this.projectList[i].id
          break
        }
      }
      if (!pid) return this.ruleList
      return this.ruleList.filter(function(r) { return r.projectId === pid })
    },
    filteredRuleSets: function() {
      if (!this.qp.projectCode) return this.ruleSetList
      var pc = this.qp.projectCode
      var pid = null
      for (var i = 0; i < this.projectList.length; i++) {
        if (this.projectList[i].projectCode === pc) {
          pid = this.projectList[i].id
          break
        }
      }
      if (!pid) return this.ruleSetList
      return this.ruleSetList.filter(function(s) { return s.projectId === pid })
    },
    ruleSetSteps: function() {
      if (!this.detail || this.detail.modelType !== 'RULE_SET') return []
      try {
        var output = JSON.parse(this.detail.outputResult)
        if (Array.isArray(output)) return output
        if (output && Array.isArray(output.steps)) return output.steps
      } catch (e) { /* ignore */ }
      return []
    },
    /** FLOW 日志的执行路径（推导失败返回 null，画布不高亮） */
    flowTracePath: function() {
      if (!this.detail || this.detail.modelType !== 'FLOW' || !this.detail.traceInfo) return null
      if (!this.definitionModel) return null
      try {
        return deriveFlowTracePath(this.definitionModel, this.detail.traceInfo)
      } catch (e) {
        return null
      }
    }
  },
  watch: {
    detailVis: function(val) {
      if (val) {
        this.detailTab = 'basic'
        this.loadVarMap()
        this.loadFunctionNameMap()
        this.loadModelJson()
      }
    },
    /** 切到追踪 Tab 后画布容器才可见，需再次 fitView */
    detailTab: function(v) {
      if (v === 'trace') this._relayoutFlowTrace()
    }
  },
  created: function() {
    this.initDefaultTimeRange()
    this.load()
    this.loadProjects()
    this.loadRules()
    this.loadRuleSets()
  },
  methods: {
    /**
     * 抽屉打开动画结束后再触发流程图 resize（避免初始仅能看见半幅）
     */
    onDetailDrawerOpened: function() {
      this._relayoutFlowTrace()
    },
    /**
     * 切换到「表达式追踪树」时再适应画布（非默认 Tab 时容器曾隐藏）
     */
    onDetailTabClick: function(tab) {
      if (!tab || tab.name !== 'trace') return
      this._relayoutFlowTrace()
    },
    /**
     * 通知决策流只读画布按当前容器尺寸重新 fitView
     */
    _relayoutFlowTrace: function() {
      var self = this
      this.$nextTick(function() {
        var c = self.$refs.flowTraceLf
        if (c && typeof c.requestRelayout === 'function') {
          c.requestRelayout()
        }
      })
    },
    async load() {
      this.loading = true
      try {
        var params = Object.assign({}, this.qp)
        if (this.timeRange && this.timeRange.length === 2) {
          params.startTime = this.timeRange[0]
          params.endTime = this.timeRange[1]
        }
        var r = await listExecutionLogs(params)
        this.list = r.data.records
        this.total = r.data.total
      } finally {
        this.loading = false
      }
    },
    /** 点击详情：调用详情接口获取完整数据（含 inputParams/outputResult/traceInfo） */
    async showDetail(row) {
      try {
        var r = await getExecutionLogDetail(row.id)
        this.detail = r.data
        this.detailVis = true
      } catch (e) {
        this.$message.error('获取日志详情失败')
      }
    },
    async loadProjects() {
      try {
        var r = await listProjects({ pageNum: 1, pageSize: 1000 })
        var list = r.data && r.data.records ? r.data.records : []
        this.projectList = list
        var map = {}
        for (var i = 0; i < list.length; i++) {
          if (list[i].projectCode && list[i].projectName) {
            map[list[i].projectCode] = list[i].projectName
          }
        }
        this.projectMap = map
      } catch (e) {
        console.warn('加载项目列表失败:', e)
      }
    },
    async loadRules() {
      try {
        var r = await listRules({ pageNum: 1, pageSize: 1000 })
        var list = r.data && r.data.records ? r.data.records : []
        this.ruleList = list
        var map = {}
        for (var i = 0; i < list.length; i++) {
          if (list[i].ruleCode && list[i].ruleName) {
            map[list[i].ruleCode] = list[i].ruleName
          }
        }
        this.ruleMap = map
      } catch (e) {
        console.warn('加载规则列表失败:', e)
      }
    },
    async loadRuleSets() {
      try {
        var r = await listRuleSets({ pageNum: 1, pageSize: 1000 })
        var list = r.data && r.data.records ? r.data.records : []
        this.ruleSetList = list
        var map = {}
        for (var i = 0; i < list.length; i++) {
          if (list[i].setCode && list[i].setName) {
            map[list[i].setCode] = list[i].setName
          }
        }
        this.ruleSetMap = map
      } catch (e) {
        console.warn('加载规则集列表失败:', e)
      }
    },
    async loadVarMap() {
      try {
        var r = await listVariables({ pageNum: 1, pageSize: 1000 })
        var vars = r.data && r.data.records ? r.data.records : []
        var map = {}
        for (var i = 0; i < vars.length; i++) {
          if (vars[i].varCode && vars[i].varLabel) {
            map[vars[i].varCode] = vars[i].varLabel
          }
        }
        this.varMap = map
      } catch (e) {
        console.warn('加载变量映射失败:', e)
      }
    },
    /**
     * 按当前日志所属项目加载启用函数列表，构建编码→中文名映射
     */
    async loadFunctionNameMap() {
      this.functionNameMap = {}
      if (!this.detail || !this.detail.projectCode) return
      var pid = null
      for (var i = 0; i < this.projectList.length; i++) {
        if (this.projectList[i].projectCode === this.detail.projectCode) {
          pid = this.projectList[i].id
          break
        }
      }
      if (!pid) return
      try {
        var r = await listAllFunctionsByProject(pid)
        var funcData = (r && r.data) ? r.data : r
        var list = Array.isArray(funcData) ? funcData : (funcData && Array.isArray(funcData.records) ? funcData.records : [])
        var map = {}
        for (var j = 0; j < list.length; j++) {
          var f = list[j]
          if (f && f.funcCode && f.funcName) map[f.funcCode] = f.funcName
        }
        this.functionNameMap = map
      } catch (e) {
        console.warn('加载函数中文名映射失败:', e)
      }
    },
    /**
     * 根据 ruleCode 加载规则 modelJson：决策树/流提取 nodes+edges；交叉表等保留完整模型供追踪矩阵高亮。
     */
    async loadModelJson() {
      this.modelData = null
      this.definitionModel = null
      if (!this.detail || !this.detail.ruleCode) return
      var def = null
      for (var i = 0; i < this.ruleList.length; i++) {
        if (this.ruleList[i].ruleCode === this.detail.ruleCode) { def = this.ruleList[i]; break }
      }
      if (!def || !def.id) return
      try {
        var r = await getContent(def.id)
        var content = r && r.data ? r.data : r
        if (content && content.modelJson) {
          var model = JSON.parse(content.modelJson)
          this.definitionModel = model
          if (model.nodes && model.edges) {
            this.modelData = { nodes: model.nodes, edges: model.edges }
          }
        }
      } catch (e) {
        console.warn('加载规则模型失败:', e)
      }
    },
    onSourceChange: function() {
      this.qp.projectCode = ''
      this.qp.ruleCode = ''
    },
    onProjectChange: function() {
      this.qp.ruleCode = ''
    },
    resetQuery: function() {
      this.qp.source = ''
      this.qp.projectCode = ''
      this.qp.ruleCode = ''
      this.qp.businessId = ''
      this.qp.pageNum = 1
      this.initDefaultTimeRange()
      this.load()
    },
    /** 初始化默认时间范围为最近三个月 */
    initDefaultTimeRange: function() {
      var end = new Date()
      var start = new Date()
      start.setMonth(start.getMonth() - 3)
      start.setHours(0, 0, 0, 0)
      end.setHours(23, 59, 59, 0)
      var pad = function(n) { return String(n).padStart(2, '0') }
      var fmt = function(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
      }
      this.timeRange = [fmt(start), fmt(end)]
    },
    fj: function(s) {
      try {
        return JSON.stringify(JSON.parse(s), null, 2)
      } catch (e) {
        return s || '(空)'
      }
    },
    /** 复制文本到剪贴板并给出成功/失败提示 */
    copyText: function(text, label) {
      var self = this
      var content = (text === undefined || text === null) ? '' : String(text)
      var done = function() { self.$message.success((label || '内容') + '已复制到剪贴板') }
      var fail = function() { self.$message.error('复制失败，请手动复制') }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(content).then(done).catch(function() {
          self._fallbackCopy(content) ? done() : fail()
        })
      } else {
        this._fallbackCopy(content) ? done() : fail()
      }
    },
    /** 兼容不支持 Clipboard API 环境的降级复制方案 */
    _fallbackCopy: function(content) {
      try {
        var ta = document.createElement('textarea')
        ta.value = content
        ta.style.position = 'fixed'
        ta.style.top = '-9999px'
        document.body.appendChild(ta)
        ta.focus()
        ta.select()
        var ok = document.execCommand('copy')
        document.body.removeChild(ta)
        return ok
      } catch (e) {
        return false
      }
    },
    formatTime: function(time) {
      if (!time) return '-'
      var d = new Date(time)
      if (isNaN(d.getTime())) return time
      var pad = function(n) { return String(n).padStart(2, '0') }
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
    },
    formatStepResult: function(result) {
      try {
        return JSON.stringify(result, null, 2)
      } catch (e) {
        return String(result)
      }
    }
  }
}
</script>
<style scoped>
.log-pre {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow: auto;
  max-height: 200px;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}
.copyable-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.copy-icon {
  cursor: pointer;
  color: #909399;
  font-size: 14px;
  transition: color 0.2s;
}
.copy-icon:hover {
  color: #409EFF;
}
.log-pre.error {
  background: #fff2f2;
  color: #F56C6C;
}
.trace-badge {
  margin-left: 4px;
}
:deep(.trace-badge .el-badge__content ){
  background-color: #1890ff;
}
/* ═══════ 规则集追踪 ═══════ */
.rs-trace { padding: 8px 0; }
.rs-step { }
.rs-connector { padding-left: 19px; }
.rs-conn-line { width: 2px; height: 16px; background: #e4e7ed; }
.rs-step-row { display: flex; align-items: flex-start; gap: 12px; }
.rs-step-num {
  width: 36px; height: 36px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; color: #fff;
  flex-shrink: 0; margin-top: 8px;
}
.rs-step-num--ok { background: #67C23A; }
.rs-step-num--fail { background: #F56C6C; }
.rs-step-card {
  flex: 1; border: 1px solid #e4e7ed; border-radius: 8px;
  background: #fff; overflow: hidden;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  border-left: 3px solid #e4e7ed;
}
.rs-step-card--ok { border-left-color: #67C23A; }
.rs-step-card--fail { border-left-color: #F56C6C; }
.rs-step-head {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px; border-bottom: 1px solid #f0f0f0;
}
.rs-step-title { font-size: 14px; font-weight: 600; color: #303133; }
.rs-step-code { font-size: 12px; color: #909399; background: #f5f7fa; padding: 1px 6px; border-radius: 3px; }
.rs-step-cost {
  font-size: 12px; color: #606266; background: #f4f4f5;
  padding: 1px 10px; border-radius: 10px;
  font-family: Consolas, Monaco, monospace;
}
.rs-step-body { padding: 10px 16px; }
.rs-empty { text-align: center; padding: 36px 0; color: #C0C4CC; font-size: 13px; }
/* ═══════ 详情汇总条 ═══════ */
.log-summary-bar {
  display: flex; align-items: center; flex-wrap: wrap;
  gap: 8px 22px;
  background: #f8f9fb; border: 1px solid #ebeef5; border-radius: 6px;
  padding: 10px 16px; margin-bottom: 14px;
}
.log-summary-cost { font-size: 15px; font-weight: 700; color: #303133; }
.log-summary-cost small { font-size: 12px; color: #909399; font-weight: 400; }
.log-summary-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #606266; }
.log-summary-item .lbl { color: #909399; }
.log-summary-item .val { color: #303133; font-weight: 600; }
.log-summary-item .val.mono { font-family: Consolas, Monaco, monospace; font-weight: 400; }

/* 表格操作项：蓝色可点击链接 + 手型光标 */
.tableOperateBtn {
  color: #326EFF;
  cursor: pointer;
}
.tableOperateBtn:hover {
  color: #5a8bff;
}
</style>
