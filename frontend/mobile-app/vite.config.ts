import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import { fileURLToPath, URL } from 'node:url'

/** uni-app 的 Vite 基础配置。 */
export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@shared': fileURLToPath(new URL('../shared/src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://19.191.192.146:20081',
        // target: 'http://localhost:20080/',
        changeOrigin: true,
      }, 
    },
  },
})