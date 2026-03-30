<template>
  <div class="uiue-list-page">
    <div class="test-layout">
      <!-- 左侧：选择规则 + 参数输入 -->
      <div class="test-left">
        <div class="uiue-card">
          <div class="uiue-card-title">选择规则</div>
          <el-form size="small" label-width="80px">
            <el-form-item label="项目">
              <el-select v-model="selectedProjectId" placeholder="请选择项目" clearable style="width: 100%;" @change="onProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="规则">
              <el-select v-model="selectedRuleId" placeholder="请先选择项目" :disabled="!selectedProjectId" style="width: 100%;" filterable @change="onRuleChange">
                <el-option v-for="r in rules" :key="r.id" :label="r.ruleName + ' (' + r.ruleCode + ')'" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-form>
          <div v-if="selectedRule" class="rule-info">
            <el-descriptions :column="2" size="mini" border>
              <el-descriptions-item label="规则编码">{{ selectedRule.ruleCode }}</el-descriptions-item>
              <el-descriptions-item label="模型类型">
                <el-tag size="mini">{{ mtl(selectedRule.modelType) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="当前版本">v{{ selectedRule.currentVersion }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="{ 0: 'info', 1: 'success', 2: 'warning' }[selectedRule.status]" size="mini">
                  {{ ['草稿', '已发布', '已下线'][selectedRule.status] }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>

        <div class="uiue-card" style="margin-top: 12px;">
          <div class="uiue-card-title">
            输入参数
            <el-button type="text" size="small" style="margin-left: 12px;" @click="loadVariables" v-if="selectedProjectId">
              <i class="el-icon-refresh" /> 加载项目变量
            </el-button>
            <el-button type="text" size="small" style="margin-left: 8px;" @click="addParam">
              <i class="el-icon-plus" /> 手动添加
            </el-button>
          </div>
          <div v-if="params.length === 0" style="color: #999; padding: 12px 0; text-align: center;">
            请选择项目后加载变量，或手动添加参数
          </div>
          <el-form v-else size="small" label-width="0">
            <div v-for="(p, idx) in params" :key="idx" class="param-row">
              <el-input v-model="p.key" placeholder="参数名" class="param-key" :disabled="p.fromVar" />
              <span class="param-label" v-if="p.label">({{ p.label }})</span>
              <template v-if="p.type === 'BOOLEAN'">
                <el-select v-model="p.value" class="param-value" placeholder="选择">
                  <el-option label="true" value="true" />
                  <el-option label="false" value="false" />
                </el-select>
              </template>
              <template v-else-if="p.type === 'ENUM' && p.options && p.options.length > 0">
                <el-select v-model="p.value" class="param-value" placeholder="选择枚举值" clearable filterable>
                  <el-option v-for="opt in p.options" :key="opt.optionValue" :label="opt.optionLabel + ' (' + opt.optionValue + ')'" :value="opt.optionValue" />
                </el-select>
              </template>
              <template v-else>
                <el-input v-model="p.value" :placeholder="p.example || '参数值'" class="param-value" />
              </template>
              <el-button type="text" size="small" style="color: #F56C6C; margin-left: 4px;" @click="params.splice(idx, 1)">
                <i class="el-icon-delete" />
              </el-button>
            </div>
          </el-form>
        </div>

        <div style="margin-top: 16px; text-align: center;">
          <el-button type="primary" :loading="executing" :disabled="!selectedRuleId" @click="handleExecute">
            <i class="el-icon-video-play" /> 执行测试
          </el-button>
          <el-button @click="handleClear">清空</el-button>
        </div>
      </div>

      <!-- 右侧：执行结果 -->
      <div class="test-right">
        <div class="uiue-card" style="height: 100%;">
          <div class="uiue-card-title">执行结果</div>
          <div v-if="!result && !executing" class="result-empty">
            <i class="el-icon-video-play" style="font-size: 48px; color: #ddd;" />
            <p style="color: #999; margin-top: 12px;">点击「执行测试」查看结果</p>
          </div>
          <div v-else-if="executing" style="text-align: center; padding: 60px 0;">
            <i class="el-icon-loading" style="font-size: 32px; color: #B30000;" />
            <p style="color: #999; margin-top: 12px;">规则执行中...</p>
          </div>
          <div v-else>
            <el-alert
              :title="result.success ? '执行成功' : '执行失败'"
              :type="result.success ? 'success' : 'error'"
              :closable="false"
              show-icon
              style="margin-bottom: 16px;"
            >
              <span>耗时 {{ result.executeTimeMs }} ms</span>
            </el-alert>

            <div v-if="result.errorMessage" style="margin-bottom: 16px;">
              <div class="result-section-title" style="color: #F56C6C;">错误信息</div>
              <pre class="result-pre" style="background: #fff2f2; border-color: #fde2e2;">{{ result.errorMessage }}</pre>
            </div>

            <div style="margin-bottom: 16px;">
              <div class="result-section-title">返回结果</div>
              <pre class="result-pre">{{ formatJson(result.result) }}</pre>
            </div>

            <div v-if="result.traces && result.traces.length > 0">
              <div class="result-section-title">执行追踪</div>
              <el-collapse>
                <el-collapse-item v-for="(trace, idx) in result.traces" :key="idx" :title="'步骤 ' + (idx + 1)">
                  <pre class="result-pre">{{ formatJson(trace) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { listProjects } from '@/api/project'
import { listDefinitions, executeRule } from '@/api/definition'
import { listVariablesByProject, getVariableOptions } from '@/api/variable'

export default {
  name: 'RuleTest',
  data() {
    return {
      projects: [],
      rules: [],
      selectedProjectId: null,
      selectedRuleId: null,
      selectedRule: null,
      params: [],
      executing: false,
      result: null
    }
  },
  created() {
    this.loadProjects()
  },
  methods: {
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 1000 })
        this.projects = res.data.records || []
      } catch (e) { /* ignore */ }
    },
    async onProjectChange() {
      this.selectedRuleId = null
      this.selectedRule = null
      this.rules = []
      this.params = []
      this.result = null
      if (!this.selectedProjectId) return
      try {
        const res = await listDefinitions({ pageNum: 1, pageSize: 1000, projectId: this.selectedProjectId })
        this.rules = res.data.records || []
      } catch (e) { /* ignore */ }
    },
    onRuleChange() {
      this.selectedRule = this.rules.find(r => r.id === this.selectedRuleId) || null
      this.result = null
    },
    async loadVariables() {
      if (!this.selectedProjectId) return
      try {
        const res = await listVariablesByProject(this.selectedProjectId)
        const vars = res.data || []
        const existingKeys = new Set(this.params.map(p => p.key))
        for (const v of vars) {
          if (existingKeys.has(v.varCode)) continue
          const param = {
            key: v.varCode,
            label: v.varLabel,
            value: v.defaultValue || '',
            type: v.varType,
            example: v.exampleValue,
            fromVar: true,
            options: []
          }
          if (v.varType === 'ENUM') {
            try {
              const optRes = await getVariableOptions(v.id)
              param.options = optRes.data || []
            } catch (e) { /* ignore */ }
          }
          this.params.push(param)
        }
        if (vars.length === 0) {
          this.$message.info('该项目暂无变量定义')
        }
      } catch (e) {
        this.$message.error('加载变量失败')
      }
    },
    addParam() {
      this.params.push({ key: '', label: '', value: '', type: 'STRING', example: '', fromVar: false, options: [] })
    },
    async handleExecute() {
      if (!this.selectedRuleId) return
      const paramMap = {}
      for (const p of this.params) {
        if (!p.key) continue
        let val = p.value
        if (p.type === 'NUMBER' && val !== '' && val !== null) {
          val = Number(val)
        } else if (p.type === 'BOOLEAN') {
          val = val === 'true'
        }
        paramMap[p.key] = val
      }
      this.executing = true
      this.result = null
      try {
        const res = await executeRule({ definitionId: this.selectedRuleId, params: paramMap })
        this.result = res.data
      } catch (e) {
        this.result = { success: false, errorMessage: e.message || '执行异常', executeTimeMs: 0 }
      } finally {
        this.executing = false
      }
    },
    handleClear() {
      this.params = []
      this.result = null
    },
    mtl(t) {
      return { TABLE: '决策表', TREE: '决策树', FLOW: '决策流', CROSS: '交叉表', SCORE: '评分卡', CROSS_ADV: '复杂交叉表', SCORE_ADV: '复杂评分卡', SCRIPT: 'QL脚本' }[t] || t
    },
    formatJson(obj) {
      if (obj === null || obj === undefined) return '(空)'
      try {
        if (typeof obj === 'string') {
          return JSON.stringify(JSON.parse(obj), null, 2)
        }
        return JSON.stringify(obj, null, 2)
      } catch (e) {
        return String(obj)
      }
    }
  }
}
</script>
<style lang="scss" scoped>
.test-layout {
  display: flex;
  gap: 16px;
  min-height: calc(100vh - 140px);
}
.test-left {
  flex: 0 0 480px;
  min-width: 380px;
}
.test-right {
  flex: 1;
  min-width: 0;
}
.rule-info {
  margin-top: 12px;
}
.param-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.param-key {
  flex: 0 0 140px;
  margin-right: 8px;
}
.param-label {
  flex: 0 0 auto;
  color: #999;
  font-size: 12px;
  margin-right: 8px;
  white-space: nowrap;
}
.param-value {
  flex: 1;
  min-width: 0;
}
.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
.result-section-title {
  font-weight: bold;
  font-size: 13px;
  margin-bottom: 8px;
  color: #282828;
}
.result-pre {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.5;
  overflow: auto;
  max-height: 300px;
  white-space: pre-wrap;
  word-break: break-all;
}
@media screen and (max-width: 1000px) {
  .test-layout {
    flex-direction: column;
  }
  .test-left {
    flex: none;
    min-width: 0;
  }
}
</style>
