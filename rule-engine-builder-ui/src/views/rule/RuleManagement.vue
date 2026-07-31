<template>
  <div class="uiue-list-page rule-mgmt-page">
    <div ref="conditionRef" class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="筛选项目">
          <el-select
            v-model="selectedProjectId"
            filterable
            clearable
            placeholder="请选择项目"
            style="width: 240px;"
            @change="onProjectChange"
          >
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="projectOptionLabel(p)"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <template v-if="selectedProjectId">
          <el-form-item label="关键字">
            <el-input v-model="listQuery.keyword" clearable placeholder="规则编码或名称" style="width: 200px;" @keyup.enter.native="handleQuery" />
          </el-form-item>
          <el-form-item label="模型类型">
            <el-select v-model="listQuery.modelType" clearable placeholder="全部" style="width: 160px;">
              <el-option label="决策表" value="TABLE" />
              <el-option label="决策树" value="TREE" />
              <el-option label="决策流" value="FLOW" />
              <el-option label="交叉表" value="CROSS" />
              <el-option label="评分卡" value="SCORE" />
              <el-option label="复杂交叉表" value="CROSS_ADV" />
              <el-option label="复杂评分卡" value="SCORE_ADV" />
              <el-option label="QL脚本" value="SCRIPT" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleQuery">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </el-form-item>
        </template>
      </el-form>
    </div>
    <div v-if="!selectedProjectId" class="rule-mgmt-empty">
      <el-empty :image-size="200">
        <template #image>
          <img src="@/assets/uiueImages/uiue_emptyIcon.png" alt="请先选择项目，下方将展示该项目下的规则">
        </template>
        <template #description>
          <span style="color: #595959;">请先选择项目，下方将展示该项目下的规则</span>
        </template>
      </el-empty>
    </div>
    <template v-else>
      <div ref="operateBtnRef" class="uiue-btn-bar">
        <div class="btn-right">
          <el-button type="success" size="small" @click="openCreateDlg">新建规则</el-button>
        </div>
      </div>
      <el-table v-loading="loading" :height="tableHeight" :max-height="tableHeight" :data="expandedList" border size="small" row-class-name="uiueTable" header-row-class-name="uiueTableHeader" :span-method="objectSpanMethod">
        <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip sortable>
          <template slot-scope="{ row }">
            {{ row.ruleName }}
            <el-tag v-if="row.preciseMode === 1" size="mini" type="warning" style="margin-left:4px;">高精度</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ruleCode" label="规则编码" min-width="150" show-overflow-tooltip sortable />
        <el-table-column label="省份" min-width="120" show-overflow-tooltip sortable>
          <template slot-scope="{ row }">{{ row._province }}</template>
        </el-table-column>
        <el-table-column prop="modelType" label="模型类型" min-width="140" align="center" show-overflow-tooltip sortable>
          <template slot-scope="{ row }">
            <el-tag size="mini">{{ mtl(row.modelType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="120" align="center" show-overflow-tooltip sortable>
          <template slot-scope="{ row }">
            <el-tag :type="{ 0: 'info', 1: 'success', 2: 'warning' }[row.status]" size="mini">
              {{ ['草稿', '已发布', '已下线'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip sortable />
        <el-table-column label="创建时间" min-width="160" align="center" sortable show-overflow-tooltip>
          <template slot-scope="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="250" align="center" class-name="rule-ops-col">
          <template slot-scope="{ row }">
            <div style="white-space:nowrap">
              <el-button type="text" size="small" @click="goDesigner(row)">设计</el-button>
              <el-button type="text" size="small" @click="openEditDlg(row)">修改</el-button>
              <el-button type="text" size="small" @click="openCopyDlg(row)">复制到省份</el-button>
              <el-button type="text" size="small" @click="pub(row)">{{ row.status === 1 ? '重新发布' : '发布' }}</el-button>
              <el-button v-if="row.status === 1" type="text" size="small" @click="unpub(row)">下线</el-button>
              <el-button type="text" size="small" style="color:#F56C6C" @click="del(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <img class="empty" src="@/assets/uiueImages/uiue_emptyIcon.png" alt="">
        </template>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="listQuery.pageNum" :limit.sync="listQuery.pageSize" @pagination="loadRules" />
    </template>

    <el-dialog
      title="新建规则"
      :visible.sync="dlgVis"
      width="510px"
      custom-class="middleDialog"
    >
      <el-form
        ref="createForm"
        :model="fm"
        :rules="createRules"
        label-width="100px"
        size="small"
      >
        <el-form-item label="规则编码" prop="ruleCode"><el-input v-model="fm.ruleCode" /></el-form-item>
        <el-form-item label="规则名称" prop="ruleName"><el-input v-model="fm.ruleName" /></el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="fm.modelType" style="width:100%">
            <el-option label="决策表" value="TABLE" />
            <el-option label="决策树" value="TREE" />
            <el-option label="决策流" value="FLOW" />
            <el-option label="交叉表" value="CROSS" />
            <el-option label="评分卡" value="SCORE" />
            <el-option label="复杂交叉表" value="CROSS_ADV" />
            <el-option label="复杂评分卡" value="SCORE_ADV" />
            <el-option label="QL脚本" value="SCRIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="高精度计算">
          <el-switch v-model="fm.preciseMode" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:#909399;font-size:12px;">开启后数值运算使用 BigDecimal，消除浮点误差</span>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="fm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="dlgVis = false">取消</el-button>
        <el-button type="success" @click="submitCreate">确定</el-button>
      </div>
    </el-dialog>

    <publish-scope-dialog
      :visible.sync="publishDlgVis"
      :definition-id="publishDefinitionId"
      :comp-scope-options="compScopeOptions"
      @published="loadRules"
    />

    <!-- 修改省份/说明弹窗 -->
    <el-dialog title="修改规则" :visible.sync="editDlgVis" width="500px">
      <el-form label-width="100px" size="small">
        <el-form-item label="省份">
          <el-select v-model="editForm.compId" filterable style="width:100%">
            <el-option
              v-for="o in compScopeOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="高精度计算">
          <el-switch v-model="editForm.preciseMode" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:#909399;font-size:12px;">开启后数值运算使用 BigDecimal，消除浮点误差</span>
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
            <el-select
              v-model="copyPickScope"
              filterable
              placeholder="选择省份 / 组织"
              style="flex:1"
            >
              <el-option
                v-for="o in copyPickableOptions"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
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
import {asyncPageHeightNew} from '@/mixins/asyncPageHeightNew'
import {
  copyDefinitionToScope,
  createDefinition,
  deleteDefinitionScope,
  listDefinitions,
  unpublishRule,
  updateContentMeta,
  updateDefinition
} from '@/api/definition'
import {listProjects} from '@/api/project'
import PublishScopeDialog from '@/components/common/PublishScopeDialog.vue'
import {COMP_SCOPE_OPTIONS} from '@/constants/compScopeOptions'
import {DICT_TYPE_COMP_SCOPE} from '@/constants/dictTypes'
import Pagination from '@/components/Pagination/index.vue'

export default {
  name: 'RuleManagement',
  components: { Pagination, PublishScopeDialog },
  mixins: [asyncPageHeightNew],
  data() {
    return {
      publishDlgVis: false,
      publishDefinitionId: null,
      copyDlgVis: false,
      copyDefinitionId: null,
      copyRuleName: '',
      copyTargetScopes: [],
      copyPickScope: null,
      copyLoading: false,
      editDlgVis: false,
      editLoading: false,
      editForm: { definitionId: null, scopeCompId: null, compId: '', description: '', preciseMode: 0, _origPreciseMode: 0, _published: false },
      projectOptions: [],
      selectedProjectId: null,
      loading: false,
      list: [],
      total: 0,
      listQuery: { pageNum: 1, pageSize: 10, keyword: '', modelType: '' },
      dlgVis: false,
      fm: { ruleCode: '', ruleName: '', modelType: '', description: '', preciseMode: 0 },
      createRules: {
        ruleCode: [{ required: true, message: '必填', trigger: 'blur' }],
        ruleName: [{ required: true, message: '必填', trigger: 'blur' }],
        modelType: [{ required: true, message: '必选', trigger: 'change' }]
      }
    }
  },
  computed: {
    ...mapState('ruleDict', {
      _dictCompScope(state) {
        const arr = state.byType[DICT_TYPE_COMP_SCOPE]
        return Array.isArray(arr) ? arr : []
      }
    }),
    /**
     * 新建规则时作用域下拉：与项目详情 / 设计器同源。
     */
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
     * 第一行 _rowspan = N（合并），其余行 _rowspan = 0（隐藏合并列）。
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
    this.bootstrap()
  },
  methods: {
    /**
     * 加载项目下拉并尝试用 URL ?projectId= 恢复选中项。
     */
    async bootstrap() {
      await this.loadProjectOptions()
      const q = this.$route.query.projectId
      if (q != null && q !== '') {
        const idStr = String(q)
        const found = this.projectOptions.find(p => String(p.id) === idStr)
        if (found) {
          this.selectedProjectId = found.id
          this.loadRules()
        }
      }
    },
    /**
     * 拉取项目列表供筛选（单页上限 500，与项目列表分页一致）。
     */
    async loadProjectOptions() {
      const res = await listProjects({ pageNum: 1, pageSize: 500, keyword: '' })
      this.projectOptions = (res.data && res.data.records) || []
    },
    /**
     * 下拉展示：项目名称（项目编码）。
     */
    projectOptionLabel(p) {
      if (!p) return ''
      return p.projectName
    },
    /**
     * 切换项目时重置分页并刷新规则；同步地址栏便于收藏。
     */
    onProjectChange() {
      this.listQuery.pageNum = 1
      const id = this.selectedProjectId
      if (id) this.loadRules()
      else {
        this.list = []
        this.total = 0
      }
      // vue-router 3.0.2 的 replace 不返回 Promise，避免对其调用 .catch() 抛错中断上面的查询逻辑
      try {
        this.$router.replace({ path: this.$route.path, query: id ? { projectId: String(id) } : {}})
      } catch (e) {
        /* 忽略重复导航异常 */
      }
    },
    /**
     * 按当前选中项目分页查询规则定义。
     */
    async loadRules() {
      if (!this.selectedProjectId) return
      this.loading = true
      try {
        const r = await listDefinitions({ ...this.listQuery, projectId: this.selectedProjectId })
        this.list = r.data.records
        this.total = r.data.total
      } finally {
        this.loading = false
      }
    },
    /**
     * 关键字 / 模型查询：回到第一页再加载。
     */
    handleQuery() {
      this.listQuery.pageNum = 1
      this.loadRules()
    },
    /**
     * el-table span-method：只有规则名称(0)和规则编码(1)合并，其余列每行独立。
     */
    objectSpanMethod({ row, column, rowIndex, columnIndex }) {
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
    /**
     * 清空筛选条件并重新加载。
     */
    resetFilters() {
      this.listQuery.keyword = ''
      this.listQuery.modelType = ''
      this.handleQuery()
    },
    /**
     * 将接口返回的时间格式化为 yyyy-MM-dd HH:mm:ss（兼容 ISO 字符串与数组形式 LocalDateTime JSON）。
     */
    formatDateTime(time) {
      if (time == null || time === '') return '-'
      let d
      if (Array.isArray(time) && time.length >= 6) {
        const y = time[0]
        const mo = time[1] - 1
        const day = time[2]
        const h = time[3] || 0
        const mi = time[4] || 0
        const s = time[5] || 0
        d = new Date(y, mo, day, h, mi, s)
      } else {
        d = new Date(time)
      }
      if (isNaN(d.getTime())) return typeof time === 'string' ? time : '-'
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    /**
     * 模型类型中文展示。
     */
    mtl(t) {
      const map = {
        TABLE: '决策表',
        TREE: '决策树',
        FLOW: '决策流',
        CROSS: '交叉表',
        SCORE: '评分卡',
        CROSS_ADV: '复杂交叉表',
        SCORE_ADV: '复杂评分卡',
        SCRIPT: 'QL脚本'
      }
      return map[t] || t
    },
    /**
     * modelType 转设计器路由 path 段。
     */
    modelTypeToPath(modelType) {
      const m = {
        TABLE: 'table',
        TREE: 'tree',
        FLOW: 'flow',
        CROSS: 'cross',
        SCORE: 'score',
        CROSS_ADV: 'cross-adv',
        SCORE_ADV: 'score-adv',
        SCRIPT: 'script'
      }
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
     * 在 Layout 抽屉中打开对应模型设计器。
     */
    goDesigner(row) {
      const defId = this.definitionIdFromRow(row)
      this.$store.commit('designerDrawer/OPEN', {
        segment: this.modelTypeToPath(row.modelType),
        definitionId: defId,
        query: { scopeCompId: row._scopeCompId }
      })
    },
    /**
     * 打开发新建规则弹窗。
     */
    openCreateDlg() {
      if (!this.selectedProjectId) {
        this.$message.warning('请先选择项目')
        return
      }
      this.fm = { ruleCode: '', ruleName: '', modelType: '', description: '', preciseMode: 0 }
      this.dlgVis = true
      this.$nextTick(() => {
        if (this.$refs.createForm) this.$refs.createForm.clearValidate()
      })
    },
    /**
     * 提交新建规则并跳转设计器。
     */
    submitCreate() {
      this.$refs.createForm.validate(async valid => {
        if (!valid) return
        const res = await createDefinition({
          ...this.fm,
          projectId: this.selectedProjectId,
          initialScopeCompId: '0'
        })
        const def = res && res.data !== undefined ? res.data : res
        this.$message.success('创建成功')
        this.dlgVis = false
        this.loadRules()
        const newDefId = def && (def.id != null && def.id !== '' ? def.id : def.definitionId)
        if (def && newDefId && def.modelType) {
          this.$store.commit('designerDrawer/OPEN', {
            segment: this.modelTypeToPath(def.modelType),
            definitionId: newDefId,
            query: {}
          })
        }
      })
    },
    /**
     * 打开发布作用域选择弹窗（可勾选本次要上线的省份 / 全国）。
     */
    pub(row) {
      this.publishDefinitionId = this.definitionIdFromRow(row)
      this.publishDlgVis = true
    },
    /**
     * 下线规则。
     */
    async unpub(row) {
      await this.$confirm('确定下线?')
      await unpublishRule(this.definitionIdFromRow(row), row._scopeCompId)
      this.$message.success('下线成功')
      this.loadRules()
    },
    /**
     * 删除规则（已发布状态不允许删除）。
     */
    async del(row) {
      if (row.status === 1) {
        this.$message.warning('已发布状态的规则不能删除，请先下线')
        return
      }
      await this.$confirm('确定删除?')
      await deleteDefinitionScope(this.definitionIdFromRow(row), row._scopeCompId)
      this.$message.success('删除成功')
      this.loadRules()
    },
    /**
     * 打开修改弹窗。
     */
    openEditDlg(row) {
      const precise = row.preciseMode === 1 ? 1 : 0
      this.editForm = {
        definitionId: this.definitionIdFromRow(row),
        scopeCompId: row._scopeCompId,
        compId: row.compId || row._scopeCompId || '',
        description: row.description || '',
        preciseMode: precise,
        _origPreciseMode: precise,
        _published: row.status === 1
      }
      this.editDlgVis = true
    },
    /**
     * 提交修改省份/说明。
     */
    async submitEdit() {
      this.editLoading = true
      try {
        await updateContentMeta(this.editForm)
        const preciseChanged = this.editForm.preciseMode !== this.editForm._origPreciseMode
        if (preciseChanged) {
          // 精度模式是规则定义级字段，单独走 definition 更新接口
          await updateDefinition({ id: this.editForm.definitionId, preciseMode: this.editForm.preciseMode })
        }
        if (preciseChanged && this.editForm._published) {
          this.$message.warning('精度模式已修改，需重新发布后对业务方生效')
        } else {
          this.$message.success('修改成功')
        }
        this.editDlgVis = false
        this.loadRules()
      } catch (e) {
        /* request interceptor handles error */
      } finally {
        this.editLoading = false
      }
    },
    /**
     * 打开复制到省份弹窗。
     */
    openCopyDlg(row) {
      this.copyDefinitionId = this.definitionIdFromRow(row)
      this.copyRuleName = row.ruleName || row.ruleCode || ''
      this.copyTargetScopes = []
      this.copyPickScope = null
      this.copyDlgVis = true
    },
    /** 添加一个省份到复制目标列表 */
    addCopyScope() {
      if (!this.copyPickScope) return
      if (!this.copyTargetScopes.includes(this.copyPickScope)) {
        this.copyTargetScopes.push(this.copyPickScope)
      }
      this.copyPickScope = null
    },
    /** 从复制目标列表中移除一个省份 */
    removeCopyScope(index) {
      this.copyTargetScopes.splice(index, 1)
    },
    /**
     * 提交复制到目标省份。
     */
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
        this.loadRules()
      } catch (e) {
        /* request 拦截器已提示 */
      } finally {
        this.copyLoading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.rule-mgmt-project-bar {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.rule-mgmt-empty {
  padding: 24px 0;
  background: #fafafa;
  border-radius: 4px;
  margin-top: 10px;
}

</style>

<style lang="scss">
/* 操作列按钮紧凑间距（unscoped 才能穿透 el-table 渲染） */
.rule-ops-col .el-button + .el-button {
  margin-left: 2px;
}
.rule-ops-col .el-button--text {
  padding-left: 4px;
  padding-right: 4px;
}
</style>
