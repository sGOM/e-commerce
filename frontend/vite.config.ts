import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 백엔드(8080)로 /api 를 프록시한다. 동일 출처가 되어 세션 쿠키/CSRF(XSRF-TOKEN)가 그대로 흐른다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
