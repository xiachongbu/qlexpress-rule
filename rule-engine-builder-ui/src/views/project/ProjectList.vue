<template>
  <div class="uiue-list-page">
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keyword" placeholder="项目编码或名称" clearable @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="uiue-btn-bar">
      <div class="btn-right">
        <el-button type="primary" size="small" icon="el-icon-plus" @click="handleCreate">新建项目</el-button>
      </div>
    </div>
    <el-table :data="tableData" border size="small" v-loading="loading" style="width: 100%;">
      <el-table-column prop="projectCode" label="项目编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" min-width="70" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="访问令牌" min-width="160">
        <template slot-scope="{ row }">
          <span v-if="row.maskedToken" style="font-family: monospace;">{{ row.maskedToken }}</span>
          <span v-else style="color: #909399;">未生成</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="160" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="text" size="small" @click="$router.push('/project/' + row.id)">进入</el-button>
          <el-button type="text" size="small" style="color: #F56C6C;" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right;" :current-page="queryParams.pageNum" :page-size="queryParams.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="p => { queryParams.pageNum = p; loadData() }" @size-change="s => { queryParams.pageSize = s; queryParams.pageNum = 1; loadData() }" />
    <el-dialog :title="form.id ? '编辑项目' : '新建项目'" :visible.sync="dialogVisible" width="500px">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px" size="small">
        <el-form-item label="项目编码" prop="projectCode"><el-input v-model="form.projectCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="项目名称" prop="projectName"><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSubmit">确定</el-button>
      </div>
    </el-dialog>
    <!-- Token显示对话框 -->
    <el-dialog title="AccessToken" :visible.sync="tokenDialogVisible" width="500px">
      <div style="padding: 20px; background: #f5f7fa; border-radius: 4px;">
        <p style="margin: 0; font-family: monospace; word-break: break-all;">{{ fullToken }}</p>
      </div>
      <div style="margin-top: 10px; color: #909399; font-size: 12px;">
        <i class="el-icon-warning"></i> 请妥善保管Token，不要泄露给他人
      </div>
      <div slot="footer">
        <el-button size="small" @click="copyToken">复制</el-button>
        <el-button size="small" type="primary" @click="tokenDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { listProjects, createProject, updateProject, deleteProject, getMaskedToken } from '@/api/project'
export default {
  name: 'ProjectList',
  data() {
    return {
      loading: false, tableData: [], total: 0,
      queryParams: { pageNum: 1, pageSize: 10, keyword: '' },
      dialogVisible: false,
      tokenDialogVisible: false,
      fullToken: '',
      form: { id: null, projectCode: '', projectName: '', description: '', status: 1 },
      rules: {
        projectCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
        projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
      }
    }
  },
  created() { this.loadData() },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const res = await listProjects(this.queryParams)
        this.tableData = res.data.records
        this.total = res.data.total
        // 加载每个项目的脱敏Token
        for (let row of this.tableData) {
          try {
            const tokenRes = await getMaskedToken(row.id)
            if (tokenRes.code === 200 && tokenRes.data) {
              this.$set(row, 'maskedToken', tokenRes.data)
            }
          } catch (e) {
            // ignore
          }
        }
      } finally { this.loading = false }
    },
    handleQuery() { this.queryParams.pageNum = 1; this.loadData() },
    resetQuery() { this.queryParams.keyword = ''; this.handleQuery() },
    handleCreate() { this.form = { id: null, projectCode: '', projectName: '', description: '', status: 1 }; this.dialogVisible = true },
    handleEdit(row) { this.form = { ...row }; this.dialogVisible = true },
    async handleSubmit() {
      this.$refs.form.validate(async v => {
        if (!v) return
        if (this.form.id) {
          await updateProject(this.form)
          this.$message.success('更新成功')
          this.dialogVisible = false
          this.loadData()
        } else {
          const res = await createProject(this.form)
          if (res.code === 200 && res.data) {
            this.$message.success('创建成功')
            this.dialogVisible = false
            // 显示新生成的Token
            this.fullToken = res.data.accessToken
            this.tokenDialogVisible = true
            this.loadData()
          }
        }
      })
    },
    handleDelete(row) {
      this.$confirm('确定删除项目「' + row.projectName + '」?', '确认', { type: 'warning' }).then(async () => {
        await deleteProject(row.id); this.$message.success('删除成功'); this.loadData()
      }).catch(() => {})
    },
    copyToken() {
      const input = document.createElement('textarea')
      input.value = this.fullToken
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
      this.$message.success('已复制到剪贴板')
    }
  }
}
</script>
