import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Fail loudly instead of silently moving to 5174 if 5173 is taken, so the URL
    // in the README is always the right one.
    strictPort: true,
    // Vite's default host is "localhost", which Node resolves to a single address.
    // On this machine that is the IPv6 ::1, leaving IPv4 127.0.0.1 refused, and a
    // browser that reaches for IPv4 first then fails with ERR_CONNECTION_REFUSED.
    // Listening on all interfaces serves both stacks.
    host: true,
    proxy: {
      // Keeps the browser on one origin during development.
      '/api': {
        // 127.0.0.1 rather than localhost: the backend listens on IPv4, and naming
        // it explicitly avoids the same resolution problem on the proxy hop.
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
