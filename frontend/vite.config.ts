import { defineConfig } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'

// 백엔드(8080)로 /api 를 프록시한다. 동일 출처가 되어 세션 쿠키/CSRF(XSRF-TOKEN)가 그대로 흐른다.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 소셜 로그인 흐름. Host 를 유지(changeOrigin: false)해야 서버가 콜백 주소를 5173 기준으로 만들고
      // 로그인 후 '/' 이동도 SPA 로 돌아온다. 제공자 콘솔의 콜백: http://localhost:5173/login/oauth2/code/{id}
      '/oauth2': { target: 'http://localhost:8080' },
      '/login/oauth2': { target: 'http://localhost:8080' },
    },
  },
})
