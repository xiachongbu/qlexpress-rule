<template>
  <div class="sc-designer">
    <!-- 顶部工具栏 -->
    <div class="sc-header">
      <div class="sc-title-area">
        <i class="el-icon-data-line sc-title-icon" />
        <span class="sc-title">评分卡设计器</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">{{ model.scoreItems.length }} 个评分项</el-tag>
      </div>
      <div class="sc-toolbar">
        <el-button size="small" icon="el-icon-plus" @click="addScoreItem">添加评分项</el-button>
        <el-button size="small" icon="el-icon-plus" @click="addThreshold">添加等级</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-document" @click="handleSave">保存</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <!-- 基础配置 -->
    <div class="sc-card sc-base-config">
      <div class="sc-card-title"><i class="el-icon-setting" /> 基础配置</div>
      <div class="base-config-row">
        <div class="base-config-item">
          <span class="base-config-label">初始分数</span>
          <el-input-number v-model="model.initialScore" :min="0" :max="1000" size="small" style="width:130px;" />
        </div>
        <div class="base-config-item">
          <span class="base-config-label">结果变量</span>
          <var-picker
            v-if="varPickerOptions.length"
            :vars="varPickerOptions"
            :value="model.resultVar.varCode"
            placeholder="选择变量、常量或对象字段..."
            width="200px"
            type-filter="NUMBER"
            :show-all-when-filter-empty="true"
            @select="onResultVarSelect"
          />
          <template v-else>
            <el-input v-model="model.resultVar.varCode" size="small" placeholder="如 totalScore" style="width:160px;" />
            <span style="margin:0 4px;color:#999;">|</span>
            <el-input v-model="model.resultVar.varLabel" size="small" placeholder="如 总评分" style="width:140px;" />
          </template>
        </div>
      </div>
    </div>

    <!-- 评分项配置 -->
    <div class="sc-card">
      <div class="sc-card-title sc-card-title-row">
        <span><i class="el-icon-s-check" /> 评分项配置</span>
        <div class="weight-summary">
          <span class="weight-label">总权重：</span>
          <el-progress
            :percentage="totalWeightPercent"
            :color="totalWeightColor"
            :stroke-width="10"
            style="width:150px;display:inline-block;vertical-align:middle;"
          />
          <span class="weight-value" :style="{ color: totalWeightColor }">{{ totalWeightDisplay }}</span>
        </div>
      </div>

      <div class="score-items">
        <div
          v-for="(item, idx) in model.scoreItems"
          :key="idx"
          class="score-item-card"
        >
          <div class="score-item-header">
            <span class="item-index">{{ idx + 1 }}</span>
            <el-input
              v-model="item.conditionLabel"
              size="small"
              placeholder="评分项名称（如 纳税信用等级）"
              class="item-label-input"
            />
            <el-button
              type="text"
              size="small"
              icon="el-icon-delete"
              style="color:#F56C6C;"
              @click="removeScoreItem(idx)"
            />
          </div>

          <div class="score-item-body">
            <div class="score-item-row">
              <span class="item-field-label">条件</span>
              <div class="condition-row">
                <var-picker
                  v-if="varPickerOptions.length"
                  :vars="varPickerOptions"
                  :value="item.condVar"
                  placeholder="选择变量"
                  width="100%"
                  class="cond-var"
                  @select="v => { item.condVar = v.varCode; item.condVarType = v.varType }"
                />
                <el-input v-else v-model="item.condVar" size="small" placeholder="变量编码" class="cond-var" />
                <el-select v-model="item.condOperator" size="small" class="cond-op">
                  <el-option label="等于" value="==" />
                  <el-option label="不等于" value="!=" />
                  <el-option label="大于" value=">" />
                  <el-option label="大于等于" value=">=" />
                  <el-option label="小于" value="<" />
                  <el-option label="小于等于" value="<=" />
                </el-select>
                <el-input v-model="item.condValue" size="small" placeholder="值" class="cond-val" />
              </div>
            </div>

            <div class="score-item-row score-weight-row">
              <div class="score-col">
                <span class="item-field-label">命中得分</span>
                <el-input-number
                  v-model="item.score"
                  :min="0"
                  :max="9999"
                  size="small"
                  style="width:120px;"
                />
              </div>
              <div class="weight-col">
                <span class="item-field-label">
                  权重
                  <el-tooltip content="权重决定该项分数在总分中的占比（0~2，推荐各项权重加总=1.0）" placement="top" effect="light">
                    <i class="el-icon-question tip-icon" />
                  </el-tooltip>
                </span>
                <div class="weight-slider-row">
                  <el-slider
                    v-model="item.weight"
                    :min="0"
                    :max="2"
                    :step="0.05"
                    :format-tooltip="v => v.toFixed(2)"
                    show-input
                    input-size="small"
                    style="flex:1;"
                  />
                </div>
              </div>
              <div class="weighted-score">
                <span class="item-field-label">加权分</span>
                <span class="weighted-value">{{ (item.score * item.weight).toFixed(1) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="model.scoreItems.length === 0" class="sc-empty">
          <i class="el-icon-s-check sc-empty-icon" />
          <p>暂无评分项，点击「添加评分项」开始配置</p>
        </div>
      </div>
    </div>

    <!-- 计算公式预览 -->
    <div class="sc-card sc-formula">
      <div class="sc-card-title"><i class="el-icon-files" /> 计算公式预览</div>
      <div class="formula-content">
        <div class="formula-text">
          <code>{{ model.resultVar.varCode || 'score' }}</code>
          <span class="op"> = </span>
          <span v-if="model.initialScore !== 0">
            <code>{{ model.initialScore }}</code>
            <span class="op"> + </span>
          </span>
          <template v-for="(item, idx) in model.scoreItems">
            <span :key="idx" class="formula-term">
              <span class="formula-cond">IF({{ item.conditionLabel || item.condVar || '条件' + (idx + 1) }} {{ item.condOperator }} {{ item.condValue }})</span>
              <span class="op"> × </span>
              <code>{{ item.score }}</code>
              <template v-if="item.weight !== 1">
                <span class="op"> × </span>
                <code>{{ item.weight.toFixed(2) }}</code>
              </template>
            </span>
            <span v-if="idx < model.scoreItems.length - 1" :key="'op-' + idx" class="op"> + </span>
          </template>
          <span v-if="model.scoreItems.length === 0" class="formula-empty">（暂未配置评分项）</span>
        </div>
      </div>
    </div>

    <!-- 分数等级配置 -->
    <div class="sc-card">
      <div class="sc-card-title"><i class="el-icon-medal" /> 分数等级配置</div>
      <div class="threshold-list">
        <div
          v-for="(thresh, ti) in model.thresholds"
          :key="ti"
          class="threshold-item"
        >
          <div class="thresh-color-bar" :style="{ background: thresholdColor(ti) }" />
          <div class="thresh-range">
            <el-input-number
              v-model="thresh.min"
              size="small"
              :min="0"
              :controls="false"
              style="width:100px;"
            />
            <span class="thresh-sep">≤ 分数 &lt;</span>
            <el-input-number
              v-model="thresh.max"
              size="small"
              :min="thresh.min"
              :controls="false"
              style="width:100px;"
            />
          </div>
          <div class="thresh-result">
            <el-input v-model="thresh.result" size="small" placeholder="等级名称（如 优质客户）" style="width:100%;min-width:240px;" />
          </div>
          <el-tag :color="thresholdColor(ti)" effect="dark" size="small" class="thresh-badge">
            {{ thresh.result || '等级 ' + (ti + 1) }}
          </el-tag>
          <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C;" @click="removeThreshold(ti)" />
        </div>
        <div v-if="model.thresholds.length === 0" class="sc-empty">
          暂未配置等级，点击「添加等级」设置分数区间
        </div>
      </div>

      <!-- 等级色带预览 -->
<!--      <div v-if="model.thresholds.length > 0" class="threshold-visual">-->
<!--        <div-->
<!--          v-for="(thresh, ti) in sortedThresholds"-->
<!--          :key="ti"-->
<!--          class="visual-segment"-->
<!--          :style="{-->
<!--            flex: thresh.max - thresh.min,-->
<!--            background: thresholdColor(ti),-->
<!--            opacity: 0.85-->
<!--          }"-->
<!--        >-->
<!--          <span class="segment-label">{{ thresh.result || ('等级' + (ti + 1)) }}</span>-->
<!--          <span class="segment-range">{{ thresh.min }}~{{ thresh.max }}</span>-->
<!--        </div>-->
<!--      </div>-->
    </div>

    <!-- 脚本预览/编辑面板 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="mode => scriptMode = mode"
    />
    <div v-if="scriptMode === 'script'" class="script-override-banner">
      <i class="el-icon-warning" /> 脚本覆盖模式已激活，可视化编辑暂停。
    </div>

    <!-- 测试执行弹窗 -->
    <el-dialog title="测试执行" :visible.sync="testVisible" width="600px" append-to-body>
      <p class="test-hint"><i class="el-icon-info" /> 请输入测试参数（JSON 格式），包含各评分条件中使用的变量</p>
      <el-input
        v-model="testParamsJson"
        type="textarea"
        :rows="6"
        placeholder='{"creditLevel": "A", "income": 600000, "age": 25}'
      />
      <template slot="footer">
        <el-button size="small" @click="testVisible = false">取消</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="doTest">执行</el-button>
      </template>
      <div v-if="testResult" class="test-result">
        <el-alert
          :title="testResult.success ? '执行成功' : '执行失败'"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="margin-bottom:10px;"
        />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="model.resultVar.varLabel || '评分结果'">
            <strong style="font-size:16px;color:#1890ff;">{{ testResult.result }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ testResult.executeTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item v-if="testResult.errorMessage" label="错误">
            <span style="color:#F56C6C">{{ testResult.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { saveContent, compileRule, executeRule, getContent } from '@/api/definition'
import varPickerMixin from '@/mixins/varPickerMixin'
import VarPicker from '@/components/common/VarPicker.vue'
import ScriptPanel from '@/components/common/ScriptPanel.vue'

const THRESHOLD_COLORS = ['#52c41a', '#1890ff', '#fa8c16', '#f5222d', '#722ed1', '#13c2c2', '#eb2f96']

export default {
  name: 'Scorecard',
  components: { VarPicker, ScriptPanel },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        initialScore: 0,
        scoreItems: [],
        resultVar: { varCode: '', varLabel: '' },
        thresholds: []
      },
      scriptMode: 'visual',
      testVisible: false,
      testParamsJson: '{}',
      testResult: null
    }
  },
  computed: {
    totalWeight() {
      return this.model.scoreItems.reduce((sum, item) => sum + (item.weight || 0), 0)
    },
    totalWeightPercent() {
      return Math.min(100, Math.round(this.totalWeight * 100))
    },
    totalWeightDisplay() {
      return this.totalWeight.toFixed(2)
    },
    totalWeightColor() {
      const w = this.totalWeight
      if (Math.abs(w - 1.0) < 0.05) return '#52c41a'
      if (w > 1.05) return '#f5222d'
      return '#fa8c16'
    },
    sortedThresholds() {
      return [...this.model.thresholds].sort((a, b) => (b.min || 0) - (a.min || 0))
    }
  },
  created() {
    this.definitionId = this.$route.params.id
    this.loadContent()
  },
  methods: {
    async loadContent() {
      try {
        const res = await getContent(this.definitionId)
        const content = res && res.data ? res.data : res
        if (content && content.modelJson && content.modelJson !== '{}') {
          this.model = JSON.parse(content.modelJson)
        }
      } catch (e) {
        this.$message.error('加载内容失败: ' + (e.message || '未知错误'))
      } finally {
        this.normalizeModel()
        this.contentLoaded = true
        this._trySyncModelVarRefs()
      }
    },
    /** 加载最新变量后，同步 model 中结果变量的 varCode 和 varLabel */
    _syncModelVarRefs() {
      if (this.model.resultVar && this.syncVarItem(this.model.resultVar)) {
        this.$forceUpdate()
      }
    },
    normalizeModel() {
      if (this.model.initialScore == null) this.$set(this.model, 'initialScore', 0)
      if (!this.model.scoreItems) this.$set(this.model, 'scoreItems', [])
      if (!this.model.resultVar) this.$set(this.model, 'resultVar', { varCode: '', varLabel: '' })
      if (!this.model.thresholds) this.$set(this.model, 'thresholds', [])
      this.model.scoreItems.forEach(item => {
        if (item.score == null) this.$set(item, 'score', 1)
        if (item.weight == null) this.$set(item, 'weight', 1.0)
        if (!item.condVar && item.condition) {
          this.parseCondition(item)
        }
        if (item.condVar == null) this.$set(item, 'condVar', '')
        if (item.condOperator == null) this.$set(item, 'condOperator', '==')
        if (item.condValue == null) this.$set(item, 'condValue', '')
        if (item.condVarType == null) this.$set(item, 'condVarType', 'STRING')
      })
    },
    /** 从已有 condition 字符串反解出结构化字段 */
    parseCondition(item) {
      const ops = ['>=', '<=', '!=', '==', '>', '<']
      for (const op of ops) {
        const idx = item.condition.indexOf(' ' + op + ' ')
        if (idx >= 0) {
          item.condVar = item.condition.substring(0, idx).trim()
          item.condOperator = op
          let val = item.condition.substring(idx + op.length + 2).trim()
          if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
            val = val.slice(1, -1)
            item.condVarType = 'STRING'
          } else {
            item.condVarType = 'NUMBER'
          }
          item.condValue = val
          return
        }
      }
    },
    thresholdColor(idx) {
      return THRESHOLD_COLORS[idx % THRESHOLD_COLORS.length]
    },
    onResultVarSelect(v) {
      if (!v) return
      const varLabel = (v.varObj && v.varObj.varLabel) || v.varLabel || v.varCode
      const _varId = v.varObj && v.varObj.id ? v.varObj.id : null
      this.$set(this.model, 'resultVar', {
        ...this.model.resultVar,
        varCode: v.varCode,
        varLabel,
        _varId
      })
    },
    addScoreItem() {
      this.model.scoreItems.push({ condVar: '', condOperator: '==', condValue: '', condVarType: 'STRING', condition: '', conditionLabel: '', score: 1, weight: 1.0 })
    },
    removeScoreItem(index) {
      this.model.scoreItems.splice(index, 1)
    },
    addThreshold() {
      const last = this.model.thresholds[this.model.thresholds.length - 1]
      const min = last ? last.max : 0
      this.model.thresholds.push({ min, max: min + 50, result: '', resultType: 'STRING' })
    },
    removeThreshold(index) {
      this.model.thresholds.splice(index, 1)
    },
    async handleSave() {
      this.model.scoreItems.forEach(item => {
        if (item.condVar && item.condOperator && item.condValue != null && item.condValue !== '') {
          const needQuote = !item.condVarType || item.condVarType === 'STRING' || item.condVarType === 'ENUM'
          const val = needQuote ? '"' + String(item.condValue).replace(/"/g, '\\"') + '"' : item.condValue
          item.condition = item.condVar + ' ' + item.condOperator + ' ' + val
        }
      })
      await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(this.model) })
      this.$message.success('保存成功')
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (res && res.data && res.data.success) {
        this.$message.success('编译成功')
        // 异步刷新变量映射和脚本面板
        await this.loadProjectVars(this.definitionId)
        if (this.$refs.scriptPanel) {
          this.$refs.scriptPanel.refresh()
        }
      } else {
        this.$message.error('编译失败: ' + (res && res.data ? res.data.errorMessage : '未知错误'))
      }
    },
    handleTest() {
      this.testParamsJson = '{}'
      this.testResult = null
      this.testVisible = true
    },
    async doTest() {
      let params = {}
      try { params = JSON.parse(this.testParamsJson || '{}') } catch (e) {
        this.$message.error('参数 JSON 格式错误')
        return
      }
      const res = await executeRule({ definitionId: this.definitionId, params })
      this.testResult = res && res.data ? res.data : res
    }
  }
}
</script>

<style lang="scss" scoped>
.sc-designer {
  background: #f3f3f3;
  padding: 20px;
  min-height: 100%;
}

/* 顶部 */
.sc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 4px;
  padding: 14px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.sc-title-area {
  display: flex;
  align-items: center;
}
.sc-title-icon {
  font-size: 18px;
  color: #eb2f96;
  margin-right: 8px;
}
.sc-title {
  font-size: 16px;
  font-weight: bold;
  color: #282828;
}
.sc-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 通用卡片 */
.sc-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  margin-bottom: 16px;
}
.sc-card-title {
  font-size: 14px;
  font-weight: bold;
  color: #333;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 6px;

  i { color: #1890ff; }
}
.sc-card-title-row {
  justify-content: space-between;
}

/* 基础配置 */
.base-config-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  align-items: center;
}
.base-config-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.base-config-label {
  font-size: 13px;
  color: #666;
  white-space: nowrap;
}

/* 权重汇总 */
.weight-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weight-label {
  font-size: 13px;
  color: #666;
  font-weight: normal;
}
.weight-value {
  font-weight: bold;
  font-size: 14px;
  min-width: 36px;
}
.tip-icon {
  color: #999;
  cursor: pointer;
  &:hover { color: #1890ff; }
}

/* 评分项列表 */
.score-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.score-item-card {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
  transition: box-shadow 0.2s;
  &:hover {
    box-shadow: 0 2px 8px rgba(24,144,255,0.15);
    border-color: #91caff;
  }
}
.score-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}
.item-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-weight: bold;
}
.item-label-input {
  flex: 1;
}
.score-item-body {
  padding: 12px;
  background: #fff;
}
.score-item-row {
  margin-bottom: 10px;
  &:last-child { margin-bottom: 0; }
}
.condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cond-var { flex: 3; min-width: 0; }
.cond-op { flex: 2; min-width: 90px; }
.cond-val { flex: 2; min-width: 0; }
.score-weight-row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
}
.item-field-label {
  display: block;
  font-size: 12px;
  color: #888;
  margin-bottom: 5px;
}
.score-col {
  flex-shrink: 0;
}
.weight-col {
  flex: 1;
}
.weight-slider-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.weighted-score {
  flex-shrink: 0;
  text-align: center;
  min-width: 60px;
}
.weighted-value {
  font-size: 18px;
  font-weight: bold;
  color: #1890ff;
  display: block;
}

/* 计算公式 */
.sc-formula {
  background: #fffbe6;
  border: 1px solid #ffe58f;
}
.formula-content {
  overflow-x: auto;
}
.formula-text {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 13px;
  line-height: 1.8;

  code {
    font-family: 'Consolas', monospace;
    background: rgba(0,0,0,0.05);
    padding: 1px 5px;
    border-radius: 3px;
    color: #c41d7f;
  }
}
.op {
  color: #888;
  font-weight: bold;
  font-size: 13px;
}
.formula-term {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 4px;
  padding: 2px 8px;
}
.formula-cond {
  color: #d46b08;
  font-size: 12px;
}
.formula-empty {
  color: #bbb;
  font-style: italic;
}

/* 阈值等级 */
.threshold-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.threshold-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}
.thresh-color-bar {
  width: 4px;
  height: 36px;
  border-radius: 2px;
  flex-shrink: 0;
}
.thresh-range {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.thresh-sep {
  font-size: 12px;
  color: #888;
  white-space: nowrap;
}
.thresh-result {
  flex: 1;
}
.thresh-badge {
  flex-shrink: 0;
  border-color: transparent !important;
}

/* 等级色带 */
.threshold-visual {
  display: flex;
  height: 36px;
  border-radius: 6px;
  overflow: hidden;
  margin-top: 4px;
}
.visual-segment {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 60px;
  color: #fff;
  font-size: 11px;
  font-weight: bold;
  text-shadow: 0 1px 2px rgba(0,0,0,0.3);
  padding: 2px 4px;
  overflow: hidden;
}
.segment-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.segment-range {
  font-size: 10px;
  opacity: 0.85;
  font-weight: normal;
}

/* 空状态 */
.sc-empty {
  text-align: center;
  padding: 30px;
  color: #bbb;
  font-size: 13px;
}
.sc-empty-icon {
  font-size: 36px;
  color: #ddd;
  display: block;
  margin-bottom: 8px;
}

/* 测试 */
.test-hint { font-size: 12px; color: #909399; margin-bottom: 8px; }
.test-result { margin-top: 16px; }

.script-override-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff1f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: #cf1322;
  i { color: #f5222d; font-size: 14px; }
}
</style>
