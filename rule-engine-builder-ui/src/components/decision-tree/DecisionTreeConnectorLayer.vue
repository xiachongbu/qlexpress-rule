<template>
  <svg
    class="dt-connect-svg"
    :width="svgW"
    :height="svgH"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      v-for="(p, i) in paths"
      :key="'p' + i"
      :d="p.d"
      fill="none"
      stroke="#bfbfbf"
      stroke-width="1.5"
    />
  </svg>
</template>

<script>
/**
 * 在横向树容器上方绘制贝塞尔连线（锚点由 data-dt-anchor 标记）
 */
export default {
  name: 'DecisionTreeConnectorLayer',
  props: {
    /** 容器元素（与内部锚点坐标同源） */
    containerEl: { type: Object, default: null },
    /** 线段列表，每项为 [fromAnchorKey, toAnchorKey] */
    segments: { type: Array, default: () => [] },
    /** 重绘触发器 */
    tick: { type: Number, default: 0 }
  },
  data() {
    return {
      svgW: 1,
      svgH: 1,
      paths: []
    }
  },
  watch: {
    tick() {
      this.scheduleDraw()
    },
    containerEl() {
      this.scheduleDraw()
    },
    segments: {
      deep: true,
      handler() {
        this.scheduleDraw()
      }
    }
  },
  mounted() {
    this.scheduleDraw()
    window.addEventListener('resize', this._onWinResize)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this._onWinResize)
  },
  methods: {
    /**
     * 窗口尺寸变化时重绘连线
     */
    _onWinResize() {
      this.scheduleDraw()
    },
    /**
     * 防抖调度重绘
     */
    scheduleDraw() {
      if (this._drawTimer) clearTimeout(this._drawTimer)
      this._drawTimer = setTimeout(() => {
        this._drawTimer = null
        this.draw()
      }, 50)
    },
    /**
     * 根据 segments 与锚点 DOM 位置生成 path
     */
    draw() {
      const root = this.containerEl
      const segs = this.segments || []
      if (!root || !root.getBoundingClientRect) {
        this.paths = []
        return
      }

      const rr = root.getBoundingClientRect()
      const sl = root.scrollLeft || 0
      const st = root.scrollTop || 0
      const sw = Math.max(1, root.scrollWidth || rr.width)
      const sh = Math.max(1, root.scrollHeight || rr.height)
      if (this.svgW !== sw) this.svgW = sw
      if (this.svgH !== sh) this.svgH = sh

      const anchors = root.querySelectorAll('[data-dt-anchor]')
      const map = {}
      for (let i = 0; i < anchors.length; i++) {
        const el = anchors[i]
        const key = el.getAttribute('data-dt-anchor')
        if (!key) continue
        const b = el.getBoundingClientRect()
        map[key] = {
          x: b.left - rr.left + sl + b.width / 2,
          y: b.top - rr.top + st + b.height / 2
        }
      }

      const paths = []
      for (let j = 0; j < segs.length; j++) {
        const pair = segs[j]
        if (!pair || pair.length < 2) continue
        const a = map[pair[0]]
        const b = map[pair[1]]
        if (!a || !b) continue
        paths.push({ d: cubicPath(a.x, a.y, b.x, b.y) })
      }
      const same =
        this.paths.length === paths.length &&
        paths.every((p, i) => this.paths[i] && this.paths[i].d === p.d)
      if (!same) this.paths = paths
    }
  }
}

/**
 * 水平方向的 cubic bezier
 */
function cubicPath(x1, y1, x2, y2) {
  const dx = Math.max(40, Math.abs(x2 - x1) * 0.45)
  const c1x = x1 + dx
  const c1y = y1
  const c2x = x2 - dx
  const c2y = y2
  return 'M ' + x1 + ' ' + y1 + ' C ' + c1x + ' ' + c1y + ', ' + c2x + ' ' + c2y + ', ' + x2 + ' ' + y2
}
</script>

<style scoped>
.dt-connect-svg {
  position: absolute;
  left: 0;
  top: 0;
  pointer-events: none;
  z-index: 0;
  overflow: visible;
}
</style>
