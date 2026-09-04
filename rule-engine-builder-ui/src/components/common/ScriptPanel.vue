<template>
  <div class="script-panel" :class="{ 'is-script-mode': isScriptMode, 'is-collapsed': !expanded, 'sp-fullscreen': isScriptMode && expanded }">

    <!-- ── 标题栏（始终可见） ── -->
    <div class="sp-header" @click="toggleExpand">
      <div class="sp-header-left">
        <i class="el-icon-s-promotion sp-icon" />
        <span class="sp-title">脚本预览 / 编辑</span>
        <el-tag :type="statusTag.type" size="small" class="sp-status-tag">{{ statusTag.text }}</el-tag>
      </div>
      <div class="sp-header-right" @click.stop>
        <el-radio-group v-model="mode" size="small" @change="onModeChange">
          <el-radio-button label="visual">
            <i class="el-icon-view" /> 可视化
          </el-radio-button>
          <el-radio-button label="script">
            <i class="el-icon-edit" /> 脚本模式
          </el-radio-button>
        </el-radio-group>
        <el-tooltip :content="expanded ? '收起脚本面板' : '展开脚本面板'" placement="top">
          <el-button size="small" circle :icon="expanded ? 'el-icon-arrow-down' : 'el-icon-arrow-up'" class="sp-toggle-btn" @click.stop="toggleExpand" />
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
          <el-button type="text" size="small" @click="switchToVisual">退出脚本模式</el-button>
        </div>

        <!-- 状态栏 -->
        <div class="sp-statusbar">
          <span v-if="content.compileTime" class="sp-statusbar-item">
            <i class="el-icon-time" /> {{ formatTime(content.compileTime) }}
          </span>
          <span v-if="content.compileStatus === 2 && content.compileMessage" class="sp-statusbar-item sp-error">
            <i class="el-icon-close-circle" /> {{ content.compileMessage }}
          </span>
          <span v-if="content.compileMessage && content.compileMessage.includes('手动')" class="sp-statusbar-item sp-manual">
            <i class="el-icon-user" /> {{ content.compileMessage }}
          </span>
          <div class="sp-statusbar-spacer" />
          <el-button-group>
            <el-button size="small" icon="el-icon-magic-stick" title="格式化 (Ctrl+Alt+L)" @click="formatScript">格式化</el-button>
            <el-button size="small" icon="el-icon-chat-line-square" title="注释/取消注释 (Ctrl+/)" @click="toggleComment">注释</el-button>
            <el-button size="small" icon="el-icon-search" title="查找/替换 (Ctrl+F)" @click="openSearch">查找</el-button>
          </el-button-group>
          <el-button-group>
            <el-button size="small" icon="el-icon-refresh" :loading="compiling" @click="handleCompile">
              {{ isScriptMode ? '验证脚本' : '编译并刷新' }}
            </el-button>
            <el-button size="small" icon="el-icon-document-copy" @click="copyScript">复制</el-button>
          </el-button-group>
          <el-button
            v-if="isScriptMode"
            size="small"
            type="primary"
            icon="el-icon-check"
            :loading="saving"
            @click="handleSaveScript"
          >保存脚本</el-button>
        </div>

        <!-- 查找 / 替换面板：ESC 在面板根节点统一拦截（stop 阻止冒泡），避免触发 el-drawer 的 ESC 关闭整个设计器抽屉 -->
        <div v-show="searchVisible" class="sp-search-panel" @keydown.esc.stop.prevent="closeSearch">
          <div class="sp-search-row">
            <el-input
              ref="searchInput"
              v-model="searchQuery"
              size="small"
              placeholder="查找"
              prefix-icon="el-icon-search"
              clearable
              class="sp-search-input"
              @input="highlightAll"
              @keydown.enter.native.prevent="findNext(false)"
            />
            <el-button size="small" @click="findNext(false)">下一个</el-button>
            <el-button size="small" @click="highlightAll">全部高亮</el-button>
            <span class="sp-search-spacer" />
            <el-checkbox v-model="showReplace" class="sp-search-toggle">替换</el-checkbox>
            <i class="el-icon-close sp-search-close" title="关闭 (Esc)" @click="closeSearch" />
          </div>
          <div v-show="showReplace" class="sp-search-row">
            <el-input
              v-model="replaceQuery"
              size="small"
              placeholder="替换为"
              class="sp-search-input"
              @keydown.enter.native.prevent="replaceCurrent"
            />
            <el-button size="small" @click="replaceCurrent">替换</el-button>
            <el-button size="small" @click="replaceAll">全部替换</el-button>
          </div>
        </div>

        <!-- 脚本编辑器（CodeMirror） -->
        <div ref="cmHost" class="sp-editor-cm" />

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
import { compileRule, getContent, saveScript, updateScriptMode, validateScript } from '@/api/definition'
import qlCodeEditorMixin from '@/mixins/qlCodeEditorMixin'

export default {
  name: 'ScriptPanel',
  mixins: [qlCodeEditorMixin],
  props: {
    definitionId: { type: [String, Number], required: true },
    /** 与后端 scope_comp_id 一致，默认 0 通用 */
    scopeCompId: { type: String, default: '0' },
    /** 父组件传入用于触发编译时保存 model */
    onBeforeCompile: { type: Function, default: null }
  },
  data() {
    return {
      expanded: false,
      mode: 'visual', // 'visual' | 'script'
      content: {}, // RuleDefinitionContent
      editScript: '',
      compiling: false,
      saving: false
    }
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
  watch: {
    // 展开面板时初始化/刷新编辑器（CodeMirror 在隐藏容器中创建会尺寸异常，需展开后 refresh）
    expanded(val) {
      if (val) {
        this.$nextTick(() => this.ensureEditor())
      }
    },
    // 切换可视化/脚本模式时同步只读态并刷新布局
    isScriptMode(val) {
      if (this.cm) {
        this.cm.setOption('readOnly', !val)
        this.$nextTick(() => this.cm && this.cm.refresh())
      }
    },
    // 外部变更（加载/编译结果）同步到编辑器；setCmValue 内部已防循环/防光标跳动
    editScript(val) {
      this.setCmValue(val)
    }
  },
  created() {
    this.initMode()
  },
  mounted() {
    if (this.expanded) {
      this.$nextTick(() => this.ensureEditor())
    }
  },
  methods: {
    // ── qlCodeEditorMixin 钩子覆写：使编辑器绑定到 editScript ──
    cmInitialValue() {
      return this.editScript || ''
    },
    cmOnChange(val) {
      if (val !== this.editScript) this.editScript = val
    },
    cmReadOnly() {
      return !this.isScriptMode
    },
    cmPlaceholder() {
      return this.isScriptMode
        ? '// 在此直接编写 QLExpress 脚本\n// 变量赋值示例：\ntaxRate = 0.09;\nresult = "低税率";'
        : '请先点击「编译并刷新」生成脚本'
    },
    /** 延迟创建编辑器（首次展开时）并同步当前脚本与尺寸 */
    ensureEditor() {
      if (!this.$refs.cmHost) return
      if (!this.cm) {
        this.initCmEditor()
      }
      if (this.cm) {
        this.setCmValue(this.editScript)
        this.$nextTick(() => this.cm && this.cm.refresh())
      }
    },
    /** 初始化：从后端加载编辑模式 */
    /**
     * 切换作用域后重置脚本面板状态并重新拉取。
     * 撤销基准点必须在内容加载完成后再重置：
     * 若在清空（editScript=''）时就设基准，随后异步加载出的脚本会成为一步可撤销操作，
     * 导致 Ctrl+Z 把已加载脚本撤销回空白。
     */
    async reloadForScope() {
      this.mode = 'visual'
      this.content = {}
      this.historyBaselineSet = false
      this.editScript = ''
      await this.initMode()
      if (this.cm) {
        this.setCmValue(this.editScript)
        this.resetCmHistoryBaseline()
      }
    },

    async initMode() {
      try {
        const res = await getContent(this.definitionId, this.scopeCompId)
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

    /**
     * 拉取最新内容。force 仅在预览（可视化）模式下强制用最新编译脚本覆盖显示，
     * 脚本覆盖模式下始终不覆盖用户手写内容。
     */
    async loadContent(force = false) {
      try {
        const res = await getContent(this.definitionId, this.scopeCompId)
        this.content = (res && res.data ? res.data : res) || {}
        if ((force && !this.isScriptMode) || !this.editScript) {
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
        const res = await compileRule(this.definitionId, this.scopeCompId)
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

    /** 脚本模式下验证脚本：纯语法校验（不持久化），与脚本设计器行为一致 */
    async handleValidateScript() {
      if (!this.editScript.trim()) {
        this.$message.warning('脚本内容不能为空')
        return
      }
      this.compiling = true
      try {
        const res = await validateScript(this.definitionId, this.editScript)
        const result = res && res.data ? res.data : res
        if (result && result.success) {
          this.$message.success('脚本验证通过')
        } else {
          this.$message.error('脚本验证失败: ' + (result && result.errorMessage ? result.errorMessage : '未知错误'))
        }
      } catch (e) {
        this.$message.error('验证失败: ' + (e.message || '未知错误'))
      } finally {
        this.compiling = false
      }
    },

    /**
     * 保存脚本。后端在同一事务内“校验 + 持久化”，校验失败回滚不落库；
     * 统一拦截器对业务错误（code!==200）不 reject，须显式判断返回码。
     * 保存成功即已编译（compileStatus=1），可直接发布，无需再编译。
     */
    async handleSaveScript() {
      if (!this.editScript.trim()) {
        this.$message.warning('脚本内容不能为空')
        return
      }
      this.saving = true
      try {
        const res = await saveScript(this.definitionId, this.editScript, this.scopeCompId)
        if (res && res.code === 200) {
          this.$message.success('保存成功，脚本已生效，可直接「发布」')
          await this.loadContent()
          this.$emit('script-saved', this.editScript)
        }
        // 校验失败时后端已回滚不落库，错误信息由统一拦截器弹出；不刷新内容以保留用户当前编辑
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
      updateScriptMode(this.definitionId, 'visual', this.scopeCompId).catch(() => {})
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
        const ta = document.createElement('textarea')
        ta.value = this.editScript
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
        this.$message.success('脚本已复制')
      }
    },

    formatTime(dt) {
      if (!dt) return ''
      const d = typeof dt === 'string' ? new Date(dt) : dt
      return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
    },

    /** 供父组件主动刷新脚本（如编译后）：预览模式强制同步最新编译产物 */
    refresh() {
      this.loadContent(true)
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
  position: relative;
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

/* 查找 / 替换面板 */
.sp-search-panel {
  position: absolute;
  top: 6px;
  right: 14px;
  z-index: 40;
  background: #f3f3f3;
  border: 1px solid #c8c8c8;
  border-radius: 4px;
  padding: 6px 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
}
.sp-search-row {
  display: flex;
  align-items: center;
  gap: 6px;
  & + .sp-search-row { margin-top: 6px; }
}
.sp-search-input { width: 200px; }
.sp-search-spacer { flex: 1; min-width: 8px; }
.sp-search-toggle { margin: 0 4px 0 0; }
.sp-search-close {
  cursor: pointer;
  color: #909399;
  font-size: 14px;
  &:hover { color: #f56c6c; }
}

/* 脚本编辑器（CodeMirror） */
.sp-editor-cm {
  position: relative;
  min-height: 220px;
  overflow: hidden;
}
.sp-editor-cm :deep(.CodeMirror ){
  height: 320px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
.sp-editor-cm :deep(.cm-matchhighlight ){
  background: rgba(102, 168, 255, 0.30);
  border-radius: 2px;
}
.sp-editor-cm :deep(.cm-search-highlight ){
  background: rgba(255, 170, 0, 0.40);
  border-radius: 2px;
}
.sp-editor-cm :deep(.CodeMirror-matchingbracket ){
  color: #ffd166 !important;
  font-weight: 700;
  background: rgba(255, 209, 102, 0.28);
  border-bottom: 2px solid #ffd166;
  border-radius: 2px;
}
.sp-editor-cm :deep(.CodeMirror-nonmatchingbracket ){
  color: #fff !important;
  font-weight: 700;
  background: rgba(255, 107, 107, 0.55);
  border-radius: 2px;
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

/* ── 脚本模式全屏覆盖：填满设计器根容器（需父容器 position:relative），编辑区 flex 撑满并内部滚动 ── */
.script-panel.sp-fullscreen {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  margin-top: 0;
  border-radius: 0;
  display: flex;
  flex-direction: column;
}
.script-panel.sp-fullscreen .sp-body {
  flex: 1 1 auto;
  min-height: 0;
  max-height: none !important;
  display: flex;
  flex-direction: column;
}
.script-panel.sp-fullscreen .sp-editor-cm {
  flex: 1 1 auto;
  min-height: 0;
}
.script-panel.sp-fullscreen .sp-editor-cm :deep(.CodeMirror ){
  height: 100%;
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
.sp-header-right :deep(.el-radio-button__inner ){
  padding: 4px 10px;
  font-size: 12px;
}
</style>
