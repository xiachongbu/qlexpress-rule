<template>
  <el-dialog
    title="保存 — 版本说明"
    :visible.sync="innerVisible"
    width="480px"
    append-to-body
    @close="onDialogClose"
  >
    <el-input
      v-model="changeLog"
      type="textarea"
      :rows="4"
      maxlength="512"
      show-word-limit
      placeholder="可选填写本次保存说明，便于日后在版本历史中辨认"
    />
    <span slot="footer" class="dialog-footer">
      <el-button @click="cancel">取 消</el-button>
      <el-button type="primary" @click="confirm">保 存</el-button>
    </span>
  </el-dialog>
</template>

<script>
/**
 * 保存前收集版本说明；通过 ref.prompt() 返回 Promise（resolve 为说明文本，取消则 reject）。
 */
export default {
  name: 'DesignSaveVersionDialog',
  data() {
    return {
      innerVisible: false,
      changeLog: '',
      resolveFn: null,
      rejectFn: null
    }
  },
  methods: {
    /**
     * 打开对话框，确认后 resolve(说明文本)，取消或关闭则 reject。
     */
    prompt() {
      this.changeLog = ''
      this.innerVisible = true
      return new Promise((resolve, reject) => {
        this.resolveFn = resolve
        this.rejectFn = reject
      })
    },
    /**
     * 用户确认保存。
     */
    confirm() {
      const text = (this.changeLog || '').trim()
      const fn = this.resolveFn
      this.resolveFn = null
      this.rejectFn = null
      if (fn) fn(text)
      this.innerVisible = false
    },
    /**
     * 用户主动取消。
     */
    cancel() {
      const rej = this.rejectFn
      this.resolveFn = null
      this.rejectFn = null
      if (rej) rej(new Error('cancel'))
      this.innerVisible = false
    },
    /**
     * 遮罩或右上角关闭：若尚未 resolve/reject 则视为取消。
     */
    onDialogClose() {
      if (!this.rejectFn && !this.resolveFn) return
      const rej = this.rejectFn
      this.resolveFn = null
      this.rejectFn = null
      if (rej) rej(new Error('cancel'))
    }
  }
}
</script>
