<template>
  <div class="uiue-list-page">
    <div class="linkage-hint">
      <i class="el-icon-info" /> 自定义函数可在决策流/决策树的脚本任务中调用。支持 QLExpress 脚本、Java 类或 Spring Bean 实现。
    </div>

    <div class="var-toolbar">
      <el-select v-model="currentProjectId" placeholder="选择项目" size="small" style="width:200px;" clearable @change="onProjectChange">
        <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <div class="toolbar-right">
        <el-button size="small" icon="el-icon-plus" type="primary" :disabled="!currentProjectId" @click="handleCreate">新建函数</el-button>
      </div>
    </div>

    <el-table :data="funcList" border size="small" v-loading="loading" style="width:100%;margin-top:12px;">
      <el-table-column prop="funcCode" label="函数编码" min-width="120" show-overflow-tooltip />
      <el-table-column prop="funcName" label="函数名称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="returnType" label="返回类型" width="90" align="center">
        <template slot-scope="{ row }">
          <el-tag size="mini">{{ typeLabel(row.returnType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="implType" label="实现方式" width="110" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="implTypeTagType(row.implType)" size="mini">{{ implTypeLabel(row.implType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="参数" min-width="150">
        <template slot-scope="{ row }">
          <span v-if="row.paramsJson">
            <el-tag v-for="(p, pi) in parseParams(row.paramsJson)" :key="pi" size="mini" type="info" style="margin:1px 2px;">
              {{ p.name }}: {{ typeLabel(p.type) }}
            </el-tag>
          </span>
          <span v-else style="color:#999">无参</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="60" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.status===1?'success':'info'" size="mini">{{ row.status===1?'启用':'停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="text" size="small" style="color:#F56C6C;" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && funcList.length === 0 && currentProjectId" style="text-align:center;padding:40px;color:#bbb;">
      暂无自定义函数，可点击「新建函数」添加
    </div>
    <div v-if="!loading && !currentProjectId" style="text-align:center;padding:40px;color:#bbb;">
      请先选择一个项目
    </div>

    <el-pagination v-if="currentProjectId" style="margin-top:16px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="onPageChange" @size-change="onSizeChange" />

    <!-- 新建/编辑弹窗 -->
    <el-dialog :title="editForm.id ? '编辑函数' : '新建函数'" :visible.sync="dialogVisible" width="600px" append-to-body>
      <el-form :model="editForm" label-width="90px" size="small">
        <el-form-item label="函数编码" required>
          <el-input v-model="editForm.funcCode" placeholder="如 calcTax（QLExpress 脚本中的调用名）" />
        </el-form-item>
        <el-form-item label="函数名称" required>
          <el-input v-model="editForm.funcName" placeholder="中文名称，如 计算税额" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="函数功能说明" />
        </el-form-item>
        <el-form-item label="返回类型">
          <el-select v-model="editForm.returnType" style="width:100%" popper-append-to-body>
            <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数列表">
          <div v-for="(p, pi) in editParams" :key="pi" style="display:flex;gap:4px;margin-bottom:4px;">
            <el-input v-model="p.name" size="mini" placeholder="参数名" style="width:100px" />
            <el-select v-model="p.type" size="mini" style="width:150px" popper-append-to-body>
              <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-input v-model="p.label" size="mini" placeholder="中文名" style="flex:1" />
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C" @click="editParams.splice(pi, 1)" />
          </div>
          <el-button size="mini" icon="el-icon-plus" @click="editParams.push({name:'',type:'STRING',label:''})">添加参数</el-button>
        </el-form-item>
        <el-form-item label="实现方式">
          <el-radio-group v-model="editForm.implType">
            <el-radio label="SCRIPT">QLExpress 脚本</el-radio>
            <el-radio label="JAVA">Java 类</el-radio>
            <el-radio label="BEAN">Spring Bean</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="实现脚本" v-if="editForm.implType === 'SCRIPT'">
          <el-input v-model="editForm.implScript" type="textarea" :rows="6" placeholder="QLExpress 脚本，参数可直接使用" class="mono-input" />
        </el-form-item>
        <el-form-item label="Java类名" v-if="editForm.implType === 'JAVA'">
          <el-input v-model="editForm.implClass" placeholder="如 com.bjjw.rule.example.functions.TaxFunctions" />
        </el-form-item>
        <el-form-item label="方法名" v-if="editForm.implType === 'JAVA'">
          <el-input v-model="editForm.implMethod" placeholder="Java 方法名，如 calculateVAT（不填则默认使用函数编码）" />
        </el-form-item>
        <el-form-item label="Bean名称" v-if="editForm.implType === 'BEAN'">
          <el-input v-model="editForm.implBeanName" placeholder="Spring Bean 名称，如 taxFunctions" />
        </el-form-item>
        <el-form-item label="方法名" v-if="editForm.implType === 'BEAN'">
          <el-input v-model="editForm.implMethod" placeholder="Bean 上的方法名，如 calculateVAT（不填则默认使用函数编码）" />
        </el-form-item>
      </el-form>
      <template slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listFunctionsByProject, createFunction, updateFunction, deleteFunction } from '@/api/function'
import request from '@/api/request'
import { VAR_TYPE_FORM_OPTIONS, varTypeLabel } from '@/constants/varTypes'

export default {
  name: 'FunctionList',
  data() {
    return {
      projects: [],
      currentProjectId: null,
      funcList: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10 },
      loading: false,
      dialogVisible: false,
      editForm: { funcCode: '', funcName: '', description: '', returnType: 'STRING', implType: 'SCRIPT', implScript: '', implClass: '', implMethod: '', implBeanName: '', status: 1 },
      editParams: [],
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS
    }
  },
  created() {
    this.loadProjects()
  },
  methods: {
    async loadProjects() {
      try {
        const res = await request.get('/rule/project/list', { params: { pageNum: 1, pageSize: 200 } })
        this.projects = (res && res.data && res.data.records) || (res && res.data) || []
      } catch (e) { /* ignore */ }
    },
    async onProjectChange() {
      this.qp.pageNum = 1
      if (!this.currentProjectId) { this.funcList = []; this.total = 0; return }
      this.loadFunctions()
    },
    async loadFunctions() {
      if (!this.currentProjectId) return
      this.loading = true
      try {
        const res = await listFunctionsByProject(this.currentProjectId, this.qp)
        this.funcList = (res && res.data && res.data.records) || []
        this.total = (res && res.data && res.data.total) || 0
      } catch (e) {
        this.$message.error('加载失败')
      } finally {
        this.loading = false
      }
    },
    onPageChange(p) {
      this.qp.pageNum = p
      this.loadFunctions()
    },
    /** 每页条数变更 */
    onSizeChange(s) {
      this.qp.pageSize = s
      this.qp.pageNum = 1
      this.loadFunctions()
    },
    parseParams(json) {
      try { return JSON.parse(json) } catch (e) { return [] }
    },
    typeLabel: varTypeLabel,
    implTypeLabel(type) {
      return { SCRIPT: '脚本', JAVA: 'Java类', BEAN: 'Bean' }[type] || type
    },
    implTypeTagType(type) {
      return { SCRIPT: '', JAVA: 'warning', BEAN: 'success' }[type] || 'info'
    },
    handleCreate() {
      this.editForm = { funcCode: '', funcName: '', description: '', returnType: 'STRING', implType: 'SCRIPT', implScript: '', implClass: '', implMethod: '', implBeanName: '', status: 1, projectId: this.currentProjectId }
      this.editParams = [{ name: '', type: 'STRING', label: '' }]
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.editForm = { ...row }
      this.editParams = this.parseParams(row.paramsJson || '[]')
      if (this.editParams.length === 0) this.editParams = [{ name: '', type: 'STRING', label: '' }]
      this.dialogVisible = true
    },
    async handleSave() {
      if (!this.editForm.funcCode || !this.editForm.funcName) {
        this.$message.warning('请填写函数编码和名称')
        return
      }
      this.editForm.paramsJson = JSON.stringify(this.editParams.filter(p => p.name))
      try {
        if (this.editForm.id) {
          await updateFunction(this.editForm)
        } else {
          this.editForm.projectId = this.currentProjectId
          await createFunction(this.editForm)
        }
        this.$message.success('保存成功')
        this.dialogVisible = false
        this.loadFunctions()
      } catch (e) {
        this.$message.error('保存失败')
      }
    },
    async handleDelete(row) {
      try {
        await this.$confirm('确认删除函数「' + row.funcName + '」？', '提示', { type: 'warning' })
        await deleteFunction(row.id)
        this.$message.success('已删除')
        this.loadFunctions()
      } catch (e) { /* cancel */ }
    }
  }
}
</script>

<style scoped>
.uiue-list-page { padding: 16px; }
.linkage-hint { font-size: 13px; color: #909399; margin-bottom: 12px; background: #fafafa; padding: 8px 12px; border-radius: 4px; }
.var-toolbar { display: flex; align-items: center; justify-content: space-between; }
.toolbar-right { display: flex; gap: 8px; }
.mono-input ::v-deep textarea { font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; }
</style>
