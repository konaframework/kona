import { defineConfig } from 'vite';
import { resolve } from 'node:path';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
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
