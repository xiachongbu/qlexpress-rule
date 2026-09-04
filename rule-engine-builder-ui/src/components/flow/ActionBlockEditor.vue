<template>
  <div class="block-editor">
    <!-- 块列表 -->
    <div v-for="(block, bi) in blocks" :key="bi" class="block-item" :class="'block-' + block.type">
      <div class="block-header">
        <span class="block-type-tag" :style="{background: typeColor(block.type)}">{{ typeLabel(block.type) }}</span>
        <div class="block-header-actions">
          <el-button v-if="bi > 0" type="text" size="small" icon="el-icon-top" @click="moveBlock(bi, -1)" />
          <el-button v-if="bi < blocks.length - 1" type="text" size="small" icon="el-icon-bottom" @click="moveBlock(bi, 1)" />
          <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C" @click="removeBlock(bi)" />
        </div>
      </div>
      <div class="block-body">

        <!-- ===== 赋值 ===== -->
        <template v-if="block.type === 'assign'">
          <div class="inline-row">
            <var-picker :vars="vars" :value="block.target" placeholder="目标变量" size="small" @select="v => onAssignTargetSelect(block, v)" />
            <span class="eq">=</span>
            <!-- 常量场景：置灰禁用，悬停提示不可编辑 -->
            <el-tooltip
              v-if="isConstTarget(block.target)"
              content="常量不允许修改，请在常量配置管理中修改后重新发布"
              placement="top"
            >
              <el-input
                v-model="block.value"
                size="small"
                disabled
                placeholder="常量默认值（只读）"
              />
            </el-tooltip>
            <!-- 非常量场景：可编辑的空输入框 -->
            <el-input
              v-else
              :value="block.value || ''"
              size="small"
              placeholder="值/表达式"
              @input="block.value = $event"
            />
          </div>
          <div class="rounding-row">
            <el-switch v-model="block.enableRounding" size="small" active-text="精度" @change="sync" />
            <template v-if="block.enableRounding">
              <span class="mini-label">小数位</span>
              <el-input-number v-model="block.decimalPlaces" :min="0" :max="10" size="small" style="width:90px;" placeholder="位数" @change="sync" />
              <span class="mini-label">进位</span>
              <el-select v-model="block.roundingMode" size="small" style="width:100px;" placeholder="进位规则" @change="sync">
                <el-option label="四舍五入" value="HALF_UP" />
                <el-option label="向上取整" value="UP" />
                <el-option label="向下截断" value="DOWN" />
                <el-option label="正无穷方向" value="CEILING" />
                <el-option label="负无穷方向" value="FLOOR" />
              </el-select>
            </template>
          </div>
        </template>

        <!-- ===== 条件分支 ===== -->
        <template v-if="block.type === 'if-block'">
          <div v-for="(br, bri) in block.branches" :key="bri" class="branch-card" :class="'branch-' + br.type">
            <div class="branch-head">
              <span class="branch-tag" :class="'tag-' + br.type">{{ br.type === 'if' ? 'IF' : br.type === 'elseif' ? 'ELSE IF' : 'ELSE' }}</span>
              <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C" @click="removeBranch(block, bri)" />
            </div>
            <div v-if="br.type !== 'else'" class="cond-area">
              <var-picker :vars="vars" :value="br.condVar" placeholder="条件变量" size="small" :exclude-constants="true" @select="v => onCondVarSelect(br, v)" />
              <el-select v-model="br.condOp" size="small" style="width:100px" @change="sync">
                <el-option label="==" value="==" /><el-option label="!=" value="!=" />
                <el-option label=">" value=">" /><el-option label=">=" value=">=" />
                <el-option label="<" value="<" /><el-option label="<=" value="<=" />
                <el-option label="in" value="in" />
                <el-option label="含" value="contains" />
                <el-option label="前" value="startsWith" />
                <el-option label="后" value="endsWith" />
              </el-select>
              <!-- 条件值输入框 -->
              <el-input
                v-model="br.condValue"
                size="small"
                placeholder="值"
                class="cond-value-input"
                @input="sync"
              />
            </div>
            <div class="branch-body">
              <div v-for="(a, ai) in br.actions" :key="ai" class="inline-row">
                <var-picker :vars="vars" :value="a.target" placeholder="变量" size="small" @select="v => onAssignTargetSelect(a, v)" />
                <span class="eq">=</span>
                <!-- 常量场景：置灰禁用，悬停提示不可编辑 -->
                <el-tooltip
                  v-if="isConstTarget(a.target)"
                  content="常量不允许修改，请在常量配置管理中修改后重新发布"
                  placement="top"
                >
                  <el-input
                    v-model="a.value"
                    size="small"
                    class="dt-act-value-ctl"
                    disabled
                    placeholder="常量默认值（只读）"
                  />
                </el-tooltip>
                <!-- 非常量场景：可编辑的空输入框 -->
                <el-input
                  v-else
                  :value="a.value || ''"
                  size="small"
                  class="dt-act-value-ctl"
                  placeholder="值"
                  @input="a.value = $event"
                />
                <el-button v-if="br.actions.length > 1" type="text" size="small" icon="el-icon-delete" style="color:#F56C6C" @click="br.actions.splice(ai,1); sync()" />
              </div>
              <el-button size="small" icon="el-icon-plus" style="width:100%;margin-top:2px" @click="br.actions.push({type:'assign',target:'',value:''})">添加赋值</el-button>
            </div>
          </div>
          <div class="branch-add-row">
            <el-button v-if="!hasElse(block)" size="small" @click="addBranch(block, 'elseif')">+ ELSE IF</el-button>
            <el-button v-if="!hasElse(block)" size="small" @click="addBranch(block, 'else')">+ ELSE</el-button>
          </div>
        </template>

        <!-- ===== Switch ===== -->
        <template v-if="block.type === 'switch-block'">
          <div class="inline-row" style="margin-bottom:6px">
            <span class="mini-label">匹配变量</span>
            <var-picker :vars="vars" :value="block.matchVar" placeholder="变量" size="small" :exclude-constants="true" @select="v => { block.matchVar = v.varCode; sync() }" />
          </div>
          <div v-for="(c, ci) in block.cases" :key="ci" class="case-card">
            <div class="case-head">
              <span class="case-tag">CASE</span>
              <el-input v-model="c.value" size="small" placeholder="匹配值" style="flex:1" @input="sync" />
              <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C" @click="block.cases.splice(ci,1); sync()" />
            </div>
            <div class="case-body">
              <div v-for="(a, ai) in c.actions" :key="ai" class="inline-row">
                <el-input v-model="a.target" size="small" placeholder="变量" @input="sync" />
                <span class="eq">=</span>
                <el-input v-model="a.value" size="small" placeholder="值" @input="sync" />
              </div>
            </div>
          </div>
          <div v-if="block.defaultActions" class="case-card default-case">
            <div class="case-head"><span class="case-tag default-tag">DEFAULT</span></div>
            <div class="case-body">
              <div v-for="(a, ai) in block.defaultActions" :key="ai" class="inline-row">
                <el-input v-model="a.target" size="small" placeholder="变量" @input="sync" />
                <span class="eq">=</span>
                <el-input v-model="a.value" size="small" placeholder="值" @input="sync" />
              </div>
            </div>
          </div>
          <el-button size="small" style="width:100%;margin-top:4px" @click="block.cases.push({value:'',actions:[{type:'assign',target:'',value:''}]}); sync()">添加 Case</el-button>
        </template>

        <!-- ===== 函数调用 ===== -->
        <template v-if="block.type === 'func-call'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <el-input v-model="block.target" size="small" placeholder="结果变量（可空）" @input="sync" />
          </div>
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">函数</span>
            <el-select v-if="functions.length" v-model="block.funcName" size="small" filterable allow-create placeholder="选择或输入函数" style="flex:1" @change="onFuncSelect(block, $event)">
              <el-option v-for="f in functions" :key="f.funcCode" :label="f.funcName + ' (' + f.funcCode + ')'" :value="f.funcCode" />
            </el-select>
            <el-input v-else v-model="block.funcName" size="small" placeholder="函数名" @input="sync" />
          </div>
          <div v-for="(arg, ai) in block.args" :key="ai" class="inline-row" style="margin-bottom:2px">
            <span class="mini-label">参数{{ ai+1 }}</span>
            <el-input v-model="block.args[ai]" size="small" placeholder="参数表达式" @input="sync" />
            <el-button v-if="block.args.length > 1" type="text" size="small" icon="el-icon-delete" style="color:#F56C6C" @click="block.args.splice(ai,1); sync()" />
          </div>
          <el-button size="small" style="width:100%;margin-top:2px" @click="block.args.push(''); sync()">添加参数</el-button>
        </template>

        <!-- ===== HTTP 调用 ===== -->
        <template v-if="block.type === 'http-call'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <el-input v-model="block.target" size="small" placeholder="结果变量（成功=业务JSON对象直取 .data/.code；失败=.success==false/.error）" @input="sync" />
          </div>
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">请求</span>
            <el-select v-model="block.method" size="small" style="width:96px" @change="sync">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
              <el-option label="PATCH" value="PATCH" />
            </el-select>
            <el-input v-model="block.url" size="small" placeholder="URL，可用 ${变量} 插值" style="flex:1" @input="sync" />
          </div>
          <div class="http-sub">
            <div class="http-sub-title">请求头</div>
            <div v-for="(h, hi) in block.headers" :key="hi" class="inline-row" style="margin-bottom:2px">
              <el-input v-model="h.key" size="small" placeholder="Header 名" style="width:120px" @input="sync" />
              <span class="eq">:</span>
              <el-input v-model="h.value" size="small" placeholder="值，可用 ${变量} 插值" @input="sync" />
              <el-button type="text" size="small" icon="el-icon-delete" style="color:#F56C6C" @click="block.headers.splice(hi,1); sync()" />
            </div>
            <el-button size="small" icon="el-icon-plus" style="width:100%;margin-top:2px" @click="addHeader(block)">添加请求头</el-button>
          </div>
          <div class="http-sub">
            <div class="inline-row" style="margin-bottom:4px">
              <span class="mini-label">请求体</span>
              <el-select v-model="block.bodyMode" size="small" style="width:110px" @change="sync">
                <el-option label="无" value="none" />
                <el-option label="文本" value="text" />
                <el-option label="JSON表达式" value="json" />
              </el-select>
            </div>
            <el-input
              v-if="block.bodyMode === 'text'"
              v-model="block.body"
              type="textarea"
              :rows="2"
              size="small"
              placeholder="文本请求体，可用 ${变量} 插值"
              @input="sync"
            />
            <el-input
              v-else-if="block.bodyMode === 'json'"
              v-model="block.body"
              size="small"
              placeholder="变量名或 QL 对象字面量（自动序列化为 JSON）"
              @input="sync"
            />
          </div>
          <div class="inline-row">
            <span class="mini-label">连接超时</span>
            <el-input-number v-model="block.connectTimeout" :min="0" :max="60000" :step="500" size="small" style="width:110px" @change="sync" />
            <span class="mini-label">读取超时</span>
            <el-input-number v-model="block.readTimeout" :min="0" :max="60000" :step="500" size="small" style="width:110px" @change="sync" />
            <span class="mini-label" style="color:#bbb">ms</span>
          </div>
        </template>

        <!-- ===== ForEach ===== -->
        <template v-if="block.type === 'foreach'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">循环变量</span>
            <el-input v-model="block.itemVar" size="small" placeholder="item" style="width:80px" @input="sync" />
            <span class="mini-label" style="margin-left:6px">列表</span>
            <el-input v-model="block.listExpr" size="small" placeholder="列表变量/表达式" @input="sync" />
          </div>
          <div class="loop-body">
            <div v-for="(a, ai) in block.actions" :key="ai" class="inline-row">
              <el-input v-model="a.target" size="small" placeholder="变量" @input="sync" />
              <span class="eq">=</span>
              <el-input v-model="a.value" size="small" placeholder="值/表达式" @input="sync" />
            </div>
            <el-button size="small" style="width:100%;margin-top:2px" @click="block.actions.push({type:'assign',target:'',value:''})">添加赋值</el-button>
          </div>
        </template>

        <!-- ===== 三元表达式 ===== -->
        <template v-if="block.type === 'ternary'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <var-picker :vars="vars" :value="block.target" placeholder="变量" size="small" @select="v => onAssignTargetSelect(block, v)" />
          </div>
          <div class="cond-area" style="margin-bottom:4px">
            <el-input v-model="block.condVar" size="small" placeholder="条件变量" style="width:80px" @input="sync" />
            <el-select v-model="block.condOp" size="small" style="width:100px" @change="sync">
              <el-option label="==" value="==" /><el-option label="!=" value="!=" />
              <el-option label=">" value=">" /><el-option label=">=" value=">=" />
              <el-option label="<" value="<" /><el-option label="<=" value="<=" />
              <el-option label="in" value="in" />
              <el-option label="含" value="contains" />
              <el-option label="前" value="startsWith" />
              <el-option label="后" value="endsWith" />
            </el-select>
            <el-input v-model="block.condValue" size="small" placeholder="值" style="width:80px" @input="sync" />
          </div>
          <div class="inline-row">
            <span class="mini-label" style="color:#52c41a">真</span>
            <el-tooltip
              v-if="isConstTarget(block.target)"
              content="常量不允许修改，请在常量配置管理中修改后重新发布"
              placement="top"
            >
              <el-input
                v-model="block.trueValue"
                size="small"
                placeholder="常量默认值（只读）"
                disabled
              />
            </el-tooltip>
            <el-input
              v-else
              :value="block.trueValue || ''"
              size="small"
              placeholder="真值"
              @input="block.trueValue = $event"
            />
            <span class="mini-label" style="color:#F56C6C">假</span>
            <el-tooltip
              v-if="isConstTarget(block.target)"
              content="常量不允许修改，请在常量配置管理中修改后重新发布"
              placement="top"
            >
              <el-input
                v-model="block.falseValue"
                size="small"
                placeholder="常量默认值（只读）"
                disabled
              />
            </el-tooltip>
            <el-input
              v-else
              :value="block.falseValue || ''"
              size="small"
              placeholder="假值"
              @input="block.falseValue = $event"
            />
          </div>
        </template>

        <!-- ===== IN 判断 ===== -->
        <template v-if="block.type === 'in-check'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <el-input v-model="block.target" size="small" placeholder="变量" @input="sync" />
            <span class="mini-label">检测</span>
            <var-picker :vars="vars" :value="block.checkVar" placeholder="变量" size="small" :exclude-constants="true" @select="v => { block.checkVar = v.varCode; sync() }" />
          </div>
          <div class="inline-row" style="flex-wrap:wrap;gap:4px;margin-bottom:4px">
            <span class="mini-label">值列表</span>
            <el-tag v-for="(v, vi) in (block.inValues || [])" :key="vi" closable size="small" @close="removeInValue(block, vi)">{{ v }}</el-tag>
            <el-input v-model="newInValue" size="small" placeholder="输入后回车或失焦添加" style="width:120px" @keyup.enter.native="addInValue(block)" @blur="addInValue(block)" />
          </div>
          <div class="inline-row">
            <span class="mini-label" style="color:#52c41a">匹配</span>
            <el-input v-model="block.trueValue" size="small" placeholder="true" @input="sync" />
            <span class="mini-label" style="color:#F56C6C">不匹配</span>
            <el-input v-model="block.falseValue" size="small" placeholder="false" @input="sync" />
          </div>
        </template>

        <!-- ===== 动态字符串 ===== -->
        <template v-if="block.type === 'template-str'">
          <div class="inline-row" style="margin-bottom:4px">
            <span class="mini-label">结果</span>
            <el-input v-model="block.target" size="small" placeholder="变量" @input="sync" />
          </div>
          <div v-for="(p, pi) in block.parts" :key="pi" class="inline-row" style="margin-bottom:2px">
            <el-select v-model="p.type" size="small" style="width:65px" @change="sync">
              <el-option label="文本" value="text" /><el-option label="表达式" value="expr" />
            </el-select>
            <el-input v-model="p.content" size="small" :placeholder="p.type === 'expr' ? '变量/表达式' : '文本内容'" @input="sync" />
            <el-button v-if="block.parts.length > 1" type="text" size="small" icon="el-icon-delete" style="color:#F56C6C" @click="block.parts.splice(pi,1); sync()" />
          </div>
          <el-button size="small" style="width:100%;margin-top:2px" @click="block.parts.push({type:'text',content:''}); sync()">添加片段</el-button>
        </template>

      </div>
    </div>

    <!-- 添加块按钮 -->
    <el-dropdown trigger="click" style="width:100%;margin-top:6px" @command="addBlock">
      <el-button size="small" icon="el-icon-plus" style="width:100%">添加动作块</el-button>
      <template #dropdown><el-dropdown-menu>
        <el-dropdown-item v-for="bt in blockTypes" :key="bt.type" :command="bt.type">
          <i :class="bt.icon" :style="{color: bt.color}" /> {{ bt.label }}
        </el-dropdown-item>
      </el-dropdown-menu></template>
    </el-dropdown>
  </div>
</template>

<script>
import {actionDataToBlocks, BLOCK_TYPES, blocksToActionData, generateScript, newBlock} from '@/utils/actionDataCodegen'
import VarPicker from '@/components/common/VarPicker.vue'

export default {
  name: 'ActionBlockEditor',
  components: { VarPicker },
  props: {
    actionData: { type: Array, default: () => [] },
    vars: { type: Array, default: () => [] },
    functions: { type: Array, default: () => [] }
  },
  data() {
    return {
      blocks: [],
      blockTypes: BLOCK_TYPES,
      newInValue: ''
    }
  },
  computed: {
    scriptPreview() {
      return generateScript(blocksToActionData(this.blocks))
    }
  },
  watch: {
    actionData: {
      handler(val) {
        this.blocks = actionDataToBlocks(val || [])
      },
      immediate: true,
      deep: false
    }
  },
  beforeUnmount() {
    // 切换至脚本预览时组件会被销毁，先提交未按回车添加的值
    if (this.newInValue.trim()) {
      const inCheckBlock = this.blocks.find(b => b.type === 'in-check')
      if (inCheckBlock) {
        const vals = [...(inCheckBlock.inValues || []).filter(v => v != null && String(v).trim() !== ''), this.newInValue.trim()]
        inCheckBlock['inValues'] = vals
        this.$emit('update', blocksToActionData(this.blocks))
      }
    }
  },
  methods: {
    sync() {
      this.$emit('update', blocksToActionData(this.blocks))
    },
    addBlock(type) {
      this.blocks.push(newBlock(type))
      this.sync()
    },
    removeBlock(bi) {
      this.blocks.splice(bi, 1)
      this.sync()
    },
    moveBlock(bi, dir) {
      const target = bi + dir
      if (target < 0 || target >= this.blocks.length) return
      const temp = this.blocks[bi]
      this.blocks[bi] = this.blocks[target]
      this.blocks[target] = temp
      this.sync()
    },
    hasElse(block) {
      return (block.branches || []).some(b => b.type === 'else')
    },
    addBranch(block, type) {
      block.branches.push({ type, condVar: '', condVarType: '', condOp: '==', condValue: '', actions: [{ type: 'assign', target: '', value: '' }] })
      this.sync()
    },
    /**
     * 选择条件变量时同步记录其类型（condVarType），供前后端编译时决定比较常量是否加引号；
     * 手动输入的自定义变量无元数据，置空后回退到启发式推断。
     */
    onCondVarSelect(branch, v) {
      if (!v) {
        branch['condVar'] = ''
        branch['condVarType'] = ''
      } else {
        branch['condVar'] = v.varCode
        branch['condVarType'] = (!v._custom && v.varType) || ''
      }
      this.sync()
    },
    removeBranch(block, bri) {
      block.branches.splice(bri, 1)
      if (block.branches.length > 0 && block.branches[0].type !== 'if') block.branches[0].type = 'if'
      this.sync()
    },
    addInValue(block) {
      if (this.newInValue.trim()) {
        const vals = [...(block.inValues || []).filter(v => v != null && String(v).trim() !== ''), this.newInValue.trim()]
        block['inValues'] = vals
        this.newInValue = ''
        this.sync()
      }
    },
    removeInValue(block, index) {
      const vals = [...(block.inValues || [])]
      vals.splice(index, 1)
      block['inValues'] = vals
      this.sync()
    },
    onFuncSelect(block, funcCode) {
      const func = this.functions.find(f => f.funcCode === funcCode)
      if (func && func.paramsJson) {
        try {
          const params = JSON.parse(func.paramsJson)
          block.args = params.map(p => p.name || '')
        } catch (e) { /* ignore */ }
      }
      this.sync()
    },
    addHeader(block) {
      if (!Array.isArray(block.headers)) block['headers'] = []
      block.headers.push({ key: '', value: '' })
      this.sync()
    },
    typeLabel(type) {
      const t = BLOCK_TYPES.find(b => b.type === type)
      return t ? t.label : type
    },
    typeColor(type) {
      const t = BLOCK_TYPES.find(b => b.type === type)
      return t ? t.color : '#999'
    },
    /**
     * 判断 select 事件传出的引用对象是否为常量。
     */
    isConstRef(v) {
      return !!(v && ((v._ref && v._ref.category === 'constant') ||
        v.varSource === 'CONSTANT' ||
        (v.varObj && v.varObj.varSource === 'CONSTANT')))
    },
    /**
     * 回显时按赋值目标 varCode 在 vars 中回查是否为常量；常量不可作为赋值目标，其「值」输入置灰。
     */
    isConstTarget(target) {
      if (!target) return false
      return this.isConstRef((this.vars || []).find(v => v.varCode === target))
    },
    /**
     * 选择赋值目标：常量不允许被赋值（值只能在常量配置管理中修改），
     * 选中常量时自动填入该常量的最新默认值并提示，仅用于参考。
     */
    onAssignTargetSelect(holder, v) {
      holder.target = v ? v.varCode : ''
      if (this.isConstRef(v)) {
        // 回填常量最新默认值（defaultValue 在选项的 varObj 上，非顶层字段）
        const raw = v.varObj && v.varObj.defaultValue != null ? v.varObj.defaultValue : v.defaultValue
        const dv = raw != null ? String(raw) : ''
        if (Object.prototype.hasOwnProperty.call(holder, 'value')) holder.value = dv
        this.$message.warning('「' + (v.varLabel || v.varCode) + '」是常量，已自动填入其默认值；如需修改常量值请到常量配置管理中修改后重新发布')
      } else {
        // 非常量清空值（因为新选的是普通变量，不是之前的常量）
        if (Object.prototype.hasOwnProperty.call(holder, 'value')) holder.value = ''
      }
      this.sync()
    }
  }
}
</script>

<style lang="scss" scoped>
.block-editor { padding: 0; }
.block-item {
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  margin-bottom: 8px;
  background: #fafafa;
  overflow: hidden;
}
.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 6px;
  background: #f0f0f0;
  border-bottom: 1px solid #e0e0e0;
}
.block-header-actions { display: flex; gap: 0; }
.block-type-tag {
  font-size: 10px;
  font-weight: bold;
  color: #fff;
  padding: 1px 6px;
  border-radius: 3px;
}
.block-body { padding: 6px 8px; }

.inline-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 3px;
}
.eq { font-weight: bold; color: #999; flex-shrink: 0; }
.mini-label { font-size: 11px; color: #888; flex-shrink: 0; white-space: nowrap; }
.rounding-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  padding: 3px 0;
}

.branch-card {
  border: 1px solid #d9d9d9;
  border-radius: 3px;
  margin-bottom: 6px;
  background: #fff;
  overflow: hidden;
}
.branch-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 6px;
  background: #f5f5f5;
}
.branch-tag {
  font-size: 10px;
  font-weight: bold;
  color: #fff;
  padding: 1px 6px;
  border-radius: 2px;
  &.tag-if { background: #1890ff; }
  &.tag-elseif { background: #fa8c16; }
  &.tag-else { background: #8c8c8c; }
}
.cond-area {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 4px 6px;
  border-bottom: 1px dashed #e8e8e8;
  // IF 条件行与赋值行对齐：条件变量与值弹性均分，操作符保持固定宽
  .var-picker-wrap { flex: 1 1 0; min-width: 0; }
  .cond-value-input { flex: 1 1 0; min-width: 0; }
}
.branch-body { padding: 4px 6px; }
.branch-add-row { display: flex; gap: 4px; margin-top: 4px; }

.case-card {
  border: 1px solid #e0e0e0;
  border-radius: 3px;
  margin-bottom: 4px;
  background: #fff;
  &.default-case { background: #f9f9f9; }
}
.case-head {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  background: #f5f5f5;
}
.case-tag {
  font-size: 10px;
  font-weight: bold;
  color: #fff;
  background: #722ed1;
  padding: 1px 6px;
  border-radius: 2px;
  flex-shrink: 0;
  &.default-tag { background: #8c8c8c; }
}
.case-body { padding: 4px 6px; }
.loop-body {
  padding: 4px 6px;
  border: 1px dashed #d9d9d9;
  border-radius: 3px;
  background: #fff;
}
.http-sub {
  padding: 4px 6px;
  margin-bottom: 4px;
  border: 1px dashed #d9d9d9;
  border-radius: 3px;
  background: #fff;
}
.http-sub-title {
  font-size: 11px;
  color: #888;
  margin-bottom: 3px;
}
.const-hint-input {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
  color: #606266;
  cursor: not-allowed;
}
</style>
