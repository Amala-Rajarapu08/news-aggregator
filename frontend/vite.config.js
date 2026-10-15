import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_BACKEND_PROXY_TARGET || process.env.BACKEND_URL || 'http://127.0.0.1:8080'
  const proxy = { '/api': { target, changeOrigin: true } }
  return {
    plugins: [react()],
    server: { host: '127.0.0.1', port: 5180, strictPort: false, proxy },
    preview: { host: '127.0.0.1', port: 4173, strictPort: false, proxy },
  }
})
