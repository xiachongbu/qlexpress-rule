<template>
  <el-dialog
    :title="title"
    v-model="innerVisible"
    :width="width"
    append-to-body
  >
    <div class="test-mode-bar">
      <el-radio-group :value="mode" size="mini" @input="onModeChange">
        <el-radio-button label="form">表单</el-radio-button>
        <el-radio-button label="json">JSON</el-radio-button>
      </el-radio-group>
      <span class="test-mode-tip">
        <i class="el-icon-info" />
        {{ mode === 'form' ? '已根据规则自动带出输入变量并填充默认值，可直接修改后执行' : '直接编辑 JSON 入参（支持嵌套对象 / 列表）' }}
      </span>
    </div>

    <!-- 表单模式 -->
    <template v-if="mode === 'form'">
      <el-form v-if="fields.length" label-width="140px" size="small" class="test-param-form">
        <el-form-item
          v-for="f in fields"
          :key="'tf-' + f.code"
          :label="f.label"
        >
          <el-select
            v-if="f.options && f.options.length"
            v-model="params[f.code]"
            style="width:100%"
            clearable
            filterable
            allow-create
          >
            <el-option v-for="opt in f.options" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-select
            v-else-if="f.varType === 'ENUM' && f.enumOptions.length"
            v-model="params[f.code]"
            style="width:100%"
            clearable
          >
            <el-option v-for="opt in f.enumOptions" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-select
            v-else-if="f.varType === 'BOOLEAN'"
            v-model="params[f.code]"
            style="width:100%"
          >
            <el-option label="true" :value="true" />
            <el-option label="false" :value="false" />
          </el-select>
          <el-input-number
            v-else-if="f.varType === 'NUMBER'"
            v-model="params[f.code]"
            style="width:100%"
            controls-position="right"
          />
          <el-input v-else v-model="params[f.code]" :placeholder="f.code" />
        </el-form-item>
      </el-form>
      <div v-else class="test-empty">
        <i class="el-icon-warning-outline" />
        <span>未从规则中识别到可输入变量，请切换到「JSON」手动输入入参</span>
      </div>
    </template>

    <!-- JSON 模式 -->
    <el-input
      v-else
      v-model="jsonText"
      type="textarea"
      :rows="12"
      placeholder="{}"
    />

    <template #footer">
      <el-button size="small" @click="innerVisible = false">取消</el-button>
      <el-button size="small" type="primary" icon="el-icon-video-play" @click="$emit('execute')">执行</el-button>
    </template>

    <div v-if="result" class="test-result">
      <slot name="result" :result="result" />
    </div>
  </el-dialog>
</template>

<script>
/**
 * 通用「测试执行」弹窗：表单 / JSON 双模式入参编辑 + 执行按钮，
 * 结果区通过 result 具名插槽由各设计器自定义展示。与 designerTestMixin 搭配使用。
 */
export default {
  name: 'TestExecuteDialog',
  props: {
    visible: {type: Boolean, default: false},
    title: {type: String, default: '测试执行'},
    width: {type: String, default: '900px'},
    /** 字段描述列表：{ code, label, varType, enumOptions[] } */
    fields: {type: Array, default: () => []},
    /** 表单模式入参对象（按引用直接双向修改） */
    params: {type: Object, default: () => ({})},
    /** JSON 模式入参文本 */
    paramsJson: {type: String, default: '{}'},
    /** 入参编辑模式：form / json */
    mode: {type: String, default: 'form'},
    /** 执行结果对象 */
    result: {type: Object, default: null}
  },
  computed: {
    innerVisible: {
      get() {
        return this.visible
      },
      set(v) {
        this.$emit('update:visible', v)
      }
    },
    jsonText: {
      get() {
        return this.paramsJson
      },
      set(v) {
        this.$emit('update:paramsJson', v)
      }
    }
  },
  methods: {
    onModeChange(mode) {
      this.$emit('update:mode', mode)
    }
  }
}
</script>

<style scoped>
.test-mode-bar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.test-mode-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
.test-param-form {
  max-height: 540px;
  overflow-y: auto;
}
.test-empty {
  padding: 24px;
  text-align: center;
  color: #909399;
  font-size: 13px;
  background: #fafafa;
  border-radius: 4px;
}
.test-empty i {
  margin-right: 6px;
}
.test-result {
  margin-top: 16px;
}
</style>
