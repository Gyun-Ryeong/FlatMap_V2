import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// dev 서버: 기본 3000(기존 CRA 와 같은 포트). 다른 포트가 필요하면 `vite --port 3001`.
// /api 요청은 백엔드(8080)로 프록시한다. 빌드 출력은 기존과 같은 build/ (gitignore 대상).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'build',
  },
});
