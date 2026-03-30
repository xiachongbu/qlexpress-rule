<template>
  <div class="script-panel" :class="{ 'is-script-mode': isScriptMode, 'is-collapsed': !expanded }">

    <!-- ── 标题栏（始终可见） ── -->
    <div class="sp-header" @click="toggleExpand">
      <div class="sp-header-left">
        <i class="el-icon-s-promotion sp-icon" />
        <span class="sp-title">脚本预览 / 编辑</span>
        <el-tag :type="statusTag.type" size="mini" class="sp-status-tag">{{ statusTag.text }}</el-tag>
      </div>
      <div class="sp-header-right" @click.stop>
        <el-radio-group v-model="mode" size="mini" @change="onModeChange">
          <el-radio-button label="visual">
            <i class="el-icon-view" /> 可视化
          </el-radio-button>
          <el-radio-button label="script">
            <i class="el-icon-edit" /> 脚本模式
          </el-radio-button>
        </el-radio-group>
        <el-tooltip :content="expanded ? '收起脚本面板' : '展开脚本面板'" placement="top">
          <el-button size="mini" circle :icon="expanded ? 'el-icon-arrow-down' : 'el-icon-arrow-up'" class="sp-toggle-btn" @click.stop="toggleExpand" />
        </el-tooltip>
      </div>
    </div>

    <!-- ── 展开内容 ── -->
    <transition name="sp-slide">
      <div v-show="expanded" class="sp-body">

        <!-- 脚本模式警告横幅 -->
        <div v-if="isScriptMode" class="sp-script-warning">
          <i class="el-icon-warning" />
          <span>当前为<strong>脚本覆盖模式</strong>：下方脚本将直接用于执行，可视化配置不再生效。保存脚本后，「编译」操作将被跳过。</span>
          <el-button type="text" size="mini" @click="switchToVisual">退出脚本模式</el-button>
        </div>

        <!-- 状态栏 -->
        <div class="sp-statusbar">
          <span class="sp-statusbar-item" v-if="content.compileTime">
            <i class="el-icon-time" /> {{ formatTime(content.compileTime) }}
          </span>
          <span class="sp-statusbar-item sp-error" v-if="content.compileStatus === 2 && content.compileMessage">
            <i class="el-icon-close-circle" /> {{ content.compileMessage }}
          </span>
          <span class="sp-statusbar-item sp-manual" v-if="content.compileMessage && content.compileMessage.includes('手动')">
            <i class="el-icon-user" /> {{ content.compileMessage }}
          </span>
          <div class="sp-statusbar-spacer" />
          <el-button-group>
            <el-button size="mini" icon="el-icon-refresh" :loading="compiling" @click="handleCompile">
              {{ isScriptMode ? '验证脚本' : '编译并刷新' }}
            </el-button>
            <el-button size="mini" icon="el-icon-document-copy" @click="copyScript">复制</el-button>
          </el-button-group>
          <el-button
            v-if="isScriptMode"
            size="mini"
            type="primary"
            icon="el-icon-check"
            :loading="saving"
            @click="handleSaveScript"
          >保存脚本</el-button>
        </div>

        <!-- 脚本编辑器 -->
        <div class="sp-editor-container">
          <!-- 行号 -->
          <div class="sp-line-numbers" aria-hidden="true">
            <div v-for="n in lineCount" :key="n" class="sp-line-num">{{ n }}</div>
          </div>
          <!-- 编辑区 -->
          <textarea
            ref="editorRef"
            v-model="editScript"
            class="sp-editor"
            :class="{ readonly: !isScriptMode }"
            :readonly="!isScriptMode"
            :placeholder="isScriptMode
              ? '// 在此直接编写 QLExpress 脚本\n// 变量赋值示例：\ntaxRate = 0.09;\nresult = &quot;低税率&quot;;'
              : '请先点击「编译并刷新」生成脚本'"
            spellcheck="false"
            autocomplete="off"
            autocorrect="off"
            autocapitalize="off"
            @scroll="syncScroll"
          />
        </div>

        <!-- 底部提示 -->
        <div class="sp-footer">
          <span v-if="!isScriptMode" class="sp-footer-tip">
            <i class="el-icon-info" /> 预览模式下脚本只读。切换「脚本模式」可直接编辑 QLExpress 脚本。
          </span>
          <span v-else class="sp-footer-tip script-mode-tip">
            <i class="el-icon-edit-outline" /> 脚本模式：直接编写 QLExpress 语法，保存后立即生效，无需经过可视化编译。
          </span>
          <span class="sp-line-info">{{ lineCount }} 行 / {{ editScript.length }} 字符</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { getContent, compileRule, saveScript, updateScriptMode, validateScript } from '@/api/definition'

export default {
  name: 'ScriptPanel',
  props: {
    definitionId: { type: [String, Number], required: true },
    /** 父组件传入用于触发编译时保存 model */
    onBeforeCompile: { type: Function, default: null }
  },
  data() {
    return {
      expanded: false,
      mode: 'visual',          // 'visual' | 'script'
      content: {},             // RuleDefinitionContent
      editScript: '',
      compiling: false,
      saving: false
    }
  },
  created() {
    this.initMode()
  },
  computed: {
    isScriptMode() {
      return this.mode === 'script'
    },
    lineCount() {
      return (this.editScript.match(/\n/g) || []).length + 1
    },
    statusTag() {
      const s = this.content.compileStatus
      if (s === 1) return { type: 'success', text: '已编译' }
      if (s === 2) return { type: 'danger', text: '编译失败' }
      return { type: 'info', text: '未编译' }
    }
  },
  methods: {
    /** 初始化：从后端加载编辑模式 */
    async initMode() {
      try {
        const res = await getContent(this.definitionId)
        const content = (res && res.data ? res.data : res) || {}
        this.content = content
        if (content.scriptMode === 'script') {
          this.mode = 'script'
          this.expanded = true
          this.editScript = content.compiledScript || ''
          this.$emit('mode-change', 'script')
        } else if (content.compiledScript) {
          this.editScript = content.compiledScript
        }
      } catch (e) {
        this.content = {}
      }
    },

    toggleExpand() {
      this.expanded = !this.expanded
      if (this.expanded && !this.editScript) {
        this.loadContent()
      }
    },

    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        this.content = (res && res.data ? res.data : res) || {}
        if (!this.editScript) {
          this.editScript = this.content.compiledScript || ''
        }
      } catch (e) {
        this.content = {}
      }
    },

    async handleCompile() {
      if (this.isScriptMode) {
        return this.handleValidateScript()
      }
      if (this.onBeforeCompile) {
        try { await this.onBeforeCompile() } catch (e) { /* ignore */ }
      }
      this.compiling = true
      try {
        const res = await compileRule(this.definitionId)
        const result = res && res.data ? res.data : res
        if (result && result.success) {
          this.editScript = result.compiledScript || ''
          this.$message.success('编译成功')
          await this.loadContent()
        } else {
          this.$message.error('编译失败: ' + (result && result.errorMessage ? result.errorMessage : '未知错误'))
        }
      } finally {
        this.compiling = false
      }
    },

    /** 脚本模式下验证脚本：保存并语法检查，不会覆盖用户脚本 */
    async handleValidateScript() {
      if (!this.editScript.trim()) {
        this.$message.warning('脚本内容不能为空')
        return
      }
      this.compiling = true
      try {
        await saveScript(this.definitionId, this.editScript)
        const res = await validateScript(this.definitionId, this.editScript)
        const result = res && res.data ? res.data : res
        if (result && result.success) {
          this.$message.success('脚本语法验证通过，已保存')
        } else {
          this.$message.warning('脚本已保存，但语法检查发现问题: ' + (result && result.errorMessage ? result.errorMessage : '未知错误'))
        }
        await this.loadContent()
      } catch (e) {
        this.$message.error('验证失败: ' + (e.message || '未知错误'))
      } finally {
        this.compiling = false
      }
    },

    async handleSaveScript() {
      if (!this.editScript.trim()) {
        this.$message.warning('脚本内容不能为空')
        return
      }
      this.saving = true
      try {
        await saveScript(this.definitionId, this.editScript)
        this.$message.success('脚本已保存，若规则已发布则自动同步到客户端')
        await this.loadContent()
        this.$emit('script-saved', this.editScript)
      } finally {
        this.saving = false
      }
    },

    onModeChange(newMode) {
      if (newMode === 'script') {
        this.$confirm(
          '切换至脚本模式后，可直接编写 QLExpress 脚本。\n保存脚本后，可视化配置将不再生效，直到您重新编译。\n\n确认切换？',
          '切换到脚本模式',
          { type: 'warning', confirmButtonText: '确认切换', cancelButtonText: '取消' }
        ).then(() => {
          this.expanded = true
          if (!this.editScript) this.loadContent()
          this.$emit('mode-change', 'script')
        }).catch(() => {
          this.mode = 'visual'
        })
      } else {
        this.$emit('mode-change', 'visual')
      }
    },

    switchToVisual() {
      this.mode = 'visual'
      this.$emit('mode-change', 'visual')
      updateScriptMode(this.definitionId, 'visual').catch(() => {})
    },

    copyScript() {
      if (!this.editScript) {
        this.$message.warning('暂无脚本，请先编译')
        return
      }
      if (navigator.clipboard) {
        navigator.clipboard.writeText(this.editScript).then(() => {
          this.$message.success('脚本已复制到剪贴板')
        })
      } else {
        const el = this.$refs.editorRef
        el.select()
        document.execCommand('copy')
        this.$message.success('脚本已复制')
      }
    },

    syncScroll(e) {
      const lineNums = this.$el.querySelector('.sp-line-numbers')
      if (lineNums) lineNums.scrollTop = e.target.scrollTop
    },

    formatTime(dt) {
      if (!dt) return ''
      const d = typeof dt === 'string' ? new Date(dt) : dt
      return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
    },

    /** 供父组件主动刷新脚本（如编译后）*/
    refresh() {
      this.loadContent()
    }
  }
}
</script>

<style lang="scss" scoped>
$editor-bg: #1e1e2e;
$editor-text: #cdd6f4;
$editor-line-bg: #181825;
$editor-line-text: #585b70;
$editor-border: #313244;
$warning-bg: #fffbe6;

.script-panel {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
  margin-top: 16px;
  transition: border-color 0.2s;

  &.is-script-mode {
    border-color: #f5222d;
    box-shadow: 0 0 0 2px rgba(245, 34, 45, 0.1);
  }
}

/* ── 标题栏 ── */
.sp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #fafafa;
  border-bottom: 1px solid transparent;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  &:hover { background: #f0f0f0; }

  .is-script-mode & {
    background: #fff1f0;
    border-bottom-color: #ffa39e;
  }
}
.sp-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sp-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: default;
}
.sp-icon {
  font-size: 16px;
  color: #1890ff;
  .is-script-mode & { color: #f5222d; }
}
.sp-title {
  font-size: 13px;
  font-weight: bold;
  color: #333;
}
.sp-status-tag {
  font-size: 11px;
}
.sp-mode-badge {
  font-size: 11px;
  animation: pulse 2s infinite;
}
.sp-toggle-btn {
  width: 22px !important;
  height: 22px !important;
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
  color: #999 !important;
  &:hover { color: #333 !important; }
}

/* ── 展开体 ── */
.sp-body {
  background: $editor-bg;
}

/* 脚本模式警告 */
.sp-script-warning {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: $warning-bg;
  border-bottom: 1px solid #ffe58f;
  font-size: 12px;
  color: #d48806;
  i { font-size: 14px; color: #faad14; }
  strong { color: #d46b08; }
  .el-button { margin-left: auto; color: #1890ff; }
}

/* 状态栏 */
.sp-statusbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #11111b;
  border-bottom: 1px solid $editor-border;
  flex-wrap: wrap;
}
.sp-statusbar-item {
  font-size: 11px;
  color: #888;
  display: flex;
  align-items: center;
  gap: 3px;
  i { font-size: 12px; }
  &.sp-error { color: #ff6b6b; }
  &.sp-manual { color: #ffd93d; }
}
.sp-statusbar-spacer { flex: 1; }

/* 编辑器容器 */
.sp-editor-container {
  display: flex;
  min-height: 200px;
  max-height: 420px;
  overflow: hidden;
}

/* 行号 */
.sp-line-numbers {
  padding: 12px 8px 12px 12px;
  background: $editor-line-bg;
  border-right: 1px solid $editor-border;
  overflow: hidden;
  flex-shrink: 0;
  min-width: 42px;
  text-align: right;
}
.sp-line-num {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: $editor-line-text;
  user-select: none;
}

/* 脚本文本域 */
.sp-editor {
  flex: 1;
  padding: 12px 16px;
  background: $editor-bg;
  color: $editor-text;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  border: none;
  outline: none;
  resize: none;
  width: 100%;
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
  tab-size: 2;
  white-space: pre;
  overflow-wrap: normal;
  overflow-x: auto;

  &::placeholder {
    color: #45475a;
    font-style: italic;
  }
  &.readonly {
    cursor: default;
    color: #a6adc8;
  }
  &:not(.readonly) {
    caret-color: #89dceb;
    &:focus { background: #1a1a2e; }
  }
}

/* 底部信息栏 */
.sp-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  background: #11111b;
  border-top: 1px solid $editor-border;
}
.sp-footer-tip {
  font-size: 11px;
  color: #585b70;
  display: flex;
  align-items: center;
  gap: 4px;
  i { font-size: 12px; }
  &.script-mode-tip { color: #a6e3a1; }
}
.sp-line-info {
  font-size: 11px;
  color: #585b70;
  font-family: 'Consolas', monospace;
}

/* 收起/展开动画 */
.sp-slide-enter-active,
.sp-slide-leave-active {
  transition: max-height 0.25s ease;
  overflow: hidden;
}
.sp-slide-enter,
.sp-slide-leave-to {
  max-height: 0;
}
.sp-slide-enter-to,
.sp-slide-leave {
  max-height: 600px;
}

/* 脚本覆盖脉冲动画 */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* 全局 el-radio-button 在深色背景下样式微调 */
.sp-header-right ::v-deep .el-radio-button__inner {
  padding: 4px 10px;
  font-size: 12px;
}
</style>
