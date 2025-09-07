import { useEffect, useMemo, useState } from 'react'
import reactLogo from './assets/react.svg'
import viteLogo from '/vite.svg'
import './App.css'

function App() {
  const [count, setCount] = useState(0)
  const [health, setHealth] = useState<unknown>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const healthUrl = useMemo(() => {
    const base = import.meta.env.PROD
      ? 'https://api.hunty-ai.javaboys.ru'
      : 'http://localhost:8000'
    return `${base}/health`
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const res = await fetch(healthUrl, { headers: { Accept: 'application/json, text/plain;q=0.9, */*;q=0.1' } })
        const text = await res.text()
        // Try to parse JSON; fallback to plain text
        let data: unknown
        try {
          data = JSON.parse(text)
        } catch {
          data = text
        }
        if (!cancelled) setHealth(data)
      } catch (e: unknown) {
        const message = e instanceof Error ? e.message : 'Unknown error'
        if (!cancelled) setError(message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [healthUrl])

  return (
    <>
      <div>
        <a href="https://vite.dev" target="_blank">
          <img src={viteLogo} className="logo" alt="Vite logo" />
        </a>
        <a href="https://react.dev" target="_blank">
          <img src={reactLogo} className="logo react" alt="React logo" />
        </a>
      </div>
      <h1>Vite + React</h1>
      <div className="card">
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>
        <p>
          Edit <code>src/App.tsx</code> and save to test HMR
        </p>
      </div>
      <p className="read-the-docs">
        Click on the Vite and React logos to learn more
      </p>

      <div style={{ textAlign: 'left', marginTop: 24 }}>
        <h2>Backend health check</h2>
        <div>
          <div><strong>Endpoint:</strong> <code>{healthUrl}</code></div>
          {loading && <div>Loading health...</div>}
          {error && <div style={{ color: 'red' }}>Error: {error}</div>}
          {!loading && !error && (
            <pre style={{
              background: '#111',
              color: '#0f0',
              padding: 12,
              borderRadius: 8,
              overflowX: 'auto',
              maxHeight: 300,
            }}>
              {typeof health === 'string' ? health : JSON.stringify(health, null, 2)}
            </pre>
          )}
        </div>
      </div>
    </>
  )
}

export default App
