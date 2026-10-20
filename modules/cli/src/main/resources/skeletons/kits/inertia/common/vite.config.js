import { defineConfig } from 'vite';
import { resolve } from 'node:path';
import { mkdirSync, unlinkSync, writeFileSync } from 'node:fs';
import vue from '@vitejs/plugin-vue';

function konaHotFile() {
  const hotFile = resolve('public/hot');
  const remove = () => {
    try { unlinkSync(hotFile); } catch { /* absent */ }
  };
  return {
    name: 'kona-hot-file',
    apply: 'serve',
    configureServer(server) {
      const httpServer = server.httpServer;
      if (!httpServer) return;
      httpServer.once('listening', () => {
        const address = httpServer.address();
        const port = address && typeof address === 'object' ? address.port : 5173;
        let host = server.config.server.host;
        if (!host || host === true || host === '0.0.0.0' || host === '::') host = '127.0.0.1';
        const protocol = server.config.server.https ? 'https' : 'http';
        mkdirSync(resolve('public'), { recursive: true });
        writeFileSync(hotFile, protocol + '://' + host + ':' + port);
      });
      httpServer.on('close', remove);
    },
  };
}

function konaHotFileBuild() {
  return {
    name: 'kona-hot-file-build',
    apply: 'build',
    buildStart() {
      try { unlinkSync(resolve('public/hot')); } catch { /* absent */ }
    },
  };
}

export default defineConfig({
  plugins: [konaHotFile(), konaHotFileBuild(), vue()],
  publicDir: false,
  build: {
    outDir: 'public/build',
    emptyOutDir: true,
    manifest: 'manifest.json',
    rollupOptions: {
      input: resolve('src/main/resources/js/app.js'),
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
  },
});
