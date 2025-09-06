// mock-backend.js
import express from 'express'
import cors from 'cors'
import {WebSocketServer} from 'ws'
import http from 'http'

const app = express()
app.use(cors())
app.use(express.json())

// In-memory storage
const meetings = new Map()
// seed
meetings.set('abc', {
    code: 'abc',
    candidateName: 'Иван Петров',
    greeting: 'Добро пожаловать! Проверьте микрофон и камеру.\nКогда будете готовы - нажмите «Начать».',
    status: 'not_started',
    interviewId: null,
    token: null,
    endAt: null,
})

function nowIso() {
    return new Date().toISOString()
}

function addMinutes(d, m) {
    return new Date(new Date(d).getTime() + m * 60000).toISOString()
}

// GET /meetings/:code
app.get('/meetings/:code', (req, res) => {
    const m = meetings.get(req.params.code)
    if (!m) return res.status(404).json({error: 'not_found', message: 'Meeting not found'})
    res.json(m)
})

// POST /meetings/:code/start
app.post('/meetings/:code/start', (req, res) => {
    const m = meetings.get(req.params.code)
    if (!m) return res.status(404).json({error: 'not_found', message: 'Meeting not found'})
    if (m.status === 'running') {
        return res.json({startAt: m.startAt, endAt: m.endAt, interviewId: m.interviewId, token: m.token})
    }
    const dur = Number(req.body?.durationMinutes) || 30
    const startAt = nowIso()
    const endAt = addMinutes(startAt, dur)
    const interviewId = `iv_${Math.random().toString(36).slice(2, 8)}`
    const token = `tok_${Math.random().toString(36).slice(2)}`
    Object.assign(m, {status: 'running', startAt, endAt, interviewId, token})
    res.json({startAt, endAt, interviewId, token})
})

// POST /meetings/:code/end
app.post('/meetings/:code/end', (req, res) => {
    const m = meetings.get(req.params.code)
    if (!m) return res.status(404).json({error: 'not_found', message: 'Meeting not found'})
    if (m.status !== 'running') return res.status(409).json({error: 'not_running', message: 'Meeting not running'})
    Object.assign(m, {status: 'ended', endAt: nowIso()})
    res.json({ok: true})
})

const server = http.createServer(app)

// WS: /voice?interviewId&token
const wss = new WebSocketServer({server, path: '/voice'})

// helper: send JSON
function sendJson(ws, obj) {
    try {
        ws.send(JSON.stringify(obj))
    } catch {
    }
}

// helper: send binary envelope (256B header + payload)
function sendBinary(ws, headerObj, payloadBuf) {
    const headerStr = JSON.stringify(headerObj)
    const head = new TextEncoder().encode(headerStr)
    const pad = new Uint8Array(256)
    pad.set(head.slice(0, 256))
    const out = new Uint8Array(256 + payloadBuf.byteLength)
    out.set(pad, 0)
    out.set(new Uint8Array(payloadBuf), 256)
    ws.send(out)
}

// generate PCM16 silence for N ms
function silencePcm16(ms, sr = 24000, ch = 1) {
    const samples = Math.floor(ms * sr / 1000) * ch
    return new ArrayBuffer(samples * 2) // already zeros
}

wss.on('connection', (ws, req) => {
    const url = new URL(req.url, 'http://localhost')
    const interviewId = url.searchParams.get('interviewId')
    const token = url.searchParams.get('token')

    // very light auth mock
    if (!interviewId /*|| !token*/) {
        sendJson(ws, {type: 'system', event: 'error', message: 'Bad params'})
        ws.close()
        return
    }

    sendJson(ws, {type: 'system', event: 'ready'})

    let speaking = false
    let ttsTimer = null

    // periodically send fake TTS (silence) chunks while "speaking"
    function startTTS() {
        if (speaking) return
        speaking = true
        ttsTimer = setInterval(() => {
            const header = {type: 'tts.chunk', codec: 'pcm16', sampleRate: 24000, channels: 1}
            const payload = silencePcm16(250) // 250ms тишины
            sendBinary(ws, header, payload)
        }, 260)
    }

    function stopTTS() {
        speaking = false
        if (ttsTimer) clearInterval(ttsTimer), ttsTimer = null
    }

    ws.on('message', (msg, isBinary) => {
        if (!isBinary) {
            // text JSON
            try {
                const m = JSON.parse(msg.toString('utf8'))
                if (m.type === 'control') {
                    if (m.action === 'start') {
                        startTTS()
                        // ещё отправим примерные паршиалы/финалы
                        setTimeout(() => sendJson(ws, {
                            type: 'stt.partial',
                            text: 'Здравствуйте, представьтесь пожалуйста...',
                            fromMs: 0,
                            toMs: 800
                        }), 500)
                        setTimeout(() => sendJson(ws, {
                            type: 'stt.final',
                            text: 'Здравствуйте, представьтесь пожалуйста.',
                            fromMs: 0,
                            toMs: 1200
                        }), 1500)
                    } else if (m.action === 'stop') {
                        stopTTS()
                        sendJson(ws, {type: 'system', event: 'ended', message: 'Interview ended (mock).'})
                    } else if (m.action === 'ping') {
                        sendJson(ws, {type: 'system', event: 'ready'})
                    }
                }
            } catch { /* ignore */
            }
        } else {
            // binary audio.chunk - тут можно имитировать распознавание
            // Отвечаем паршиалами/финалами по таймеру, если хочется
        }
    })

    ws.on('close', () => stopTTS())
})

const PORT = Number(process.env.MOCK_PORT || 8000)
server.listen(PORT, () => {
    console.log(`Mock backend on http://localhost:${PORT}`)
})
