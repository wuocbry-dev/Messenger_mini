import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // Cho phép truy cập từ mạng LAN
    host: true,
    // Proxy requests sang backend
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Trình duyệt coi request qua proxy là same-origin. Bỏ header Origin
        // để Spring CORS không chặn (403) khi truy cập qua devtunnel / IP LAN.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
          });
        },
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  define: {
    'process.env': {},
    global: 'globalThis',
  },
  optimizeDeps: {
    // stompjs 2.x is CommonJS; let Vite pre-bundle the browser build into ESM.
    include: ['stompjs/lib/stomp.js'],
  },
});
