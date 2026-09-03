<template>
  <el-container style="height: 100vh;">
    <el-header height="50px" class="layout-header">
      <div class="header-title">规则引擎可视化编排系统</div>
      <div v-if="loginEnabled" class="header-actions">
        <span class="user-label">{{ username }}</span>
        <el-button type="text" @click="doLogout">退出</el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside :width="sideBarWidth + 'px'" class="layout-aside">
        <el-menu
          :default-active="$route.path"
          router
          :background-color="menuBg"
          :text-color="menuText"
          :active-text-color="menuActiveText"
        >
          <el-menu-item index="/project">
            <i class="el-icon-folder" />
            <span>规则项目</span>
          </el-menu-item>
          <el-menu-item index="/rule">
            <i class="el-icon-tickets" />
            <span>规则管理</span>
          </el-menu-item>
          <el-menu-item index="/ruleset">
            <i class="el-icon-files" />
            <span>规则集管理</span>
          </el-menu-item>
          <el-menu-item index="/variable">
            <i class="el-icon-collection-tag" />
            <span>变量管理</span>
          </el-menu-item>
          <el-menu-item index="/function">
            <i class="el-icon-s-operation" />
            <span>函数管理</span>
          </el-menu-item>
          <el-menu-item index="/test">
            <i class="el-icon-video-play" />
            <span>规则测试</span>
          </el-menu-item>
          <el-menu-item index="/log">
            <i class="el-icon-document" />
            <span>执行日志</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
    <!-- 规则引擎设计器抽屉（全局挂载，由 Vuex designerDrawer 模块控制显隐；从右侧滑出） -->
    <el-drawer
      :visible.sync="designerDrawerSync"
      direction="rtl"
      size="92%"
      :with-header="false"
      :destroy-on-close="true"
      append-to-body
      custom-class="designer-shell-drawer"
    >
      <div class="designer-drawer-inner">
        <DesignerDrawerHost v-if="designerDrawerSync && drawerSegment && drawerDefinitionId" />
      </div>
    </el-drawer>
  </el-container>
</template>

<script>
// ICSS :export 在 vite 下不可用，改用 JS 常量（与 styles/variables.scss 保持一致）
const variables = {
  sideBarWidth: '210px',
  menuBg: '#E9EBF0',
  menuText: '#161617',
  menuActiveText: '#B30000',
  menuHover: '#F4F5F7'
}
import { mapState } from 'vuex'
import { getConsoleAuthConfig, consoleLogout, getConsoleMe } from '@/api/auth'
import DesignerDrawerHost from '@/components/common/DesignerDrawerHost.vue'

export default {
  name: 'Layout',
  components: { DesignerDrawerHost },
  data() {
    return {
      loginEnabled: false,
      username: ''
    }
  },
  computed: {
    sideBarWidth() { return parseInt(variables.sideBarWidth) },
    menuBg() { return variables.menuBg },
    menuText() { return variables.menuText },
    menuActiveText() { return variables.menuActiveText },
    ...mapState('designerDrawer', {
      drawerSegment: 'segment',
      drawerDefinitionId: 'definitionId'
    }),
    /**
     * 设计器抽屉 visible 双向绑定到 Vuex designerDrawer 模块
     */
    designerDrawerSync: {
      get() {
        return this.$store.state.designerDrawer.visible
      },
      set(v) {
        if (!v) this.$store.commit('designerDrawer/CLOSE')
      }
    }
  },
  async mounted() {
    await this.refreshAuthBar()
  },
  methods: {
    /**
     * 根据后端配置决定是否展示登录用户与退出按钮。
     */
    async refreshAuthBar() {
      try {
        const cfg = await getConsoleAuthConfig()
        this.loginEnabled = !!(cfg.data && cfg.data.loginEnabled)
        if (!this.loginEnabled) return
        const me = await getConsoleMe()
        this.username = (me.data && me.data.username) || ''
      } catch (e) {
        this.loginEnabled = false
        this.username = ''
      }
    },
    /**
     * 调用登出接口并回到登录页。
     */
    async doLogout() {
      try {
        await consoleLogout()
      } finally {
        this.$router.replace({ path: '/login' })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.layout-header {
  background: #fff;
  border-bottom: 2px solid $--color-primary;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;

  .header-title {
    font-size: 18px;
    font-weight: bold;
    color: $--color-primary;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    font-size: 14px;
    color: #606266;
  }

  .user-label {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.layout-aside {
  background: $menuBg;
  overflow: hidden;
}

.layout-main {
  background: #F3F3F3;
  padding: 16px;
  overflow-y: auto;
  overflow-x: hidden;
  min-width: 0;
}
</style>

<style lang="scss">
/* 设计器抽屉：内容区可上下滚动、去内边距 */
.designer-shell-drawer .el-drawer__body {
  padding: 0;
  overflow-y: auto;
}
.designer-shell-drawer .designer-drawer-inner {
  min-height: 100%;
}
</style>
