import CodeMirror from 'codemirror'
import 'codemirror/lib/codemirror.css'
import 'codemirror/theme/darcula.css'
import 'codemirror/mode/clike/clike'
import 'codemirror/addon/edit/matchbrackets'
import 'codemirror/addon/edit/closebrackets'
import 'codemirror/addon/comment/comment'
import 'codemirror/addon/display/placeholder'
import 'codemirror/addon/search/searchcursor'
import 'codemirror/addon/search/match-highlighter'

/**
 * QLExpress 类 IDE 代码编辑器公共能力（基于 CodeMirror 5）。
 * <p>供 ScriptEditor.vue（独立脚本设计器）与 ScriptPanel.vue（可视化设计器的脚本模式）共用，
 * 统一提供：语法高亮、行号、括号匹配、撤销重做、代码格式化、注释切换、查找/替换面板。</p>
 *
 * 宿主组件约定：
 * - 模板中提供 `ref="cmHost"` 的容器元素承载编辑器；
 * - 需要查找/替换面板时，模板中提供 `ref="searchInput"` 的输入框（可选）；
 * - 可覆写 `cmInitialValue()` 返回初始脚本文本（默认读取 this.script）；
 * - 可覆写 `cmOnChange(val)` 处理内容变化（默认写回 this.script）；
 * - 可覆写 `cmReadOnly()` 返回 true 使编辑器只读；
 * - 可覆写 `cmPlaceholder()` 自定义占位提示；
 * - 可覆写 `cmExtraKeys()` 返回额外快捷键（会与默认快捷键合并）。
 */
export default {
  data() {
    return {
      // 查找 / 替换面板状态
      searchVisible: false,
      searchQuery: '',
      replaceQuery: '',
      showReplace: false,
      // 是否已将初始加载的脚本设为撤销基准点（避免撤销到空白）
      historyBaselineSet: false
    }
  },
  beforeDestroy() {
    this.destroyCmEditor()
  },
  methods: {
    /** 宿主可覆写：编辑器初始内容 */
    cmInitialValue() {
      return this.script || ''
    },
    /** 宿主可覆写：内容变化回调 */
    cmOnChange(val) {
      if (val !== this.script) this.script = val
    },
    /** 宿主可覆写：是否只读 */
    cmReadOnly() {
      return false
    },
    /** 宿主可覆写：占位提示 */
    cmPlaceholder() {
      return '// 在此编写 QLExpress 脚本\n\nresult = 0'
    },
    /** 宿主可覆写：额外快捷键 */
    cmExtraKeys() {
      return {}
    },
    /**
     * 初始化 CodeMirror 编辑器（语法高亮 + 行号 + 括号匹配 + 快捷键）。
     * @param {HTMLElement} [hostEl] 承载容器，默认取 this.$refs.cmHost
     */
    initCmEditor(hostEl) {
      const host = hostEl || this.$refs.cmHost
      if (!host || this.cm) return
      this.searchMarks = []
      const extraKeys = {
        Tab: 'indentMore',
        'Shift-Tab': 'indentLess',
        'Ctrl-/': cm => cm.toggleComment({ indent: true }),
        'Ctrl-Alt-L': () => this.formatScript(),
        'Ctrl-F': () => this.openSearch(),
        'Esc': () => {
          if (this.searchVisible) {
            this.closeSearch()
            return
          }
          return CodeMirror.Pass
        },
        'Shift-Ctrl-Z': 'redo',
        'Ctrl-Y': 'redo',
        ...this.cmExtraKeys()
      }
      this.cm = CodeMirror(host, {
        value: this.cmInitialValue() || '',
        mode: 'text/x-java',
        theme: 'darcula',
        lineNumbers: true,
        lineWrapping: false,
        readOnly: this.cmReadOnly(),
        matchBrackets: true,
        autoCloseBrackets: true,
        smartIndent: true,
        indentUnit: 4,
        tabSize: 4,
        indentWithTabs: false,
        highlightSelectionMatches: { showToken: true, annotateScrollbar: true, delay: 120 },
        placeholder: this.cmPlaceholder(),
        extraKeys
      })
      this.cm.on('change', () => {
        this.cmOnChange(this.cm.getValue())
      })
      // 查找面板打开时，编辑器内按 ESC 由 extraKeys 关闭面板；
      // CodeMirror 消费按键只 preventDefault 不 stopPropagation，
      // 需手动阻止冒泡，避免 ESC 上冒到 document 触发 el-drawer 关闭整个设计器抽屉
      this.cm.on('keydown', (cm, e) => {
        if ((e.key === 'Escape' || e.keyCode === 27) && this.searchVisible) {
          e.stopPropagation()
        }
      })
    },
    /** 销毁编辑器实例，释放引用 */
    destroyCmEditor() {
      this.clearSearchMarks()
      this.cm = null
    },
    /**
     * 将外部内容写入编辑器（编程式赋值，不重复触发脏检查死循环）。
     * 首次写入非空内容时把其设为撤销基准点，避免撤销到空白。
     */
    setCmValue(val) {
      if (!this.cm) return
      const next = val == null ? '' : val
      if (this.cm.getValue() !== next) {
        this.cm.setValue(next)
      }
      if (!this.historyBaselineSet) {
        this.cm.clearHistory()
        this.historyBaselineSet = true
      }
    },
    /** 以当前内容为撤销基准点 */
    resetCmHistoryBaseline() {
      if (!this.cm) return
      this.cm.clearHistory()
      this.historyBaselineSet = true
    },
    /** 在光标处插入文本 */
    insertAtCursor(code) {
      if (!this.cm) return
      this.cm.replaceSelection(code)
      this.cm.focus()
    },
    /**
     * 注释 / 取消注释当前行或选中行（以 // 行注释形式）。
     */
    toggleComment() {
      if (!this.cm) return
      this.cm.toggleComment({ indent: true })
      this.cm.focus()
    },
    /** 格式化当前脚本（轻量级 QLExpress 格式化：语句换行 + 花括号缩进 + 空行归并） */
    formatScript() {
      if (!this.cm) return
      const cur = this.cm.getValue()
      const formatted = this.formatQlScript(cur)
      if (formatted === cur) {
        this.$message.info('代码格式无需调整')
        return
      }
      const scroll = this.cm.getScrollInfo()
      this.cm.operation(() => {
        const last = this.cm.lastLine()
        const lastCh = this.cm.getLine(last).length
        this.cm.replaceRange(formatted, { line: 0, ch: 0 }, { line: last, ch: lastCh })
      })
      this.cm.scrollTo(scroll.left, scroll.top)
      this.cm.focus()
      this.$message.success('代码已格式化')
    },
    /** 先按 ; { } 重排语句，再按花括号层级缩进，最后归并多余空行 */
    formatQlScript(src) {
      if (!src) return src
      const INDENT = '    '
      const reflowed = this.reflowStatements(src)
      const lines = reflowed.split('\n')
      let depth = 0
      let blankRun = 0
      const out = []
      for (const raw of lines) {
        const line = raw.trim()
        if (line === '') {
          blankRun++
          if (blankRun <= 1) out.push('') // 连续空行折叠为一行
          continue
        }
        blankRun = 0
        const { open, close, leadingClose } = this.countBraces(line)
        // else / catch / finally 紧跟在前一行的 } 之后（同一行）
        if (/^(else|catch|finally)\b/.test(line)) {
          while (out.length && out[out.length - 1] === '') out.pop()
          if (out.length && out[out.length - 1].replace(/\s+$/, '').endsWith('}')) {
            out[out.length - 1] = out[out.length - 1].replace(/\s+$/, '') + ' ' + line
            depth += open - close
            if (depth < 0) depth = 0
            continue
          }
        }
        let indent = depth - leadingClose
        if (indent < 0) indent = 0
        out.push(INDENT.repeat(indent) + line)
        depth += open - close
        if (depth < 0) depth = 0
      }
      // 去除首尾空行
      while (out.length && out[0] === '') out.shift()
      while (out.length && out[out.length - 1] === '') out.pop()
      return out.join('\n')
    },
    /**
     * 语句重排：在 { } ; 处换行（{ 保持在行尾并前置一个空格）；对 JSON / Map 对象字面量在顶层逗号处换行。
     * 通过括号上下文栈区分“代码块 {}”与“对象字面量 {}”：
     * - 代码块：如 if/for/while/function 后的 { }，内部 ; 换行，逗号不换行；
     * - 对象字面量：如 x = {"a": a, "b": b}，内部顶层逗号换行。
     * 字符串字面量、行注释、块注释内容原样保留，圆括号/方括号内的 ; 与逗号不换行（兼容 for 循环与函数调用）。
     */
    reflowStatements(src) {
      const s = src.replace(/\r\n/g, '\n')
      const len = s.length
      let out = ''
      let inStr = null
      let inLineComment = false
      let inBlockComment = false
      let lastAuto = false // 上一处是否为结构化自动换行，用于吞掉其后冗余的空白与换行
      const stack = [] // 括号上下文：'paren' | 'bracket' | 'block' | 'map'
      let lastSig = '' // 上一个有效非空白字符
      let word = '' // 当前正在累积的标识符
      let lastWord = '' // 上一个完整标识符
      const top = () => stack[stack.length - 1]
      const trimTrailingSpace = () => { out = out.replace(/[ \t]+$/, '') }
      const flushWord = () => { if (word) { lastWord = word; word = '' } }
      // 向后看是否为 "key": / ident: 形态，用于识别语句起始处的对象字面量
      const looksLikeMap = (idx) => {
        let j = idx + 1
        while (j < len && (s[j] === ' ' || s[j] === '\t' || s[j] === '\n' || s[j] === '\r')) j++
        if (j >= len) return false
        const q = s[j]
        if (q === '"' || q === "'") {
          j++
          while (j < len && s[j] !== q) { if (s[j] === '\\') j++; j++ }
          j++
        } else if (/[A-Za-z_$]/.test(q)) {
          while (j < len && /[A-Za-z0-9_$]/.test(s[j])) j++
        } else {
          return false
        }
        while (j < len && (s[j] === ' ' || s[j] === '\t')) j++
        return s[j] === ':'
      }
      // 判定 { 是对象字面量（map）还是代码块（block）
      const classifyBrace = (idx) => {
        if (lastWord === 'return') return 'map'
        if (lastWord === 'else' || lastWord === 'try' || lastWord === 'finally' || lastWord === 'do') return 'block'
        if (lastSig === ')') return 'block'
        if (lastSig && '=([,:?&|!+-*/<>%'.indexOf(lastSig) !== -1) return 'map'
        // 语句起始等歧义情况：向后看是否为键值对
        if (looksLikeMap(idx)) return 'map'
        return 'block'
      }
      for (let k = 0; k < len; k++) {
        const c = s[k]
        const next = s[k + 1]
        if (inLineComment) {
          out += c
          if (c === '\n') inLineComment = false
          lastAuto = false
          continue
        }
        if (inBlockComment) {
          out += c
          if (c === '*' && next === '/') { out += next; k++; inBlockComment = false }
          lastAuto = false
          continue
        }
        if (inStr) {
          out += c
          if (c === '\\') { out += next; k++ } else if (c === inStr) { inStr = null; lastSig = '"'; flushWord() }
          lastAuto = false
          continue
        }
        // 吞掉结构化换行后紧跟的行首空白，避免产生多余空行
        if (lastAuto && (c === ' ' || c === '\t')) continue
        if (c === '/' && next === '/') { inLineComment = true; out += c; lastAuto = false; flushWord(); continue }
        if (c === '/' && next === '*') { inBlockComment = true; out += c; lastAuto = false; flushWord(); continue }
        if (c === '"' || c === "'") { inStr = c; out += c; lastAuto = false; flushWord(); continue }
        if (c === '(') { stack.push('paren'); out += c; lastSig = c; flushWord(); lastAuto = false; continue }
        if (c === '[') { stack.push('bracket'); out += c; lastSig = c; flushWord(); lastAuto = false; continue }
        if (c === ')') { if (top() === 'paren') stack.pop(); out += c; lastSig = c; flushWord(); lastAuto = false; continue }
        if (c === ']') { if (top() === 'bracket') stack.pop(); out += c; lastSig = c; flushWord(); lastAuto = false; continue }
        if (c === '{') {
          flushWord()
          stack.push(classifyBrace(k))
          trimTrailingSpace()
          if (out.length && !out.endsWith('\n') && !out.endsWith('(') && !out.endsWith('[')) out += ' '
          out += '{\n'
          lastAuto = true
          lastSig = '{'
          lastWord = ''
          continue
        }
        if (c === '}') {
          flushWord()
          if (top() === 'block' || top() === 'map') stack.pop()
          trimTrailingSpace()
          if (out.length && !out.endsWith('\n')) out += '\n'
          out += '}\n'
          lastAuto = true
          lastSig = '}'
          lastWord = ''
          continue
        }
        if (c === ',') {
          flushWord()
          if (top() === 'map') {
            trimTrailingSpace()
            if (out.endsWith('\n')) out = out.replace(/\n$/, '') // 让 , 紧跟前一个 }
            out += ',\n'
            lastAuto = true
            lastSig = ','
            continue
          }
          out += c
          lastSig = c
          lastAuto = false
          continue
        }
        if (c === ';') {
          flushWord()
          const t = top()
          if (t === undefined || t === 'block') {
            trimTrailingSpace()
            if (out.endsWith('\n')) out = out.replace(/\n$/, '') // 让 ; 紧跟前一个 }
            out += ';\n'
            lastAuto = true
            lastSig = ';'
            continue
          }
          out += c
          lastSig = c
          lastAuto = false
          continue
        }
        if (c === '\n') {
          if (lastAuto) { lastAuto = false; continue }
          out += c
          flushWord()
          continue
        }
        // 普通字符
        out += c
        if (c === ' ' || c === '\t') {
          flushWord()
        } else {
          if (/[A-Za-z0-9_$]/.test(c)) word += c
          else flushWord()
          lastSig = c
        }
        lastAuto = false
      }
      return out
    },
    /** 统计单行有效花括号数量，跳过字符串字面量与行注释 */
    countBraces(line) {
      let open = 0
      let close = 0
      let leadingClose = 0
      let started = false
      let inStr = null
      for (let i = 0; i < line.length; i++) {
        const c = line[i]
        const next = line[i + 1]
        if (inStr) {
          if (c === '\\') { i++; continue }
          if (c === inStr) inStr = null
          continue
        }
        if (c === '/' && next === '/') break
        if (c === '"' || c === "'") { inStr = c; started = true; continue }
        if (c === '{') { open++; started = true } else if (c === '}') {
          close++
          if (!started) leadingClose++
        } else if (c !== ' ' && c !== '\t') {
          started = true
        }
      }
      return { open, close, leadingClose }
    },
    /** 打开查找/替换面板（Ctrl+F），若有选中文本则预填入查找框 */
    openSearch() {
      const sel = this.cm ? this.cm.getSelection() : ''
      if (sel) this.searchQuery = sel
      this.searchVisible = true
      this.$nextTick(() => {
        if (this.$refs.searchInput) this.$refs.searchInput.focus()
        this.highlightAll()
      })
    },
    /** 关闭查找/替换面板并清除高亮 */
    closeSearch() {
      this.searchVisible = false
      this.clearSearchMarks()
      if (this.cm) this.cm.focus()
    },
    /** 清除查找高亮标记 */
    clearSearchMarks() {
      if (this.searchMarks) {
        this.searchMarks.forEach(m => m.clear())
      }
      this.searchMarks = []
    },
    /** 高亮所有匹配项（大小写不敏感） */
    highlightAll() {
      this.clearSearchMarks()
      if (!this.cm || !this.searchQuery) return
      const cm = this.cm
      const cursor = cm.getSearchCursor(this.searchQuery, { line: 0, ch: 0 }, { caseFold: true })
      let count = 0
      while (cursor.findNext() && count < 5000) {
        this.searchMarks.push(cm.markText(cursor.from(), cursor.to(), { className: 'cm-search-highlight' }))
        count++
      }
    },
    /** 查找下一个/上一个（到头尾自动循环） */
    findNext(reverse) {
      if (!this.cm || !this.searchQuery) return
      const cm = this.cm
      const opts = { caseFold: true }
      const startPos = reverse ? cm.getCursor('from') : cm.getCursor('to')
      let cursor = cm.getSearchCursor(this.searchQuery, startPos, opts)
      let found = reverse ? cursor.findPrevious() : cursor.findNext()
      if (!found) {
        const wrapPos = reverse
          ? { line: cm.lastLine(), ch: cm.getLine(cm.lastLine()).length }
          : { line: 0, ch: 0 }
        cursor = cm.getSearchCursor(this.searchQuery, wrapPos, opts)
        found = reverse ? cursor.findPrevious() : cursor.findNext()
      }
      if (found) {
        cm.setSelection(cursor.from(), cursor.to())
        cm.scrollIntoView({ from: cursor.from(), to: cursor.to() }, 40)
      } else {
        this.$message.info('未找到匹配内容')
      }
    },
    /** 替换当前匹配项；若当前选区即匹配项则替换后跳下一个，否则先选中下一个 */
    replaceCurrent() {
      if (!this.cm || !this.searchQuery) return
      const cm = this.cm
      const sel = cm.getSelection()
      const isMatch = sel.length > 0 && sel.toLowerCase() === this.searchQuery.toLowerCase()
      if (isMatch) {
        cm.replaceSelection(this.replaceQuery)
        this.highlightAll()
        this.findNext(false)
      } else {
        this.findNext(false)
      }
    },
    /** 全部替换（大小写不敏感） */
    replaceAll() {
      if (!this.cm || !this.searchQuery) return
      const cm = this.cm
      let count = 0
      cm.operation(() => {
        const cursor = cm.getSearchCursor(this.searchQuery, { line: 0, ch: 0 }, { caseFold: true })
        while (cursor.findNext()) {
          cursor.replace(this.replaceQuery)
          count++
        }
      })
      this.highlightAll()
      this.$message.success('已替换 ' + count + ' 处')
    }
  }
}
