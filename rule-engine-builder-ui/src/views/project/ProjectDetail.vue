<template>
  <div class="uiue-list-page">
    <div style="margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;">
      <h2 style="margin:0;">{{ project ? project.projectName : '加载中...' }}</h2>
      <el-button size="small" icon="el-icon-back" @click="$router.back()">返回</el-button>
    </div>
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="关键字"><el-input v-model="qp.keyword" clearable @keyup.enter.native="handleQuery" /></el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="qp.modelType" clearable>
            <el-option label="决策表" value="TABLE" /><el-option label="决策树" value="TREE" /><el-option label="决策流" value="FLOW" /><el-option label="交叉表" value="CROSS" /><el-option label="评分卡" value="SCORE" /><el-option label="复杂交叉表" value="CROSS_ADV" /><el-option label="复杂评分卡" value="SCORE_ADV" /><el-option label="QL脚本" value="SCRIPT" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="qp.keyword='';qp.modelType='';handleQuery()">重置</el-button></el-form-item>
      </el-form>
    </div>
    <div class="uiue-btn-bar"><div class="btn-right"><el-button type="primary" size="small" @click="openCreateDlg">新建规则</el-button></div></div>
    <el-table v-loading="loading" :data="expandedList" border size="small" style="width: 100%;" :span-method="objectSpanMethod">
      <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip sortable />
      <el-table-column prop="ruleCode" label="规则编码" min-width="150" show-overflow-tooltip sortable />
      <el-table-column label="省份" min-width="120" show-overflow-tooltip sortable>
        <template slot-scope="{ row }">{{ row._province }}</template>
      </el-table-column>
      <el-table-column prop="modelType" label="模型类型" min-width="90" align="center" show-overflow-tooltip sortable>
        <template slot-scope="{ row }">
          <el-tag size="mini">{{ mtl(row.modelType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="70" align="center" sortable>
        <template slot-scope="{ row }">
          <el-tag :type="{ 0: 'info', 1: 'success', 2: 'warning' }[row.status]" size="mini">
            {{ ['草稿', '已发布', '已下线'][row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip sortable />
      <el-table-column label="创建时间" min-width="160" align="center">
        <template slot-scope="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="250" align="center" class-name="rule-ops-col">
        <template slot-scope="{ row }">
          <div style="white-space:nowrap">
            <el-button type="text" size="small" @click="go(row)">设计</el-button>
            <el-button type="text" size="small" @click="openEditDlg(row)">修改</el-button>
            <el-button type="text" size="small" @click="openCopyDlg(row)">复制到省份</el-button>
            <el-button type="text" size="small" @click="pub(row)">{{ row.status === 1 ? '重新发布' : '发布' }}</el-button>
            <el-button v-if="row.status === 1" type="text" size="small" @click="unpub(row)">下线</el-button>
            <el-button type="text" size="small" style="color:#F56C6C" @click="del(row)">删除</el-button>
          </div>
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
    <el-dialog title="新建规则" :visible.sync="dlgVis" width="500px">
      <el-form ref="f" :model="fm" :rules="{ruleCode:[{required:true,message:'必填',trigger:'blur'}],ruleName:[{required:true,message:'必填',trigger:'blur'}],modelType:[{required:true,message:'必选',trigger:'change'}]}" label-width="100px" size="small">
        <el-form-item label="规则编码" prop="ruleCode"><el-input v-model="fm.ruleCode" /></el-form-item>
        <el-form-item label="规则名称" prop="ruleName"><el-input v-model="fm.ruleName" /></el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="fm.modelType" style="width:100%"><el-option label="决策表" value="TABLE" /><el-option label="决策树" value="TREE" /><el-option label="决策流" value="FLOW" /><el-option label="交叉表" value="CROSS" /><el-option label="评分卡" value="SCORE" /><el-option label="复杂交叉表" value="CROSS_ADV" /><el-option label="复杂评分卡" value="SCORE_ADV" /><el-option label="QL脚本" value="SCRIPT" /></el-select>
        </el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="fm.initialScopeCompId" filterable placeholder="选择省份 / 组织" style="width:100%">
            <el-option v-for="o in compScopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="高精度计算">
          <el-switch v-model="fm.preciseMode" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:#909399;font-size:12px;">开启后数值运算使用 BigDecimal，消除浮点误差</span>
        </el-form-item>
        <el-form-item label="执行超时(ms)">
          <el-input-number v-model="fm.timeoutMillis" :min="0" :step="100" />
          <span style="margin-left:8px;color:#909399;font-size:12px;">单次执行超时毫秒数，0 表示不限制</span>
        </el-form-item>
        <el-form-item label="日志上报">
          <el-switch v-model="fm.reportLog" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:#909399;font-size:12px;">开启后执行时写入执行日志表</span>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="fm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button size="small" @click="dlgVis=false">取消</el-button><el-button size="small" type="primary" @click="submit">确定</el-button></div>
    </el-dialog>
    <publish-scope-dialog
      :visible.sync="publishDlgVis"
      :definition-id="publishDefinitionId"
      :comp-scope-options="compScopeOptions"
      @published="load"
    />

    <!-- 修改省份/说明弹窗 -->
    <el-dialog title="修改规则" :visible.sync="editDlgVis" width="500px">
      <el-form label-width="100px" size="small">
        <el-form-item label="省份">
          <el-select v-model="editForm.compId" filterable style="width:100%">
            <el-option v-for="o in compScopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="editDlgVis = false">取消</el-button>
        <el-button size="small" type="primary" :loading="editLoading" @click="submitEdit">确定</el-button>
      </div>
    </el-dialog>

    <!-- 复制到省份弹窗 -->
    <el-dialog title="复制规则到其他省份" :visible.sync="copyDlgVis" width="560px">
      <p style="margin:0 0 12px;color:#606266;font-size:13px;">选择要复制到的目标省份，规则内容将被复制到选中的省份作用域下。</p>
      <el-form label-width="100px" size="small">
        <el-form-item label="当前规则">
          <span style="font-weight:600;">{{ copyRuleName }}</span>
        </el-form-item>
        <el-form-item label="添加省份">
          <div style="display:flex;gap:8px;">
            <el-select v-model="copyPickScope" filterable placeholder="选择省份 / 组织" style="flex:1">
              <el-option v-for="o in copyPickableOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-button type="primary" :disabled="!copyPickScope" @click="addCopyScope">添加</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="copyTargetScopes.length" label="目标省份">
          <el-table :data="copyTargetScopeRows" border size="mini" style="width:100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="label" label="省份 / 组织" />
            <el-table-column label="操作" width="80" align="center">
              <template slot-scope="{ $index }">
                <el-button type="text" size="mini" style="color:#F56C6C" @click="removeCopyScope($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="copyDlgVis = false">取消</el-button>
        <el-button size="small" type="primary" :loading="copyLoading" @click="submitCopy">确定复制</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import {mapState} from 'vuex'
import {
  copyDefinitionToScope,
  createDefinition,
  deleteDefinitionScope,
  listDefinitions,
  unpublishRule,
  updateContentMeta
} from '@/api/definition'
import {getProject} from '@/api/project'
import PublishScopeDialog from '@/components/common/PublishScopeDialog.vue'
import {COMP_SCOPE_OPTIONS} from '@/constants/compScopeOptions'
import {DICT_TYPE_COMP_SCOPE} from '@/constants/dictTypes'

export default {
  name: 'ProjectDetail',
  components: { PublishScopeDialog },
  data() {
    return {
      publishDlgVis: false,
      publishDefinitionId: null,
      editDlgVis: false,
      editLoading: false,
      editForm: { definitionId: null, scopeCompId: null, compId: '', description: '' },
      copyDlgVis: false,
      copyDefinitionId: null,
      copyRuleName: '',
      copyTargetScopes: [],
      copyPickScope: null,
      copyLoading: false,
      pid: null,
      project: null,
      loading: false,
      list: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10, keyword: '', modelType: '' },
      dlgVis: false,
      fm: { ruleCode: '', ruleName: '', modelType: '', description: '', initialScopeCompId: '0', preciseMode: 0, timeoutMillis: 0, reportLog: 1 }
    }
  },
  computed: {
    ...mapState('ruleDict', {
      _dictCompScope(state) {
        const arr = state.byType[DICT_TYPE_COMP_SCOPE]
        return Array.isArray(arr) ? arr : []
      }
    }),
    /** 与 design 页作用域下拉同源：字典缓存优先，否则静态兗底 */
    compScopeOptions() {
      if (this._dictCompScope.length > 0) return this._dictCompScope
      return COMP_SCOPE_OPTIONS
    },
    /** 复制弹窗中尚未选择的省份选项 */
    copyPickableOptions() {
      const picked = new Set(this.copyTargetScopes)
      return this.compScopeOptions.filter(o => !picked.has(o.value))
    },
    /** 已选省份的行数据（带 label） */
    copyTargetScopeRows() {
      const map = new Map(this.compScopeOptions.map(o => [o.value, o.label]))
      return this.copyTargetScopes.map(v => ({ value: v, label: map.get(v) || v }))
    },
    /**
     * 将规则列表按省份展开为一对多行：每条规则 × N个省份 = N 行。
     */
    expandedList() {
      const scopeMap = new Map(this.compScopeOptions.map(o => [o.value, o.label]))
      const result = []
      for (const row of this.list) {
        const contents = row.contentSummaries || []
        if (contents.length === 0) {
          result.push({ ...row, _province: '-', _scopeCompId: '0', _contentId: null, _rowspan: 1, modelType: null, status: 0, currentVersion: 0, publishedVersion: null, compId: '0', description: row.description })
          continue
        }
        for (let i = 0; i < contents.length; i++) {
          const c = contents[i]
          result.push({
            ...row,
            _province: scopeMap.get(c.compId || c.scopeCompId) || c.compId || c.scopeCompId || '-',
            _scopeCompId: c.scopeCompId,
            _contentId: c.id,
            _rowspan: i === 0 ? contents.length : 0,
            modelType: c.modelType,
            status: c.status != null ? c.status : 0,
            currentVersion: c.currentVersion,
            publishedVersion: c.publishedVersion,
            compId: c.compId,
            description: c.description || row.description
          })
        }
      }
      return result
    }
  },
  created() {
    this.pid = this.$route.params.id
    getProject(this.pid).then(r => { this.project = r.data })
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const r = await listDefinitions({ ...this.qp, projectId: this.pid })
        this.list = r.data.records
        this.total = r.data.total
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.load()
    },
    /**
     * el-table span-method：只有规则名称(0)和规则编码(1)合并，其余列每行独立。
     */
    objectSpanMethod({ row, columnIndex }) {
      // 规则名称(0)和规则编码(1)按记录合并
      if (columnIndex === 0 || columnIndex === 1) {
        if (row._rowspan === 0) {
          return { rowspan: 0, colspan: 0 }
        }
        return { rowspan: row._rowspan, colspan: 1 }
      }
      // 其余所有列每行独立显示
      return { rowspan: 1, colspan: 1 }
    },
    mtl(t) {
      return { TABLE: '决策表', TREE: '决策树', FLOW: '决策流', CROSS: '交叉表', SCORE: '评分卡', CROSS_ADV: '复杂交叉表', SCORE_ADV: '复杂评分卡', SCRIPT: 'QL脚本' }[t] || t
    },
    formatDateTime(time) {
      if (time == null || time === '') return '-'
      let d
      if (Array.isArray(time) && time.length >= 6) {
        d = new Date(time[0], time[1] - 1, time[2], time[3] || 0, time[4] || 0, time[5] || 0)
      } else {
        d = new Date(time)
      }
      if (isNaN(d.getTime())) return typeof time === 'string' ? time : '-'
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    /**
     * modelType 转设计器路由 path 段
     */
    modelTypeToPath(modelType) {
      const m = { TABLE: 'table', TREE: 'tree', FLOW: 'flow', CROSS: 'cross', SCORE: 'score', CROSS_ADV: 'cross-adv', SCORE_ADV: 'score-adv', SCRIPT: 'script' }
      return m[modelType] || 'table'
    },
    /**
     * 从列表行解析规则定义主键（兼容接口返回 definitionId）
     */
    definitionIdFromRow(row) {
      if (!row) return null
      return row.id != null && row.id !== '' ? row.id : row.definitionId
    },
    /**
     * 在 Layout 右侧抽屉中打开设计器（92% 宽），不离开当前页。
     */
    go(r) {
      this.$store.commit('designerDrawer/OPEN', {
        segment: this.modelTypeToPath(r.modelType),
        definitionId: this.definitionIdFromRow(r),
        query: { scopeCompId: r._scopeCompId }
      })
    },
    /**
     * 打开新建弹窗并重置表单（含作用域默认通用）
     */
    openCreateDlg() {
      this.fm = { ruleCode: '', ruleName: '', modelType: '', description: '', initialScopeCompId: '0', preciseMode: 0, timeoutMillis: 0, reportLog: 1 }
      this.dlgVis = true
      this.$nextTick(() => {
        if (this.$refs.f) this.$refs.f.clearValidate()
      })
    },
    /**
     * 打开发布作用域选择弹窗。
     */
    pub(r) {
      this.publishDefinitionId = this.definitionIdFromRow(r)
      this.publishDlgVis = true
    },
    async unpub(r) {
      await this.$confirm('确定下线?')
      await unpublishRule(this.definitionIdFromRow(r), r._scopeCompId)
      this.$message.success('下线成功')
      this.load()
    },
    async del(r) {
      if (r.status === 1) {
        this.$message.warning('已发布状态的规则不能删除，请先下线')
        return
      }
      await this.$confirm('确定删除?')
      await deleteDefinitionScope(this.definitionIdFromRow(r), r._scopeCompId)
      this.$message.success('删除成功')
      this.load()
    },
    openEditDlg(row) {
      this.editForm = {
        definitionId: this.definitionIdFromRow(row),
        scopeCompId: row._scopeCompId,
        compId: row.compId || row._scopeCompId || '',
        description: row.description || ''
      }
      this.editDlgVis = true
    },
    async submitEdit() {
      this.editLoading = true
      try {
        await updateContentMeta(this.editForm)
        this.$message.success('修改成功')
        this.editDlgVis = false
        this.load()
      } catch (e) { /* interceptor handles */ } finally {
        this.editLoading = false
      }
    },
    openCopyDlg(row) {
      this.copyDefinitionId = this.definitionIdFromRow(row)
      this.copyRuleName = row.ruleName || row.ruleCode || ''
      this.copyTargetScopes = []
      this.copyPickScope = null
      this.copyDlgVis = true
    },
    addCopyScope() {
      if (!this.copyPickScope) return
      if (!this.copyTargetScopes.includes(this.copyPickScope)) {
        this.copyTargetScopes.push(this.copyPickScope)
      }
      this.copyPickScope = null
    },
    removeCopyScope(index) {
      this.copyTargetScopes.splice(index, 1)
    },
    async submitCopy() {
      if (!this.copyTargetScopes.length) {
        this.$message.warning('请至少选择一个目标省份')
        return
      }
      this.copyLoading = true
      try {
        await copyDefinitionToScope({
          definitionId: this.copyDefinitionId,
          targetScopeCompIds: this.copyTargetScopes
        })
        this.$message.success('复制成功')
        this.copyDlgVis = false
        this.load()
      } catch (e) { /* interceptor handles */ } finally {
        this.copyLoading = false
      }
    },
    /**
     * 创建规则后在抽屉中打开设计器，作用域来自所选 scopeCompId
     */
    submit() {
      this.$refs.f.validate(async valid => {
        if (!valid) return
        const scopeQ = String(this.fm.initialScopeCompId != null && this.fm.initialScopeCompId !== '' ? this.fm.initialScopeCompId : '0')
        const res = await createDefinition({
          ...this.fm,
          projectId: this.pid,
          initialScopeCompId: scopeQ
        })
        const def = res && res.data !== undefined ? res.data : res
        this.$message.success('创建成功')
        this.dlgVis = false
        this.load()
        const newDefId = def && (def.id != null && def.id !== '' ? def.id : def.definitionId)
        if (def && newDefId && def.modelType) {
          this.$store.commit('designerDrawer/OPEN', {
            segment: this.modelTypeToPath(def.modelType),
            definitionId: newDefId,
            query: { scopeCompId: scopeQ }
          })
        }
      })
    }
  }
}
</script>

<style scoped>
</style>

<style>
/* 操作列按钮紧凑间距 */
.rule-ops-col .el-button + .el-button {
  margin-left: 2px;
}
.rule-ops-col .el-button--text {
  padding-left: 4px;
  padding-right: 4px;
}
</style>
