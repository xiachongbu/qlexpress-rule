<template>
  <div class="cg" :class="{ 'cg--nested': depth > 0 }">
    <div class="cg-head">
      <span v-if="depth === 0" class="cg-title">条件</span>
      <el-button-group class="cg-op">
        <el-button
          size="mini"
          :type="group.op === 'AND' ? 'primary' : 'default'"
          @click="setGroupOp('AND')"
        >与</el-button>
        <el-button
          size="mini"
          :type="group.op === 'OR' ? 'primary' : 'default'"
          @click="setGroupOp('OR')"
        >或</el-button>
      </el-button-group>
      <el-button
        v-if="depth > 0"
        type="text"
        size="mini"
        class="cg-remove-group"
        @click="$emit('remove-group')"
      >删除组</el-button>
    </div>

    <div class="cg-stem">
      <div class="cg-stem-line" aria-hidden="true" />
      <div class="cg-children">
        <div
          v-for="(child, idx) in group.children"
          :key="'n-' + depth + '-' + idx"
          class="cg-row"
        >
          <!-- 嵌套条件组 -->
          <condition-group-editor
            v-if="child.type === 'group'"
            :group="child"
            :vars="vars"
            :depth="depth + 1"
            :get-var-options-fn="getVarOptionsFn"
            @remove-group="removeChild(idx)"
          />
          <!-- 单条条件叶：整行铺满容器，字段按比例伸缩 -->
          <div v-else class="cg-leaf">
            <div class="cg-field cg-field--var-left">
              <var-picker
                :vars="vars"
                :value="child.varCode"
                placeholder="选择字段..."
                size="mini"
                width="100%"
                @select="v => onLeafLeftSelect(child, v)"
              />
            </div>
            <div class="cg-field cg-field--op">
              <el-select v-model="child.operator" size="mini" class="cg-sel-full" @change="onOpChange(child)">
                <el-option v-for="o in opOptions" :key="o.v" :label="o.l" :value="o.v" />
              </el-select>
            </div>
            <div class="cg-field cg-field--kind">
              <el-select v-model="child.valueKind" size="mini" class="cg-sel-full" @change="onValueKindChange(child)">
                <el-option label="常量" value="CONST" />
                <el-option label="变量" value="VAR" />
              </el-select>
            </div>
            <template v-if="child.operator !== '*'">
              <div v-if="child.valueKind === 'VAR'" class="cg-field cg-field--var-right">
                <var-picker
                  :vars="vars"
                  :value="child.value"
                  placeholder="右侧变量"
                  size="mini"
                  width="100%"
                  @select="v => onLeafRightSelect(child, v)"
                />
              </div>
              <div v-else class="cg-field cg-field--value">
                <el-select
                  v-if="child.varType === 'ENUM' && enumOpts(child).length"
                  v-model="child.value"
                  size="mini"
                  class="cg-sel-full"
                  clearable
                >
                  <el-option v-for="opt in enumOpts(child)" :key="opt" :label="opt" :value="opt" />
                </el-select>
                <el-select
                  v-else-if="child.varType === 'BOOLEAN'"
                  v-model="child.value"
                  size="mini"
                  class="cg-sel-full"
                >
                  <el-option label="true" value="true" />
                  <el-option label="false" value="false" />
                </el-select>
                <el-input
                  v-else-if="child.varType === 'NUMBER'"
                  v-model="child.value"
                  size="mini"
                  class="cg-input-full"
                  placeholder="数值"
                />
                <el-input v-else v-model="child.value" size="mini" class="cg-input-full" placeholder="值" />
              </div>
            </template>
            <span v-else class="cg-field cg-field--any">任意</span>
            <div class="cg-field cg-field--actions">
              <el-button type="text" size="mini" class="cg-del" @click="removeChild(idx)">删除</el-button>
            </div>
          </div>
        </div>

        <div class="cg-footer-btns">
          <el-button size="mini" round @click="addLeaf">加条件</el-button>
          <el-button size="mini" round @click="addSubGroup">加条件组</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import VarPicker from '@/components/common/VarPicker.vue'
import { createEmptyGroup, createEmptyLeaf } from '@/utils/decisionConditionTree'

export default {
  name: 'ConditionGroupEditor',
  components: { VarPicker },
  props: {
    /** 组节点 { type:'group', op, children } */
    group: { type: Object, required: true },
    /** VarPicker 选项列表 */
    vars: { type: Array, default: () => [] },
    /** 嵌套深度，根为 0 */
    depth: { type: Number, default: 0 },
    /** 父组件提供的 (varCode) => options[]，用于枚举选项 */
    getVarOptionsFn: { type: Function, default: null }
  },
  data() {
    return {
      opOptions: [
        { l: '等于', v: '==' },
        { l: '不等于', v: '!=' },
        { l: '大于', v: '>' },
        { l: '大于等于', v: '>=' },
        { l: '小于', v: '<' },
        { l: '小于等于', v: '<=' },
        { l: '包含', v: 'in' },
        { l: '任意', v: '*' }
      ]
    }
  },
  methods: {
    /**
     * 切换组内与/或。
     */
    setGroupOp(op) {
      this.$set(this.group, 'op', op)
    },

    /**
     * 枚举叶子的可选项字符串列表。
     */
    enumOpts(leaf) {
      if (!leaf.enumOptions) return []
      return leaf.enumOptions.split(',').map(s => s.trim()).filter(Boolean)
    },

    /**
     * 运算符变更：任意通配时清空右侧。
     */
    onOpChange(leaf) {
      if (leaf.operator === '*') {
        this.$set(leaf, 'value', '')
      }
    },

    /**
     * 常量/变量切换时重置右侧。
     */
    onValueKindChange(leaf) {
      this.$set(leaf, 'value', '')
      this.$set(leaf, 'rightVarType', '')
      this.$set(leaf, 'rightVarLabel', '')
      this.$set(leaf, '_rightVarId', undefined)
    },

    /**
     * 选择左侧比较字段。
     */
    onLeafLeftSelect(leaf, variable) {
      if (!variable) {
        this.$set(leaf, 'varCode', '')
        this.$set(leaf, 'varLabel', '')
        this.$set(leaf, 'varType', 'STRING')
        this.$set(leaf, 'enumOptions', '')
        this.$set(leaf, '_varId', undefined)
        return
      }
      const varLabel = (variable.varObj && variable.varObj.varLabel) || variable.varLabel || variable.varCode
      const _varId = variable.varObj && variable.varObj.id ? variable.varObj.id : null
      this.$set(leaf, 'varCode', variable.varCode)
      this.$set(leaf, 'varLabel', varLabel)
      this.$set(leaf, 'varType', variable.varType || 'STRING')
      this.$set(leaf, '_varId', _varId)
      if (variable.varType === 'ENUM' && this.getVarOptionsFn) {
        const opts = this.getVarOptionsFn(variable.varCode) || []
        this.$set(leaf, 'enumOptions', opts.map(o => o.value || o.optionValue).filter(Boolean).join(','))
      } else {
        this.$set(leaf, 'enumOptions', '')
      }
    },

    /**
     * 变量比较时选择右侧变量。
     */
    onLeafRightSelect(leaf, variable) {
      if (!variable) {
        this.$set(leaf, 'value', '')
        this.$set(leaf, 'rightVarType', '')
        this.$set(leaf, 'rightVarLabel', '')
        this.$set(leaf, '_rightVarId', undefined)
        return
      }
      const varLabel = (variable.varObj && variable.varObj.varLabel) || variable.varLabel || variable.varCode
      const _varId = variable.varObj && variable.varObj.id ? variable.varObj.id : null
      this.$set(leaf, 'value', variable.varCode)
      this.$set(leaf, 'rightVarType', variable.varType || 'STRING')
      this.$set(leaf, 'rightVarLabel', varLabel)
      this.$set(leaf, '_rightVarId', _varId)
    },

    /**
     * 追加一条叶条件。
     */
    addLeaf() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      this.group.children.push(createEmptyLeaf())
    },

    /**
     * 追加嵌套的与/或组。
     */
    addSubGroup() {
      if (!Array.isArray(this.group.children)) this.$set(this.group, 'children', [])
      const g = createEmptyGroup('AND')
      g.children.push(createEmptyLeaf())
      this.group.children.push(g)
    },

    /**
     * 删除子项（叶或组）。
     */
    removeChild(idx) {
      this.group.children.splice(idx, 1)
    }
  }
}
</script>

<style lang="scss" scoped>
.cg {
  font-size: 13px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}
.cg--nested {
  margin-left: 8px;
  padding-left: 12px;
  border-left: 2px solid #e8e8e8;
}
.cg-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.cg-title {
  font-weight: 600;
  color: #333;
  margin-right: 4px;
}
.cg-op ::v-deep .el-button--mini {
  border-radius: 4px;
}
.cg-remove-group {
  color: #f56c6c !important;
  margin-left: auto;
}
.cg-stem {
  display: flex;
  gap: 0;
  align-items: stretch;
  width: 100%;
  min-width: 0;
}
.cg-stem-line {
  width: 2px;
  flex-shrink: 0;
  background: #e0e0e0;
  border-radius: 1px;
  margin-right: 12px;
  min-height: 24px;
}
.cg-children {
  flex: 1 1 0;
  min-width: 0;
}
.cg-row {
  margin-bottom: 10px;
  width: 100%;
  max-width: 100%;
}
.cg-row > .cg {
  width: 100%;
  max-width: 100%;
}
/* 条件行：主字段吃掉右侧留白 */
.cg-leaf {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 8px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}
.cg-field {
  min-width: 0;
  display: flex;
  align-items: center;
}
/* 左侧变量选择：优先变宽 */
.cg-field--var-left {
  flex: 3 1 220px;
  min-width: 160px;
}
/* 运算符、常量/变量切换：固定宽度 */
.cg-field--op {
  flex: 0 0 108px;
  width: 108px;
}
.cg-field--kind {
  flex: 0 0 92px;
  width: 92px;
}
/* 右侧比较值或变量 */
.cg-field--var-right {
  flex: 2 1 180px;
  min-width: 140px;
}
.cg-field--value {
  flex: 2 1 180px;
  min-width: 120px;
}
.cg-field--any {
  flex: 1 1 120px;
  min-width: 80px;
  color: #999;
  font-size: 12px;
}
.cg-field--actions {
  flex: 0 0 auto;
  margin-left: auto;
}
.cg-sel-full {
  width: 100%;
}
.cg-input-full {
  width: 100%;
}
.cg-field ::v-deep .var-picker-wrap {
  width: 100% !important;
  max-width: 100%;
}
.cg-field ::v-deep .el-select {
  width: 100%;
  display: block;
}
.cg-field ::v-deep .el-select > .el-input {
  width: 100%;
}
.cg-field ::v-deep .el-input {
  width: 100%;
}
.cg-del {
  color: #f56c6c !important;
  flex-shrink: 0;
}
.cg-footer-btns {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
@media (max-width: 768px) {
  .cg-field--var-left {
    flex: 1 1 100%;
    width: 100%;
  }
  .cg-field--value,
  .cg-field--var-right {
    flex: 1 1 100%;
    width: 100%;
  }
  .cg-field--actions {
    margin-left: 0;
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
