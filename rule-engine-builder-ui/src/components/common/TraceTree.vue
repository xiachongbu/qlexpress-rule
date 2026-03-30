<template>
  <div class="trace-wrap">
    <div v-if="hasTraceData">

      <!-- ========== 决策流追踪（卡片式） ========== -->
      <!-- 执行日志详情：统一不展示各模型顶部标题横幅（规则名/耗时/版本/结果） -->
      <!-- SCRIPT：在能解析出 flowCards 时与 FLOW 共用同一时间线卡片；否则回退下方树形追踪 -->
      <div v-if="showFlowCardTrace" class="fc-wrap">

        <!-- 步骤卡片列表 -->
        <div class="fc-steps">
          <div v-for="(card, idx) in flowCards" :key="idx" class="fc-step">
            <!-- 步骤间连线 -->
            <div class="fc-connector" v-if="idx > 0"><div class="fc-conn-line"></div></div>
            <div class="fc-step-row">
              <!-- 左侧图标 -->
              <div class="fc-icon" :class="'fc-icon--' + card.stepType">
                {{ card.stepType === 'decision' ? '判' : card.stepType === 'end' ? '终' : card.stepType === 'function' ? '函' : card.stepType === 'assign' ? '赋' : '算' }}
              </div>
              <!-- 右侧卡片 -->
              <div class="fc-card" :class="'fc-card--' + card.stepType">
                <div class="fc-card-head">
                  <span class="fc-card-title">{{ card.stepNo }}. {{ card.title }}</span>
                  <span class="fc-card-badge" :class="'fc-badge--' + card.status">{{ card.statusText }}</span>
                </div>
                <div class="fc-card-body">
                  <!-- 判断类 -->
                  <template v-if="card.stepType === 'decision'">
                    <div v-for="(c, ci) in card.conditions" :key="ci" class="fc-cond-row" :style="ci > 0 ? 'padding-left:20px' : ''">
                      <code class="fc-cond-var">{{ c.varLabel || c.varCode }}</code>
                      <span class="fc-cond-op">{{ c.operator }}</span>
                      <code class="fc-cond-val">{{ c.compareDisplay }}</code>
                      <span class="fc-cond-check" :class="c.result ? 'is-true' : 'is-false'">
                        {{ c.result ? '✓ 满足' : '✗ 不满足' }}
                      </span>
                    </div>
                    <div v-if="card.actions.length" class="fc-action-bar" :class="{ 'fc-action-bar--else': card.isElseBranch }">
                      <span class="fc-action-prefix">{{ card.isElseBranch ? '未满足，执行：' : '满足，执行：' }}</span>
                      <strong v-for="(a, ai) in card.actions" :key="ai" class="fc-action-text">{{ a.targetLabel || a.targetVar }} = {{ a.valueDisplay }}</strong>
                    </div>
                  </template>
                  <!-- 函数调用类 -->
                  <template v-else-if="card.stepType === 'function'">
                    <div class="fc-func-row">
                      <div class="fc-func-name">
                        <span class="fc-func-icon">ƒ</span>
                        <code>{{ funcDisplayName(card.funcName) }}</code>
                      </div>
                      <div v-if="card.funcArgs && card.funcArgs.length" class="fc-func-args">
                        <span class="fc-func-args-label">参数：</span>
                        <span v-for="(arg, ai) in card.funcArgs" :key="ai" class="fc-func-arg">
                          <span class="fc-func-arg-name">{{ arg.label }}</span>
                          <span class="fc-func-arg-eq">=</span>
                          <code class="fc-func-arg-val">{{ arg.value }}</code>
                        </span>
                      </div>
                    </div>
                    <div class="fc-expr-row" style="margin-top: 8px">
                      <code class="fc-expr">{{ card.expression }}</code>
                      <span class="fc-expr-result">{{ card.resultDisplay }}</span>
                    </div>
                  </template>
                  <!-- 赋值类 / 计算类 / 结束：_result 汇总不展示左侧表达式，只展示 a:b c:d 式节点结果 -->
                  <template v-else>
                    <div class="fc-expr-row" :class="{ 'fc-expr-row--result-only': card.targetVar === '_result' }">
                      <code v-if="card.targetVar !== '_result'" class="fc-expr">{{ card.expression }}</code>
                      <span class="fc-expr-result" :class="{ 'is-end': card.stepType === 'end', 'is-result-map': card.targetVar === '_result' }">{{ card.resultDisplay }}</span>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 决策树追踪（层级树） ========== -->
      <div v-else-if="effectiveType === 'TREE'" class="mv-tree">
        <!-- 命中路径摘要 -->
        <div class="dt-path" v-if="treePath.length">
          <span class="dt-path-label">命中路径：</span>
          <span v-for="(seg, si) in treePath" :key="si" class="dt-path-seg">
            <span class="dt-path-arrow" v-if="si > 0">&rarr;</span>
            <span class="dt-path-text">{{ seg }}</span>
          </span>
        </div>
        <!-- 层级树主体 -->
        <div class="dt-body" v-if="treeData">
          <decision-tree-trace-node :node="treeData" :is-root="true" :var-map="varMap" :function-name-map="effectiveFunctionNameMap" />
        </div>
        <!-- 图例 -->
        <div class="dt-legend">
          <span class="dt-legend-item"><span class="dt-legend-dot dt-legend-dot--hit"></span> 命中：条件满足，路径通过</span>
          <span class="dt-legend-item"><span class="dt-legend-dot dt-legend-dot--blocked"></span> 阻断：条件不满足，路径终止</span>
          <span class="dt-legend-item"><span class="dt-legend-dot dt-legend-dot--skipped"></span> 跳过：兄弟已命中，未执行</span>
        </div>
      </div>

      <!-- ========== 评分卡 / 复杂评分卡追踪 ========== -->
      <!-- SCORE / SCORE_ADV：简单评分卡本无顶部横幅；复杂评分卡与执行日志统一去掉标题区 -->
      <div v-else-if="effectiveType === 'SCORE' || effectiveType === 'SCORE_ADV'" class="mv-score">
        <table class="sc-table">
          <thead><tr><th>评估维度</th><th>输入值</th><th>规则条件</th><th>得分</th><th v-if="effectiveType === 'SCORE_ADV'">状态</th><th>小计</th></tr></thead>
          <tbody>
            <tr v-for="(item, idx) in effectiveScoreItems" :key="idx"
                :class="{ 'sc-hit': item.hit, 'sc-result': item.isResult, 'sc-skipped': item.status === 'skipped', 'sc-miss': item.status === 'miss' }">
              <td>{{ item.dimension }}</td>
              <td>{{ item.inputValue }}</td>
              <td>{{ scoreRuleConditionText(item) }}</td>
              <td :class="{ 'is-pos': item.score > 0, 'is-neg': item.score < 0 }">{{ item.scoreDisplay }}</td>
              <td v-if="effectiveType === 'SCORE_ADV'">
                <span class="sc-status" :class="'sc-status--' + item.status">{{ item.status === 'hit' ? '✓ 命中' : item.status === 'miss' ? '✗ 不满足' : item.status === 'skipped' ? '— 跳过' : '' }}</span>
              </td>
              <td>{{ item.subtotalDisplay }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ========== 交叉表追踪：矩阵展示 + 命中单元格高亮 ========== -->
      <div v-else-if="effectiveType === 'CROSS'" class="mv-table mv-cross-trace">
        <div v-if="traceCrossSimpleModel" class="trace-cross-matrix-wrap">
          <table class="trace-cross-matrix trace-cross-matrix--simple">
            <thead>
              <tr>
                <th class="trace-corner-cell">
                  <div class="trace-corner-row">{{ traceCrossSimpleModel.rowVar && traceCrossSimpleModel.rowVar.varLabel ? traceCrossSimpleModel.rowVar.varLabel : '行' }}</div>
                  <div class="trace-corner-divider" />
                  <div class="trace-corner-col">{{ traceCrossSimpleModel.colVar && traceCrossSimpleModel.colVar.varLabel ? traceCrossSimpleModel.colVar.varLabel : '列' }}</div>
                </th>
                <th
                  v-for="(ch, ci) in traceCrossSimpleModel.colHeaders"
                  :key="'sch-' + ci"
                  class="trace-col-header"
                  :class="{ 'trace-cross-axis--hit': crossSimpleHitCoords && crossSimpleHitCoords.c === ci }"
                >{{ ch }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(rh, ri) in traceCrossSimpleModel.rowHeaders" :key="'sr-' + ri">
                <td
                  class="trace-row-header"
                  :class="{ 'trace-cross-axis--hit': crossSimpleHitCoords && crossSimpleHitCoords.r === ri }"
                >{{ rh }}</td>
                <td
                  v-for="(ch, ci) in traceCrossSimpleModel.colHeaders"
                  :key="'sc-' + ri + '-' + ci"
                  class="trace-data-cell"
                  :class="{ 'trace-cross-cell--hit': crossSimpleHitCoords && crossSimpleHitCoords.r === ri && crossSimpleHitCoords.c === ci }"
                >
                  <span class="trace-cell-inner">{{ traceCrossSimpleCellDisplay(ri, ci) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="trace-cross-legend"><span class="trace-cross-legend-dot" /> 绿色高亮为本次执行命中的单元格（与设计器交叉矩阵一致）</div>
        </div>
        <table v-else class="rt-table">
          <thead><tr>
            <th>规则编号</th>
            <th v-for="c in ruleCols" :key="c">{{ varMap[c] || c }}</th>
            <th v-for="a in actionCols" :key="a">{{ varMap[a] || a }}</th>
            <th>当前匹配</th>
          </tr></thead>
          <tbody>
            <tr v-for="r in tableRules" :key="r.no" :class="{ 'rt-hit': r.hit }">
              <td>R{{ r.no }}</td>
              <td v-for="c in ruleCols" :key="c"><strong v-if="r.hit">{{ r.conds[c] || '-' }}</strong><template v-else>{{ r.conds[c] || '-' }}</template></td>
              <td v-for="a in actionCols" :key="a">{{ r.acts[a] || '-' }}</td>
              <td><span v-if="r.hit" class="rt-badge">命中</span><span v-else>-</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ========== 复杂交叉表追踪：多维矩阵 + 命中高亮 ========== -->
      <div v-else-if="effectiveType === 'CROSS_ADV'" class="mv-table mv-cross-trace">
        <div v-if="traceCrossAdvModel" class="trace-cross-matrix-wrap">
          <table class="trace-cross-matrix trace-cross-matrix--adv">
            <thead>
              <tr v-for="(headerRow, level) in traceAdvColHeaderRows" :key="'adv-ch-' + level">
                <th
                  v-if="level === 0"
                  class="trace-corner-cell trace-corner-cell--adv"
                  :rowspan="traceAdvColDimLevels"
                  :colspan="traceAdvRowDimLevels"
                >
                  <div class="trace-corner-inner-adv">
                    <div class="trace-corner-row">{{ traceAdvRowDimLabel }}</div>
                    <div class="trace-corner-divider" />
                    <div class="trace-corner-col">{{ traceAdvColDimLabel }}</div>
                  </div>
                </th>
                <th
                  v-for="(cell, hci) in headerRow"
                  :key="'adv-chc-' + level + '-' + hci"
                  class="trace-col-header"
                  :colspan="cell.colspan"
                  :class="{ 'trace-cross-axis--hit': crossAdvHitCoords && crossAdvHitCoords.ci >= cell.colStart && crossAdvHitCoords.ci < cell.colEnd }"
                >{{ cell.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(rowCombo, ri) in traceAdvRowCombinations" :key="'adv-r-' + ri">
                <td
                  v-for="cell in traceAdvRowHeaderCells[ri]"
                  :key="'adv-rh-' + ri + '-' + cell.level"
                  class="trace-row-header"
                  :rowspan="cell.rowspan"
                  :class="{ 'trace-cross-axis--hit': crossAdvHitCoords && crossAdvHitCoords.ri >= ri && crossAdvHitCoords.ri < ri + cell.rowspan }"
                >{{ cell.label }}</td>
                <td
                  v-for="(cc, ci) in traceAdvColCombinations"
                  :key="'adv-d-' + ri + '-' + ci"
                  class="trace-data-cell"
                  :class="{ 'trace-cross-cell--hit': crossAdvHitCoords && crossAdvHitCoords.ri === ri && crossAdvHitCoords.ci === ci }"
                >
                  <span class="trace-cell-inner">{{ traceAdvCellDisplay(ri, ci) }}</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="trace-cross-legend"><span class="trace-cross-legend-dot" /> 绿色高亮为本次执行命中的单元格</div>
        </div>
        <table v-else class="rt-table">
          <thead><tr>
            <th>规则编号</th>
            <th v-for="c in ruleCols" :key="c">{{ varMap[c] || c }}</th>
            <th v-for="a in actionCols" :key="a">{{ varMap[a] || a }}</th>
            <th>当前匹配</th>
          </tr></thead>
          <tbody>
            <tr v-for="r in tableRules" :key="r.no" :class="{ 'rt-hit': r.hit }">
              <td>R{{ r.no }}</td>
              <td v-for="c in ruleCols" :key="c"><strong v-if="r.hit">{{ r.conds[c] || '-' }}</strong><template v-else>{{ r.conds[c] || '-' }}</template></td>
              <td v-for="a in actionCols" :key="a">{{ r.acts[a] || '-' }}</td>
              <td><span v-if="r.hit" class="rt-badge">命中</span><span v-else>-</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ========== 决策表追踪 ========== -->
      <!-- TABLE：七种模型中本类型无独立顶部标题区，仅表格列头 -->
      <div v-else-if="effectiveType === 'TABLE'" class="mv-table">
        <table class="rt-table">
          <thead><tr>
            <th>规则号</th>
            <template v-if="!tableUseCondSummary">
              <th v-for="c in ruleCols" :key="c">{{ varMap[c] || c }}</th>
            </template>
            <th>条件</th>
            <th v-for="a in actionCols" :key="a">{{ varMap[a] || a }}</th>
            <th>状态</th>
          </tr></thead>
          <tbody>
            <tr v-for="r in tableRules" :key="r.no" :class="{ 'rt-hit': r.hit }">
              <td>R{{ r.no }}</td>
              <template v-if="!tableUseCondSummary">
                <td v-for="c in ruleCols" :key="c"><strong v-if="r.hit">{{ r.conds[c] || '-' }}</strong><template v-else>{{ r.conds[c] || '-' }}</template></td>
              </template>
              <td class="rt-cond-cell">{{ tableCondSummaryText(r) }}</td>
              <td v-for="a in actionCols" :key="a">{{ r.acts[a] || '-' }}</td>
              <td><span v-if="r.hit" class="rt-badge">命中</span><span v-else>-</span></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- ========== 默认：表达式追踪树 ========== -->
      <template v-else>
        <div class="t-sec" v-if="usedVariables.length > 0">
          <div class="t-hd">输入变量</div>
          <div class="var-list">
            <span v-for="v in usedVariables" :key="v.code" class="var-tag">
              <span class="var-k">{{ v.label }}</span>
              <span class="var-v" :class="valCls(v.value)">{{ fmtVal(v.value) }}</span>
            </span>
          </div>
        </div>
        <div class="t-sec">
          <div class="t-hd">
            求值过程
            <el-button class="fullscreen-btn" type="text" size="mini" @click="fullscreen = true">
              <i class="el-icon-full-screen"></i> 全屏查看
            </el-button>
          </div>
          <div class="tree-viewport">
            <div class="tree-canvas">
              <trace-node v-for="(nd, i) in rootNodes" :key="i" :node="nd" :var-map="varMap" />
            </div>
          </div>
        </div>
        <div class="fs-mask" v-if="fullscreen" @click.self="fullscreen = false">
          <div class="fs-panel">
            <div class="fs-bar">
              <span class="fs-title">求值过程</span>
              <el-button type="text" class="fs-close" @click="fullscreen = false"><i class="el-icon-close"></i></el-button>
            </div>
            <div class="fs-body">
              <div class="tree-canvas">
                <trace-node v-for="(nd, i) in rootNodes" :key="i" :node="nd" :var-map="varMap" />
              </div>
            </div>
          </div>
        </div>
        <div class="t-sec t-sec--result">
          <span class="result-label">最终结果</span>
          <span class="result-val" :class="finalCls">{{ finalText }}</span>
        </div>
      </template>

    </div>
    <div v-else class="t-empty"><i class="el-icon-info"></i> 暂无追踪信息</div>
  </div>
</template>

<script>
import TraceNode from './TraceNode.vue'
import DecisionTreeTraceNode from './DecisionTreeTraceNode.vue'

/** 运算符中文映射 */
var OP_CN = {
  '==': '等于', '!=': '不等于',
  '>': '大于', '>=': '大于等于', '<': '小于', '<=': '小于等于',
  '&&': '且', '||': '或'
}

/**
 * 追踪树中常见的内置/QL 函数名 → 中文展示（无项目配置时回退英文原名）
 */
var BUILTIN_FUNC_CN = {
  max: '最大值', min: '最小值', abs: '绝对值', round: '四舍五入', floor: '向下取整', ceil: '向上取整',
  sqrt: '平方根', pow: '幂', random: '随机数', parseInt: '转整数', parseDouble: '转小数', parseFloat: '转浮点',
  substring: '子串', length: '长度', size: '大小', isEmpty: '是否为空', trim: '去首尾空白',
  toUpperCase: '转大写', toLowerCase: '转小写', contains: '包含', startsWith: '前缀匹配', endsWith: '后缀匹配',
  String: '文本', Integer: '整数', Long: '长整数', Double: '小数', BigDecimal: '高精度数', Math: '数学'
}

export default {
  name: 'TraceTree',
  components: { TraceNode, DecisionTreeTraceNode },
  props: {
    traceInfo: { type: String, default: '' },
    varMap: { type: Object, default: function () { return {} } },
    modelType: { type: String, default: '' },
    inputParams: { type: String, default: '' },
    outputResult: { type: String, default: '' },
    /** 规则名称（从 ExecutionLog 传入） */
    ruleName: { type: String, default: '' },
    /** 规则版本号 */
    ruleVersion: { type: [Number, String], default: '' },
    /** 执行耗时(ms) */
    executeTimeMs: { type: [Number, String], default: '' },
    /** 规则模型数据（含 nodes/edges，用于提取节点中文名称） */
    modelData: { type: Object, default: null },
    /** 规则完整 modelJson 对象（交叉表矩阵高亮依赖 rowHeaders/cells 等） */
    definitionModel: { type: Object, default: null },
    /** 函数编码 → 中文名称（项目自定义函数，来自执行日志页加载） */
    functionNameMap: { type: Object, default: function () { return {} } }
  },
  data: function () {
    return { fullscreen: false }
  },
  watch: {
    fullscreen: function (val) {
      if (val) document.addEventListener('keydown', this._onEsc)
      else document.removeEventListener('keydown', this._onEsc)
    }
  },
  beforeDestroy: function () {
    document.removeEventListener('keydown', this._onEsc)
  },
  computed: {
    traceData: function () {
      if (!this.traceInfo) return null
      try {
        var d = JSON.parse(this.traceInfo)
        while (Array.isArray(d) && d.length > 0 && Array.isArray(d[0])) d = d.flat()
        return d
      } catch (e) { return null }
    },
    hasTraceData: function () {
      return this.traceData && (Array.isArray(this.traceData) ? this.traceData.length > 0 : true)
    },
    effectiveType: function () {
      if (this.modelType) return this.modelType
      if (this._isFlowFormat()) return 'FLOW'
      return ''
    },
    /**
     * 是否渲染决策流式步骤卡片区域：FLOW 与现网一致始终展示；SCRIPT 仅在 flowCards 非空时展示，否则回退树形追踪
     */
    showFlowCardTrace: function () {
      if (this.effectiveType === 'FLOW') return true
      if (this.modelType === 'SCRIPT' && this.flowCards && this.flowCards.length > 0) return true
      return false
    },
    rootNodes: function () {
      var nodes = Array.isArray(this.traceData) ? this.traceData : [this.traceData]
      if (nodes.length === 1 && nodes[0] && nodes[0].type === 'BLOCK' && nodes[0].children) nodes = nodes[0].children
      if (nodes.length === 1 && nodes[0] && nodes[0].type === 'STATEMENT' && nodes[0].children) nodes = nodes[0].children
      return nodes.filter(function (n) { return n && typeof n === 'object' })
    },
    /** 解析后的输入参数 */
    parsedInput: function () {
      try { return JSON.parse(this.inputParams) } catch (e) { return {} }
    },
    parsedOutput: function () {
      try { return JSON.parse(this.outputResult) } catch (e) { return null }
    },

    // ─── 决策表 / 交叉表 ───
    tableRules: function () {
      var stmts = this._getStatements()
      var ifNode = this._findFirstIf(stmts)
      if (!ifNode) return []
      return this._walkIfChain(ifNode)
    },
    ruleCols: function () {
      var cols = [], seen = {}
      for (var i = 0; i < this.tableRules.length; i++) {
        var keys = Object.keys(this.tableRules[i].conds)
        for (var j = 0; j < keys.length; j++) {
          if (!seen[keys[j]]) { seen[keys[j]] = true; cols.push(keys[j]) }
        }
      }
      return cols
    },
    actionCols: function () {
      var cols = [], seen = {}
      for (var i = 0; i < this.tableRules.length; i++) {
        var keys = Object.keys(this.tableRules[i].acts)
        for (var j = 0; j < keys.length; j++) {
          if (!seen[keys[j]]) { seen[keys[j]] = true; cols.push(keys[j]) }
        }
      }
      return cols
    },
    hitRuleNo: function () {
      for (var i = 0; i < this.tableRules.length; i++) {
        if (this.tableRules[i].hit) return this.tableRules[i].no
      }
      return null
    },
    /**
     * 决策表条件中含「或」时，按变量分列会失真，改为仅展示条件摘要列。
     */
    tableUseCondSummary: function () {
      var rules = this.tableRules
      for (var i = 0; i < rules.length; i++) {
        if (this._condHasOr(rules[i].condNode)) return true
      }
      return false
    },
    tableInputText: function () {
      var self = this
      return this.ruleCols.map(function (c) {
        var label = self.varMap[c] || c
        var val = self.parsedInput[c]
        return val !== undefined ? label + ' = ' + val : label
      }).join(' + ')
    },

    // ─── 评分卡 ───
    scoreItems: function () {
      var stmts = this._getStatements()
      var items = []
      var running = 0
      var self = this
      for (var i = 0; i < stmts.length; i++) {
        var s = stmts[i]
        if (s.type === 'OPERATOR' && s.token === '=' && items.length === 0) {
          var vn = s.children && s.children[0] && s.children[0].token
          if (vn === 'totalScore') {
            var iv = this._getAssignLiteral(s)
            running = typeof iv === 'number' ? iv : 0
            items.push({ dimension: '基础分', inputValue: '-', ruleText: '-', score: running, scoreDisplay: String(running), subtotalDisplay: String(running), hit: false, isResult: false })
          }
        } else if (s.type === 'IF') {
          var assigns = this._extractAssigns(s.children && s.children[1])
          if (assigns.length > 0 && assigns[0].varName === 'totalScore') {
            var cond = s.children && s.children[0]
            var condHit = cond && cond.value === true
            var delta = this._getScoreDelta(s.children && s.children[1])
            var dim = this._getScoreDimension(cond)
            var inputVal = this._getScoreInputValue(cond)
            if (condHit) running = running + delta
            items.push({
              dimension: dim,
              inputValue: inputVal,
              ruleText: this._condTextSimple(cond),
              score: delta,
              scoreDisplay: (delta >= 0 ? '+' : '') + delta,
              subtotalDisplay: condHit ? String(running) : '-',
              hit: condHit,
              isResult: false
            })
          } else if (assigns.length > 0 && assigns[0].varName === 'riskLevel') {
            var condR = s.children && s.children[0]
            var hitR = condR && condR.value === true
            var rv = hitR ? assigns[0].value : this._findThresholdResult(stmts, i)
            items.push({
              dimension: '风险评级',
              inputValue: '-',
              ruleText: this._buildThresholdText(stmts, i),
              score: '',
              scoreDisplay: '',
              subtotalDisplay: rv || '-',
              hit: true,
              isResult: true
            })
            break
          }
        }
      }
      return items
    },

    /** 复杂评分卡专用解析：遍历完整 if/else if 链（含未求值分支） */
    scoreAdvItems: function () {
      var stmts = this._getStatements()
      var items = []
      var running = 0
      var resCode = null
      var self = this
      var i = 0

      while (i < stmts.length) {
        var s = stmts[i]
        if (s && s.type === 'OPERATOR' && s.token === '=' && s.children && s.children[0]) {
          resCode = s.children[0].token
          var iv = this._getAssignLiteral(s)
          running = typeof iv === 'number' ? iv : 0
          items.push({ dimension: '基础分', inputValue: '-', ruleText: '-', score: running, scoreDisplay: String(running), subtotalDisplay: String(running), hit: false, isResult: false, status: '' })
          i++
          break
        }
        i++
      }

      while (i < stmts.length) {
        var s = stmts[i]
        if (!s) { i++; continue }

        if (s.type === 'OPERATOR' && s.token === '=' && s.children && s.children[0]) {
          var varName = s.children[0].token
          if (varName && varName.indexOf('_dim_') === 0) {
            var nextStmt = (i + 1 < stmts.length) ? stmts[i + 1] : null
            if (nextStmt && nextStmt.type === 'IF') {
              var dimItems = this._walkScoreAdvChain(nextStmt, varName, running)
              var dimHitScore = 0
              var anyHit = false
              for (var d = 0; d < dimItems.length; d++) {
                if (dimItems[d].hit) { dimHitScore = dimItems[d].score; anyHit = true }
                items.push(dimItems[d])
              }
              i += 2

              if (i < stmts.length && stmts[i] && stmts[i].type === 'OPERATOR' && stmts[i].token === '=') {
                var accumTarget = stmts[i].children && stmts[i].children[0] && stmts[i].children[0].token
                if (accumTarget === resCode) {
                  if (anyHit) running = running + dimHitScore
                  for (var d2 = items.length - 1; d2 >= 0; d2--) {
                    if (items[d2].hit && !items[d2].isResult) { items[d2].subtotalDisplay = String(running); break }
                  }
                  i++
                }
              }
              continue
            }
          }
          i++
          continue
        }

        if (s.type === 'IF') {
          var assigns = this._extractAssigns(s.children && s.children[1])
          if (assigns.length > 0 && assigns[0].varName !== resCode) {
            var levelVarName = assigns[0].varName
            var condR = s.children && s.children[0]
            var hitR = condR && condR.value === true
            var rv = hitR ? this._fv(assigns[0].value) : this._findThresholdResult(stmts, i)
            items.push({
              dimension: this.varMap[levelVarName] || '风险评级',
              inputValue: '-',
              ruleText: this._buildThresholdText(stmts, i),
              score: '', scoreDisplay: '', subtotalDisplay: rv || '-',
              hit: true, isResult: true, status: ''
            })
            break
          }
        }
        i++
      }
      return items
    },

    /** 根据模型类型返回对应的评分卡行数据 */
    effectiveScoreItems: function () {
      return this.effectiveType === 'SCORE_ADV' ? this.scoreAdvItems : this.scoreItems
    },

    /**
     * 合并内置函数中文名与项目函数中文名（项目配置覆盖内置）
     */
    effectiveFunctionNameMap: function () {
      var m = Object.assign({}, BUILTIN_FUNC_CN)
      var ext = this.functionNameMap || {}
      for (var k in ext) {
        if (ext[k]) m[k] = ext[k]
      }
      return m
    },

    /**
     * 评分卡「风险等级」行规则条件列展示的汇总公式（仅命中分支；形如 100 +IF(…)×分）
     */
    scoreFormulaDisplayText: function () {
      var items = this.effectiveScoreItems
      if (!items || items.length === 0) return ''
      var parts = []
      for (var i = 0; i < items.length; i++) {
        var item = items[i]
        if (item.isResult) continue
        if (item.dimension === '基础分') {
          parts.push(String(item.score))
          continue
        }
        if (!item.hit) continue
        var condLabel = this._condTextCompact(item.ruleText, item.dimension)
        parts.push('+IF(' + condLabel + ')×' + item.score)
      }
      return parts.join(' ')
    },

    /** 复杂评分卡最终得分 */
    scoreAdvFinalResult: function () {
      var items = this.effectiveScoreItems
      if (!items || items.length === 0) return null
      for (var i = items.length - 1; i >= 0; i--) {
        if (items[i].isResult) return items[i].subtotalDisplay
      }
      var running = 0
      for (var i = 0; i < items.length; i++) {
        if (items[i].hit && items[i].subtotalDisplay !== '-') {
          running = Number(items[i].subtotalDisplay)
        }
      }
      return running || null
    },

    /** 复杂交叉表命中结果汇总 */
    crossAdvHitResult: function () {
      var rules = this.tableRules
      for (var i = 0; i < rules.length; i++) {
        if (rules[i].hit) {
          var vals = []
          var keys = Object.keys(rules[i].acts)
          for (var j = 0; j < keys.length; j++) {
            vals.push(rules[i].acts[keys[j]])
          }
          return vals.join(', ') || 'R' + rules[i].no
        }
      }
      return ''
    },

    /** 简单交叉表：来自设计器的模型快照 */
    traceCrossSimpleModel: function () {
      var m = this.definitionModel
      if (!m || !Array.isArray(m.rowHeaders) || !Array.isArray(m.colHeaders) || !Array.isArray(m.cells)) return null
      return m
    },
    /** 复杂交叉表：含多维 rowDimensions / colDimensions */
    traceCrossAdvModel: function () {
      var m = this.definitionModel
      if (!m || !Array.isArray(m.rowDimensions) || !Array.isArray(m.colDimensions) || !Array.isArray(m.cells)) return null
      if (m.rowDimensions.length < 1 || m.colDimensions.length < 1) return null
      return m
    },
    /** 行维度笛卡尔积（复杂交叉表） */
    traceAdvRowCombinations: function () {
      if (!this.traceCrossAdvModel) return []
      return this._cartesianProductSegs(this.traceCrossAdvModel.rowDimensions)
    },
    /** 列维度笛卡尔积（复杂交叉表） */
    traceAdvColCombinations: function () {
      if (!this.traceCrossAdvModel) return []
      return this._cartesianProductSegs(this.traceCrossAdvModel.colDimensions)
    },
    traceAdvColDimLevels: function () {
      if (!this.traceCrossAdvModel) return 1
      return Math.max(1, this.traceCrossAdvModel.colDimensions.length)
    },
    traceAdvRowDimLevels: function () {
      if (!this.traceCrossAdvModel) return 1
      return Math.max(1, this.traceCrossAdvModel.rowDimensions.length)
    },
    traceAdvRowDimLabel: function () {
      if (!this.traceCrossAdvModel) return '行'
      var dims = this.traceCrossAdvModel.rowDimensions || []
      return dims.map(function (d) { return d.varLabel || d.varCode || '' }).join(' / ') || '行'
    },
    traceAdvColDimLabel: function () {
      if (!this.traceCrossAdvModel) return '列'
      var dims = this.traceCrossAdvModel.colDimensions || []
      return dims.map(function (d) { return d.varLabel || d.varCode || '' }).join(' / ') || '列'
    },
    /**
     * 与 AdvancedCrossTable 一致的多级列表头；每格带 colStart/colEnd（数据列下标区间，左闭右开），
     * 用于命中时同时高亮第一级合并列头。
     */
    traceAdvColHeaderRows: function () {
      var m = this.traceCrossAdvModel
      if (!m) return []
      var dims = m.colDimensions || []
      if (dims.length === 0) return []
      var rows = []
      for (var level = 0; level < dims.length; level++) {
        var cells = []
        var colspan = 1
        for (var l = level + 1; l < dims.length; l++) {
          colspan *= ((dims[l].segments || []).length || 1)
        }
        var repeat = 1
        for (var l2 = 0; l2 < level; l2++) {
          repeat *= ((dims[l2].segments || []).length || 1)
        }
        var segs = dims[level].segments || []
        var colCursor = 0
        for (var r = 0; r < repeat; r++) {
          for (var s = 0; s < segs.length; s++) {
            var seg = segs[s]
            var colStart = colCursor
            var colEnd = colCursor + colspan
            cells.push({
              label: seg.label || seg.value || '-',
              colspan: colspan,
              colStart: colStart,
              colEnd: colEnd
            })
            colCursor = colEnd
          }
        }
        rows.push(cells)
      }
      return rows
    },
    /** 与 AdvancedCrossTable 一致的多级行表头（每行一组 td） */
    traceAdvRowHeaderCells: function () {
      var m = this.traceCrossAdvModel
      if (!m) return []
      var dims = m.rowDimensions || []
      var combos = this.traceAdvRowCombinations
      var totalRows = combos.length
      var result = []
      for (var ri = 0; ri < totalRows; ri++) {
        var cells = []
        for (var level = 0; level < dims.length; level++) {
          var rowspan = 1
          for (var l = level + 1; l < dims.length; l++) {
            rowspan *= ((dims[l].segments || []).length || 1)
          }
          if (ri % rowspan === 0) {
            var combo = combos[ri]
            var seg = combo && combo[level]
            cells.push({
              label: seg ? (seg.label || seg.value || '-') : '-',
              rowspan: rowspan,
              level: level
            })
          }
        }
        result.push(cells)
      }
      return result
    },
    /** 简单交叉：与编译器相同的非空单元格顺序下，命中分支对应的 (r,c) */
    crossSimpleHitCoords: function () {
      var m = this.traceCrossSimpleModel
      if (!m) return null
      var order = this._crossCompileOrder(m.rowHeaders, m.colHeaders, m.cells)
      var rules = this.tableRules
      for (var i = 0; i < rules.length; i++) {
        if (rules[i].hit && i < order.length) return order[i]
      }
      return null
    },
    /** 复杂交叉：命中单元格 (ri,ci)，顺序与 AdvancedCrossTableCompiler 一致 */
    crossAdvHitCoords: function () {
      var m = this.traceCrossAdvModel
      if (!m) return null
      var order = this._advCrossCompileOrder(m.rowDimensions, m.colDimensions, m.cells)
      var rules = this.tableRules
      for (var i = 0; i < rules.length; i++) {
        if (rules[i].hit && i < order.length) return order[i]
      }
      return null
    },

    // ─── 决策树 ───
    treeData: function () {
      var stmts = this._getStatements()
      var ifNode = this._findFirstIf(stmts)
      if (!ifNode) return null
      var labelIdx = { i: 0 }
      return this._buildTree(ifNode, this.treeGraphLabels, labelIdx)
    },
    /** 从 modelData 按图遍历顺序提取决策树标注：[{ name, edges: [{ name, targetName, isLeaf }] }] */
    treeGraphLabels: function () {
      if (!this.modelData || !this.modelData.nodes || !this.modelData.edges) return []
      var nodes = this.modelData.nodes
      var edges = this.modelData.edges
      var nodeMap = {}
      var outEdgeMap = {}
      for (var i = 0; i < nodes.length; i++) {
        nodeMap[nodes[i].id] = nodes[i]
        outEdgeMap[nodes[i].id] = []
      }
      for (var i = 0; i < edges.length; i++) {
        if (!outEdgeMap[edges[i].source]) outEdgeMap[edges[i].source] = []
        outEdgeMap[edges[i].source].push(edges[i])
      }
      var startId = null
      for (var k in nodeMap) { if (nodeMap[k].type === 'start') { startId = k; break } }
      if (!startId) return []
      var result = []
      var startOuts = outEdgeMap[startId] || []
      var firstTarget = startOuts.length > 0 ? startOuts[0].target : null
      this._walkTreeGraph(firstTarget, nodeMap, outEdgeMap, result)
      return result
    },
    treePath: function () {
      if (!this.treeData) return []
      var path = []
      this._collectPath(this.treeData, path)
      return path
    },
    /** 决策树最终命中结果（叶子节点的值） */
    treeHitResult: function () {
      if (!this.treeData) return ''
      var result = this._findHitLeaf(this.treeData)
      return result || ''
    },

    // ─── 决策流 ───
    flowSteps: function () {
      var steps = []
      var self = this
      var inp = this.parsedInput || {}
      var params = Object.keys(inp).map(function (k) { return { label: self.varMap[k] || k, value: self._fv(inp[k]) } })
      steps.push({ type: 'start', params: params })
      var stmts = this._getStatements()
      this._walkFlow(stmts, steps)
      var lastAction = null
      for (var i = steps.length - 1; i >= 0; i--) { if (steps[i].type === 'action') { lastAction = steps[i]; break } }
      var endText = lastAction ? lastAction.text.split('=').map(function (s) { return s.trim() }).join(' 确定为 ') : ''
      steps.push({ type: 'end', text: endText })
      return steps
    },

    /** 决策流卡片式数据（增强版） */
    flowCards: function () {
      var cards = []
      var stmts = this._getStatements()
      var stepNo = { n: 1 }
      var nameIdx = { i: 0 }
      var nodeNames = this.orderedNodeNames
      this._buildFlowCards(stmts, cards, stepNo, nodeNames, nameIdx)
      if (cards.length > 0) {
        var last = cards[cards.length - 1]
        last.stepType = 'end'
        last.title = '最终结果汇总'
        last.status = 'end'
        last.statusText = '流程结束'
      }
      return cards
    },
    /** 决策流最终输出值 */
    flowFinalResult: function () {
      var cards = this.flowCards
      if (cards.length > 0) {
        var last = cards[cards.length - 1]
        if (last.result !== undefined) return last.result
      }
      return this.parsedOutput
    },
    /** 决策流最终结果标签 */
    flowFinalLabel: function () {
      var nn = this.orderedNodeNames
      if (nn.length > 0) {
        for (var i = nn.length - 1; i >= 0; i--) {
          if (nn[i].type === 'task' && nn[i].name) return nn[i].name
        }
      }
      var cards = this.flowCards
      if (cards.length > 0) {
        var last = cards[cards.length - 1]
        if (last.targetVar && this.varMap[last.targetVar]) return this.varMap[last.targetVar]
      }
      return '最终结果'
    },
    /** 决策流最终结果展示文本 */
    flowFinalDisplay: function () {
      var v = this.flowFinalResult
      if (v === null || v === undefined) return '-'
      if (typeof v === 'object') { try { return JSON.stringify(v) } catch (e) { return String(v) } }
      return String(v)
    },

    /** 从 modelData 按图遍历顺序提取节点名称列表 [{type, name}] */
    orderedNodeNames: function () {
      if (!this.modelData || !this.modelData.nodes || !this.modelData.edges) return []
      var nodes = this.modelData.nodes
      var edges = this.modelData.edges
      var nodeMap = {}
      var outEdgeMap = {}
      for (var i = 0; i < nodes.length; i++) {
        nodeMap[nodes[i].id] = nodes[i]
        outEdgeMap[nodes[i].id] = []
      }
      for (var i = 0; i < edges.length; i++) {
        if (!outEdgeMap[edges[i].source]) outEdgeMap[edges[i].source] = []
        outEdgeMap[edges[i].source].push(edges[i])
      }
      var startId = null
      for (var k in nodeMap) { if (nodeMap[k].type === 'start') { startId = k; break } }
      if (!startId) return []
      var result = []
      var visited = {}
      var current = startId
      while (current && !visited[current]) {
        visited[current] = true
        var nd = nodeMap[current]
        if (!nd) break
        if (nd.type === 'task') {
          result.push({ type: 'task', name: nd.name || '' })
          var outs = outEdgeMap[current] || []
          current = outs.length > 0 ? outs[0].target : null
        } else if (nd.type === 'decision') {
          result.push({ type: 'decision', name: nd.name || '' })
          current = this._findGraphMerge(current, outEdgeMap, nodeMap)
        } else {
          var outs = outEdgeMap[current] || []
          current = outs.length > 0 ? outs[0].target : null
        }
      }
      return result
    },

    // ─── 默认表达式追踪树 ───
    usedVariables: function () {
      var vars = [], seen = {}
      this._walk(this.rootNodes, vars, seen)
      return vars
    },
    finalResult: function () {
      if (!this.rootNodes || this.rootNodes.length === 0) return undefined
      return this.rootNodes[this.rootNodes.length - 1].value
    },
    finalText: function () { return this.fmtVal(this.finalResult) },
    finalCls: function () {
      if (this.finalResult === true) return 'is-true'
      if (this.finalResult === false) return 'is-false'
      return 'is-val'
    }
  },
  methods: {
    /**
     * 决策流卡片等处：模板中使用的函数展示名
     */
    funcDisplayName: function (code) {
      if (code === undefined || code === null || code === '') return '?'
      var s = String(code)
      var m = this.effectiveFunctionNameMap
      return (m && m[s]) || s
    },
    /**
     * 评分卡表格「规则条件」列：汇总行展示 scoreFormulaDisplayText，其余行仍为 ruleText
     */
    scoreRuleConditionText: function (item) {
      if (!item) return '-'
      if (item.isResult && (this.effectiveType === 'SCORE' || this.effectiveType === 'SCORE_ADV')) {
        return this.scoreFormulaDisplayText || '-'
      }
      return item.ruleText == null || item.ruleText === '' ? '-' : item.ruleText
    },
    /**
     * 简单交叉表矩阵单元格展示（只读）
     */
    traceCrossSimpleCellDisplay: function (ri, ci) {
      var m = this.traceCrossSimpleModel
      if (!m || !m.cells[ri]) return ''
      var v = m.cells[ri][ci]
      if (v == null) return ''
      return String(v)
    },
    /**
     * 复杂交叉表矩阵单元格展示（只读）
     */
    traceAdvCellDisplay: function (ri, ci) {
      var m = this.traceCrossAdvModel
      if (!m || !m.cells[ri]) return ''
      var raw = this._getAdvCellValue(m.cells[ri], ci)
      return raw != null && raw !== '' ? String(raw) : ''
    },
    /**
     * 与 CrossTableCompiler 一致：行优先遍历非空单元格，顺序对应 trace 中 if/else if 链
     */
    _crossCompileOrder: function (rowHeaders, colHeaders, cells) {
      var order = []
      if (!rowHeaders || !colHeaders || !cells) return order
      for (var r = 0; r < rowHeaders.length; r++) {
        for (var c = 0; c < colHeaders.length; c++) {
          var row = cells[r]
          var raw = row && row[c]
          var v = raw != null ? String(raw).trim() : ''
          if (v !== '') order.push({ r: r, c: c })
        }
      }
      return order
    },
    /**
     * 与 AdvancedCrossTableCompiler.getCellValue 一致解析单元格
     */
    _getAdvCellValue: function (rowCells, colIndex) {
      try {
        if (!rowCells) return null
        var val = rowCells[colIndex]
        if (Array.isArray(val)) return val[0] != null ? String(val[0]) : null
        return val != null ? String(val) : null
      } catch (e) {
        return null
      }
    },
    /**
     * 维度分段笛卡尔积（复杂交叉表），返回 segment 对象数组的数组
     */
    _cartesianProductSegs: function (dimensions) {
      if (!dimensions || dimensions.length === 0) return []
      var result = [[]]
      for (var d = 0; d < dimensions.length; d++) {
        var dim = dimensions[d]
        var segs = dim.segments || []
        if (segs.length === 0) return []
        var newResult = []
        for (var i = 0; i < result.length; i++) {
          var existing = result[i]
          for (var s = 0; s < segs.length; s++) {
            newResult.push(existing.concat([segs[s]]))
          }
        }
        result = newResult
      }
      return result
    },
    /**
     * 与 AdvancedCrossTableCompiler 一致：按 ri、ci 双层循环跳过空单元格后的顺序
     */
    _advCrossCompileOrder: function (rowDims, colDims, cells) {
      var order = []
      if (!rowDims || !colDims || !cells) return order
      var rowProduct = this._cartesianProductSegs(rowDims)
      var colProduct = this._cartesianProductSegs(colDims)
      for (var ri = 0; ri < rowProduct.length; ri++) {
        var rowCells = cells[ri]
        if (!rowCells) continue
        for (var ci = 0; ci < colProduct.length; ci++) {
          var cv = this._getAdvCellValue(rowCells, ci)
          if (cv != null && String(cv).trim() !== '') order.push({ ri: ri, ci: ci })
        }
      }
      return order
    },
    _onEsc: function (e) { if (e.keyCode === 27) this.fullscreen = false },
    fmtVal: function (v) {
      if (v === true) return '真'
      if (v === false) return '假'
      if (v === null || v === undefined) return '空'
      if (typeof v === 'object') {
        try { return JSON.stringify(v) } catch (e) { return String(v) }
      }
      return String(v)
    },
    valCls: function (v) {
      if (v === true) return 'is-true'
      if (v === false) return 'is-false'
      return 'is-val'
    },
    _fv: function (v) {
      if (v === true) return 'true'
      if (v === false) return 'false'
      if (v === null || v === undefined) return '空'
      return String(v)
    },
    /** 面向展示的值格式化：布尔值转中文，其他原样 */
    _displayVal: function (v) {
      if (v === true) return '是'
      if (v === false) return '否'
      if (v === null || v === undefined) return '空'
      return String(v)
    },
    _isFlowFormat: function () {
      if (!this.traceData || !Array.isArray(this.traceData)) return false
      var f = this.traceData[0]
      return f && f.node && ['task', 'decision', 'end'].indexOf(f.type) !== -1
    },
    _walk: function (nodes, vars, seen) {
      var list = Array.isArray(nodes) ? nodes : [nodes]
      for (var i = 0; i < list.length; i++) {
        var n = list[i]
        if (!n || typeof n !== 'object') continue
        if (n.type === 'VARIABLE' && n.token && !seen[n.token]) {
          seen[n.token] = true
          vars.push({ code: n.token, label: this.varMap[n.token] || n.token, value: n.value })
        }
        if (n.children) this._walk(n.children, vars, seen)
      }
    },
    /** 展开 BLOCK/STATEMENT 包装，获取实际语句列表 */
    _getStatements: function () {
      var nodes = this.rootNodes
      var result = []
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i]
        if (n && (n.type === 'DEFINE_FUNCTION' || n.type === 'DEFINE_MACRO')) continue
        if (n && (n.type === 'BLOCK' || n.type === 'STATEMENT') && n.children) {
          for (var j = 0; j < n.children.length; j++) {
            if (n.children[j] && typeof n.children[j] === 'object' && n.children[j].type !== 'DEFINE_FUNCTION' && n.children[j].type !== 'DEFINE_MACRO') result.push(n.children[j])
          }
        } else if (n) {
          result.push(n)
        }
      }
      return result
    },
    _findFirstIf: function (stmts) {
      for (var i = 0; i < stmts.length; i++) {
        if (stmts[i] && stmts[i].type === 'IF') return stmts[i]
      }
      return null
    },

    // ─── 决策表/交叉表解析 ───
    _walkIfChain: function (node) {
      var rules = []
      var current = node
      var no = 1
      while (current && current.type === 'IF') {
        var ch = current.children || []
        var condNode = ch[0]
        var thenNode = ch[1]
        var elseNode = ch[2]
        var hit = condNode && condNode.evaluated && condNode.value === true
        rules.push({ no: no++, conds: this._extractConds(condNode), condNode: condNode, acts: this._extractActMap(thenNode), hit: hit })
        if (!elseNode) break
        var next = this._unwrapToIf(elseNode)
        if (next) { current = next }
        else {
          var isEvaled = elseNode.evaluated !== false
          rules.push({ no: no++, conds: { _default: '其他' }, condNode: null, acts: this._extractActMap(elseNode), hit: isEvaled && !this._anyHit(rules) })
          break
        }
      }
      return rules
    },
    _anyHit: function (rules) {
      for (var i = 0; i < rules.length; i++) { if (rules[i].hit) return true }
      return false
    },
    _unwrapToIf: function (node) {
      if (!node) return null
      if (node.type === 'IF') return node
      if ((node.type === 'BLOCK' || node.type === 'STATEMENT') && node.children) {
        for (var i = 0; i < node.children.length; i++) {
          if (node.children[i] && node.children[i].type === 'IF') return node.children[i]
        }
      }
      return null
    },
    _extractConds: function (node) {
      var map = {}
      if (!node) return map
      var comps = this._flattenAnd(node)
      for (var i = 0; i < comps.length; i++) {
        var c = comps[i]
        if (c.type === 'OPERATOR' && c.children && c.children.length === 2) {
          var left = c.children[0]
          var right = c.children[1]
          var varName = left && left.token ? left.token : ''
          var val = right && right.value !== undefined ? right.value : (right && right.token || '')
          map[varName] = this._fv(val)
        }
      }
      return map
    },
    _flattenAnd: function (node) {
      if (!node) return []
      if (node.type === 'OPERATOR' && node.token === '&&' && node.children) {
        return this._flattenAnd(node.children[0]).concat(this._flattenAnd(node.children[1]))
      }
      return [node]
    },
    _extractActMap: function (block) {
      var map = {}
      var assigns = this._extractAssigns(block)
      for (var i = 0; i < assigns.length; i++) {
        map[assigns[i].varName] = this._fv(assigns[i].value)
      }
      return map
    },
    _extractAssigns: function (block) {
      var acts = []
      var self = this
      var walk = function (n) {
        if (!n) return
        if (n.type === 'OPERATOR' && n.token === '=') {
          var ch = n.children || []
          acts.push({ varName: ch[0] ? ch[0].token : '', value: n.value !== undefined ? n.value : (ch[1] ? ch[1].value : undefined) })
          return
        }
        if (n.children) { for (var i = 0; i < n.children.length; i++) walk(n.children[i]) }
      }
      walk(block)
      return acts
    },

    // ─── 评分卡解析 ───
    /** 生成评分卡公式中的条件紧凑文本，如 "信用等级A" 或 "年营收>=5000万" */
    _condTextCompact: function (ruleText, dimension) {
      if (!ruleText || ruleText === '-') return dimension || '?'
      var trimmed = ruleText.replace(/\s+/g, '')
      if (trimmed.indexOf('等于') !== -1) {
        return trimmed.replace(/等于/g, '')
      }
      return trimmed
    },
    _getAssignLiteral: function (assignNode) {
      if (!assignNode || !assignNode.children || !assignNode.children[1]) return 0
      var rhs = assignNode.children[1]
      return rhs.value !== undefined ? rhs.value : 0
    },
    _getScoreDelta: function (thenBlock) {
      var self = this
      var delta = 0
      var walk = function (n) {
        if (!n) return
        if (n.type === 'OPERATOR' && n.token === '=' && n.children && n.children[0] && n.children[0].token === 'totalScore') {
          var rhs = n.children[1]
          if (rhs && rhs.type === 'OPERATOR' && (rhs.token === '+' || rhs.token === '-') && rhs.children) {
            var numChild = rhs.children[1]
            delta = numChild && numChild.value !== undefined ? numChild.value : 0
            if (rhs.token === '-') delta = -delta
          }
          return
        }
        if (n.children) { for (var i = 0; i < n.children.length; i++) walk(n.children[i]) }
      }
      walk(thenBlock)
      return delta
    },
    /** 遍历复杂评分卡同维度 if/else if 完整链（含未求值分支） */
    _walkScoreAdvChain: function (ifNode, dimVarName, currentRunning) {
      var items = []
      var current = ifNode
      var dimName = null
      var inputValue = null
      var self = this

      while (current && current.type === 'IF') {
        var ch = current.children || []
        var condNode = ch[0]
        var thenNode = ch[1]
        var elseNode = ch[2]

        var wasEvaluated = condNode && condNode.evaluated !== false
        var condHit = wasEvaluated && condNode.value === true

        if (!dimName) {
          dimName = this._getScoreDimension(condNode)
          inputValue = this._getScoreInputValue(condNode)
        }

        var score = this._getDirectAssignScore(thenNode, dimVarName)
        var statusText = condHit ? 'hit' : (wasEvaluated ? 'miss' : 'skipped')

        items.push({
          dimension: dimName,
          inputValue: inputValue,
          ruleText: this._condTextSimple(condNode),
          score: score,
          scoreDisplay: (score >= 0 ? '+' : '') + score,
          subtotalDisplay: '-',
          hit: condHit,
          isResult: false,
          status: statusText
        })

        if (!elseNode) break
        var next = this._unwrapToIf(elseNode)
        if (!next) break
        current = next
      }
      return items
    },
    /** 从赋值块中提取直接赋值的分数（如 _dim_0_0 = 10） */
    _getDirectAssignScore: function (block, varName) {
      var result = null
      var walk = function (n) {
        if (!n || result !== null) return
        if (n.type === 'OPERATOR' && n.token === '=' && n.children) {
          var left = n.children[0]
          var right = n.children[1]
          if (left && left.token === varName && right) {
            if (n.value !== undefined && n.value !== null) { result = Number(n.value) }
            else if (right.value !== undefined && right.value !== null) { result = Number(right.value) }
            else if (right.token !== undefined && right.token !== null) { result = Number(right.token) }
          }
          return
        }
        if (n.children) { for (var i = 0; i < n.children.length; i++) walk(n.children[i]) }
      }
      walk(block)
      return result !== null && !isNaN(result) ? result : 0
    },
    _getScoreDimension: function (condNode) {
      if (!condNode) return '-'
      var comps = this._flattenAnd(condNode)
      if (comps.length === 0) return '-'
      var first = comps[0]
      if (first.children && first.children[0]) {
        var vn = first.children[0].token
        return this.varMap[vn] || vn
      }
      return '-'
    },
    _getScoreInputValue: function (condNode) {
      if (!condNode) return '-'
      var comps = this._flattenAnd(condNode)
      if (comps.length === 0) return '-'
      var first = comps[0]
      if (first.children && first.children[0]) {
        var vn = first.children[0].token
        var val = this.parsedInput[vn]
        if (val !== undefined) return this._fv(val)
        if (first.children[0].value !== undefined) return this._fv(first.children[0].value)
      }
      return '-'
    },
    _condTextSimple: function (node) {
      if (!node) return '-'
      var comps = this._flattenAnd(node)
      var parts = []
      for (var i = 0; i < comps.length; i++) {
        var c = comps[i]
        if (c.children && c.children.length === 2) {
          var vn = c.children[0] && c.children[0].token ? c.children[0].token : '?'
          var label = this.varMap[vn] || vn
          var op = c.token || '?'
          var opCn = OP_CN[op] || op
          var val = c.children[1] && c.children[1].value !== undefined ? c.children[1].value : (c.children[1] && c.children[1].token || '?')
          parts.push(label + ' ' + opCn + ' ' + this._displayVal(val))
        }
      }
      return parts.join('，') || '-'
    },
    /**
     * 追踪节点中是否出现「或」运算（决策表含 OR 时列摊平不可用）。
     */
    _condHasOr: function (node) {
      if (!node) return false
      if (node.type === 'OPERATOR' && node.token === '||') return true
      if (node.children) {
        for (var i = 0; i < node.children.length; i++) {
          if (this._condHasOr(node.children[i])) return true
        }
      }
      return false
    },
    /**
     * 决策表追踪表格：条件列展示（含与/或嵌套的递归文案）。
     */
    tableCondSummaryText: function (r) {
      if (!r || !r.condNode) return '-'
      if (this._condHasOr(r.condNode)) return this._traceCondText(r.condNode)
      return this._condTextSimple(r.condNode)
    },
    /**
     * 递归将条件追踪节点格式化为可读中文（支持 && 与 ||）。
     */
    _traceCondText: function (node) {
      if (!node) return '-'
      if (node.type === 'OPERATOR' && (node.token === '&&' || node.token === '||')) {
        var ch = node.children || []
        var a = ch[0] ? this._traceCondText(ch[0]) : ''
        var b = ch[1] ? this._traceCondText(ch[1]) : ''
        var sep = node.token === '&&' ? ' 且 ' : ' 或 '
        if (!a && !b) return '-'
        return (a || '-') + sep + (b || '-')
      }
      if (node.type === 'OPERATOR' && node.children && node.children.length === 2) {
        var vn = node.children[0] && node.children[0].token ? node.children[0].token : '?'
        var label = this.varMap[vn] || vn
        var opCn = OP_CN[node.token] || node.token
        var rv = node.children[1]
        var val = rv && rv.value !== undefined ? rv.value : (rv && rv.token !== undefined ? rv.token : '?')
        return label + ' ' + opCn + ' ' + this._displayVal(val)
      }
      return '-'
    },
    _findThresholdResult: function (stmts, fromIdx) {
      for (var i = fromIdx; i < stmts.length; i++) {
        var s = stmts[i]
        if (s.type === 'IF') {
          var a = this._extractAssigns(s.children && s.children[1])
          if (a.length > 0 && a[0].varName === 'riskLevel' && s.children && s.children[0] && s.children[0].value === true) return this._fv(a[0].value)
          var elseResult = this._findThresholdInElse(s)
          if (elseResult !== null) return elseResult
        }
      }
      return '-'
    },
    _findThresholdInElse: function (ifNode) {
      if (!ifNode || ifNode.type !== 'IF') return null
      var ch = ifNode.children || []
      if (ch[0] && ch[0].value === true) {
        var a = this._extractAssigns(ch[1])
        if (a.length > 0) return this._fv(a[0].value)
      }
      if (ch[2]) {
        var next = this._unwrapToIf(ch[2])
        if (next) return this._findThresholdInElse(next)
        var a2 = this._extractAssigns(ch[2])
        if (a2.length > 0 && ch[2].evaluated !== false) return this._fv(a2[0].value)
      }
      return null
    },
    _buildThresholdText: function (stmts, fromIdx) {
      var parts = []
      for (var i = fromIdx; i < stmts.length; i++) {
        var s = stmts[i]
        if (s.type !== 'IF') continue
        this._collectThresholds(s, parts)
        break
      }
      return parts.join('；') || '-'
    },
    _collectThresholds: function (ifNode, parts) {
      if (!ifNode || ifNode.type !== 'IF') return
      var a = this._extractAssigns(ifNode.children && ifNode.children[1])
      if (a.length > 0) {
        var cond = this._condTextSimple(ifNode.children && ifNode.children[0])
        parts.push(cond + ':' + this._fv(a[0].value))
      }
      if (ifNode.children && ifNode.children[2]) {
        var next = this._unwrapToIf(ifNode.children[2])
        if (next) this._collectThresholds(next, parts)
      }
    },

    // ─── 决策树解析 ───
    /** 递归遍历模型图，按编译器顺序提取标注信息 */
    _walkTreeGraph: function (nodeId, nodeMap, outEdgeMap, result) {
      if (!nodeId) return
      var nd = nodeMap[nodeId]
      if (!nd || nd.type !== 'decision') return
      var outs = outEdgeMap[nodeId] || []
      var condEdges = []
      var defaultEdge = null
      for (var i = 0; i < outs.length; i++) {
        if (outs[i].conditionExpression) {
          condEdges.push(outs[i])
        } else {
          defaultEdge = outs[i]
        }
      }
      var edgeLabels = []
      for (var i = 0; i < condEdges.length; i++) {
        var e = condEdges[i]
        var targetNd = nodeMap[e.target]
        edgeLabels.push({
          name: e.name || '',
          targetName: targetNd && targetNd.type === 'task' ? (targetNd.name || '') : '',
          isLeaf: targetNd && targetNd.type === 'task'
        })
      }
      if (defaultEdge) {
        var defTarget = nodeMap[defaultEdge.target]
        edgeLabels.push({
          name: defaultEdge.name || '其他',
          targetName: defTarget && defTarget.type === 'task' ? (defTarget.name || '') : '',
          isLeaf: defTarget && defTarget.type === 'task'
        })
      }
      result.push({ name: nd.name || '', edges: edgeLabels })
      for (var i = 0; i < condEdges.length; i++) {
        var targetNd = nodeMap[condEdges[i].target]
        if (targetNd && targetNd.type === 'decision') {
          this._walkTreeGraph(condEdges[i].target, nodeMap, outEdgeMap, result)
        }
      }
      if (defaultEdge) {
        var defTarget = nodeMap[defaultEdge.target]
        if (defTarget && defTarget.type === 'decision') {
          this._walkTreeGraph(defaultEdge.target, nodeMap, outEdgeMap, result)
        }
      }
    },
    /** 三态判定：hit=命中, blocked=条件不满足, skipped=兄弟已命中未执行 */
    _resolveStatus: function (isHit, wasEvaluated) {
      if (isHit) return 'hit'
      if (wasEvaluated) return 'blocked'
      return 'skipped'
    },
    _buildTree: function (ifNode, graphLabels, labelIdx) {
      if (!ifNode || ifNode.type !== 'IF') return null
      var ch = ifNode.children || []
      var condNode = ch[0]
      var thenNode = ch[1]
      var elseNode = ch[2]
      var condInfo = this._parseTreeCond(condNode)
      var condHit = condNode && condNode.value === true
      var condEvaluated = condNode && condNode.evaluated !== false
      var gl = (graphLabels && labelIdx && labelIdx.i < graphLabels.length) ? graphLabels[labelIdx.i++] : null
      var edgeIdx = { e: 0 }
      var decisionLabel = (gl && gl.name) ? gl.name : condInfo.varLabel
      var children = []
      var thenChild = this._buildTreeBranch(thenNode, graphLabels, labelIdx)
      if (thenChild) {
        var edgeInfo = (gl && gl.edges && edgeIdx.e < gl.edges.length) ? gl.edges[edgeIdx.e++] : null
        thenChild.branchLabel = (edgeInfo && edgeInfo.name) ? edgeInfo.name : (condInfo.valueText || '是')
        thenChild.hit = condHit
        thenChild.status = this._resolveStatus(condHit, condEvaluated)
        thenChild.conditionText = (edgeInfo && edgeInfo.name) ? '' : condInfo.condText
        if (edgeInfo && edgeInfo.targetName && thenChild.resultVar) {
          thenChild.taskName = edgeInfo.targetName
        }
        children.push(thenChild)
      }
      if (elseNode) {
        var elseIf = this._unwrapToIf(elseNode)
        if (elseIf) {
          this._flattenElseIfBranches(elseIf, children, !condHit, condEvaluated && !condHit, gl, edgeIdx, graphLabels, labelIdx)
        } else {
          var elseChild = this._buildTreeBranch(elseNode, graphLabels, labelIdx)
          if (elseChild) {
            var elseEdgeInfo = (gl && gl.edges && edgeIdx.e < gl.edges.length) ? gl.edges[edgeIdx.e++] : null
            elseChild.branchLabel = (elseEdgeInfo && elseEdgeInfo.name) ? elseEdgeInfo.name : '其他'
            var elseHit = !condHit && condEvaluated
            elseChild.hit = elseHit
            elseChild.status = this._resolveStatus(elseHit, condEvaluated)
            elseChild.conditionText = ''
            if (elseEdgeInfo && elseEdgeInfo.targetName && elseChild.resultVar) {
              elseChild.taskName = elseEdgeInfo.targetName
            }
            children.push(elseChild)
          }
        }
      }
      return { label: decisionLabel, children: children }
    },
    _buildTreeBranch: function (node, graphLabels, labelIdx) {
      if (!node) return null
      var inner = this._unwrapToIf(node)
      if (inner) return this._buildTree(inner, graphLabels, labelIdx)
      var stmts = this._unwrapList(node)
      var funcCalls = []
      var assigns = []
      for (var i = 0; i < stmts.length; i++) {
        var s = stmts[i]
        if (!s) continue
        if (s.type === 'DEFINE_FUNCTION' || s.type === 'DEFINE_MACRO') continue
        if (s.type === 'FUNCTION' || s.type === 'METHOD') {
          funcCalls.push({ name: s.token || '?', args: this._buildFuncArgs(s), expr: this._formatFuncCallExpr(s), value: s.value })
        } else if (s.type === 'OPERATOR' && s.token === '=') {
          var ch = s.children || []
          var rhs = ch[1]
          var rhsFunc = rhs && (rhs.type === 'FUNCTION' || rhs.type === 'METHOD')
          var nested = !rhsFunc ? this._findFuncCalls(rhs) : []
          if (rhsFunc) {
            funcCalls.push({ name: rhs.token || '?', args: this._buildFuncArgs(rhs), expr: this._formatFuncCallExpr(rhs), value: rhs.value })
          } else if (nested.length > 0) {
            for (var fi = 0; fi < nested.length; fi++) {
              funcCalls.push({ name: nested[fi].token || '?', args: this._buildFuncArgs(nested[fi]), expr: this._formatFuncCallExpr(nested[fi]), value: nested[fi].value })
            }
          }
          assigns.push({ varName: ch[0] ? ch[0].token : '', value: s.value !== undefined ? s.value : (ch[1] ? ch[1].value : undefined) })
        }
      }
      if (assigns.length === 0 && funcCalls.length === 0) {
        assigns = this._extractAssigns(node)
      }
      if (assigns.length > 0) {
        var varName = assigns[0].varName
        return {
          label: this._displayVal(assigns[0].value),
          resultVar: varName,
          resultVarLabel: this.varMap[varName] || varName,
          taskName: '',
          children: null,
          branchLabel: '',
          hit: false,
          status: 'skipped',
          conditionText: '',
          funcCalls: funcCalls.length > 0 ? funcCalls : null
        }
      }
      if (funcCalls.length > 0) {
        return {
          label: this._fv(funcCalls[0].value),
          resultVar: '',
          resultVarLabel: '',
          taskName: '',
          children: null,
          branchLabel: '',
          hit: false,
          status: 'skipped',
          conditionText: '',
          funcCalls: funcCalls
        }
      }
      return null
    },
    _flattenElseIfBranches: function (ifNode, children, parentCondFalse, parentWasEvaluated, gl, edgeIdx, graphLabels, labelIdx) {
      if (!ifNode || ifNode.type !== 'IF') return
      var ch = ifNode.children || []
      var condNode = ch[0]
      var condInfo = this._parseTreeCond(condNode)
      var condHit = condNode && condNode.value === true
      var condEvaluated = condNode && condNode.evaluated !== false
      var isHit = parentCondFalse && parentWasEvaluated && condHit
      var wasEval = parentCondFalse && parentWasEvaluated && condEvaluated
      var edgeInfo = (gl && gl.edges && edgeIdx.e < gl.edges.length) ? gl.edges[edgeIdx.e++] : null
      var thenChild = this._buildTreeBranch(ch[1], graphLabels, labelIdx)
      if (thenChild) {
        thenChild.branchLabel = (edgeInfo && edgeInfo.name) ? edgeInfo.name : (condInfo.valueText || condInfo.condText)
        thenChild.hit = isHit
        thenChild.status = this._resolveStatus(isHit, wasEval)
        thenChild.conditionText = (edgeInfo && edgeInfo.name) ? '' : condInfo.condText
        if (edgeInfo && edgeInfo.targetName && thenChild.resultVar) {
          thenChild.taskName = edgeInfo.targetName
        }
        children.push(thenChild)
      }
      if (ch[2]) {
        var nextIf = this._unwrapToIf(ch[2])
        var nextParentFalse = parentCondFalse && !condHit
        var nextParentEval = parentWasEvaluated && condEvaluated && !condHit
        if (nextIf) {
          this._flattenElseIfBranches(nextIf, children, nextParentFalse, nextParentEval, gl, edgeIdx, graphLabels, labelIdx)
        } else {
          var elseChild = this._buildTreeBranch(ch[2], graphLabels, labelIdx)
          if (elseChild) {
            var elseEdgeInfo = (gl && gl.edges && edgeIdx.e < gl.edges.length) ? gl.edges[edgeIdx.e++] : null
            elseChild.branchLabel = (elseEdgeInfo && elseEdgeInfo.name) ? elseEdgeInfo.name : '其他'
            var elseIsHit = nextParentFalse && nextParentEval
            elseChild.hit = elseIsHit
            elseChild.status = this._resolveStatus(elseIsHit, nextParentEval)
            elseChild.conditionText = ''
            if (elseEdgeInfo && elseEdgeInfo.targetName && elseChild.resultVar) {
              elseChild.taskName = elseEdgeInfo.targetName
            }
            children.push(elseChild)
          }
        }
      }
    },
    _parseTreeCond: function (condNode) {
      if (!condNode) return { varLabel: '?', valueText: '', condText: '' }
      var self = this
      var comps = this._flattenAnd(condNode)
      if (comps.length > 0 && comps[0].children && comps[0].children.length === 2) {
        var vn = comps[0].children[0].token || ''
        var val = comps[0].children[1] ? comps[0].children[1].value : ''
        var parts = []
        for (var i = 0; i < comps.length; i++) {
          if (comps[i].children && comps[i].children[1]) {
            var v = comps[i].children[1].value
            parts.push(self._displayVal(v))
          }
        }
        return { varLabel: this.varMap[vn] || vn, valueText: parts.join(' & '), condText: this._condTextSimple(condNode) }
      }
      return { varLabel: condNode.token || '?', valueText: '', condText: '' }
    },
    _collectPath: function (node, path) {
      if (!node) return
      if (node.children) {
        for (var i = 0; i < node.children.length; i++) {
          var ch = node.children[i]
          if (ch && ch.hit) {
            path.push(ch.branchLabel || '')
            if (ch.children && ch.children.length) {
              this._collectPath(ch, path)
            } else {
              path.push(ch.label || '')
            }
            return
          }
        }
      }
    },
    /** 递归查找命中的叶子节点值 */
    _findHitLeaf: function (node) {
      if (!node || !node.children) return null
      for (var i = 0; i < node.children.length; i++) {
        var ch = node.children[i]
        if (ch && ch.status === 'hit') {
          if (ch.children && ch.children.length) {
            return this._findHitLeaf(ch)
          }
          var varLabel = ch.resultVarLabel || ch.resultVar
          return (varLabel ? varLabel + ' = ' : '') + (ch.label || '')
        }
      }
      return null
    },

    // ─── 决策流卡片式解析 ───

    /**
     * 赋值右侧是否为简单右值（字面量、null、或单一变量引用，无运算与函数）。
     * 决策流时间线中此类步骤用「赋」与「赋值完成」，与真正的表达式运算「算」区分。
     */
    _isFlowSimpleAssignmentRhs: function (node) {
      if (!node) return true
      var t = node.type
      if (t === 'FUNCTION' || t === 'METHOD') return false
      if (t === 'VALUE' || t === 'PRIMARY') return true
      if (t === 'VARIABLE') return true
      return false
    },
    /** 递归构建决策流卡片数据 */
    _buildFlowCards: function (stmts, cards, stepNo, nodeNames, nameIdx) {
      for (var i = 0; i < stmts.length; i++) {
        var s = stmts[i]
        if (!s) continue
        if (s.type === 'DEFINE_FUNCTION' || s.type === 'DEFINE_MACRO') continue
        if (s.type === 'IF') {
          var cond = s.children && s.children[0]
          var thenBlock = s.children && s.children[1]
          var elseBlock = s.children && s.children[2]
          var condHit = cond && cond.value === true
          var conditions = this._extractFlowConditions(cond)
          var executedBlock = condHit ? thenBlock : elseBlock
          var branchStmts = executedBlock ? this._unwrapList(executedBlock) : []
          var directActions = []
          var remainingStmts = []
          var seenNonAssign = false
          for (var j = 0; j < branchStmts.length; j++) {
            var bs = branchStmts[j]
            if (!seenNonAssign && bs && bs.type === 'OPERATOR' && bs.token === '=') {
              var tgt = bs.children && bs.children[0] && bs.children[0].token
              directActions.push({ targetVar: tgt, targetLabel: this.varMap[tgt] || tgt, value: bs.value, valueDisplay: this._fv(bs.value) })
            } else {
              seenNonAssign = true
              remainingStmts.push(bs)
            }
          }
          var nodeName = this._matchNodeName(nodeNames, nameIdx, 'decision')
          cards.push({
            stepNo: stepNo.n++,
            stepType: 'decision',
            title: nodeName || this._deriveStepTitle(conditions, directActions),
            status: condHit ? 'hit' : 'miss',
            statusText: condHit ? '命中规则' : '条件不满足',
            conditions: conditions,
            actions: directActions,
            isElseBranch: !condHit
          })
          if (remainingStmts.length > 0) {
            this._buildFlowCards(remainingStmts, cards, stepNo, nodeNames, nameIdx)
          }
        } else if (s.type === 'FUNCTION' || s.type === 'METHOD') {
          var fnName = s.token || '?'
          var fnArgs = this._buildFuncArgs(s)
          var nodeName3 = this._matchNodeName(nodeNames, nameIdx, 'task')
          var fnDisp = this.funcDisplayName(fnName)
          cards.push({
            stepNo: stepNo.n++,
            stepType: 'function',
            title: nodeName3 || ('调用 ' + fnDisp),
            status: 'done',
            statusText: '调用完成',
            funcName: fnName,
            funcArgs: fnArgs,
            expression: this._formatFuncCallExpr(s),
            result: s.value,
            resultDisplay: this._flowCardValueDisplay(null, s.value)
          })
        } else if (s.type === 'OPERATOR' && s.token === '=') {
          var target = s.children && s.children[0] && s.children[0].token
          var targetLabel = this.varMap[target] || target
          var rhs = s.children && s.children[1]
          var rhsIsFunc = rhs && (rhs.type === 'FUNCTION' || rhs.type === 'METHOD')
          var nestedFuncs = !rhsIsFunc ? this._findFuncCalls(rhs) : []
          var hasFunc = rhsIsFunc || nestedFuncs.length > 0
          var simpleAssign = !hasFunc && this._isFlowSimpleAssignmentRhs(rhs)
          var nodeName2 = this._matchNodeName(nodeNames, nameIdx, 'task')
          var resultDisplay = this._flowCardValueDisplay(target, s.value)
          var callTok = rhsIsFunc ? rhs.token : (nestedFuncs.length > 0 ? nestedFuncs[0].token : '')
          var card = {
            stepNo: stepNo.n++,
            stepType: hasFunc ? 'function' : (simpleAssign ? 'assign' : 'calculation'),
            title: nodeName2 || (hasFunc ? ('调用 ' + this.funcDisplayName(callTok)) : (simpleAssign ? ('赋值：' + targetLabel) : ('计算' + targetLabel))),
            status: 'done',
            statusText: hasFunc ? '调用完成' : (simpleAssign ? '赋值完成' : '计算完成'),
            targetVar: target,
            targetLabel: targetLabel,
            expression: target === '_result' ? '_result = { … }' : (target + ' = ' + this._formatExprReadable(rhs)),
            result: s.value,
            resultDisplay: resultDisplay
          }
          if (rhsIsFunc) {
            card.funcName = rhs.token
            card.funcArgs = this._buildFuncArgs(rhs)
          } else if (nestedFuncs.length > 0) {
            card.funcName = nestedFuncs[0].token
            card.funcArgs = this._buildFuncArgs(nestedFuncs[0])
          }
          cards.push(card)
        }
      }
    },
    /**
     * 决策流步骤卡片右侧展示值：_result 优先用执行日志中的 outputResult（parsedOutput），避免 trace 里 Map 被序列化为 $ref 导致显示异常。
     */
    _flowCardValueDisplay: function (target, value) {
      if (target === '_result') {
        var po = this.parsedOutput
        if (po !== null && po !== undefined && typeof po === 'object' && !Array.isArray(po)) {
          return this._formatFlowResultMapDisplay(po)
        }
        if (value !== null && value !== undefined && typeof value === 'object' && !Array.isArray(value) && !this._isTraceJsonRefStub(value)) {
          return this._formatFlowResultMapDisplay(value)
        }
        return '—'
      }
      if (value !== null && value !== undefined && typeof value === 'object' && !Array.isArray(value)) {
        try { return JSON.stringify(value) } catch (e) { return String(value) }
      }
      return this._fv(value)
    },
    /**
     * 追踪 JSON 中是否为 JSON Pointer 风格的 $ref 占位（无法直接当 Map 展示）。
     */
    _isTraceJsonRefStub: function (v) {
      return !!(v && typeof v === 'object' && typeof v.$ref === 'string')
    },
    /**
     * 是否按「最后一个 task 节点」过滤决策流多键输出（需有 nodes/edges 模型）。
     */
    _shouldSummarizeFlowByLastTask: function () {
      var mt = this.modelType || this.effectiveType
      if (mt !== 'FLOW' && mt !== 'SCRIPT') return false
      return !!(this.modelData && this.modelData.nodes && this.modelData.edges)
    },
    /**
     * 将引擎返回的 Map 格式化为「中文名：值  中文名：值」，仅含最后一个 task 顶层动作对应的变量（可映射时）。
     */
    _formatFlowResultMapDisplay: function (obj) {
      if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return this._fv(obj)
      var filterKeys = this._shouldSummarizeFlowByLastTask() ? this._flowLastTaskOutputVarCodes() : []
      var keysToShow = filterKeys && filterKeys.length > 0
        ? filterKeys.filter(function (k) { return Object.prototype.hasOwnProperty.call(obj, k) })
        : Object.keys(obj)
      if (keysToShow.length === 0) keysToShow = Object.keys(obj)
      var self = this
      return keysToShow.map(function (k) {
        var lab = self.varMap[k] || k
        return lab + ':' + self._displayVal(obj[k])
      }).join(' ')
    },
    /**
     * 与 orderedNodeNames 相同主路径上，最后一个 task 节点 actionData「顶层」输出变量码（不递归条件分支内的赋值，便于「减免计算」类节点只展示最终一条赋值如 finalTaxAmount）。
     */
    _flowLastTaskOutputVarCodes: function () {
      if (!this.modelData || !this.modelData.nodes || !this.modelData.edges) return []
      var nodes = this.modelData.nodes
      var edges = this.modelData.edges
      var nodeMap = {}
      var outEdgeMap = {}
      for (var i = 0; i < nodes.length; i++) {
        nodeMap[nodes[i].id] = nodes[i]
        outEdgeMap[nodes[i].id] = []
      }
      for (var j = 0; j < edges.length; j++) {
        if (!outEdgeMap[edges[j].source]) outEdgeMap[edges[j].source] = []
        outEdgeMap[edges[j].source].push(edges[j])
      }
      var startId = null
      for (var k in nodeMap) {
        if (nodeMap[k].type === 'start') { startId = k; break }
      }
      if (!startId) return []
      var lastTask = null
      var visited = {}
      var current = startId
      while (current && !visited[current]) {
        visited[current] = true
        var nd = nodeMap[current]
        if (!nd) break
        if (nd.type === 'task') {
          lastTask = nd
          var outs = outEdgeMap[current] || []
          current = outs.length > 0 ? outs[0].target : null
        } else if (nd.type === 'decision') {
          current = this._findGraphMerge(current, outEdgeMap, nodeMap)
        } else {
          var outs2 = outEdgeMap[current] || []
          current = outs2.length > 0 ? outs2[0].target : null
        }
      }
      if (!lastTask || !lastTask.actionData) return []
      return this._collectTopLevelActionDataTargets(lastTask.actionData)
    },
    /**
     * 仅收集 actionData 数组顶层块中的赋值目标（不进入 if-block / switch-block / foreach 内部）。
     */
    _collectTopLevelActionDataTargets: function (actionData) {
      var out = []
      var seen = {}
      if (!actionData || !actionData.length) return out
      for (var i = 0; i < actionData.length; i++) {
        var block = actionData[i]
        if (!block || !block.type) continue
        var t = block.type
        if (t === 'assign' || t === 'func-call' || t === 'ternary' || t === 'in-check' || t === 'template-str') {
          var tgt = block.target
          if (tgt && typeof tgt === 'string' && tgt.trim() && !seen[tgt]) {
            seen[tgt] = true
            out.push(tgt.trim())
          }
        }
      }
      return out
    },
    /** 从条件节点提取各个比较条件 */
    _extractFlowConditions: function (condNode) {
      if (!condNode) return []
      var comps = this._flattenAnd(condNode)
      var result = []
      for (var i = 0; i < comps.length; i++) {
        var c = comps[i]
        if (c.children && c.children.length === 2) {
          var left = c.children[0]
          var right = c.children[1]
          var cv = right.value !== undefined ? right.value : right.token
          var rawOp = c.token || '=='
          result.push({
            varCode: left.token || '?',
            varLabel: this.varMap[left.token] || left.token || '?',
            operator: OP_CN[rawOp] || rawOp,
            compareValue: cv,
            compareDisplay: typeof cv === 'string' ? '"' + cv + '"' : this._displayVal(cv),
            actualValue: left.value,
            result: c.value === true
          })
        }
      }
      return result
    },
    /** 按类型顺序匹配节点名称 */
    _matchNodeName: function (nodeNames, nameIdx, type) {
      if (!nodeNames || !nameIdx) return ''
      while (nameIdx.i < nodeNames.length) {
        if (nodeNames[nameIdx.i].type === type) {
          var name = nodeNames[nameIdx.i].name
          nameIdx.i++
          return name || ''
        }
        nameIdx.i++
      }
      return ''
    },
    /** 自动推导步骤标题 */
    _deriveStepTitle: function (conditions, actions) {
      if (actions.length > 0) {
        return (this.varMap[actions[0].targetVar] || actions[0].targetVar) + '判定'
      }
      if (conditions.length > 0) {
        return conditions[0].varLabel + '判定'
      }
      return '条件判定'
    },
    /** 递归格式化表达式（变量后括号内嵌实际值） */
    _formatExprReadable: function (node, parentToken) {
      if (!node) return '?'
      if (node.type === 'VARIABLE') {
        var label = this.varMap[node.token] || node.token
        if (node.value !== undefined) return label + ' (' + this._fv(node.value) + ')'
        return label
      }
      if (node.type === 'VALUE' || node.type === 'PRIMARY') {
        return this._fv(node.value !== undefined ? node.value : node.token)
      }
      if (node.type === 'OPERATOR' && node.children) {
        if (node.children.length === 2) {
          var left = this._formatExprReadable(node.children[0], node.token)
          var right = this._formatExprReadable(node.children[1], node.token)
          var op = node.token
          if (op === '*') op = '×'
          if (op === '/') op = '÷'
          var expr = left + ' ' + op + ' ' + right
          var needParen = parentToken && this._opPrec(parentToken) > this._opPrec(node.token)
          return needParen ? '(' + expr + ')' : expr
        }
        if (node.children.length === 1) {
          return node.token + this._formatExprReadable(node.children[0], node.token)
        }
      }
      if (node.type === 'FUNCTION' || node.type === 'METHOD') {
        var args = (node.children || []).map(function (c) { return this._formatExprReadable(c) }.bind(this))
        return (node.token || '?') + '(' + args.join(', ') + ')'
      }
      return node.token || '?'
    },
    /** 运算符优先级（用于判断是否需要括号） */
    _opPrec: function (token) {
      var map = { '+': 1, '-': 1, '*': 2, '/': 2, '%': 2 }
      return map[token] || 0
    },

    /** 图遍历：找到决策节点的汇合点（复刻后端 GraphScriptGenerator.findMergeNode 逻辑） */
    _findGraphMerge: function (decisionId, outEdgeMap, nodeMap) {
      var outs = outEdgeMap[decisionId] || []
      if (outs.length < 2) return outs.length > 0 ? outs[0].target : null
      var branchSets = []
      for (var b = 0; b < outs.length; b++) {
        var reachable = {}
        var queue = [outs[b].target]
        while (queue.length > 0) {
          var nid = queue.shift()
          if (reachable[nid]) continue
          reachable[nid] = true
          var e = outEdgeMap[nid] || []
          for (var j = 0; j < e.length; j++) queue.push(e[j].target)
        }
        branchSets.push(reachable)
      }
      var firstKeys = Object.keys(branchSets[0])
      for (var i = 0; i < firstKeys.length; i++) {
        var nid = firstKeys[i]
        var ok = true
        for (var j = 1; j < branchSets.length; j++) { if (!branchSets[j][nid]) { ok = false; break } }
        if (ok && nodeMap[nid] && nodeMap[nid].type === 'join') return nid
      }
      for (var i = 0; i < firstKeys.length; i++) {
        var nid = firstKeys[i]
        var ok = true
        for (var j = 1; j < branchSets.length; j++) { if (!branchSets[j][nid]) { ok = false; break } }
        if (ok) return nid
      }
      return null
    },

    // ─── 决策流解析 ───
    _walkFlow: function (stmts, steps) {
      for (var i = 0; i < stmts.length; i++) {
        var s = stmts[i]
        if (!s) continue
        if (s.type === 'DEFINE_FUNCTION' || s.type === 'DEFINE_MACRO') continue
        if (s.type === 'IF') {
          var cond = s.children && s.children[0]
          var thenBlock = s.children && s.children[1]
          var elseBlock = s.children && s.children[2]
          var condResult = cond && cond.value
          steps.push({
            type: 'decision',
            text: this._condTextFull(cond),
            result: condResult === true,
            detail: condResult !== true ? this._condActualValues(cond) : ''
          })
          if (condResult === true) {
            var branchLabel = this._condBranchLabel(cond, true)
            if (branchLabel) steps.push({ type: 'branch', label: branchLabel })
            this._walkFlow(this._unwrapList(thenBlock), steps)
          } else if (elseBlock) {
            var branchLabel2 = this._condBranchLabel(cond, false)
            if (branchLabel2) steps.push({ type: 'branch', label: branchLabel2 })
            this._walkFlow(this._unwrapList(elseBlock), steps)
          }
          return
        } else if (s.type === 'FUNCTION' || s.type === 'METHOD') {
          steps.push({ type: 'action', text: '调用 ' + this._formatFuncCallExpr(s) + ' → ' + this._fv(s.value), value: this._fv(s.value), isFunc: true })
        } else if (s.type === 'OPERATOR' && s.token === '=') {
          var vn = s.children && s.children[0] && s.children[0].token
          var val = s.value
          var rhs2 = s.children && s.children[1]
          var isFunc2 = rhs2 && (rhs2.type === 'FUNCTION' || rhs2.type === 'METHOD')
          if (isFunc2) {
            steps.push({ type: 'action', text: (this.varMap[vn] || vn) + ' = ' + this._formatFuncCallExpr(rhs2), value: this._fv(val), isFunc: true })
          } else {
            steps.push({ type: 'action', text: (this.varMap[vn] || vn) + ' = ' + this._fv(val), value: this._fv(val) })
          }
        }
      }
    },
    _condTextFull: function (node) {
      if (!node) return '?'
      var comps = this._flattenAnd(node)
      var parts = []
      for (var i = 0; i < comps.length; i++) {
        var c = comps[i]
        if (c.children && c.children.length === 2) {
          var vn = c.children[0].token || '?'
          var label = this.varMap[vn] || vn
          var opCn = OP_CN[c.token] || c.token
          var val = c.children[1].value !== undefined ? c.children[1].value : c.children[1].token
          parts.push(label + ' ' + opCn + ' "' + this._displayVal(val) + '"')
        } else {
          parts.push(c.token || '?')
        }
      }
      return parts.join(' 且 ')
    },
    _condActualValues: function (condNode) {
      if (!condNode) return ''
      var comps = this._flattenAnd(condNode)
      var parts = []
      for (var i = 0; i < comps.length; i++) {
        var c = comps[i]
        if (c.children && c.children[0] && c.value === false) {
          var vn = c.children[0].token
          var label = this.varMap[vn] || vn
          parts.push(label + ' 实际值："' + this._fv(c.children[0].value) + '"')
        }
      }
      return parts.join('，')
    },
    _condBranchLabel: function (condNode, result) {
      if (!condNode) return ''
      var comps = this._flattenAnd(condNode)
      if (comps.length === 0) return ''
      var first = comps[0]
      if (first.children && first.children[1]) {
        var val = first.children[1].value !== undefined ? first.children[1].value : first.children[1].token
        if (result) return this._fv(val)
        if (first.children[0] && first.children[0].value !== undefined) return this._fv(first.children[0].value)
      }
      return ''
    },
    /** 从 FUNCTION/METHOD 追踪节点中提取参数列表 */
    _buildFuncArgs: function (funcNode) {
      if (!funcNode || !funcNode.children) return []
      var self = this
      return funcNode.children.map(function (c) {
        var name = c.token || '?'
        var label = (c.type === 'VARIABLE') ? (self.varMap[name] || name) : name
        var val = c.value !== undefined ? self._fv(c.value) : '?'
        return { name: name, label: label, value: val, display: label + '=' + val }
      })
    },
    /** 递归在表达式树中查找所有 FUNCTION/METHOD 节点 */
    _findFuncCalls: function (node) {
      if (!node) return []
      var result = []
      if (node.type === 'FUNCTION' || node.type === 'METHOD') {
        result.push(node)
      }
      if (node.children) {
        for (var i = 0; i < node.children.length; i++) {
          result = result.concat(this._findFuncCalls(node.children[i]))
        }
      }
      return result
    },
    /** 格式化函数调用表达式（含参数值） */
    _formatFuncCallExpr: function (funcNode) {
      if (!funcNode) return '?'
      var name = this.funcDisplayName(funcNode.token || '?')
      var self = this
      var args = (funcNode.children || []).map(function (c) {
        if (c.type === 'VARIABLE') {
          var label = self.varMap[c.token] || c.token
          return c.value !== undefined ? label + '(' + self._fv(c.value) + ')' : label
        }
        return self._fv(c.value !== undefined ? c.value : c.token)
      })
      return name + '(' + args.join(', ') + ')'
    },
    _unwrapList: function (node) {
      if (!node) return []
      if ((node.type === 'BLOCK' || node.type === 'STATEMENT') && node.children) {
        var result = []
        for (var i = 0; i < node.children.length; i++) {
          var c = node.children[i]
          if (c && typeof c === 'object') {
            if ((c.type === 'BLOCK' || c.type === 'STATEMENT') && c.children) {
              for (var j = 0; j < c.children.length; j++) { if (c.children[j]) result.push(c.children[j]) }
            } else { result.push(c) }
          }
        }
        return result
      }
      return [node]
    }
  }
}
</script>

<style scoped>
.trace-wrap { font-size: 13px; color: #303133; line-height: 1.5; }

/* ── 区块（默认视图） ── */
.t-sec { padding: 14px 0; }
.t-sec + .t-sec { border-top: 1px solid #EBEEF5; }
.t-hd { font-size: 12px; color: #909399; margin-bottom: 10px; font-weight: 500; letter-spacing: 0.5px; }
.var-list { display: flex; flex-wrap: wrap; gap: 8px; }
.var-tag { display: inline-flex; align-items: center; height: 28px; padding: 0 10px; background: #F2F6FC; border: 1px solid #EBEEF5; border-radius: 4px; font-size: 12px; gap: 4px; }
.var-k { color: #606266; }
.var-v { font-weight: 600; }
.var-v.is-true { color: #67C23A; } .var-v.is-false { color: #F56C6C; } .var-v.is-val { color: #409EFF; }
.tree-viewport { overflow-x: auto; padding: 8px 0 16px; }
.tree-canvas { display: inline-flex; justify-content: center; min-width: 100%; }
.t-sec--result { display: flex; align-items: center; gap: 10px; }
.result-label { font-size: 12px; color: #909399; flex-shrink: 0; }
.result-val { font-size: 15px; font-weight: 600; }
.result-val.is-true { color: #67C23A; } .result-val.is-false { color: #F56C6C; } .result-val.is-val { color: #409EFF; }
.t-empty { text-align: center; padding: 36px 0; color: #C0C4CC; font-size: 13px; }
.fullscreen-btn { float: right; padding: 0; font-size: 12px; color: #909399; }
.fullscreen-btn:hover { color: #409EFF; }
.fs-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.45); z-index: 3000; display: flex; align-items: center; justify-content: center; }
.fs-panel { background: #fff; border-radius: 6px; width: 94vw; height: 90vh; display: flex; flex-direction: column; box-shadow: 0 4px 20px rgba(0,0,0,0.15); }
.fs-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-bottom: 1px solid #EBEEF5; flex-shrink: 0; }
.fs-title { font-size: 14px; font-weight: 500; color: #303133; }
.fs-close { padding: 0; font-size: 18px; color: #909399; }
.fs-close:hover { color: #303133; }
.fs-body { flex: 1; overflow: auto; padding: 24px; }

/* ═══════ 决策流 - 卡片式 ═══════ */
.fc-wrap { }
.fc-steps { padding: 0 4px; }
.fc-step { }
.fc-connector { padding-left: 19px; }
.fc-conn-line { width: 2px; height: 20px; background: #e4e7ed; }
.fc-step-row { display: flex; align-items: flex-start; gap: 12px; }
.fc-icon {
  width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; color: #fff;
  flex-shrink: 0; margin-top: 8px;
}
.fc-icon--decision { background: #fa8c16; }
.fc-icon--calculation { background: #1890ff; }
.fc-icon--assign { background: #13c2c2; }
.fc-icon--function { background: #722ed1; }
.fc-icon--end { background: #52c41a; }

.fc-card {
  flex: 1; border: 1px solid #e4e7ed; border-radius: 8px;
  background: #fff; overflow: hidden;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  border-left: 3px solid #e4e7ed;
}
.fc-card--decision { border-left-color: #fa8c16; }
.fc-card--calculation { border-left-color: #1890ff; }
.fc-card--assign { border-left-color: #13c2c2; }
.fc-card--function { border-left-color: #722ed1; }
.fc-card--end { border-left-color: #52c41a; background: #f6ffed; }

.fc-card-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 16px; border-bottom: 1px solid #f0f0f0;
}
.fc-card-title { font-size: 14px; font-weight: 600; color: #303133; }
.fc-card-badge {
  font-size: 11px; padding: 2px 10px; border-radius: 10px; font-weight: 500; white-space: nowrap;
}
.fc-badge--hit { background: #e6f7ff; color: #1890ff; }
.fc-badge--miss { background: #fff2e8; color: #fa541c; }
.fc-badge--done { background: #e6f7ff; color: #1890ff; }
.fc-badge--end { background: #f6ffed; color: #52c41a; }

.fc-card-body { padding: 12px 16px; }

.fc-cond-row {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 0; font-size: 13px; line-height: 1.8;
}
.fc-cond-var { font-family: 'Consolas', 'Monaco', monospace; color: #1890ff; background: #f0f7ff; padding: 1px 6px; border-radius: 3px; }
.fc-cond-op { color: #909399; font-weight: 600; letter-spacing: -1px; }
.fc-cond-val { font-family: 'Consolas', 'Monaco', monospace; color: #595959; }
.fc-cond-check { font-size: 12px; margin-left: auto; font-weight: 500; white-space: nowrap; }
.fc-cond-check.is-true { color: #52c41a; }
.fc-cond-check.is-false { color: #ff4d4f; }

.fc-action-bar {
  margin-top: 8px; padding: 8px 12px;
  border-left: 3px solid #52c41a; background: #f6ffed;
  border-radius: 0 4px 4px 0; font-size: 13px;
}
.fc-action-bar--else { border-left-color: #fa8c16; background: #fff7e6; }
.fc-action-prefix { color: #8c8c8c; margin-right: 6px; font-size: 12px; }
.fc-action-text { color: #303133; margin-right: 12px; }

/* 函数调用卡片内容 */
.fc-func-row {
  display: flex; flex-direction: column; gap: 6px;
}
.fc-func-name {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 14px; font-weight: 600; color: #531dab;
}
.fc-func-name code {
  font-family: 'Consolas', 'Monaco', monospace;
  background: #f9f0ff; padding: 1px 8px; border-radius: 3px;
  border: 1px solid #d3adf7;
}
.fc-func-icon {
  font-weight: 700; font-size: 15px; font-style: italic;
  color: #722ed1;
}
.fc-func-args {
  display: flex; flex-wrap: wrap; align-items: center; gap: 6px;
  font-size: 12px; padding-left: 2px;
}
.fc-func-args-label { color: #8c8c8c; }
.fc-func-arg {
  display: inline-flex; align-items: center; gap: 3px;
  background: #fafafa; border: 1px solid #e8e8e8;
  padding: 1px 8px; border-radius: 3px;
}
.fc-func-arg-name { color: #595959; font-weight: 500; }
.fc-func-arg-eq { color: #bfbfbf; }
.fc-func-arg-val { font-family: 'Consolas', 'Monaco', monospace; color: #1890ff; }

.fc-expr-row {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
}
.fc-expr {
  font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; color: #303133;
  flex: 1; word-break: break-all; line-height: 1.7;
}
.fc-expr-result {
  font-size: 18px; font-weight: 700; color: #1890ff; flex-shrink: 0;
  padding: 2px 12px; background: #f0f7ff; border-radius: 4px;
}
.fc-expr-result.is-end { color: #52c41a; background: #f6ffed; font-size: 20px; }
.fc-expr-row--result-only { justify-content: flex-start; }
.fc-expr-row--result-only .fc-expr-result.is-result-map {
  flex: 1; max-width: 100%; text-align: left; white-space: normal; word-break: break-word;
  font-size: 15px; font-weight: 600; line-height: 1.6;
}

/* ═══════ 决策树 - 层级树 ═══════ */
.mv-tree { padding: 12px 0; }
.dt-path {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 10px 14px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 6px;
  margin-bottom: 16px;
  font-size: 13px;
}
.dt-path-label { color: #389e0d; font-weight: 600; margin-right: 4px; }
.dt-path-seg { display: inline-flex; align-items: center; gap: 4px; }
.dt-path-arrow { color: #b7eb8f; font-size: 14px; }
.dt-path-text { color: #237804; font-weight: 500; }
.dt-body {
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}
.dt-legend {
  display: flex;
  gap: 24px;
  margin-top: 14px;
  padding: 8px 14px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
  color: #8c8c8c;
}
.dt-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dt-legend-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dt-legend-dot--hit { background: #52c41a; }
.dt-legend-dot--blocked { background: #ff4d4f; }
.dt-legend-dot--skipped { background: #d9d9d9; }

/* ═══════ 评分卡 ═══════ */
.mv-score { padding: 12px 0; }
.sc-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.sc-table th { background: #FAFAFA; padding: 8px 12px; border: 1px solid #EBEEF5; font-weight: 600; color: #606266; text-align: left; }
.sc-table td { padding: 8px 12px; border: 1px solid #EBEEF5; color: #303133; }
.sc-table tr.sc-hit td { background: #f0f9eb; }
.sc-table tr.sc-result td { background: #f0f9eb; font-weight: 600; }
.sc-table tr.sc-skipped td { background: #fafafa; color: #c0c4cc; }
.sc-table tr.sc-miss td { background: #fff; }
.sc-table .is-pos { color: #67C23A; }
.sc-table .is-neg { color: #F56C6C; }
.sc-status { font-size: 11px; font-weight: 500; white-space: nowrap; }
.sc-status--hit { color: #67C23A; }
.sc-status--miss { color: #E6A23C; }
.sc-status--skipped { color: #C0C4CC; }

/* ═══════ 交叉表 / 复杂交叉表：执行日志矩阵高亮 ═══════ */
.trace-cross-matrix-wrap {
  overflow-x: auto;
  border-radius: 6px;
  border: 1px solid #e8e8e8;
  margin-top: 12px;
  background: #fafafa;
  padding: 12px;
}
.trace-cross-matrix {
  border-collapse: collapse;
  width: 100%;
  background: #fff;
}
.trace-cross-matrix th,
.trace-cross-matrix td {
  border: 1px solid #e8e8e8;
  padding: 8px 10px;
  text-align: center;
  vertical-align: middle;
  font-size: 13px;
}
.trace-corner-cell {
  background: #f5f5f5;
  position: relative;
  min-width: 100px;
  min-height: 56px;
  padding: 0 !important;
}
.trace-corner-inner-adv {
  position: relative;
  width: 100%;
  min-height: 56px;
}
.trace-corner-row {
  position: absolute;
  bottom: 6px;
  left: 8px;
  font-size: 11px;
  color: #888;
}
.trace-corner-col {
  position: absolute;
  top: 6px;
  right: 8px;
  font-size: 11px;
  color: #888;
}
.trace-corner-divider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to bottom right, transparent calc(50% - 0.5px), #d0d0d0, transparent calc(50% + 0.5px));
  pointer-events: none;
}
.trace-col-header {
  font-weight: 600;
  color: #333;
}
.trace-cross-matrix--simple .trace-col-header {
  background: #e8f3ff;
}
.trace-cross-matrix--adv .trace-col-header {
  background: #daeeff;
}
.trace-row-header {
  background: #f0fff4;
  font-weight: 500;
  color: #333;
  white-space: nowrap;
}
.trace-cross-matrix--adv .trace-row-header {
  background: #e0f5e9;
}
.trace-data-cell {
  background: #fff;
}
.trace-cell-inner {
  display: inline-block;
  min-width: 48px;
  font-weight: 500;
}
.trace-cross-cell--hit {
  background: #d9f7be !important;
  box-shadow: inset 0 0 0 2px #52c41a;
  border-radius: 4px;
}
.trace-cross-axis--hit {
  background: #b7eb8f !important;
  box-shadow: inset 0 0 0 1px #73d13d;
}
.trace-cross-legend {
  margin-top: 10px;
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
}
.trace-cross-legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: #d9f7be;
  border: 2px solid #52c41a;
  flex-shrink: 0;
}

/* ═══════ 决策表 / 交叉表 ═══════ */
.mv-table { padding: 12px 0; }
.rt-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.rt-table th { background: #FAFAFA; padding: 8px 10px; border: 1px solid #EBEEF5; font-weight: 600; color: #606266; text-align: center; white-space: nowrap; }
.rt-table td { padding: 8px 10px; border: 1px solid #EBEEF5; text-align: center; color: #303133; }
.rt-table tr.rt-hit { background: #f0f9eb; }
.rt-table tr.rt-hit td { font-weight: 600; }
.rt-badge { color: #67C23A; font-weight: 700; white-space: nowrap; }
.rt-footer { text-align: center; margin-top: 10px; font-size: 12px; color: #606266; line-height: 1.8; }
</style>
