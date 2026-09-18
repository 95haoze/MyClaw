import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    plugins: [vue(), tailwindcss()],
    server: { port: 5173, proxy: { '/api': { target: env.API_PROXY_TARGET || 'http://localhost:19090', changeOrigin: true } } },
  }
})
