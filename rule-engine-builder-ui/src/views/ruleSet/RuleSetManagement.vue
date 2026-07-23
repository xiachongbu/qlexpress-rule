<template>
  <div class="uiue-list-page rule-set-page">
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
            <el-input v-model="listQuery.keyword" clearable placeholder="集编码或名称" style="width: 200px;" @keyup.enter.native="handleQuery" />
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
          <img src="@/assets/uiueImages/uiue_emptyIcon.png">
        </template>
        <template #description>
          <span style="color: #595959;">请先选择项目</span>
        </template>
      </el-empty>
    </div>
    <template v-else>
      <div class="uiue-btn-bar">
        <div class="btn-right">
          <el-button type="success" @click="openCreate">新建规则集</el-button>
        </div>
      </div>
      <el-table v-loading="loading" :data="list" border row-class-name="uiueTable" header-row-class-name="uiueTableHeader">
        <el-table-column prop="setName" label="规则集名称" min-width="160" show-overflow-tooltip sortable />
        <el-table-column prop="setCode" label="规则集编码" min-width="140" show-overflow-tooltip sortable />
        <el-table-column prop="memberCount" label="成员数" width="120" align="center" sortable />
        <el-table-column prop="status" label="状态" min-width="140" align="center" sortable>
          <template slot-scope="{ row }">
            <el-tag :type="{ 0: 'info', 1: 'success', 2: 'warning' }[row.status]" size="mini">
              {{ ['草稿', '已发布', '已下线'][row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip sortable />
        <el-table-column label="操作" min-width="410" align="center">
          <template slot-scope="{ row }">
            <div style="white-space:nowrap">
              <el-button type="text" size="small" @click="openMembers(row)">成员</el-button>
              <el-button type="text" size="small" @click="openTest(row)">试跑</el-button>
              <el-button type="text" size="small" @click="openPublish(row)">发布</el-button>
              <el-button v-if="row.status === 1" type="text" size="small" @click="doUnpublish(row)">下线</el-button>
              <el-button type="text" size="small" style="color:#F56C6C" @click="del(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <img class="empty" src="@/assets/uiueImages/uiue_emptyIcon.png" alt="">
        </template>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="listQuery.pageNum" :limit.sync="listQuery.pageSize" @pagination="load" />
    </template>

    <el-dialog title="新建规则集" :visible.sync="createVis" width="510px" append-to-body custom-class="middleDialog">
      <el-form ref="createForm" :model="createFm" :rules="createRules" label-width="100px" size="small">
        <el-form-item label="集编码" prop="setCode"><el-input v-model="createFm.setCode" placeholder="全局唯一，勿与规则编码重复" /></el-form-item>
        <el-form-item label="集名称" prop="setName"><el-input v-model="createFm.setName" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="createFm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" type="primary" @click="createVis = false">取消</el-button>
        <el-button size="small" type="success" :loading="createSubmitting" @click="submitCreate">确定</el-button>
      </div>
    </el-dialog>

    <el-drawer title="维护成员顺序" :visible.sync="membersVis" size="92%" class="members-drawer" @open="onMembersOpen">
      <div v-if="membersCtx" class="members-drawer-content">
        <div class="members-scroll-area">
          <p class="rule-set-tip">自上而下为执行顺序。从下方多选添加本项目规则。</p>
          <el-table :data="memberRows" border size="small">
            <el-table-column label="#" width="50" type="index" />
            <el-table-column prop="ruleCode" label="规则编码" min-width="120" />
            <el-table-column prop="ruleName" label="规则名称" min-width="140" />
            <el-table-column label="操作" width="300" align="center">
              <template slot-scope="{ $index }">
                <el-button type="text" size="mini" :disabled="$index === 0" @click="moveMember($index, -1)">上移</el-button>
                <el-button type="text" size="mini" :disabled="$index === memberRows.length - 1" @click="moveMember($index, 1)">下移</el-button>
                <el-button type="text" size="mini" style="color:#F56C6C" @click="removeMember($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="members-bottom-bar">
          <div class="members-actions">
            <el-select
              v-model="pickIds"
              multiple
              filterable
              collapse-tags
              placeholder="选择要加入的规则"
              style="min-width:320px;"
              size="small"
            >
              <el-option
                v-for="d in pickableDefinitions"
                :key="d.id"
                :label="`${d.ruleName}（${d.ruleCode}）`"
                :value="d.id"
              />
            </el-select>
            <el-button type="primary" size="small" @click="addPickedMembers">加入</el-button>
          </div>
          <div class="members-footer">
            <el-button type="primary" @click="membersVis = false">关闭</el-button>
            <el-button type="success" :loading="membersSaving" @click="saveMembers">保存顺序</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog title="发布规则集" :visible.sync="publishVis" width="510px" append-to-body custom-class="middleDialog">
      <el-form size="small" label-width="120px">
        <el-form-item label="作用域策略">
          <el-radio-group v-model="publishMode">
            <el-radio label="auto">自动（成员已上线作用域交集）</el-radio>
            <el-radio label="manual">指定作用域</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="publishMode === 'manual'" label="作用域">
          <el-select v-model="publishScopes" multiple filterable placeholder="多选省份/组织" style="width:100%;">
            <el-option v-for="o in compScopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="publishVis = false">取消</el-button>
        <el-button type="success" :loading="publishSubmitting" @click="submitPublish">确定发布</el-button>
      </div>
    </el-dialog>

    <el-dialog title="规则集试跑" :visible.sync="testVis" width="510px" append-to-body custom-class="middleDialog">
      <el-form size="small" label-width="88px">
        <el-form-item label="作用域">
          <el-select v-model="testScope" filterable style="width:100%;">
            <el-option v-for="o in compScopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="入参 JSON">
          <el-input v-model="testParamsJson" type="textarea" :rows="6" placeholder="例如 {&quot;amount&quot;:100}" />
        </el-form-item>
      </el-form>
      <el-alert v-if="testResultText" :title="testResultText" type="info" show-icon :closable="false" style="margin-top:8px;" />
      <div slot="footer">
        <el-button type="primary" @click="testVis = false">关闭</el-button>
        <el-button type="success" :loading="testRunning" @click="runTest">执行</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import { listProjects } from '@/api/project'
import { listDefinitions } from '@/api/definition'
import {
  listRuleSets,
  createRuleSet,
  deleteRuleSet,
  listRuleSetMembers,
  saveRuleSetMembers,
  publishRuleSet,
  unpublishRuleSet,
  executeRuleSet
} from '@/api/ruleSet'
import { COMP_SCOPE_OPTIONS } from '@/constants/compScopeOptions'
import { DICT_TYPE_COMP_SCOPE } from '@/constants/dictTypes'
import Pagination from '@/components/Pagination/index.vue'

export default {
  name: 'RuleSetManagement',
  components: { Pagination },
  data() {
    return {
      projectOptions: [],
      selectedProjectId: null,
      loading: false,
      list: [],
      total: 0,
      listQuery: { pageNum: 1, pageSize: 10, keyword: '' },
      createVis: false,
      createSubmitting: false,
      createFm: { setCode: '', setName: '', description: '' },
      createRules: {
        setCode: [{ required: true, message: '必填', trigger: 'blur' }],
        setName: [{ required: true, message: '必填', trigger: 'blur' }]
      },
      membersVis: false,
      membersCtx: null,
      memberRows: [],
      projectDefinitions: [],
      pickIds: [],
      membersSaving: false,
      publishVis: false,
      publishRow: null,
      publishMode: 'auto',
      publishScopes: [],
      publishSubmitting: false,
      testVis: false,
      testRow: null,
      testScope: '0',
      testParamsJson: '{}',
      testRunning: false,
      testResultText: ''
    }
  },
  computed: {
    ...mapState('ruleDict', {
      _dictCompScope(state) {
        const arr = state.byType[DICT_TYPE_COMP_SCOPE]
        return Array.isArray(arr) ? arr : []
      }
    }),
    compScopeOptions() {
      if (this._dictCompScope.length > 0) return this._dictCompScope
      return COMP_SCOPE_OPTIONS
    },
    /** 尚未在成员列表中的规则，供多选添加 */
    pickableDefinitions() {
      const inSet = new Set(this.memberRows.map(r => r.id))
      return this.projectDefinitions.filter(d => !inSet.has(d.id))
    }
  },
  created() {
    this.bootstrap()
  },
  methods: {
    async bootstrap() {
      const res = await listProjects({ pageNum: 1, pageSize: 500, keyword: '' })
      this.projectOptions = (res.data && res.data.records) || []
      const q = this.$route.query.projectId
      if (q != null && q !== '') {
        const found = this.projectOptions.find(p => String(p.id) === String(q))
        if (found) {
          this.selectedProjectId = found.id
          this.load()
        }
      }
    },
    projectOptionLabel(p) {
      if (!p) return ''
      return p.projectName
    },
    onProjectChange() {
      this.listQuery.pageNum = 1
      const id = this.selectedProjectId
      this.$router.replace({ path: this.$route.path, query: id ? { projectId: String(id) } : {}}).catch(() => {})
      if (id) this.load()
      else {
        this.list = []
        this.total = 0
      }
    },
    async load() {
      if (!this.selectedProjectId) return
      this.loading = true
      try {
        const r = await listRuleSets({ ...this.listQuery, projectId: this.selectedProjectId })
        this.list = r.data.records
        this.total = r.data.total
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.listQuery.pageNum = 1
      this.load()
    },
    resetFilters() {
      this.listQuery.keyword = ''
      this.handleQuery()
    },
    openCreate() {
      this.createFm = { setCode: '', setName: '', description: '' }
      this.createVis = true
      this.$nextTick(() => this.$refs.createForm && this.$refs.createForm.clearValidate())
    },
    submitCreate() {
      this.$refs.createForm.validate(async valid => {
        if (!valid) return
        this.createSubmitting = true
        try {
          await createRuleSet({
            projectId: this.selectedProjectId,
            setCode: this.createFm.setCode.trim(),
            setName: this.createFm.setName.trim(),
            description: this.createFm.description || undefined
          })
          this.$message.success('创建成功')
          this.createVis = false
          this.load()
        } catch (e) {
          /* 全局拦截 */
        } finally {
          this.createSubmitting = false
        }
      })
    },
    async del(row) {
      try {
        await this.$confirm('确定删除该规则集？', '提示', { type: 'warning' })
      } catch (e) {
        return
      }
      await deleteRuleSet(row.id)
      this.$message.success('已删除')
      this.load()
    },
    async openMembers(row) {
      this.membersCtx = row
      this.membersVis = true
    },
    async onMembersOpen() {
      if (!this.membersCtx || !this.selectedProjectId) return
      const [memRes, defRes] = await Promise.all([
        listRuleSetMembers({ setId: this.membersCtx.id }),
        listDefinitions({ pageNum: 1, pageSize: 500, projectId: this.selectedProjectId, keyword: '', modelType: '' })
      ])
      this.memberRows = (memRes.data || []).map(d => ({ id: d.id, ruleCode: d.ruleCode, ruleName: d.ruleName }))
      this.projectDefinitions = (defRes.data && defRes.data.records) || []
      this.pickIds = []
    },
    moveMember(idx, delta) {
      const j = idx + delta
      if (j < 0 || j >= this.memberRows.length) return
      const arr = this.memberRows.slice()
      const t = arr[idx]
      arr[idx] = arr[j]
      arr[j] = t
      this.memberRows = arr
    },
    removeMember(idx) {
      this.memberRows.splice(idx, 1)
    },
    addPickedMembers() {
      if (!this.pickIds.length) return
      const byId = new Map(this.projectDefinitions.map(d => [d.id, d]))
      for (const id of this.pickIds) {
        const d = byId.get(id)
        if (d && !this.memberRows.some(r => r.id === d.id)) {
          this.memberRows.push({ id: d.id, ruleCode: d.ruleCode, ruleName: d.ruleName })
        }
      }
      this.pickIds = []
    },
    async saveMembers() {
      if (!this.membersCtx) return
      this.membersSaving = true
      try {
        await saveRuleSetMembers({
          setId: this.membersCtx.id,
          definitionIdsInOrder: this.memberRows.map(r => r.id)
        })
        this.$message.success('成员已保存')
        this.membersVis = false
        this.load()
      } finally {
        this.membersSaving = false
      }
    },
    openPublish(row) {
      this.publishRow = row
      this.publishMode = 'auto'
      this.publishScopes = []
      this.publishVis = true
    },
    async submitPublish() {
      if (!this.publishRow) return
      const body = {}
      if (this.publishMode === 'manual') {
        if (!this.publishScopes.length) {
          this.$message.warning('请选择至少一个作用域')
          return
        }
        body.scopeCompIds = this.publishScopes.map(String)
      }
      this.publishSubmitting = true
      try {
        await publishRuleSet(this.publishRow.id, body)
        this.$message.success('发布成功')
        this.publishVis = false
        this.load()
      } finally {
        this.publishSubmitting = false
      }
    },
    async doUnpublish(row) {
      try {
        await this.$confirm('确定下线该规则集？', '提示', { type: 'warning' })
      } catch (e) {
        return
      }
      await unpublishRuleSet(row.id)
      this.$message.success('已下线')
      this.load()
    },
    openTest(row) {
      this.testRow = row
      this.testScope = '0'
      this.testParamsJson = '{}'
      this.testResultText = ''
      this.testVis = true
    },
    async runTest() {
      if (!this.testRow) return
      let params = {}
      try {
        params = this.testParamsJson ? JSON.parse(this.testParamsJson) : {}
      } catch (e) {
        this.$message.error('入参 JSON 格式不正确')
        return
      }
      this.testRunning = true
      this.testResultText = ''
      try {
        const r = await executeRuleSet({
          setId: this.testRow.id,
          scopeCompId: this.testScope != null ? String(this.testScope) : '0',
          params
        })
        const s = r.data
        this.testResultText = s && s.success ? `成功，最终摘要：${JSON.stringify(s.finalResult)}` : `失败：${(s && s.errorMessage) || '未知'}`
      } finally {
        this.testRunning = false
      }
    }
  }
}
</script>

<style scoped>
.rule-set-page {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.rule-set-project-bar {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.rule-set-empty {
  padding: 24px 0;
  background: #fafafa;
  border-radius: 4px;
}
.rule-set-tip { color: #606266; font-size: 13px; margin-bottom: 8px; }
.rule-set-page >>> .uiue-list-heading,
.rule-set-page >>> .uiue-search-container,
.rule-set-page >>> .uiue-btn-bar { flex-shrink: 0; }
.rule-set-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* 成员维护抽屉样式 - 占满全屏高度，flex布局分层 */
.members-drawer >>> .el-drawer {
  height: 100% !important;
  top: 0 !important;
}

.members-drawer >>> .el-drawer__body {
  padding: 0 !important;
  overflow: hidden !important;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.members-drawer-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* 中间可滚动区域 - 表格区 */
.members-scroll-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 16px 0;
}

.rule-set-tip {
  color: #606266;
  font-size: 13px;
  margin: 0 0 12px 0;
}

/* 底部固定操作栏 */
.members-bottom-bar {
  flex-shrink: 0;
  padding: 12px 16px 16px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.members-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.members-footer {
  margin-top: 12px;
  text-align: right;
}
</style>
