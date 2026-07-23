<template>
  <el-select
    v-model="selectedId"
    class="design-version-switcher"
    :size="selectSize"
    clearable
    filterable
    :placeholder="placeholder"
    :loading="loading"
    :disabled="!definitionId"
    @visible-change="onDropdownVisible"
    @change="onSelectChange"
  >
    <el-option
      v-for="row in options"
      :key="row.id"
      :label="formatOptionLabel(row)"
      :value="row.id"
    />
  </el-select>
</template>

<script>
import {getDesignSnapshot, listDesignSnapshots} from '@/api/definition'

/**
 * 工具栏下拉：加载当前规则与作用域下的设计快照，选中后拉取 modelJson 并交给父组件应用到画布。
 */
export default {
  name: 'DesignVersionSwitcher',
  props: {
    /** 规则定义 ID */
    definitionId: {
      type: [Number, String],
      default: null
    },
    /** 作用域 compId */
    scopeCompId: {
      type: [Number, String],
      default: '0'
    },
    /** 占位文案 */
    placeholder: {
      type: String,
      default: '切换历史版本'
    },
    /** 列表单次拉取条数（一般足够覆盖近期保存） */
    listPageSize: {
      type: Number,
      default: 100
    },
    /** el-select 尺寸：与工具栏按钮一致时可传 mini */
    selectSize: {
      type: String,
      default: 'small'
    }
  },
  data() {
    return {
      loading: false,
      options: [],
      selectedId: null
    }
  },
  watch: {
    definitionId() {
      this.resetLocal()
    },
    scopeCompId() {
      this.resetLocal()
    }
  },
  methods: {
    /**
     * 切换规则或作用域时清空选项与选中项。
     */
    resetLocal() {
      this.options = []
      this.selectedId = null
    },

    /**
     * 下拉展开时重新拉取列表，保证刚保存的快照能出现在选项中。
     */
    onDropdownVisible(open) {
      if (open && this.definitionId) {
        this.fetchOptions()
      }
    },

    /**
     * 拉取快照列表（单页，按时间倒序）。
     */
    async fetchOptions() {
      if (!this.definitionId) return
      this.loading = true
      try {
        const res = await listDesignSnapshots({
          definitionId: this.definitionId,
          scopeCompId: this.scopeCompId != null && this.scopeCompId !== '' ? String(this.scopeCompId) : '0',
          pageNum: 1,
          pageSize: this.listPageSize
        })
        const page = res && res.data ? res.data : res
        this.options = (page && page.records) || []
      } catch (e) {
        this.options = []
      } finally {
        this.loading = false
      }
    },

    /**
     * 将接口返回的时间格式化为 yyyy-MM-dd HH:mm:ss（与规则列表创建时间一致，兼容 ISO 与 LocalDateTime 数组 JSON）。
     */
    formatDateTime(time) {
      if (time == null || time === '') return '-'
      let d
      if (Array.isArray(time) && time.length >= 6) {
        const y = time[0]
        const mo = time[1] - 1
        const day = time[2]
        const h = time[3] || 0
        const mi = time[4] || 0
        const s = time[5] || 0
        d = new Date(y, mo, day, h, mi, s)
      } else {
        d = new Date(time)
      }
      if (isNaN(d.getTime())) return typeof time === 'string' ? time : '-'
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },

    /**
     * 生成下拉项展示文案。
     */
    formatOptionLabel(row) {
      const ver = row.designVersion != null ? `v${row.designVersion}` : `#${row.id}`
      const t = this.formatDateTime(row.createTime)
      const log = (row.changeLog && String(row.changeLog).trim()) || '无说明'
      return `${ver} · ${t} · ${log}`
    },

    /**
     * 选中某条快照：请求详情、解析 JSON、emit；随后清空选中以便可重复选同一条。
     */
    async onSelectChange(id) {
      if (id == null || id === '') return
      try {
        const res = await getDesignSnapshot(id, {
          definitionId: this.definitionId,
          scopeCompId: this.scopeCompId != null && this.scopeCompId !== '' ? String(this.scopeCompId) : '0'
        })
        const wrap = res && res.data ? res.data : res
        const raw = wrap && wrap.modelJson
        if (raw == null) {
          this.$message.error('未获取到快照内容')
          return
        }
        let parsed
        try {
          parsed = JSON.parse(raw)
        } catch (err) {
          this.$message.error('快照 JSON 解析失败')
          return
        }
        // 先让下拉关闭并完成一帧绘制，再整块替换模型，减轻与重排叠在同一时刻的主线程卡顿感
        await this.$nextTick()
        await new Promise(resolve => requestAnimationFrame(resolve))
        this.$emit('apply-model', parsed)
        this.$message.success('已加载该版本到编辑器，确认后请点击「保存」写回服务器')
      } catch (e) {
        /* 拦截器已提示 */
      } finally {
        await this.$nextTick()
        this.selectedId = null
      }
    }
  }
}
</script>

<style scoped>
.design-version-switcher {
  min-width: 220px;
  max-width: 360px;
}
</style>
