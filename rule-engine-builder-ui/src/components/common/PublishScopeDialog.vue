<template>
  <el-dialog
    title="选择发布作用域"
    :visible.sync="innerVisible"
    width="520px"
    append-to-body
    @closed="onClosed"
  >
    <p v-if="loading" class="pub-scope-tip">正在加载作用域与编译状态…</p>
    <p v-else-if="loadError" class="pub-scope-err">{{ loadError }}</p>
    <template v-else>
      <p class="pub-scope-hint">仅展示已编译成功的作用域，可勾选本次要发布上线的省份（或全国）。未编译成功的作用域请先在设计器中保存并编译。</p>
      <el-checkbox-group v-model="selectedNormIds" class="pub-scope-group">
        <div v-for="row in publishableRows" :key="row.normId" class="pub-scope-row">
          <el-checkbox :label="row.normId">{{ row.label }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <p v-if="publishableRows.length === 0" class="pub-scope-err">暂无可发布的作用域，请先在各作用域下编译成功后再发布。</p>
      <div v-if="skippedRows.length" class="pub-scope-skipped">
        <span class="pub-scope-skipped-title">未编译，本次不可选：</span>
        <span>{{ skippedSummary }}</span>
      </div>
    </template>
    <div slot="footer">
      <el-button size="small" @click="innerVisible = false">取消</el-button>
      <el-button
        size="small"
        type="primary"
        :loading="submitting"
        :disabled="loading || !!loadError || publishableRows.length === 0"
        @click="submit"
      >
        确定发布
      </el-button>
    </div>
  </el-dialog>
</template>

<script>
import {listContentScopes, publishRule} from '@/api/definition'

/**
 * 将作用域 ID 规范为与后端一致的字符串（空视为全国 0）。
 */
function normalizeScopeId(v) {
  if (v == null || v === '') return '0'
  const t = String(v).trim()
  return t === '' ? '0' : t
}

export default {
  name: 'PublishScopeDialog',
  props: {
    /** 是否显示弹窗 */
    visible: { type: Boolean, default: false },
    /** 规则定义 ID */
    definitionId: { type: Number, default: null },
    /**
     * 作用域下拉同源选项 { label, value }，用于展示省份名称。
     */
    compScopeOptions: { type: Array, default: () => [] }
  },
  data() {
    return {
      innerVisible: false,
      loading: false,
      submitting: false,
      loadError: '',
      allRows: [],
      selectedNormIds: []
    }
  },
  computed: {
    /**
     * 已编译成功、可勾选发布的作用域行。
     */
    publishableRows() {
      return this.allRows.filter(r => r.compileOk)
    },
    /**
     * 未编译成功的作用域行（仅展示说明）。
     */
    skippedRows() {
      return this.allRows.filter(r => !r.compileOk)
    },
    /**
     * 不可选作用域的简短文案。
     */
    skippedSummary() {
      return this.skippedRows.map(r => r.label).join('、')
    }
  },
  watch: {
    visible(v) {
      this.innerVisible = v
      if (v && this.definitionId != null) {
        this.openLoad()
      }
    },
    innerVisible(v) {
      this.$emit('update:visible', v)
    }
  },
  methods: {
    /**
     * 根据 compId 解析展示名称（字典优先）。
     */
    scopeLabel(normId) {
      const id = normalizeScopeId(normId)
      const opts = this.compScopeOptions || []
      const hit = opts.find(o => String(o.value) === id)
      if (hit) return hit.label
      return id === '0' ? '全国（默认）' : `作用域 ${id}`
    },
    /**
     * 打开弹窗时拉取全部作用域内容并默认全选可发布项。
     */
    async openLoad() {
      this.loading = true
      this.loadError = ''
      this.allRows = []
      this.selectedNormIds = []
      try {
        const res = await listContentScopes(this.definitionId)
        const list = (res && res.data) || []
        this.allRows = list.map(raw => {
          const normId = normalizeScopeId(raw.scopeCompId)
          const compileOk = raw.compileStatus != null && Number(raw.compileStatus) === 1
          return {
            normId,
            label: this.scopeLabel(normId),
            compileOk
          }
        })
        this.selectedNormIds = this.publishableRows.map(r => r.normId)
      } catch (e) {
        this.loadError = (e && e.message) || '加载失败'
      } finally {
        this.loading = false
      }
    },
    /**
     * 提交所选作用域并调用发布接口。
     */
    async submit() {
      if (!this.selectedNormIds.length) {
        this.$message.warning('请至少选择一个要发布的作用域')
        return
      }
      this.submitting = true
      try {
        await publishRule(this.definitionId, { scopeCompIds: this.selectedNormIds })
        this.$message.success('发布成功')
        this.innerVisible = false
        this.$emit('published')
      } catch (e) {
        /* request 拦截器已提示 */
      } finally {
        this.submitting = false
      }
    },
    /**
     * 关闭后清理错误状态，避免下次闪错。
     */
    onClosed() {
      this.loadError = ''
    }
  }
}
</script>

<style scoped>
.pub-scope-tip {
  margin: 0 0 8px;
  color: #909399;
  font-size: 13px;
}
.pub-scope-hint {
  margin: 0 0 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.pub-scope-err {
  margin: 0;
  color: #f56c6c;
  font-size: 13px;
}
.pub-scope-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}
.pub-scope-row {
  line-height: 1.5;
}
.pub-scope-skipped {
  margin-top: 12px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.pub-scope-skipped-title {
  color: #606266;
}
</style>
