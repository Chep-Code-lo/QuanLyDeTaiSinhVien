import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

const configDir = dirname(fileURLToPath(import.meta.url))
const workspaceRoot = resolve(configDir, '..')

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const rootEnv = loadEnv(mode, workspaceRoot, '')
  const frontendPort = Number.parseInt(rootEnv.FRONTEND_PORT ?? '', 10)
  const backendPort = rootEnv.BACKEND_PORT ?? '8080'

  return {
    plugins: [react()],
    server: {
      port: Number.isNaN(frontendPort) ? 5173 : frontendPort,
      proxy: {
        '/api': {
          changeOrigin: true,
          target: `http://localhost:${backendPort}`,
        },
      },
    },
  }
})
