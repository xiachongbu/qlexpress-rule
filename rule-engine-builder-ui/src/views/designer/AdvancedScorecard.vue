<template>
  <div class="asc-designer">
    <!-- 顶部工具栏 -->
    <div class="asc-header">
      <div class="asc-title-area">
        <i class="el-icon-data-line asc-title-icon" />
        <span class="asc-title">复杂评分卡设计器</span>
        <el-tag size="mini" type="info" style="margin-left:8px;">{{ totalDimensions }} 个评分维度</el-tag>
      </div>
      <div class="asc-toolbar">
        <el-button size="small" icon="el-icon-plus" @click="addGroup">添加维度组</el-button>
        <el-divider direction="vertical" />
        <el-button size="small" icon="el-icon-document" @click="handleSave">保存</el-button>
        <el-button size="small" type="warning" icon="el-icon-cpu" @click="handleCompile">编译</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="handleTest">测试</el-button>
      </div>
    </div>

    <!-- 基础配置 -->
    <div class="asc-card asc-base-config">
      <div class="asc-card-title"><i class="el-icon-setting" /> 基础配置</div>
      <div class="base-config-row">
        <div class="base-config-item">
          <span class="base-config-label">初始分数</span>
          <el-input-number v-model="model.initialScore" :min="0" :max="10000" size="small" style="width:130px;" />
        </div>
        <div class="base-config-item">
          <span class="base-config-label">结果变量</span>
          <var-picker
            v-if="varPickerOptions.length"
            :vars="varPickerOptions"
            :value="model.resultVar.varCode"
            placeholder="选择结果变量..."
            width="200px"
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

    <!-- 维度组列表 -->
    <div
      v-for="(group, gi) in model.dimensionGroups"
      :key="gi"
      class="asc-card asc-group"
    >
      <div class="asc-group-header">
        <div class="asc-group-left" @click="toggleGroup(gi)">
          <i :class="group._collapsed ? 'el-icon-arrow-right' : 'el-icon-arrow-down'" />
          <el-input
            v-model="group.groupLabel"
            size="small"
            placeholder="维度组名称（如 客户基础信息）"
            class="group-label-input"
            @click.native.stop
          />
          <el-tag size="mini" type="info">{{ (group.dimensions || []).length }} 维度</el-tag>
          <div class="group-weight-summary" @click.stop>
            <span class="weight-label">组权重</span>
            <el-input-number
              v-model="group.weight"
              :min="0"
              :max="2"
              :step="0.1"
              :precision="2"
              size="mini"
              controls-position="right"
              style="width:100px;"
            />
          </div>
        </div>
        <div class="asc-group-right">
          <el-button size="small" icon="el-icon-plus" @click="addDimension(gi)">添加维度</el-button>
          <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C;" @click="removeGroup(gi)" />
        </div>
      </div>

      <div v-show="!group._collapsed" class="asc-group-body">
        <div
          v-for="(dim, di) in group.dimensions"
          :key="di"
          class="asc-dimension"
        >
          <div class="dim-header">
            <span class="dim-index">{{ gi + 1 }}.{{ di + 1 }}</span>
            <el-input v-model="dim.varLabel" size="small" placeholder="维度名称" style="width:160px;" />
            <var-picker
              v-if="varPickerOptions.length"
              :vars="varPickerOptions"
              :value="dim.varCode"
              placeholder="主变量..."
              width="180px"
              @select="v => onDimVarSelect(gi, di, v)"
            />
            <el-input v-else v-model="dim.varCode" size="small" placeholder="变量编码" style="width:140px;" />
            <div class="dim-weight-area">
              <span class="item-field-label">权重</span>
              <el-input-number
                v-model="dim.weight"
                :min="0"
                :max="2"
                :step="0.05"
                :precision="2"
                size="mini"
                controls-position="right"
                style="width:90px;"
              />
            </div>
            <el-button size="mini" icon="el-icon-plus" @click="addRule(gi, di)">添加规则</el-button>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C;" @click="removeDimension(gi, di)" />
          </div>

          <!-- 规则表格 -->
          <table class="rule-table" v-if="dim.rules && dim.rules.length">
            <thead>
              <tr>
                <th class="col-idx">#</th>
                <th class="col-conditions">条件组合</th>
                <th class="col-score">分值</th>
                <th class="col-action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(rule, ri) in dim.rules" :key="ri">
                <td class="col-idx">{{ ri + 1 }}</td>
                <td class="col-conditions">
                  <div
                    v-for="(cond, ci) in rule.conditions"
                    :key="ci"
                    class="condition-row"
                  >
                    <var-picker
                      v-if="varPickerOptions.length"
                      :vars="varPickerOptions"
                      :value="cond.varCode"
                      placeholder="变量"
                      width="100%"
                      class="cond-var"
                      @select="v => { cond.varCode = v.varCode }"
                    />
                    <el-input v-else v-model="cond.varCode" size="mini" placeholder="变量" class="cond-var" />
                    <el-select v-model="cond.operator" size="mini" class="cond-op">
                      <el-option label="等于" value="==" />
                      <el-option label="不等于" value="!=" />
                      <el-option label="大于" value=">" />
                      <el-option label="大于等于" value=">=" />
                      <el-option label="小于" value="<" />
                      <el-option label="小于等于" value="<=" />
                    </el-select>
                    <el-input v-model="cond.value" size="mini" placeholder="值" class="cond-val" />
                    <el-button
                      v-if="rule.conditions.length > 1"
                      type="text" size="mini" icon="el-icon-close" style="color:#ccc;"
                      @click="rule.conditions.splice(ci, 1)"
                    />
                    <span v-if="ci < rule.conditions.length - 1" class="cond-and">且</span>
                  </div>
                  <el-button type="text" size="mini" icon="el-icon-plus" @click="addCondition(rule)">添加条件</el-button>
                </td>
                <td class="col-score">
                  <el-input-number v-model="rule.score" size="mini" :min="-9999" :max="9999" class="score-input" />
                </td>
                <td class="col-action">
                  <el-button type="text" size="mini" icon="el-icon-delete" style="color:#F56C6C;" @click="dim.rules.splice(ri, 1)" />
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="dim-empty">暂无规则，点击「添加规则」</div>
        </div>

        <div v-if="!group.dimensions || group.dimensions.length === 0" class="group-empty">
          暂无维度，点击「添加维度」
        </div>
      </div>
    </div>

    <div v-if="model.dimensionGroups.length === 0" class="asc-card asc-empty">
      <i class="el-icon-s-data" style="font-size:36px;color:#ddd;" />
      <p>暂无维度组，点击「添加维度组」开始配置</p>
    </div>

    <!-- 计算公式预览 -->
    <div class="asc-card asc-formula" v-if="model.dimensionGroups.length > 0">
      <div class="asc-card-title"><i class="el-icon-files" style="color:#d46b08;" /> 计算公式预览</div>
      <div class="formula-content">
        <div class="formula-text">
          <code>{{ model.resultVar.varCode || 'score' }}</code>
          <span class="op"> = </span>
          <span v-if="model.initialScore !== 0">
            <code>{{ model.initialScore }}</code>
            <span class="op"> + </span>
          </span>
          <template v-for="(group, gi) in model.dimensionGroups">
            <span :key="'g-' + gi" class="formula-group">
              <span class="formula-group-label">{{ group.groupLabel || '维度组' + (gi + 1) }}</span>
              <template v-if="group.weight != null && group.weight !== 1">
                <span class="op"> × </span>
                <code>{{ (group.weight || 0).toFixed(2) }}</code>
              </template>
              <span class="formula-dims">
                (
                <template v-for="(dim, di) in (group.dimensions || [])">
                  <span :key="'d-' + di" class="formula-term">
                    {{ dim.varLabel || dim.varCode || '维度' + (di + 1) }}
                    <template v-if="dim.weight != null && dim.weight !== 1">
                      <span class="op">×</span>{{ (dim.weight || 0).toFixed(2) }}
                    </template>
                  </span>
                  <span v-if="di < (group.dimensions || []).length - 1" :key="'dop-' + di" class="op"> + </span>
                </template>
                )
              </span>
            </span>
            <span v-if="gi < model.dimensionGroups.length - 1" :key="'gop-' + gi" class="op"> + </span>
          </template>
        </div>
      </div>
    </div>

    <!-- 权重汇总 -->
    <div class="asc-card" v-if="model.dimensionGroups.length > 0">
      <div class="asc-card-title asc-card-title-row">
        <span><i class="el-icon-s-check" /> 权重汇总</span>
        <div class="weight-summary">
          <span class="weight-label-sm">总权重：</span>
          <el-progress
            :percentage="totalWeightPercent"
            :color="totalWeightColor"
            :stroke-width="10"
            style="width:150px;display:inline-block;vertical-align:middle;"
          />
          <span class="weight-value" :style="{ color: totalWeightColor }">{{ totalWeight.toFixed(2) }}</span>
        </div>
      </div>
      <div class="weight-detail-list">
        <div v-for="(group, gi) in model.dimensionGroups" :key="gi" class="weight-detail-item">
          <span class="weight-detail-name">{{ group.groupLabel || '维度组' + (gi + 1) }}</span>
          <span class="weight-detail-val">组权重 {{ (group.weight || 1).toFixed(2) }}</span>
          <span class="weight-detail-dims">
            × ( <template v-for="(dim, di) in (group.dimensions || [])">
              <span :key="di">{{ dim.varLabel || '维度' }}:{{ (dim.weight || 1).toFixed(2) }}</span>
              <span v-if="di < (group.dimensions || []).length - 1" :key="'s-' + di">, </span>
            </template> )
          </span>
        </div>
      </div>
    </div>

    <!-- 分数等级配置 -->
    <div class="asc-card">
      <div class="asc-card-title asc-card-title-row">
        <span><i class="el-icon-medal" /> 分数等级配置</span>
        <el-button size="small" icon="el-icon-plus" @click="addThreshold">添加等级</el-button>
      </div>
      <div class="threshold-list">
        <div v-for="(thresh, ti) in model.thresholds" :key="ti" class="threshold-item">
          <div class="thresh-color-bar" :style="{ background: thresholdColor(ti) }" />
          <div class="thresh-range">
            <el-input-number v-model="thresh.min" size="small" :min="0" :controls="false" style="width:100px;" />
            <span class="thresh-sep">&le; 分数 &lt;</span>
            <el-input-number v-model="thresh.max" size="small" :min="thresh.min" :controls="false" style="width:100px;" />
          </div>
          <div class="thresh-result">
            <el-input v-model="thresh.result" size="small" placeholder="等级名称（如 低风险）" style="width:100%;min-width:200px;" />
          </div>
          <el-tag :color="thresholdColor(ti)" effect="dark" size="small" class="thresh-badge">
            {{ thresh.result || '等级 ' + (ti + 1) }}
          </el-tag>
          <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C;" @click="model.thresholds.splice(ti, 1)" />
        </div>
        <div v-if="model.thresholds.length === 0" class="group-empty">
          暂未配置等级，点击「添加等级」
        </div>
      </div>
    </div>

    <!-- 脚本预览 -->
    <script-panel
      v-if="definitionId"
      ref="scriptPanel"
      :definitionId="definitionId"
      :onBeforeCompile="handleSave"
      @mode-change="mode => scriptMode = mode"
    />

    <!-- 测试弹窗 -->
    <el-dialog title="测试执行" :visible.sync="testVisible" width="600px" append-to-body>
      <p class="test-hint"><i class="el-icon-info" /> 输入测试参数（JSON 格式）</p>
      <el-input v-model="testParamsJson" type="textarea" :rows="6" placeholder='{}' />
      <template slot="footer">
        <el-button size="small" @click="testVisible = false">取消</el-button>
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="doTest">执行</el-button>
      </template>
      <div v-if="testResult" class="test-result">
        <el-alert
          :title="testResult.success ? '执行成功' : '执行失败'"
          :type="testResult.success ? 'success' : 'error'"
          :closable="false" show-icon style="margin-bottom:10px;"
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
  name: 'AdvancedScorecard',
  components: { VarPicker, ScriptPanel },
  mixins: [varPickerMixin],
  data() {
    return {
      definitionId: null,
      contentLoaded: false,
      model: {
        initialScore: 100,
        resultVar: { varCode: '', varLabel: '' },
        dimensionGroups: [],
        thresholds: []
      },
      scriptMode: 'visual',
      testVisible: false,
      testParamsJson: '{}',
      testResult: null
    }
  },
  computed: {
    totalDimensions() {
      return this.model.dimensionGroups.reduce((sum, g) => sum + (g.dimensions || []).length, 0)
    },
    /** 所有维度组的有效权重之和 */
    totalWeight() {
      return this.model.dimensionGroups.reduce((sum, g) => {
        const groupWeight = g.weight != null ? g.weight : 1
        const dimWeights = (g.dimensions || []).reduce((ds, d) => ds + (d.weight != null ? d.weight : 1), 0)
        return sum + groupWeight * dimWeights
      }, 0)
    },
    totalWeightPercent() {
      return Math.min(100, Math.round(this.totalWeight * 100))
    },
    totalWeightColor() {
      const w = this.totalWeight
      if (Math.abs(w - 1.0) < 0.05) return '#52c41a'
      if (w > 1.05) return '#f5222d'
      return '#fa8c16'
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
      }
    },
    normalizeModel() {
      if (this.model.initialScore == null) this.$set(this.model, 'initialScore', 100)
      if (!this.model.resultVar) this.$set(this.model, 'resultVar', { varCode: '', varLabel: '' })
      if (!this.model.dimensionGroups) this.$set(this.model, 'dimensionGroups', [])
      if (!this.model.thresholds) this.$set(this.model, 'thresholds', [])
      this.model.dimensionGroups.forEach(g => {
        if (g.weight == null) this.$set(g, 'weight', 1.0)
        ;(g.dimensions || []).forEach(d => {
          if (d.weight == null) this.$set(d, 'weight', 1.0)
        })
      })
    },
    onResultVarSelect(v) {
      if (!v) return
      this.$set(this.model, 'resultVar', {
        varCode: v.varCode,
        varLabel: (v.varObj && v.varObj.varLabel) || v.varLabel || v.varCode
      })
    },
    onDimVarSelect(gi, di, v) {
      if (!v) return
      const dim = this.model.dimensionGroups[gi].dimensions[di]
      dim.varCode = v.varCode
      dim.varLabel = (v.varObj && v.varObj.varLabel) || v.varLabel || v.varCode
    },
    thresholdColor(idx) {
      return THRESHOLD_COLORS[idx % THRESHOLD_COLORS.length]
    },
    toggleGroup(gi) {
      const g = this.model.dimensionGroups[gi]
      this.$set(g, '_collapsed', !g._collapsed)
    },
    addGroup() {
      this.model.dimensionGroups.push({ groupLabel: '', dimensions: [], weight: 1.0, _collapsed: false })
    },
    removeGroup(gi) {
      this.model.dimensionGroups.splice(gi, 1)
    },
    addDimension(gi) {
      this.model.dimensionGroups[gi].dimensions.push({ varCode: '', varLabel: '', weight: 1.0, rules: [] })
    },
    removeDimension(gi, di) {
      this.model.dimensionGroups[gi].dimensions.splice(di, 1)
    },
    addRule(gi, di) {
      this.model.dimensionGroups[gi].dimensions[di].rules.push({
        conditions: [{ varCode: '', operator: '==', value: '' }],
        score: 0
      })
    },
    addCondition(rule) {
      rule.conditions.push({ varCode: '', operator: '==', value: '' })
    },
    addThreshold() {
      const last = this.model.thresholds[this.model.thresholds.length - 1]
      const min = last ? last.max : 0
      this.model.thresholds.push({ min, max: min + 50, result: '' })
    },
    async handleSave() {
      const saveModel = JSON.parse(JSON.stringify(this.model))
      ;(saveModel.dimensionGroups || []).forEach(g => { delete g._collapsed })
      await saveContent({ definitionId: this.definitionId, modelJson: JSON.stringify(saveModel) })
      this.$message.success('保存成功')
    },
    async handleCompile() {
      await this.handleSave()
      const res = await compileRule(this.definitionId)
      if (res && res.data && res.data.success) {
        this.$message.success('编译成功')
        if (this.$refs.scriptPanel) this.$refs.scriptPanel.refresh()
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
.asc-designer {
  background: #f3f3f3;
  padding: 20px;
  min-height: 100%;
}
.asc-header {
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
.asc-title-area { display: flex; align-items: center; }
.asc-title-icon { font-size: 18px; color: #eb2f96; margin-right: 8px; }
.asc-title { font-size: 16px; font-weight: bold; color: #282828; }
.asc-toolbar { display: flex; align-items: center; gap: 6px; }

.asc-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  margin-bottom: 16px;
}
.asc-card-title {
  font-size: 14px; font-weight: bold; color: #333; margin-bottom: 14px;
  display: flex; align-items: center; gap: 6px;
  i { color: #1890ff; }
}
.asc-card-title-row { justify-content: space-between; }

.base-config-row { display: flex; gap: 24px; flex-wrap: wrap; align-items: center; }
.base-config-item { display: flex; align-items: center; gap: 10px; }
.base-config-label { font-size: 13px; color: #666; white-space: nowrap; }

/* 维度组 */
.asc-group { padding: 0; overflow: hidden; }
.asc-group-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #fafafa; border-bottom: 1px solid #f0f0f0;
  gap: 8px;
}
.asc-group-left { display: flex; align-items: center; gap: 8px; cursor: pointer; flex: 1; flex-wrap: wrap; }
.asc-group-right { display: flex; align-items: center; gap: 4px; }
.group-label-input { max-width: 300px; }
.group-weight-summary {
  display: flex; align-items: center; gap: 6px; margin-left: 8px;
}
.weight-label { font-size: 12px; color: #888; white-space: nowrap; }
.asc-group-body { padding: 16px; }

/* 维度 */
.asc-dimension {
  border: 1px solid #e8e8e8; border-radius: 6px; margin-bottom: 12px; overflow: hidden;
  transition: box-shadow 0.2s, border-color 0.2s;
  &:hover {
    box-shadow: 0 2px 8px rgba(24,144,255,0.15);
    border-color: #91caff;
  }
}
.dim-header {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  background: #f5f5f5; border-bottom: 1px solid #f0f0f0; flex-wrap: wrap;
}
.dim-index {
  width: 32px; height: 22px; border-radius: 11px; background: #1890ff; color: #fff;
  font-size: 11px; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; font-weight: bold;
}
.dim-weight-area {
  display: flex; align-items: center; gap: 4px; margin-left: auto;
}
.item-field-label {
  font-size: 12px; color: #888; white-space: nowrap;
}

/* 规则表格 */
.rule-table {
  width: 100%; border-collapse: collapse;
  th, td { border: 1px solid #f0f0f0; padding: 8px 10px; vertical-align: top; }
  th { background: #fafafa; font-size: 12px; color: #888; font-weight: 600; text-align: center; }
  .col-idx { width: 40px; text-align: center; }
  .col-conditions { }
  .col-score { width: 120px; text-align: center; }
  .col-action { width: 60px; text-align: center; }
}
.condition-row {
  display: flex; align-items: center; gap: 6px; margin-bottom: 6px;
  &:last-child { margin-bottom: 2px; }
}
.cond-var { flex: 3; min-width: 0; }
.cond-op { flex: 2; min-width: 80px; }
.cond-val { flex: 2; min-width: 0; }
.score-input { width: 100%; }
.cond-and { font-size: 11px; color: #1890ff; font-weight: bold; margin: 0 2px; flex-shrink: 0; }

.dim-empty, .group-empty, .asc-empty {
  text-align: center; padding: 20px; color: #bbb; font-size: 13px;
}
.asc-empty { display: flex; flex-direction: column; align-items: center; gap: 8px; }

/* 计算公式预览（参照评分卡样式） */
.asc-formula {
  background: #fffbe6;
  border: 1px solid #ffe58f;
}
.formula-content { overflow-x: auto; }
.formula-text {
  display: flex; align-items: center; flex-wrap: wrap; gap: 4px;
  font-size: 13px; line-height: 1.8;
  code {
    font-family: 'Consolas', monospace;
    background: rgba(0,0,0,0.05);
    padding: 1px 5px; border-radius: 3px; color: #c41d7f;
  }
}
.op { color: #888; font-weight: bold; font-size: 13px; }
.formula-group {
  display: inline-flex; align-items: center; gap: 3px;
  background: #fff7e6; border: 1px solid #ffd591;
  border-radius: 4px; padding: 2px 8px;
}
.formula-group-label { color: #d46b08; font-size: 12px; font-weight: 600; }
.formula-dims { font-size: 12px; color: #555; }
.formula-term { color: #1890ff; font-weight: 500; }

/* 权重汇总 */
.weight-summary { display: flex; align-items: center; gap: 8px; }
.weight-label-sm { font-size: 13px; color: #666; font-weight: normal; }
.weight-value { font-weight: bold; font-size: 14px; min-width: 36px; }
.weight-detail-list { display: flex; flex-direction: column; gap: 6px; }
.weight-detail-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px; background: #fafafa; border-radius: 4px;
  font-size: 12px; color: #555;
}
.weight-detail-name { font-weight: 600; color: #333; min-width: 120px; }
.weight-detail-val { color: #1890ff; font-weight: 500; }
.weight-detail-dims { color: #888; }

/* 阈值 */
.threshold-list { display: flex; flex-direction: column; gap: 10px; }
.threshold-item {
  display: flex; align-items: center; gap: 12px; padding: 10px 12px;
  border: 1px solid #f0f0f0; border-radius: 6px; background: #fafafa;
}
.thresh-color-bar { width: 4px; height: 36px; border-radius: 2px; flex-shrink: 0; }
.thresh-range { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.thresh-sep { font-size: 12px; color: #888; white-space: nowrap; }
.thresh-result { flex: 1; }
.thresh-badge { flex-shrink: 0; border-color: transparent !important; }

.test-hint { font-size: 12px; color: #909399; margin-bottom: 8px; }
.test-result { margin-top: 16px; }
</style>
