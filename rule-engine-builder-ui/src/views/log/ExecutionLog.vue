<template>
  <div class="uiue-list-page">
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
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
        <el-form-item label="规则">
          <el-select v-model="qp.ruleCode" clearable filterable placeholder="全部规则">
            <el-option v-for="r in filteredRules" :key="r.ruleCode" :label="r.ruleName" :value="r.ruleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
            start-placeholder="开始时间" end-placeholder="结束时间" value-format="yyyy-MM-dd HH:mm:ss"
            :default-time="['00:00:00','23:59:59']" :picker-options="pickerOptions" size="small" style="width:360px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="qp.pageNum=1;load()">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <el-table :data="list" border size="small" v-loading="loading" style="width: 100%;">
      <el-table-column prop="projectCode" label="项目" min-width="120" show-overflow-tooltip>
        <template slot-scope="{row}">{{ projectMap[row.projectCode] || row.projectCode || '-' }}</template>
      </el-table-column>
      <el-table-column prop="ruleCode" label="规则编码" min-width="140" show-overflow-tooltip>
        <template slot-scope="{row}">{{ ruleMap[row.ruleCode] || row.ruleCode }}</template>
      </el-table-column>
      <el-table-column prop="modelType" label="模型类型" min-width="80" align="center">
        <template slot-scope="{row}">{{ modelTypeMap[row.modelType] || row.modelType }}</template>
      </el-table-column>
      <el-table-column prop="source" label="来源" min-width="70" align="center">
        <template slot-scope="{row}"><el-tag :type="row.source==='SERVER'?'':'success'" size="mini">{{row.source==='SERVER'?'服务端':'客户端'}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="success" label="结果" min-width="65" align="center">
        <template slot-scope="{row}"><el-tag :type="row.success===1?'success':'danger'" size="mini">{{row.success===1?'成功':'失败'}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="executeTimeMs" label="耗时(ms)" min-width="80" align="center" />
      <el-table-column prop="clientAppName" label="客户端" min-width="110" show-overflow-tooltip />
      <el-table-column prop="createTime" label="执行时间" min-width="150">
        <template slot-scope="{row}">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="追踪" min-width="70" align="center">
        <template slot-scope="{row}">
          <el-tag v-if="row.traceInfo" type="info" size="mini" effect="plain"><i class="el-icon-view"></i> 有</el-tag>
          <span v-else style="color:#bfbfbf">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="70" align="center">
        <template slot-scope="{row}"><el-button type="text" size="small" @click="detail=row;detailVis=true">详情</el-button></template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="p=>{qp.pageNum=p;load()}" @size-change="s=>{qp.pageSize=s;qp.pageNum=1;load()}" />
    <el-drawer title="日志详情" :visible.sync="detailVis" size="84%">
      <div style="padding:16px" v-if="detail">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="基本信息" name="basic">
            <div class="uiue-card">
              <div class="uiue-card-title">输入参数</div>
              <pre class="log-pre">{{fj(detail.inputParams)}}</pre>
            </div>
            <div class="uiue-card" style="margin-top:12px">
              <div class="uiue-card-title">输出结果</div>
              <pre class="log-pre">{{fj(detail.outputResult)}}</pre>
            </div>
            <div class="uiue-card" style="margin-top:12px" v-if="detail.errorMessage">
              <div class="uiue-card-title" style="color:#F56C6C">错误信息</div>
              <pre class="log-pre error">{{detail.errorMessage}}</pre>
            </div>
          </el-tab-pane>
          <el-tab-pane name="trace" :disabled="!detail.traceInfo">
            <span slot="label">
              <i class="el-icon-connection"></i> 表达式追踪树
              <el-badge v-if="detail.traceInfo" is-dot class="trace-badge" />
            </span>
            <trace-tree :trace-info="detail.traceInfo" :var-map="varMap" :function-name-map="functionNameMap" :model-type="detail.modelType" :input-params="detail.inputParams" :output-result="detail.outputResult" :rule-name="ruleMap[detail.ruleCode] || detail.ruleCode" :rule-version="detail.ruleVersion" :execute-time-ms="detail.executeTimeMs" :model-data="modelData" :definition-model="definitionModel" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>
<script>
import request from '@/api/request'
import { listVariables } from '@/api/variable'
import { listDefinitions as listRules, getContent } from '@/api/definition'
import { listProjects } from '@/api/project'
import { listAllFunctionsByProject } from '@/api/function'
import TraceTree from '@/components/common/TraceTree.vue'

export default {
  name: 'ExecutionLog',
  components: { TraceTree },
  data () {
    return {
      loading: false,
      list: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10, ruleCode: '', projectCode: '', source: '' },
      /** 时间范围，默认最近三个月 */
      timeRange: null,
      /** 日期快捷选项 */
      pickerOptions: {
        shortcuts: [
          { text: '最近一周', onClick: function (picker) { var end = new Date(); var start = new Date(); start.setTime(start.getTime() - 7 * 24 * 3600 * 1000); picker.$emit('pick', [start, end]) } },
          { text: '最近一个月', onClick: function (picker) { var end = new Date(); var start = new Date(); start.setMonth(start.getMonth() - 1); picker.$emit('pick', [start, end]) } },
          { text: '最近三个月', onClick: function (picker) { var end = new Date(); var start = new Date(); start.setMonth(start.getMonth() - 3); picker.$emit('pick', [start, end]) } },
          { text: '最近半年', onClick: function (picker) { var end = new Date(); var start = new Date(); start.setMonth(start.getMonth() - 6); picker.$emit('pick', [start, end]) } },
          { text: '最近一年', onClick: function (picker) { var end = new Date(); var start = new Date(); start.setFullYear(start.getFullYear() - 1); picker.$emit('pick', [start, end]) } }
        ]
      },
      detailVis: false,
      detail: null,
      detailTab: 'basic',
      varMap: {},
      ruleMap: {},
      projectMap: {},
      projectList: [],
      ruleList: [],
      modelTypeMap: {
        'TABLE': '决策表',
        'TREE': '决策树',
        'FLOW': '决策流',
        'CROSS': '交叉表',
        'SCORE': '评分卡',
        'CROSS_ADV': '复杂交叉表',
        'SCORE_ADV': '复杂评分卡',
        'SCRIPT': '脚本'
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
    filteredRules: function () {
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
      return this.ruleList.filter(function (r) { return r.projectId === pid })
    }
  },
  watch: {
    detailVis: function (val) {
      if (val) {
        this.detailTab = 'basic'
        this.loadVarMap()
        this.loadFunctionNameMap()
        this.loadModelJson()
      }
    }
  },
  created: function () {
    this.initDefaultTimeRange()
    this.load()
    this.loadProjects()
    this.loadRules()
  },
  methods: {
    async load () {
      this.loading = true
      try {
        var params = Object.assign({}, this.qp)
        if (this.timeRange && this.timeRange.length === 2) {
          params.startTime = this.timeRange[0]
          params.endTime = this.timeRange[1]
        }
        var r = await request({ url: '/rule/log/list', method: 'get', params: params })
        this.list = r.data.records
        this.total = r.data.total
      } finally {
        this.loading = false
      }
    },
    async loadProjects () {
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
    async loadRules () {
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
    async loadVarMap () {
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
    async loadFunctionNameMap () {
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
    async loadModelJson () {
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
    onSourceChange: function () {
      this.qp.projectCode = ''
      this.qp.ruleCode = ''
    },
    onProjectChange: function () {
      this.qp.ruleCode = ''
    },
    resetQuery: function () {
      this.qp.source = ''
      this.qp.projectCode = ''
      this.qp.ruleCode = ''
      this.qp.pageNum = 1
      this.initDefaultTimeRange()
      this.load()
    },
    /** 初始化默认时间范围为最近三个月 */
    initDefaultTimeRange: function () {
      var end = new Date()
      var start = new Date()
      start.setMonth(start.getMonth() - 3)
      start.setHours(0, 0, 0, 0)
      end.setHours(23, 59, 59, 0)
      var pad = function (n) { return String(n).padStart(2, '0') }
      var fmt = function (d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
      }
      this.timeRange = [fmt(start), fmt(end)]
    },
    fj: function (s) {
      try {
        return JSON.stringify(JSON.parse(s), null, 2)
      } catch (e) {
        return s || '(空)'
      }
    },
    formatTime: function (time) {
      if (!time) return '-'
      var d = new Date(time)
      if (isNaN(d.getTime())) return time
      var pad = function (n) { return String(n).padStart(2, '0') }
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
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
.log-pre.error {
  background: #fff2f2;
  color: #F56C6C;
}
.trace-badge {
  margin-left: 4px;
}
::v-deep .trace-badge .el-badge__content {
  background-color: #1890ff;
}
</style>
