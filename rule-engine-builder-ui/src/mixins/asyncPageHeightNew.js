export const asyncPageHeightNew = {
  /*
    固定高度：198（分别是页头56、一级导航高度40px,内容外部上下padding:16、tabs页签：44、内容-内部padding:16px 8px,总共198）
    this.tableHeight = this.windowHeight(去除固定高度后的内容高度) - this.conditionHeight(查询条件高度) - this.operateBtnHeight(页面操作栏高度) - 48(分页高度) - 6(这个暂时不知道从哪来的)
  */
  data() {
    return {
      windowHeight: 0,
      conditionHeight: 0, // 查询条件高度
      operateBtnHeight: 0, // 操作按钮高度
      tableHeight: 500, // 表格高度
      clearTimer: ''
    }
  },
  mounted() {
    const _this = this
    _this.getTableMaxHeight()
    window.onresize = function() {
      window.clientHeight = document.body.clientHeight - 198
      _this.windowHeight = document.body.clientHeight - 198
      _this.getTableMaxHeight()
    }
  },
  methods: {
    getTableMaxHeight() {
      this.$nextTick(function() {
        const pageUiMode = localStorage.getItem('uiMode')
        this.windowHeight = document.documentElement.clientHeight - 198

        // 处理 conditionRef：可能是子组件或原生DOM元素
        let conditionElement = null
        if (this.$refs.conditionRef) {
          // 如果是Vue组件实例，通过$el获取其根DOM元素；如果是原生DOM元素则直接使用
          conditionElement = this.$refs.conditionRef.$el || this.$refs.conditionRef
        }

        const conditionOffsetHeight = (conditionElement && conditionElement.offsetHeight) || 0
        const operateBtnOffsetHeight = (this.$refs.operateBtnRef && this.$refs.operateBtnRef.offsetHeight) || 0

        this.conditionHeight = conditionOffsetHeight > 0 ? conditionOffsetHeight : 112
        this.operateBtnHeight = operateBtnOffsetHeight > 0 ? operateBtnOffsetHeight : 48
        if (pageUiMode === '32') {
          this.tableHeight = this.windowHeight - this.conditionHeight - this.operateBtnHeight - 86
        } else if (pageUiMode === '26') {
          this.tableHeight = this.windowHeight - this.conditionHeight - this.operateBtnHeight - 54
        }
      })
    },
    moreConditons() {
      this.clearTimer = setTimeout(() => {
        this.getTableMaxHeight()
      }, 1000)
    }
  },
  destroyed() {
    clearTimeout(this.clearTimer)
  }
}
