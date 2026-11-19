import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 8791,
    proxy: {
      '/api': {
        target: 'http://localhost:8790',
        changeOrigin: true
      },
      '/webhook': {
        target: 'http://localhost:8790',
        changeOrigin: true
      },
      '/swagger-ui': {
        target: 'http://localhost:8790',
        changeOrigin: true
      },
      '/v3/api-docs': {
        target: 'http://localhost:8790',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.js'
  }
});
