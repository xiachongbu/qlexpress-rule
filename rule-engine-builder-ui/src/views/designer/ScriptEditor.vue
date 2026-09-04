<template>
  <div class="se-designer">
    <!-- 顶部工具栏 -->
    <div class="se-header">
      <div class="se-title-area">
        <i class="el-icon-edit-outline se-title-icon" />
        <span class="se-title">QL脚本编辑器</span>
        <el-tag size="small" type="info" style="margin-left:8px;">{{ lineCount }} 行</el-tag>
      </div>
      <div class="se-toolbar">
        <el-button size="small" icon="el-icon-document" @click="handleSave">保存</el-button>
        <design-version-switcher
          :definition-id="definitionId"
          :scope-comp-id="scopeCompId"
          @apply-model="onApplyDesignSnapshot"
        />
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">验证脚本</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <div v-loading="scopeContentLoading" class="se-body">
      <!-- 左侧变量面板 -->
      <div class="se-var-panel" :class="{ collapsed: varPanelCollapsed }">
        <div class="se-var-header" @click="varPanelCollapsed = !varPanelCollapsed">
          <span v-if="!varPanelCollapsed"><i class="el-icon-s-data" /> 项目变量</span>
          <i :class="varPanelCollapsed ? 'el-icon-arrow-right' : 'el-icon-arrow-left'" />
        </div>
        <div v-show="!varPanelCollapsed" class="se-var-list">
          <div v-if="loadingVars" style="text-align:center;padding:20px;color:#999;">
            <i class="el-icon-loading" /> 加载中...
          </div>
          <div v-else-if="varsLoadError" style="text-align:center;padding:20px;color:#F56C6C;">
            <i class="el-icon-warning" /> 加载失败
            <el-button type="text" size="small" style="display:block;margin:6px auto 0;" @click="loadProjectVars(definitionId)">重试</el-button>
          </div>
          <div v-else-if="varTree.length === 0" style="text-align:center;padding:20px;color:#bbb;">
            <i class="el-icon-folder-opened" /> 暂无项目变量
          </div>
          <template v-else>
            <!-- 搜索过滤 -->
            <div class="se-var-search">
              <el-input
                v-model="varSearchKey"
                size="small"
                placeholder="搜索变量..."
                prefix-icon="el-icon-search"
                clearable
              />
            </div>
            <!-- 树形分组 -->
            <div v-for="cat in filteredVarTree" :key="cat.key" class="se-cat">
              <div class="se-cat-header" @click="toggleCat(cat.key)">
                <i :class="expandedCats[cat.key] ? 'el-icon-caret-bottom' : 'el-icon-caret-right'" class="se-toggle-icon" />
                <i :class="cat.icon" class="se-cat-icon" />
                <span class="se-cat-label">{{ cat.label }}</span>
                <span class="se-cat-count">{{ countLeaves(cat) }}</span>
              </div>
              <div v-show="expandedCats[cat.key]" class="se-cat-body">
                <!-- 两级结构：一级分类 -> 直接叶子 -->
                <template v-if="!cat.hasSubGroups">
                  <div
                    v-for="v in cat.children"
                    :key="v.varCode"
                    class="se-var-item"
                    :title="'双击插入: ' + v.varCode"
                    @dblclick="insertVar(v.varCode)"
                  >
                    <el-tag :type="varTypeColor(v.varType)" size="small" class="var-type-tag">{{ varTypeLabel(v.varType) }}</el-tag>
                    <span class="var-code">{{ v.varCode }}</span>
                    <span class="var-label">{{ v.varLabel }}</span>
                  </div>
                </template>
                <!-- 三级结构：一级分类 -> 二级组 -> 叶子 -->
                <template v-else>
                  <div v-for="group in cat.children" :key="cat.key + '.' + group.key" class="se-group">
                    <div class="se-group-header" @click="toggleGroup(cat.key + '.' + group.key)">
                      <i :class="expandedGroups[cat.key + '.' + group.key] ? 'el-icon-caret-bottom' : 'el-icon-caret-right'" class="se-toggle-icon" />
                      <span class="se-group-label">{{ group.label }}</span>
                      <span class="se-cat-count">{{ group.children.length }}</span>
                    </div>
                    <div v-show="expandedGroups[cat.key + '.' + group.key]">
                      <div
                        v-for="v in group.children"
                        :key="v.varCode"
                        class="se-var-item se-var-indent"
                        :title="'双击插入: ' + v.varCode"
                        @dblclick="insertVar(v.varCode)"
                      >
                        <el-tag :type="varTypeColor(v.varType)" size="small" class="var-type-tag">{{ varTypeLabel(v.varType) }}</el-tag>
                        <span class="var-code">{{ v.varCode }}</span>
                        <span class="var-label">{{ v.varLabel }}</span>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 主编辑区 -->
      <div class="se-editor-area">
        <!-- 状态栏 -->
        <div class="se-statusbar">
          <span v-if="compileStatus === 1" class="se-status-item status-ok">
            <i class="el-icon-success" /> 脚本有效
          </span>
          <span v-else-if="compileStatus === 2" class="se-status-item status-err">
            <i class="el-icon-error" /> {{ compileMessage || '脚本错误' }}
          </span>
          <span v-else class="se-status-item">
            <i class="el-icon-info" /> 未验证
          </span>
          <span class="se-statusbar-spacer" />
          <span class="se-line-info">{{ lineCount }} 行 / {{ script.length }} 字符</span>
        </div>

        <!-- 查找 / 替换面板：ESC 在面板根节点统一拦截（stop 阻止冒泡），避免触发 el-drawer 的 ESC 关闭整个设计器抽屉 -->
        <div v-show="searchVisible" class="se-search-panel" @keydown.esc.stop.prevent="closeSearch">
          <div class="se-search-row">
            <el-input
              ref="searchInput"
              v-model="searchQuery"
              size="small"
              placeholder="查找"
              prefix-icon="el-icon-search"
              clearable
              class="se-search-input"
              @input="highlightAll"
              @keydown.enter.native.prevent="findNext(false)"
            />
            <el-button size="small" @click="findNext(false)">下一个</el-button>
            <el-button size="small" @click="highlightAll">全部高亮显示</el-button>
            <span class="se-search-spacer" />
            <el-checkbox v-model="showReplace" class="se-search-toggle">替换</el-checkbox>
            <i class="el-icon-close se-search-close" title="关闭 (Esc)" @click="closeSearch" />
          </div>
          <div v-show="showReplace" class="se-search-row">
            <el-input
              v-model="replaceQuery"
              size="small"
              placeholder="替换为"
              class="se-search-input"
              @keydown.enter.native.prevent="replaceCurrent"
            />
            <el-button size="small" @click="replaceCurrent">替换</el-button>
            <el-button size="small" @click="replaceAll">全部替换</el-button>
          </div>
        </div>

        <!-- 编辑器 -->
        <div ref="cmHost" class="se-editor-cm" />

        <!-- 底部提示 -->
        <div class="se-footer">
          <span class="se-footer-tip">
            <i class="el-icon-edit-outline" /> 直接编写 QLExpress 脚本，保存后即可用于规则执行；按 Ctrl+Alt+L 格式化，Ctrl+/ 注释，Ctrl+F 查找/替换，Ctrl+Z 撤销 / Ctrl+Shift+Z 重做
          </span>
        </div>
      </div>
    </div>

    <!-- 测试弹窗 -->
    <test-execute-dialog
      v-model:visible="testVisible"
      :fields="testFields"
      :params="testParams"
      v-model:paramsJson="testParamsJson"
      v-model:mode="testMode"
      :result="testResult"
      @execute="doTest"
    >
      <template #result">
        <div v-if="testResult">
        <el-alert
          :title="testResult.success ? '执行成功' : '执行失败'"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="margin-bottom:10px;"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="返回值">
            <strong style="font-size:16px;color:#1890ff;">{{ testResult.result }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ testResult.executeTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item v-if="testResult.errorMessage" label="错误">
            <span style="color:#F56C6C">{{ testResult.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
        </div>
      </template>
    </test-execute-dialog>

    <design-save-version-dialog ref="designSaveVersionDialog" />
  </div>
</template>

<script>
import {getContent, saveContent, validateScript} from '@/api/definition'
import varPickerMixin from '@/mixins/varPickerMixin'
import designerTestMixin from '@/mixins/designerTestMixin'
import TestExecuteDialog from '@/components/designer/TestExecuteDialog.vue'
import DesignSaveVersionDialog from '@/components/designer/DesignSaveVersionDialog.vue'
import DesignVersionSwitcher from '@/components/designer/DesignVersionSwitcher.vue'
import designerDefinitionIdMixin from '@/mixins/designerDefinitionIdMixin'
import designerScopeMixin from '@/mixins/designerScopeMixin'
import qlCodeEditorMixin from '@/mixins/qlCodeEditorMixin'

export default {
  name: 'ScriptEditor',
  components: { DesignSaveVersionDialog, DesignVersionSwitcher, TestExecuteDialog },
  mixins: [varPickerMixin, designerScopeMixin, designerDefinitionIdMixin, qlCodeEditorMixin, designerTestMixin],
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      script: '',
      compileStatus: 0,
      compileMessage: '',
      varPanelCollapsed: false,
      varSearchKey: '',
      expandedCats: {},
      expandedGroups: {}
    }
  },
  computed: {
    lineCount() {
      return (this.script.match(/\n/g) || []).length + 1
    },
    /** 将 varPickerOptions + projectFunctions 构建为三级树形结构 */
    varTree() {
      const tree = []
      const refs = this.varPickerOptions

      const standalone = refs.filter(v => v._ref && v._ref.category === 'standalone')
      if (standalone.length) {
        tree.push({ key: '__standalone__', label: '普通变量', icon: 'el-icon-s-data', hasSubGroups: false, children: standalone })
      }

      const constRefs = refs.filter(v => v._ref && v._ref.category === 'constant')
      if (constRefs.length) {
        const byGroup = {}
        constRefs.forEach(v => {
          const gc = v._ref.groupCode || '_ungrouped'
          const gl = v._ref.groupLabel || gc
          if (!byGroup[gc]) byGroup[gc] = { key: gc, label: gl, children: [] }
          byGroup[gc].children.push(v)
        })
        tree.push({ key: '__constant__', label: '常量', icon: 'el-icon-collection', hasSubGroups: true, children: Object.values(byGroup) })
      }

      const objRefs = refs.filter(v => v._ref && v._ref.category === 'object')
      if (objRefs.length) {
        const byObj = {}
        objRefs.forEach(v => {
          const oc = v._ref.objectCode || '_ungrouped'
          const ol = v._ref.objectLabel || oc
          if (!byObj[oc]) byObj[oc] = { key: oc, label: ol, children: [] }
          byObj[oc].children.push(v)
        })
        tree.push({ key: '__object__', label: '对象', icon: 'el-icon-files', hasSubGroups: true, children: Object.values(byObj) })
      }

      if (this.projectFunctions && this.projectFunctions.length) {
        tree.push({
          key: '__function__',
          label: '自定义函数',
          icon: 'el-icon-s-operation',
          hasSubGroups: false,
          children: this.projectFunctions.map(f => ({
            varCode: f.funcCode + '()',
            varLabel: f.funcName,
            varType: 'FUNC'
          }))
        })
      }

      return tree
    },
    /** 搜索过滤后的树 */
    filteredVarTree() {
      const kw = (this.varSearchKey || '').trim().toLowerCase()
      if (!kw) return this.varTree
      return this.varTree.map(cat => {
        if (!cat.hasSubGroups) {
          const filtered = cat.children.filter(v =>
            (v.varCode && v.varCode.toLowerCase().includes(kw)) ||
            (v.varLabel && v.varLabel.toLowerCase().includes(kw))
          )
          return filtered.length ? { ...cat, children: filtered } : null
        }
        const filteredGroups = cat.children.map(group => {
          const filtered = group.children.filter(v =>
            (v.varCode && v.varCode.toLowerCase().includes(kw)) ||
            (v.varLabel && v.varLabel.toLowerCase().includes(kw))
          )
          return filtered.length ? { ...group, children: filtered } : null
        }).filter(Boolean)
        return filteredGroups.length ? { ...cat, children: filteredGroups } : null
      }).filter(Boolean)
    }
  },
  watch: {
    varTree: {
      immediate: true,
      handler(tree) {
        if (!tree || !tree.length) return
        const cats = { ...this.expandedCats }
        const groups = { ...this.expandedGroups }
        tree.forEach(cat => {
          if (cats[cat.key] === undefined) cats[cat.key] = false
          if (cat.hasSubGroups) {
            cat.children.forEach(g => {
              const gk = cat.key + '.' + g.key
              if (groups[gk] === undefined) groups[gk] = false
            })
          }
        })
        this.expandedCats = cats
        this.expandedGroups = groups
      }
    },
    script(val) {
      // 将外部变更同步到编辑器，首次非空内容自动设为撤销基准点
      this.setCmValue(val)
    }
  },
  created() {
    this.definitionId = this.resolveDefinitionIdFromContext()
    ;(async() => {
      try {
        await this.bootstrapDesignerWithScope()
      } catch (e) {
        this.$message.error('加载失败: ' + (e.message || '未知错误'))
        this.contentLoaded = true
      }
    })()
  },
  mounted() {
    this.initCmEditor()
    // 若初始内容在编辑器创建前已加载完成，则直接以当前内容为撤销基准点
    if (this.contentLoaded) {
      this.resetCmHistoryBaseline()
    }
  },
  beforeUnmount() {
    this.destroyCmEditor()
  },
  methods: {
    toggleCat(key) {
      this.expandedCats[key] = !this.expandedCats[key]
    },
    toggleGroup(key) {
      this.expandedGroups[key] = !this.expandedGroups[key]
    },
    /** 统计一级分类下叶子节点总数 */
    countLeaves(cat) {
      if (!cat.hasSubGroups) return cat.children.length
      return cat.children.reduce((sum, g) => sum + g.children.length, 0)
    },
    async loadContent() {
      try {
        const res = await getContent(this.definitionId, this.scopeCompId)
        const content = res && res.data ? res.data : res
        if (content) {
          if (content.compiledScript) {
            this.script = content.compiledScript
          } else if (content.modelJson && content.modelJson !== '{}') {
            try {
              const model = JSON.parse(content.modelJson)
              if (model.script) this.script = model.script
            } catch (e) {
              this.script = content.modelJson
            }
          }
          this.compileStatus = content.compileStatus || 0
          this.compileMessage = content.compileMessage || ''
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.contentLoaded = true
      }
    },
    /**
     * 将快照中的 script 字段写回编辑器。
     */
    onApplyDesignSnapshot(parsed) {
      if (!parsed || typeof parsed !== 'object') return
      if (parsed.script != null) {
        this.script = String(parsed.script)
      }
    },

    /**
     * 带版本说明保存。
     * 后端对 SCRIPT 类型在同一事务内“校验 + 编译 + 持久化”一步到位：
     * 校验不通过则抛异常回滚（不落库），前端 catch 后拦截并提示。
     */
    async handleSave() {
      let changeLog = ''
      try {
        changeLog = await this.$refs.designSaveVersionDialog.prompt()
      } catch (e) {
        return
      }
      const modelJson = JSON.stringify({ script: this.script })
      try {
        // 响应拦截器对业务错误（code!==200）不会 reject，只会 resolve 返回 res，
        // 因此必须显式判断返回体的 code，不能仅依赖 await 是否抛异常。
        const res = await saveContent({
          definitionId: this.definitionId,
          scopeCompId: this.scopeCompId,
          modelJson,
          changeLog: changeLog || undefined,
          recordHistory: true
        })
        if (res && res.code === 200) {
          this.compileStatus = 1
          this.compileMessage = ''
          this.$message.success('保存成功')
        } else {
          // 后端校验失败（如脚本语法错误）已由拦截器弹出错误提示，这里仅同步状态栏
          this.compileStatus = 2
          this.compileMessage = (res && (res.message || res.msg)) || '脚本校验失败，请检查脚本'
        }
      } catch (e) {
        this.compileStatus = 2
        this.compileMessage = (e && e.message) || '脚本校验失败'
        this.$message.error(this.compileMessage)
      }
    },
    async handleCompile() {
      // 无副作用的语法校验（不持久化、不加版本）
      const res = await validateScript(this.definitionId, this.script)
      const result = res && res.data ? res.data : res
      if (result && result.success) {
        this.compileStatus = 1
        this.compileMessage = ''
        this.$message.success('脚本验证通过')
      } else {
        this.compileStatus = 2
        this.compileMessage = result && result.errorMessage ? result.errorMessage : '未知错误'
        this.$message.error('脚本验证失败: ' + this.compileMessage)
      }
    },
    /** 脚本文本作为扫描测试变量的设计源 */
    getTestSourceText() {
      return this.script || ''
    },
    /** 编辑器占位提示（覆写 mixin 默认值，保留双击插入提示） */
    cmPlaceholder() {
      return '// 在此编写 QLExpress 脚本\n// 双击左侧变量可快速插入\n\nresult = 0'
    },
    insertVar(code) {
      this.insertAtCursor(code)
    }
    // 注释/取消注释、格式化、查找/替换、编辑器初始化等能力均由 qlCodeEditorMixin 提供（与 ScriptPanel 共用）
  }
}
</script>

<style lang="scss" scoped>
$editor-bg: #1e1e2e;
$editor-text: #cdd6f4;
$editor-line-bg: #181825;
$editor-line-text: #585b70;
$editor-border: #313244;

.se-designer {
  background: #f3f3f3;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.se-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 12px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  flex-wrap: wrap;
  gap: 8px;
}
.se-title-area { display: flex; align-items: center; }
.se-title-icon { font-size: 18px; color: #722ed1; margin-right: 8px; }
.se-title { font-size: 16px; font-weight: bold; color: #282828; }
.se-toolbar { display: flex; align-items: center; gap: 6px; }

.se-body {
  flex: 1;
  display: flex;
  margin: 12px;
  gap: 0;
  min-height: 0;
}

/* 变量面板 */
.se-var-panel {
  width: 260px;
  min-width: 260px;
  background: #fff;
  border-radius: 6px 0 0 6px;
  border: 1px solid #e8e8e8;
  border-right: none;
  display: flex;
  flex-direction: column;
  transition: width 0.2s, min-width 0.2s;
  &.collapsed { width: 36px; min-width: 36px; }
}
.se-var-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
  font-weight: 600;
  color: #555;
  cursor: pointer;
  gap: 6px;
  i { color: #999; }
}
.se-var-list {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}
.se-var-search {
  padding: 6px 8px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
}

/* 一级分类 */
.se-cat-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 10px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: #333;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  user-select: none;
  &:hover { background: #f0f0f0; }
}
.se-toggle-icon { font-size: 12px; color: #999; width: 14px; text-align: center; flex-shrink: 0; }
.se-cat-icon { font-size: 13px; color: #8c8c8c; flex-shrink: 0; }
.se-cat-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.se-cat-count {
  flex-shrink: 0;
  font-size: 10px;
  color: #fff;
  background: #bfbfbf;
  border-radius: 8px;
  padding: 0 5px;
  line-height: 16px;
  font-weight: normal;
}
.se-cat-body { }

/* 二级分组 */
.se-group-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px 5px 22px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  color: #555;
  user-select: none;
  border-bottom: 1px solid #fafafa;
  &:hover { background: #f5f5f5; }
}
.se-group-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 叶子节点 */
.se-var-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 24px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.15s;
  &:hover { background: #e6f7ff; }
}
.se-var-indent { padding-left: 38px; }
.var-type-tag { flex-shrink: 0; }
.var-code { font-family: 'Consolas', monospace; color: #333; white-space: nowrap; }
.var-label { color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 编辑区 */
.se-editor-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-radius: 0 6px 6px 0;
  border: 1px solid #e8e8e8;
  overflow: hidden;
  position: relative;
}

/* 查找 / 替换面板 */
.se-search-panel {
  position: absolute;
  top: 34px;
  right: 14px;
  z-index: 20;
  background: #f3f3f3;
  border: 1px solid #c8c8c8;
  border-radius: 4px;
  padding: 6px 8px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
}
.se-search-row {
  display: flex;
  align-items: center;
  gap: 6px;
  & + .se-search-row { margin-top: 6px; }
}
.se-search-input { width: 220px; }
.se-search-spacer { flex: 1; min-width: 8px; }
.se-search-toggle { margin: 0 4px 0 0; }
.se-search-close {
  cursor: pointer;
  color: #909399;
  font-size: 14px;
  &:hover { color: #f56c6c; }
}

.se-statusbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #11111b;
  border-bottom: 1px solid $editor-border;
}
.se-status-item {
  font-size: 11px;
  color: #888;
  display: flex;
  align-items: center;
  gap: 3px;
  &.status-ok { color: #52c41a; }
  &.status-err { color: #ff6b6b; }
}
.se-statusbar-spacer { flex: 1; }
.se-line-info { font-size: 11px; color: #585b70; font-family: 'Consolas', monospace; }

.se-editor-cm {
  flex: 1;
  min-height: 400px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.se-editor-cm :deep(.CodeMirror ){
  height: 100%;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}
/* 同名变量高亮：光标点到某变量时，所有同名出现处高亮 */
.se-editor-cm :deep(.cm-matchhighlight ){
  background: rgba(102, 168, 255, 0.30);
  border-radius: 2px;
}
.se-editor-cm :deep(.CodeMirror-selection-highlight-scrollbar ){
  background: #66a8ff;
}
/* 查找匹配项高亮（全部高亮显示） */
.se-editor-cm :deep(.cm-search-highlight ){
  background: rgba(255, 170, 0, 0.40);
  border-radius: 2px;
}
/* 匹配的花括号/括号对：高亮加粗 + 背景 + 下划线，便于快速识别配对关系 */
.se-editor-cm :deep(.CodeMirror-matchingbracket ){
  color: #ffd166 !important;
  font-weight: 700;
  background: rgba(255, 209, 102, 0.28);
  border-bottom: 2px solid #ffd166;
  border-radius: 2px;
}
/* 未找到匹配的括号：红色告警 */
.se-editor-cm :deep(.CodeMirror-nonmatchingbracket ){
  color: #fff !important;
  font-weight: 700;
  background: rgba(255, 107, 107, 0.55);
  border-radius: 2px;
}

.se-footer {
  display: flex;
  align-items: center;
  padding: 5px 12px;
  background: #11111b;
  border-top: 1px solid $editor-border;
}
.se-footer-tip {
  font-size: 11px;
  color: #a6e3a1;
  display: flex;
  align-items: center;
  gap: 4px;
}

.test-hint { font-size: 12px; color: #909399; margin-bottom: 8px; }
.test-result { margin-top: 16px; }
</style>
