// vite.config.ts
import {defineConfig, loadEnv} from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), '')
    const httpTarget = env.VITE_BACKEND_HTTP || 'http://localhost:8000'
    const wsTarget = env.VITE_BACKEND_WS || httpTarget.replace(/^http/, 'ws')

    return {
        plugins: [react()],
        server: {
            port: 5173,
            strictPort: true,
            proxy: {
                /**
                 * ОДИН и тот же путь /meetings:
                 * - Если браузер запрашивает страницу (Accept: text/html) - отдаем index.html (SPA).
                 * - Иначе (fetch/XHR, JSON и т.п.) - проксируем на бэкенд.
                 */
                '^/meetings(/|$)': {
                    target: httpTarget,
                    changeOrigin: true,
                    secure: false,
                    ws: false,
                    bypass(req) {
                        const accept = req.headers.accept || ''
                        // Навигация по URL в браузере -> SPA
                        if (accept.includes('text/html')) {
                            return '/index.html'
                        }
                        // Иначе это API - пусть проксируется
                    },
                },

                // WebSocket шлюз (оставь путь /voice на фронте)
                '^/voice$': {
                    target: wsTarget,
                    changeOrigin: true,
                    secure: false,
                    ws: true,
                },

                // Если есть прочие API - можно добавлять тут ещё правила
            },
        },
    }
})
