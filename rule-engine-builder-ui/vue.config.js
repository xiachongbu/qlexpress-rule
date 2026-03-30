/**
 * Vue CLI 构建配置：生产构建为常规压缩打包，不再使用 javascript-obfuscator 混淆。
 * 构建产物默认输出到本目录下的 dist/，不写入 rule-engine-server。
 */
module.exports = {
  /** 生产构建不跑 eslint-loader（避免历史代码阻断打包）；开发可依赖 IDE，或执行 npm run lint */
  lintOnSave: process.env.NODE_ENV !== 'production',
  productionSourceMap: false,
  devServer: {
    port: 9090,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  css: {
    loaderOptions: {
      scss: {
        prependData: `@import "~@/styles/variables.scss";`
      }
    }
  },
  chainWebpack: (config) => {
    if (process.env.NODE_ENV === 'production') {
      config.module.rules.delete('eslint')
    }
  }
}
