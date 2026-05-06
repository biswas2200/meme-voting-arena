import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [
    react({
      // Treat .js files as JSX (CRA legacy project — avoids renaming all files)
      include: ['**/*.{jsx,js,tsx,ts}'],
    }),
  ],
  // Polyfill Node.js `global` for sockjs-client which uses it in the browser
  define: {
    global: 'globalThis',
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['./src/setupTests.js'],
    css: false,
    include: ['src/**/*.test.{jsx,tsx}'],
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'build',
  },
  optimizeDeps: {
    esbuildOptions: {
      loader: {
        '.js': 'jsx',
      },
      // Also define global at the esbuild level for deps like sockjs-client
      define: {
        global: 'globalThis',
      },
    },
  },
});
