import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

/**
 * Vite 构建配置（自 vue-cli 迁移）：
 * - 开发端口 9090，/api 代理到 rule-engine-server（8080）
 * - 全局注入 variables.scss
 * - 生产构建不输出 sourcemap
 * 构建产物输出到本目录下的 dist/，不写入 rule-engine-server。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 9090,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        // 全局注入变量文件；variables.scss 自身编译时跳过，避免自引用循环
        additionalData: (source, filename) =>
          filename.replace(/\\/g, '/').endsWith('/styles/variables.scss')
            ? source
            : `@import "@/styles/variables.scss";\n${source}`
      }
    }
  },
  build: {
    sourcemap: false
  }
})
