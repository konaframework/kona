import { defineConfig } from 'vite';
import { resolve } from 'node:path';

export default defineConfig({
  publicDir: false,
  build: {
    outDir: 'public/build',
    emptyOutDir: true,
    manifest: true,
    rollupOptions: {
      input: resolve('src/main/resources/js/app.js'),
    },
  },
  server: {
    origin: 'http://127.0.0.1:5173',
  },
});
