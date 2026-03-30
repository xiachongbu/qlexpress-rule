import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    currentProject: null
  },
  mutations: {
    SET_CURRENT_PROJECT(state, project) {
      state.currentProject = project
    }
  },
  actions: {},
  modules: {}
})
