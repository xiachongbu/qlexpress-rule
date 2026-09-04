import { createStore } from 'vuex'
import designerDrawer from './modules/designerDrawer'
import ruleDict from './modules/ruleDict'

export default createStore({
  state: {
    currentProject: null
  },
  mutations: {
    SET_CURRENT_PROJECT(state, project) {
      state.currentProject = project
    }
  },
  actions: {},
  modules: {
    designerDrawer,
    ruleDict
  }
})
