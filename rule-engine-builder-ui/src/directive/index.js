import Vue from 'vue'

/**
 * v-preventMousedown：阻止按钮 mousedown 默认行为，避免点击后焦点样式残留。
 */
Vue.directive('preventMousedown', (el) => {
  el.addEventListener('mousedown', (e) => {
    e.preventDefault()
  })
})

/**
 * v-dialog-drag：使 el-dialog 可通过标题栏拖拽移动。
 */
Vue.directive('dialogDrag', {
  bind(el) {
    const dialogHeaderEl = el.querySelector('.el-dialog__header')
    const dragDom = el.querySelector('.el-dialog')
    if (!dialogHeaderEl || !dragDom) {
      return
    }
    dialogHeaderEl.style.cssText += ';cursor:move;'
    dragDom.style.cssText += ';top:0px;'

    const sty = (function () {
      if (window.document.currentStyle) {
        return (dom, attr) => dom.currentStyle[attr]
      }
      return (dom, attr) => getComputedStyle(dom, false)[attr]
    })()

    dialogHeaderEl.onmousedown = (e) => {
      const disX = e.clientX - dialogHeaderEl.offsetLeft
      const disY = e.clientY - dialogHeaderEl.offsetTop

      const screenWidth = document.body.clientWidth
      const screenHeight = document.documentElement.clientHeight

      const dragDomWidth = dragDom.offsetWidth
      const dragDomheight = dragDom.offsetHeight

      const minDragDomLeft = dragDom.offsetLeft
      const maxDragDomLeft = screenWidth - dragDom.offsetLeft - dragDomWidth

      const minDragDomTop = dragDom.offsetTop
      const maxDragDomTop = screenHeight - dragDom.offsetTop - dragDomheight

      let styL = sty(dragDom, 'left')
      let styT = sty(dragDom, 'top')

      if (styL.includes('%')) {
        styL = +document.body.clientWidth * (+styL.replace(/%/g, '') / 100)
        styT = +document.body.clientHeight * (+styT.replace(/%/g, '') / 100)
      } else {
        styL = +styL.replace('px', '')
        styT = +styT.replace('px', '')
      }

      document.onmousemove = function (e) {
        let left = e.clientX - disX
        let top = e.clientY - disY

        if (-left > minDragDomLeft) {
          left = -minDragDomLeft
        } else if (left > maxDragDomLeft) {
          left = maxDragDomLeft
        }

        if (-top > minDragDomTop) {
          top = -minDragDomTop
        } else if (top > maxDragDomTop) {
          top = maxDragDomTop
        }

        dragDom.style.cssText += `;left:${left + styL}px;top:${top + styT}px;`
      }

      document.onmouseup = function () {
        document.onmousemove = null
        document.onmouseup = null
      }
    }
  }
})
