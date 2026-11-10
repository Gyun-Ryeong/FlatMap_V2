import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// dev 서버: 기본 3000(기존 CRA 와 같은 포트). 다른 포트가 필요하면 `vite --port 3001`.
// /api 요청은 백엔드(기본 8080)로 프록시한다. 백엔드를 다른 포트로 띄웠다면 VITE_DEV_PROXY_TARGET 으로 바꾼다.
// 빌드 출력은 기존과 같은 build/ (gitignore 대상).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    plugins: [react()],
    server: {
      port: 3000,
      proxy: {
        '/api': env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080',
      },
    },
    test: {
      environment: 'node',
      include: ['src/**/*.test.{js,jsx}'],
    },
    build: {
      outDir: 'build',
    },
  };
});
