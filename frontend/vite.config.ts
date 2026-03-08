import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';

/** Handle client disconnect/EPIPE gracefully so the terminal doesn't show "ws proxy socket error". */
function suppressWsEpipe() {
  return {
    name: 'suppress-ws-epipe',
    configureServer(server) {
      server.httpServer?.on('clientError', (err, socket) => {
        const code = (err as NodeJS.ErrnoException).code;
        if (code === 'ECONNRESET' || code === 'EPIPE') {
          socket.destroy();
          return;
        }
        server.config.logger.error('Client error', err);
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), suppressWsEpipe()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // No /ws proxy: frontend connects directly to backend (ws://host:8080) to avoid Vite proxy EPIPE and flaky connections
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './vitest.setup.ts'
  }
});

