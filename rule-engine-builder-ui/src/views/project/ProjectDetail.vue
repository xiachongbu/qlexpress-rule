<template>
  <div class="uiue-list-page">
    <div style="margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;">
      <h2 style="margin:0;">{{ project ? project.projectName : '加载中...' }}</h2>
      <el-button size="small" icon="el-icon-back" @click="$router.push('/project')">返回</el-button>
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
    <div class="uiue-btn-bar"><div class="btn-right"><el-button type="primary" size="small" icon="el-icon-plus" @click="dlgVis=true">新建规则</el-button></div></div>
    <el-table :data="list" border size="small" v-loading="loading" style="width: 100%;">
      <el-table-column prop="ruleCode" label="规则编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="modelType" label="模型类型" min-width="90" align="center"><template slot-scope="{row}"><el-tag size="mini">{{mtl(row.modelType)}}</el-tag></template></el-table-column>
      <el-table-column prop="status" label="状态" min-width="70" align="center"><template slot-scope="{row}"><el-tag :type="{0:'info',1:'success',2:'warning'}[row.status]" size="mini">{{['草稿','已发布','已下线'][row.status]}}</el-tag></template></el-table-column>
      <el-table-column prop="currentVersion" label="设计版本" min-width="80" align="center" />
      <el-table-column prop="publishedVersion" label="发布版本" min-width="80" align="center" />
      <el-table-column label="操作" min-width="180" align="center">
        <template slot-scope="{row}">
          <el-button type="text" size="small" @click="go(row)">设计</el-button>
          <el-button type="text" size="small" @click="pub(row)">{{ row.status === 1 ? '重新发布' : '发布' }}</el-button>
          <el-button type="text" size="small" v-if="row.status===1" @click="unpub(row)">下线</el-button>
          <el-button type="text" size="small" style="color:#F56C6C" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="p=>{qp.pageNum=p;load()}" @size-change="s=>{qp.pageSize=s;qp.pageNum=1;load()}" />
    <el-dialog title="新建规则" :visible.sync="dlgVis" width="500px">
      <el-form ref="f" :model="fm" :rules="{ruleCode:[{required:true,message:'必填',trigger:'blur'}],ruleName:[{required:true,message:'必填',trigger:'blur'}],modelType:[{required:true,message:'必选',trigger:'change'}]}" label-width="100px" size="small">
        <el-form-item label="规则编码" prop="ruleCode"><el-input v-model="fm.ruleCode" /></el-form-item>
        <el-form-item label="规则名称" prop="ruleName"><el-input v-model="fm.ruleName" /></el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="fm.modelType" style="width:100%"><el-option label="决策表" value="TABLE" /><el-option label="决策树" value="TREE" /><el-option label="决策流" value="FLOW" /><el-option label="交叉表" value="CROSS" /><el-option label="评分卡" value="SCORE" /><el-option label="复杂交叉表" value="CROSS_ADV" /><el-option label="复杂评分卡" value="SCORE_ADV" /><el-option label="QL脚本" value="SCRIPT" /></el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="fm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button size="small" @click="dlgVis=false">取消</el-button><el-button size="small" type="primary" @click="submit">确定</el-button></div>
    </el-dialog>
  </div>
</template>
<script>
import { listDefinitions, createDefinition, deleteDefinition } from '@/api/definition'
import { getProject } from '@/api/project'
import request from '@/api/request'
export default {
  name: 'ProjectDetail',
  data() { return { pid: null, project: null, loading: false, list: [], total: 0, qp: { pageNum: 1, pageSize: 10, keyword: '', modelType: '' }, dlgVis: false, fm: { ruleCode: '', ruleName: '', modelType: '', description: '' } } },
  created() { this.pid = this.$route.params.id; getProject(this.pid).then(r => { this.project = r.data }); this.load() },
  methods: {
    async load() { this.loading = true; try { const r = await listDefinitions({ ...this.qp, projectId: this.pid }); this.list = r.data.records; this.total = r.data.total } finally { this.loading = false } },
    handleQuery() { this.qp.pageNum = 1; this.load() },
    mtl(t) { return { TABLE: '决策表', TREE: '决策树', FLOW: '决策流', CROSS: '交叉表', SCORE: '评分卡', CROSS_ADV: '复杂交叉表', SCORE_ADV: '复杂评分卡', SCRIPT: 'QL脚本' }[t] || t },
    go(r) { const m = { TABLE: 'table', TREE: 'tree', FLOW: 'flow', CROSS: 'cross', SCORE: 'score', CROSS_ADV: 'cross-adv', SCORE_ADV: 'score-adv', SCRIPT: 'script' }; this.$router.push('/designer/' + m[r.modelType] + '/' + r.id) },
    async pub(r) { await this.$confirm('确定发布?'); await request({ url: '/rule/definition/publish/' + r.id, method: 'post', data: {} }); this.$message.success('发布成功'); this.load() },
    async unpub(r) { await this.$confirm('确定下线?'); await request({ url: '/rule/definition/unpublish/' + r.id, method: 'post' }); this.$message.success('下线成功'); this.load() },
    async del(r) { await this.$confirm('确定删除?'); await deleteDefinition(r.id); this.$message.success('删除成功'); this.load() },
    submit() { this.$refs.f.validate(async v => { if (!v) return; await createDefinition({ ...this.fm, projectId: this.pid }); this.$message.success('创建成功'); this.dlgVis = false; this.load() }) }
  }
}
</script>
