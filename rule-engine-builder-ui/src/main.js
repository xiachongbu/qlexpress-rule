import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/dist/index.css'
// element-plus 移除了字体图标；引入 element-ui 兼容字体图标（存量 el-icon-* 类名零改动）
import './assets/element-ui-icons/icon.css'
import App from './App.vue'
import router from './router'
import store from './store'
import './styles/index.scss'
import { installDirectives } from './directive'

const app = createApp(App)

app.use(ElementPlus, { size: 'small', locale: zhCn })
app.use(router)
app.use(store)

// Vue2 全局 API 兼容垫片：存量代码中的 this.$message / $confirm / $alert 调用零改动
app.config.globalProperties.$message = ElMessage
app.config.globalProperties.$confirm = ElMessageBox.confirm
app.config.globalProperties.$alert = ElMessageBox.alert

installDirectives(app)

app.mount('#app')
