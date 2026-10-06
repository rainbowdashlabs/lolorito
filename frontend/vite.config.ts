import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        // /api/v1/ws/valuations is a WebSocket upgrade.
        ws: true,
      },
      // Auth flow: Discord's OAuth callback lands on the Vite origin so
      // the session cookie is set there; Vite proxies it through to the
      // backend where the real handler lives.
      '/auth': 'http://localhost:8080',
    },
  },
})
