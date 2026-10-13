import { defineConfig, loadEnv, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // Backend the dev proxy forwards to. Not exposed to the browser bundle.
  const backend = env.DEV_PROXY_TARGET || 'http://localhost:8080';
  // Origin the backend CORS config already allows; the proxy presents it so
  // requests arriving through a tunnel (https://xxx.devtunnels.ms, ngrok, ...)
  // are not rejected by Spring.
  const backendOrigin = env.DEV_PROXY_ORIGIN || 'http://localhost:5173';

  const rewriteOrigin: Pick<ProxyOptions, 'configure'> = {
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq) => {
        if (proxyReq.getHeader('origin')) proxyReq.setHeader('origin', backendOrigin);
      });
      proxy.on('proxyReqWs', (proxyReq) => {
        proxyReq.setHeader('origin', backendOrigin);
      });
    },
  };

  return {
    plugins: [react()],
    server: {
      host: true, // listen on all interfaces so a tunnel can reach it
      port: 5173,
      strictPort: true,
      allowedHosts: true, // tunnel hostnames change per session
      proxy: {
        '/api': { target: backend, changeOrigin: true, ...rewriteOrigin },
        '/ws': { target: backend, changeOrigin: true, ws: true, ...rewriteOrigin },
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
  };
});
