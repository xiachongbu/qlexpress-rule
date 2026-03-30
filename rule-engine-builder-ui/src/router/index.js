import Vue from 'vue'
import VueRouter from 'vue-router'
import axios from 'axios'
import Layout from '@/layout/index.vue'

Vue.use(VueRouter)

/**
 * 与后端会话 Cookie 配合的裸 axios，避免与 response 封装循环依赖。
 */
const authAxios = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true
})

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/project',
    children: [
      {
        path: 'project',
        name: 'ProjectList',
        component: () => import('@/views/project/ProjectList.vue'),
        meta: { title: '规则项目' }
      },
      {
        path: 'project/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetail.vue'),
        meta: { title: '项目详情' }
      },
      {
        path: 'designer/table/:id',
        name: 'DecisionTable',
        component: () => import('@/views/designer/DecisionTable.vue'),
        meta: { title: '决策表设计器' }
      },
      {
        path: 'designer/tree/:id',
        name: 'DecisionTree',
        component: () =>
          import(/* webpackChunkName: "designer-decision-tree" */ '@/views/designer/DecisionTree.vue'),
        meta: { title: '决策树设计器' }
      },
      {
        path: 'designer/flow/:id',
        name: 'DecisionFlow',
        component: () =>
          import(/* webpackChunkName: "designer-decision-flow" */ '@/views/designer/DecisionFlow.vue'),
        meta: { title: '决策流设计器' }
      },
      {
        path: 'designer/cross/:id',
        name: 'CrossTable',
        component: () => import('@/views/designer/CrossTable.vue'),
        meta: { title: '交叉表设计器' }
      },
      {
        path: 'designer/score/:id',
        name: 'Scorecard',
        component: () => import('@/views/designer/Scorecard.vue'),
        meta: { title: '评分卡设计器' }
      },
      {
        path: 'designer/cross-adv/:id',
        name: 'AdvancedCrossTable',
        component: () => import('@/views/designer/AdvancedCrossTable.vue'),
        meta: { title: '复杂交叉表设计器' }
      },
      {
        path: 'designer/score-adv/:id',
        name: 'AdvancedScorecard',
        component: () => import('@/views/designer/AdvancedScorecard.vue'),
        meta: { title: '复杂评分卡设计器' }
      },
      {
        path: 'designer/script/:id',
        name: 'ScriptEditor',
        component: () => import('@/views/designer/ScriptEditor.vue'),
        meta: { title: 'QL脚本编辑器' }
      },
      {
        path: 'variable',
        name: 'VariableList',
        component: () => import('@/views/variable/VariableList.vue'),
        meta: { title: '变量管理' }
      },
      {
        path: 'function',
        name: 'FunctionList',
        component: () => import('@/views/function/FunctionList.vue'),
        meta: { title: '函数管理' }
      },
      {
        path: 'test',
        name: 'RuleTest',
        component: () => import('@/views/test/RuleTest.vue'),
        meta: { title: '规则测试' }
      },
      {
        path: 'log',
        name: 'ExecutionLog',
        component: () => import('@/views/log/ExecutionLog.vue'),
        meta: { title: '执行日志' }
      }
    ]
  }
]

const router = new VueRouter({
  routes
})

/**
 * 若后端启用控制台登录，则校验会话；未启用时与改造前行为一致。
 */
router.beforeEach(async (to, from, next) => {
  if (to.path === '/login') {
    try {
      const cfgRes = await authAxios.get('/auth/console/config')
      const body = cfgRes.data
      if (body && body.code === 200 && body.data && !body.data.loginEnabled) {
        return next('/project')
      }
    } catch (e) {
      /* 无法拉取配置时仍展示登录页 */
    }
    return next()
  }
  try {
    const cfgRes = await authAxios.get('/auth/console/config')
    const body = cfgRes.data
    if (!body || body.code !== 200 || !body.data || !body.data.loginEnabled) {
      return next()
    }
    const meRes = await authAxios.get('/auth/console/me')
    const me = meRes.data
    if (me && me.code === 200 && me.data && me.data.username) {
      return next()
    }
    return next({ path: '/login', query: { redirect: to.fullPath } })
  } catch (e) {
    return next()
  }
})

export default router
