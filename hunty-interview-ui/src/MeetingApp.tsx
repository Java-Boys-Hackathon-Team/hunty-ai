// =============================================
// FILE: src/MeetingApp.tsx
// =============================================

import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react'
import './custom.css'
import {AlertTriangle, Loader2, Mic, MicOff, Play, RefreshCw, Square, Video, VideoOff,} from 'lucide-react'

// ----------------------
// Types & helpers
// ----------------------

type MeetingStatus = 'not_started' | 'running' | 'ended'
type CamPermission = 'prompt' | 'granted' | 'denied' | 'unsupported'

type MeetingInfo = {
    code: string
    candidateName: string
    greeting: string
    status: MeetingStatus
    token?: string
    interviewId?: string
    endAt?: string
}

type SystemEvent = { type: 'ready' | 'ended' | 'error'; message?: string }
type STTPartial = { text: string; fromMs: number; toMs: number }
type STTFinal = { text: string; fromMs: number; toMs: number }

interface UIState {
    page: 'lobby' | 'live'
    status: MeetingStatus
}

interface TTSEnvelopeHeader {
    type: 'tts.chunk'
    codec: string
    sampleRate: number
    channels: number
}

interface AudioChunkHeader {
    type: 'audio.chunk'
    codec: 'opus/webm' | 'pcm16'
    sampleRate: number
    channels: number
}

const LS_END_AT_KEY = 'interview_end_at'
const WS_MAX_RETRIES = 3

// Backend base URL (prod/dev)
const backendBase = import.meta.env.PROD
    ? 'https://api.hunty-ai.javaboys.ru'
    : 'http://localhost:8000'

function formatRemaining(ms: number) {
    if (ms <= 0) return '00:00'
    let totalSeconds = Math.floor(ms / 1000)
    const hours = Math.floor(totalSeconds / 3600)
    totalSeconds -= hours * 3600
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    const pad = (n: number) => String(n).padStart(2, '0')
    return hours > 0
        ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
        : `${pad(minutes)}:${pad(seconds)}`
}

// Parse meeting code from URL: https://example.net/meetings/{code}
function parseMeetingCodeFromPath(): string | null {
    const parts = window.location.pathname.split('/').filter(Boolean)
    if (parts.length >= 2 && parts[0] === 'meetings') return decodeURIComponent(parts[1])
    return null
}

async function fetchJSON<T>(url: string, init?: RequestInit): Promise<T> {
    const res = await fetch(url, init)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
}

// ----------------------
// Mic level (VU) hook (safe in StrictMode)
// ----------------------

function useMicLevel(stream: MediaStream | null, enabled: boolean) {
    const [level, setLevel] = useState(0)
    const rafRef = useRef<number | null>(null)
    const analyserRef = useRef<AnalyserNode | null>(null)
    const audioCtxRef = useRef<AudioContext | null>(null)
    const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null)

    useEffect(() => {
        let stopped = false

        const cleanup = () => {
            if (rafRef.current != null) {
                cancelAnimationFrame(rafRef.current)
                rafRef.current = null
            }
            try {
                sourceRef.current?.disconnect()
            } catch {
            }
            try {
                analyserRef.current?.disconnect()
            } catch {
            }
            sourceRef.current = null
            analyserRef.current = null
            const ctx = audioCtxRef.current
            if (ctx && ctx.state !== 'closed') {
                ctx.close().catch(() => {
                })
            }
            audioCtxRef.current = null
        }

        if (!stream || !enabled) {
            setLevel(0)
            cleanup()
            return cleanup
        }

        // reset previous chain defensively
        cleanup()

        const Ctor = (window as any).AudioContext || (window as any).webkitAudioContext
        const ctx: AudioContext = new Ctor()
        const source = ctx.createMediaStreamSource(stream)
        const analyser = ctx.createAnalyser()
        analyser.fftSize = 512
        source.connect(analyser)

        audioCtxRef.current = ctx
        sourceRef.current = source
        analyserRef.current = analyser

        const data = new Uint8Array(analyser.frequencyBinCount)

        const tick = () => {
            if (stopped) return
            analyser.getByteTimeDomainData(data)
            let sum = 0
            for (let i = 0; i < data.length; i++) {
                const v = (data[i] - 128) / 128
                sum += v * v
            }
            const rms = Math.sqrt(sum / data.length)
            setLevel(prev => prev * 0.8 + rms * 0.2)
            rafRef.current = requestAnimationFrame(tick)
        }
        rafRef.current = requestAnimationFrame(tick)

        return () => {
            stopped = true;
            cleanup()
        }
    }, [stream, enabled])

    return level
}

// ----------------------
// Helpers: test tone PCM16
// ----------------------

function generateTonePCM16(
    durationMs: number,
    sampleRate = 24000,
    freqHz = 440,
    channels = 1,
    volume = 0.2
): ArrayBuffer {
    const frames = Math.floor((durationMs / 1000) * sampleRate)
    const pcm = new Int16Array(frames * channels)
    const amp = Math.floor(32767 * Math.max(0, Math.min(1, volume)))
    for (let i = 0; i < frames; i++) {
        const s = Math.floor(amp * Math.sin((2 * Math.PI * freqHz * i) / sampleRate))
        for (let ch = 0; ch < channels; ch++) {
            pcm[i * channels + ch] = s
        }
    }
    return pcm.buffer
}

// ----------------------
// PCM16 Player for TTS chunks
// ----------------------

class PcmPlayer {
    private ctx: AudioContext
    private gain: GainNode
    private queue: Float32Array[] = []
    private isPlaying = false

    constructor() {
        const Ctor: typeof AudioContext = (window as any).AudioContext || (window as any).webkitAudioContext
        this.ctx = new Ctor()
        this.gain = this.ctx.createGain()
        // Чуть поднимем уровень, чтобы мок был слышнее
        this.gain.gain.value = 1.25
        this.gain.connect(this.ctx.destination)
    }

    async resume() {
        if (this.ctx.state === 'suspended') {
            try {
                await this.ctx.resume()
            } catch {
            }
        }
    }

    enqueuePCM16(buffer: ArrayBuffer, sampleRate: number, channels: number) {
        const view = new DataView(buffer)
        const samples = buffer.byteLength / 2
        const floatBuf = new Float32Array(samples)
        for (let i = 0; i < samples; i++) {
            const s = view.getInt16(i * 2, true)
            floatBuf[i] = Math.max(-1, Math.min(1, s / 32768))
        }
        this.queue.push(floatBuf)
        if (!this.isPlaying) this.playFromQueue(sampleRate, channels)
    }

    private async playFromQueue(sampleRate: number, channels: number) {
        this.isPlaying = true
        // ВАЖНО: гарантированно «будим» контекст перед стартом
        await this.resume()

        while (this.queue.length) {
            const chunk = this.queue.shift()!
            // Длина буфера — это число кадров (а не байт)
            const frames = chunk.length
            const audioBuf = this.ctx.createBuffer(channels, frames, sampleRate)

            // Моно-чанк копируем во все каналы (если вдруг ch>1)
            for (let ch = 0; ch < channels; ch++) {
                audioBuf.getChannelData(ch).set(chunk)
            }

            const src = this.ctx.createBufferSource()
            src.buffer = audioBuf
            src.connect(this.gain)

            await new Promise<void>((resolve) => {
                src.onended = () => resolve()
                // ВАЖНО: стартуем чуть в будущем, чтобы избежать «нулевого» старта
                const t0 = this.ctx.currentTime + 0.01
                try {
                    src.start(t0)
                } catch {
                    // в редких случаях, если уже «прошли», пробуем немедленно
                    try {
                        src.start()
                    } catch {
                    }
                }
            })
        }
        this.isPlaying = false
    }

    speaking() {
        return this.isPlaying
    }

    close() {
        if (this.ctx.state !== 'closed') {
            this.ctx.close().catch(() => {
            })
        }
    }
}

function tryParseHeader(buf: ArrayBuffer, headerLen: number): unknown | null {
    if (buf.byteLength < headerLen) return null
    const headerBytes = buf.slice(0, headerLen)
    const dec = new TextDecoder('utf-8')
    const jsonText = dec.decode(headerBytes).replace(/\u0000+$/g, '').trim()
    if (!jsonText) return null
    try {
        return JSON.parse(jsonText)
    } catch {
        return null
    }
}

function parseBinaryEnvelopeSmart(buf: ArrayBuffer): { header: unknown; payload: ArrayBuffer; headerLen: number } {
    // 1) Пытаемся 256-байтный заголовок
    let header = tryParseHeader(buf, 256)
    if (header && typeof header === 'object') {
        return {header, payload: buf.slice(256), headerLen: 256}
    }
    // 2) Пытаемся 16-байтный заголовок (вдруг сервер шлёт короткий пролог)
    header = tryParseHeader(buf, 16)
    if (header && typeof header === 'object') {
        return {header, payload: buf.slice(16), headerLen: 16}
    }
    // 3) Без заголовка — считаем всё payload (PCM16 24k mono как фолбэк)
    return {header: null, payload: buf, headerLen: 0}
}

// ----------------------
// WebSocket hook
// ----------------------

function useVoiceWS(opts: {
    url: string
    token?: string
    onSystem: (e: SystemEvent) => void
    onPartial: (e: STTPartial) => void
    onFinal: (e: STTFinal) => void
    onTTSChunk: (payload: ArrayBuffer, header: TTSEnvelopeHeader) => void
    enabled: boolean
}) {
    const {url, token, onSystem, onPartial, onFinal, onTTSChunk, enabled} = opts
    const wsRef = useRef<WebSocket | null>(null)
    const [connected, setConnected] = useState(false)
    const [retries, setRetries] = useState(0)

    useEffect(() => {
        if (!enabled) {
            if (wsRef.current) wsRef.current.close()
            setConnected(false)
            return
        }

        let closedByUs = false
        let retryTimer: number | null = null

        const fullUrl = token ? `${url}&token=${encodeURIComponent(token)}` : url
        const ws = new WebSocket(fullUrl)
        ws.binaryType = 'arraybuffer'
        wsRef.current = ws

        ws.onopen = () => {
            console.log('[WS] open', fullUrl)
            setConnected(true)
            setRetries(0)
        }
        ws.onclose = (ev) => {
            console.log('[WS] close', ev.code, ev.reason)
            setConnected(false)
            if (!closedByUs && retries < WS_MAX_RETRIES) {
                retryTimer = window.setTimeout(() => setRetries(r => r + 1), 500 * (retries + 1))
            }
        }
        ws.onerror = (ev) => {
            console.log('[WS] error', ev)
        }
        ws.onmessage = async (ev) => {
            if (typeof ev.data === 'string') {
                console.log('[WS] text', ev.data)
                try {
                    const m = JSON.parse(ev.data)

                    if (m.type === 'system') {
                        // Сузим произвольное m.event до допустимого union
                        const raw = m.event as unknown
                        const evt: 'ready' | 'ended' | 'error' =
                            raw === 'ready' ? 'ready' :
                                raw === 'ended' ? 'ended' :
                                    raw === 'error' ? 'error' : 'error'

                        onSystem({type: evt, message: m.message})
                    } else if (m.type === 'stt.partial') {
                        onPartial(m as STTPartial)
                    } else if (m.type === 'stt.final') {
                        onFinal(m as STTFinal)
                    }
                } catch {
                }
                return
            }

            // --- бинарные/Blob сообщения ---
            let ab: ArrayBuffer | null = null
            if (ev.data instanceof ArrayBuffer) {
                ab = ev.data as ArrayBuffer
            } else if (ev.data instanceof Blob) {
                ab = await (ev.data as Blob).arrayBuffer()
            } else {
                // неожиданный тип
                return
            }
            if (!ab) return

            const {header, payload, headerLen} = parseBinaryEnvelopeSmart(ab)

            // DEBUG: выведем первые 8 сэмплов
            const dv16 = new DataView(payload)
            const first: number[] = []
            for (let off = 0; off < Math.min(16, payload.byteLength); off += 2) {
                first.push(dv16.getInt16(off, true))
            }
            console.log('[TTS] first16', first, 'len=', payload.byteLength)

            const h = (header || {}) as Partial<TTSEnvelopeHeader & {
                type?: string; codec?: string; sampleRate?: number; channels?: number
            }>
            const type = h.type || 'unknown'
            const codec = h.codec || 'unknown'
            const sr = h.sampleRate ?? 24000
            const ch = h.channels ?? 1

            // простая диагностика RMS
            const dv = new DataView(payload)
            let sum = 0
            for (let i = 0; i + 1 < payload.byteLength; i += 2) {
                const s = dv.getInt16(i, true) / 32768
                sum += s * s
            }
            const rms = Math.sqrt(sum / Math.max(1, payload.byteLength / 2))
            console.log('[TTS] incoming rms=', +rms.toFixed(4), 'sr=', sr, 'ch=', ch)

            console.log('[WS] bin', {
                headerLen,
                type,
                codec,
                sampleRate: sr,
                channels: ch,
                bytes: payload.byteLength,
                rms: +rms.toFixed(4)
            })

            if (type === 'tts.chunk' && codec.toLowerCase().includes('pcm16')) {
                onTTSChunk(payload, {type: 'tts.chunk', codec: codec as string, sampleRate: sr, channels: ch})
                return
            }

            if (type === 'unknown' || headerLen === 0) {
                console.warn('[WS] no/unknown header — fallback: assume PCM16 24k mono')
                onTTSChunk(payload, {type: 'tts.chunk', codec: 'pcm16', sampleRate: 24000, channels: 1})
                return
            }

            if (type === 'tts.chunk' && !codec.toLowerCase().includes('pcm16')) {
                console.warn(`[WS] unsupported codec for tts.chunk: ${codec} — please send PCM16. Fallback: beep.`)
                const testBuf = generateTonePCM16(200, 24000, 660, 1, 0.25)
                onTTSChunk(testBuf, {type: 'tts.chunk', codec: 'pcm16', sampleRate: 24000, channels: 1})
            }
        }

        return () => {
            closedByUs = true
            if (retryTimer != null) clearTimeout(retryTimer)
            ws.close()
        }
    }, [url, token, enabled, retries])

    const sendJSON = useCallback((obj: Record<string, unknown>) => {
        const ws = wsRef.current
        if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj))
    }, [])

    const sendAudioChunk = useCallback((payload: ArrayBuffer, header: AudioChunkHeader) => {
        const ws = wsRef.current
        if (!ws || ws.readyState !== WebSocket.OPEN) return
        const enc = new TextEncoder()
        const head = enc.encode(JSON.stringify(header))
        const padded = new Uint8Array(256)
        padded.set(head.slice(0, 256))
        const out = new Uint8Array(256 + payload.byteLength)
        out.set(padded, 0)
        out.set(new Uint8Array(payload), 256)
        ws.send(out)
    }, [])

    return {connected, sendJSON, sendAudioChunk}
}

// =============================================
// Main component
// =============================================

const MeetingApp: React.FC = () => {
    const code = useMemo(parseMeetingCodeFromPath, [])
    const [meeting, setMeeting] = useState<MeetingInfo | null>(null)
    const [ui, setUI] = useState<UIState>({page: 'lobby', status: 'not_started'})
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    // --- HealthCheck URL & logging ---
    const healthUrl = `${backendBase}/health`
    useEffect(() => {
        let cancelled = false
        ;(async () => {
            try {
                const res = await fetch(healthUrl, {headers: {Accept: 'application/json, text/plain;q=0.9, */*;q=0.1'}})
                const text = await res.text()
                let data: unknown
                try {
                    data = JSON.parse(text)
                } catch {
                    data = text
                }
                if (!cancelled) {
                    const rendered = typeof data === 'string' ? data : JSON.stringify(data)
                    console.log(`HealthCheck: ${rendered} (Endpoint: ${healthUrl}).`)
                }
            } catch (e) {
                const message = e instanceof Error ? e.message : 'Unknown error'
                if (!cancelled) console.log(`HealthCheck: ERROR ${message} (Endpoint: ${healthUrl}).`)
            }
        })()
        return () => {
            cancelled = true
        }
    }, [healthUrl])

    // Separate controls
    const [micEnabled, setMicEnabled] = useState(true)
    const [camEnabled, setCamEnabled] = useState(false)

    // Separate streams
    const [localStream, setLocalStream] = useState<MediaStream | null>(null)   // audio only
    const [cameraStream, setCameraStream] = useState<MediaStream | null>(null) // video only
    const level = useMicLevel(localStream, micEnabled)

    const [endAt, setEndAt] = useState<number | null>(null)
    const [now, setNow] = useState(Date.now())
    const [subtitles, setSubtitles] = useState<{ partial?: string; finals: string[] }>({finals: []})
    const [showEndConfirm, setShowEndConfirm] = useState(false)

    // Camera permission state
    const [camPerm, setCamPerm] = useState<CamPermission>('prompt')
    const [camRequesting, setCamRequesting] = useState(false)

    // TTS playback
    const ttsPlayerRef = useRef<PcmPlayer | null>(null)
    const [avatarSpeaking, setAvatarSpeaking] = useState(false)

    // MediaRecorder (mic)
    const recorderRef = useRef<MediaRecorder | null>(null)

    // ---- Load meeting ----
    useEffect(() => {
        (async () => {
            try {
                if (!code) throw new Error('Meeting code not found in URL')
                const info = await fetchJSON<MeetingInfo>(`${backendBase}/meetings/${encodeURIComponent(code)}`)
                setMeeting(info)

                let status: MeetingStatus = info.status
                const ls = localStorage.getItem(LS_END_AT_KEY)
                if (ls) {
                    const t = Number(ls)
                    if (!isNaN(t) && t > Date.now()) {
                        status = 'running'
                        setEndAt(t)
                    } else {
                        localStorage.removeItem(LS_END_AT_KEY)
                    }
                } else if (info.endAt) {
                    const t = new Date(info.endAt).getTime()
                    if (t > Date.now()) {
                        status = 'running';
                        setEndAt(t)
                    }
                }
                setUI({page: status === 'running' ? 'live' : 'lobby', status})
            } catch (e: unknown) {
                const msg = e instanceof Error ? e.message : 'Failed to load meeting'
                setError(msg)
            } finally {
                setLoading(false)
            }
        })()
    }, [code])

    // ---- Global tick ----
    useEffect(() => {
        const t = setInterval(() => {
            setNow(Date.now())
            setAvatarSpeaking(ttsPlayerRef.current?.speaking() || false)
        }, 250)
        return () => clearInterval(t)
    }, [])

    // ---- Watch camera permission (where supported) ----
    useEffect(() => {
        let unmounted = false

        async function watchPermission() {
            try {
                // @ts-ignore Safari may not support this
                const status: PermissionStatus | undefined = await navigator.permissions?.query({name: 'camera' as any})
                if (!status) {
                    if (!unmounted) setCamPerm('unsupported');
                    return
                }
                const apply = () => {
                    if (unmounted) return
                    if (status.state === 'granted') setCamPerm('granted')
                    else if (status.state === 'denied') setCamPerm('denied')
                    else setCamPerm('prompt')
                }
                apply()
                status.onchange = apply
            } catch {
                if (!unmounted) setCamPerm('unsupported')
            }
        }

        watchPermission()
        return () => {
            unmounted = true
        }
    }, [])

    // ---- AUDIO mic stream (audio only), independent of cam ----
    useEffect(() => {
        if (!micEnabled) {
            localStream?.getAudioTracks().forEach(t => t.stop())
            setLocalStream(null)
            return
        }
        if (localStream) return

        navigator.mediaDevices.getUserMedia({
            audio: {echoCancellation: true, noiseSuppression: true, autoGainControl: true},
            video: false,
        }).then((s) => {
            setLocalStream(s)
        }).catch(() => {
            setError('Доступ к микрофону отклонён. Разрешите доступ и попробуйте снова.')
            setMicEnabled(false)
        })
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [micEnabled])

    // ---- CAMERA stream (video only), used in Lobby & Live ----
    useEffect(() => {
        let cancelled = false

        async function openCamera() {
            setCamRequesting(true)
            try {
                const s = await navigator.mediaDevices.getUserMedia({video: true, audio: false})
                if (cancelled) {
                    s.getVideoTracks().forEach(t => t.stop());
                    return
                }
                setCamPerm('granted')
                setCameraStream(prev => {
                    prev?.getVideoTracks().forEach(t => t.stop());
                    return s
                })
            } catch {
                setCamPerm('denied')
                setCameraStream(prev => {
                    prev?.getVideoTracks().forEach(t => t.stop());
                    return null
                })
            } finally {
                if (!cancelled) setCamRequesting(false)
            }
        }

        if (camEnabled) {
            openCamera()
        } else {
            setCameraStream(prev => {
                prev?.getVideoTracks().forEach(t => t.stop());
                return null
            })
        }

        return () => {
            cancelled = true
        }
    }, [camEnabled])

    // ---- Start meeting ----
    const startMeeting = useCallback(async () => {
        if (!meeting) return
        try {
            setLoading(true)
            const res = await fetchJSON<{ startAt: string; endAt: string; token?: string; interviewId: string }>(
                `${backendBase}/meetings/${encodeURIComponent(meeting.code)}/start`,
                {method: 'POST'}
            )
            const end = new Date(res.endAt).getTime()
            setEndAt(end)
            localStorage.setItem(LS_END_AT_KEY, String(end))
            setMeeting((m) => (m ? {...m, token: res.token, interviewId: res.interviewId} : m))
            setUI({page: 'live', status: 'running'})

            // Разрешаем воспроизведение аудио (после пользовательского клика)
            if (!ttsPlayerRef.current) ttsPlayerRef.current = new PcmPlayer()
            await ttsPlayerRef.current.resume()
        } catch {
            setError('Не удалось начать встречу. Попробуйте ещё раз.')
        } finally {
            setLoading(false)
        }
    }, [meeting])

    // ---- End meeting ----
    const endMeeting = useCallback(async () => {
        if (!meeting) return
        try {
            await fetchJSON(`${backendBase}/meetings/${encodeURIComponent(meeting.code)}/end`, {method: 'POST'})
        } catch {
        }
        localStorage.removeItem(LS_END_AT_KEY)
        setUI({page: 'lobby', status: 'ended'})
    }, [meeting])

    // ---- WS URL as a stable hook (FIX: no hooks inside args) ----
    const wsUrl = useMemo(() => {
        const id = meeting?.interviewId || ''
        const wsBase = backendBase.replace(/^http/, 'ws')
        return `${wsBase}/voice?interviewId=${encodeURIComponent(id)}`
    }, [meeting?.interviewId])

    // ---- WebSocket ----
    const startedRef = useRef(false)
    const {connected, sendJSON, sendAudioChunk} = useVoiceWS({
        url: wsUrl,
        token: meeting?.token,
        enabled: ui.page === 'live' && ui.status === 'running' && Boolean(meeting?.interviewId),
        onSystem: (e) => {
            if (e.type === 'ready' && !startedRef.current) {
                startedRef.current = true
                sendJSON({type: 'control', action: 'start'})
            } else if (e.type === 'ended') {
                localStorage.removeItem(LS_END_AT_KEY)
                setUI({page: 'lobby', status: 'ended'})
            } else if (e.type === 'error') {
                setError(e.message || 'Ошибка соединения')
            }
        },
        onPartial: (e) => setSubtitles((s) => ({...s, partial: e.text})),
        onFinal: (e) => setSubtitles((s) => ({partial: undefined, finals: [...s.finals, e.text]})),
        onTTSChunk: async (payload, header) => {
            const sr = header.sampleRate ?? 24000
            const ch = header.channels ?? 1
            if (!ttsPlayerRef.current) ttsPlayerRef.current = new PcmPlayer()
            await ttsPlayerRef.current.resume()
            ttsPlayerRef.current.enqueuePCM16(payload, sr, ch)
        },
    })

    // ---- Stream mic via MediaRecorder (audio only) ----
    const beginStreaming = useCallback(() => {
        if (!localStream) return
        try {
            const rec = new MediaRecorder(localStream, {
                mimeType: 'audio/webm;codecs=opus',
                audioBitsPerSecond: 32000,
            })
            recorderRef.current = rec
            rec.ondataavailable = async (ev) => {
                if (!ev.data || ev.data.size === 0) return
                const arrayBuf = await ev.data.arrayBuffer()
                const header: AudioChunkHeader = {
                    type: 'audio.chunk',
                    codec: 'opus/webm',
                    sampleRate: 48000,
                    channels: 1,
                }
                sendAudioChunk(arrayBuf, header)
            }
            rec.start(250)
        } catch {
            setError('Не удалось запустить запись микрофона')
        }
    }, [localStream, sendAudioChunk])

    const stopStreaming = useCallback(() => {
        recorderRef.current?.stop()
        recorderRef.current = null
    }, [])

    useEffect(() => {
        if (ui.page !== 'live') return
        if (micEnabled) beginStreaming()
        else stopStreaming()
        return () => stopStreaming()
    }, [ui.page, micEnabled]) // eslint-disable-line react-hooks/exhaustive-deps

    const remainingMs = useMemo(() => (endAt ? endAt - now : 0), [endAt, now])
    const split = ui.page === 'live' && camEnabled // split only in Live

    // Тестовый звук (кнопка в лобби)
    const handleTestSound = useCallback(async () => {
        if (!ttsPlayerRef.current) ttsPlayerRef.current = new PcmPlayer()
        await ttsPlayerRef.current.resume()
        const sr = 24000
        const buf = generateTonePCM16(300, sr, 440, 1, 0.25) // 300мс бип
        // Только для отладки
        console.log('[Audio]', (ttsPlayerRef.current as any)['ctx']?.state)
        ttsPlayerRef.current.enqueuePCM16(buf, sr, 1)
    }, [])

    // -------- UI --------
    if (loading) {
        return (
            <div className="page loading">
                <div className="loader"><Loader2 className="spin"/> Загрузка…</div>
            </div>
        )
    }
    if (error) {
        return (
            <div className="page error">
                <div className="error-card">
                    <AlertTriangle/>
                    <div>
                        <h2>Что-то пошло не так</h2>
                        <p>{error}</p>
                        <button className="btn" onClick={() => window.location.reload()}>Перезагрузить</button>
                    </div>
                </div>
            </div>
        )
    }
    if (!meeting) return null

    return (
        <div className="page">
            {/* Top bar */}
            <header className="topbar">
                <div className="topbar-inner">
                    {ui.status === 'running' && endAt ? (
                        <div>
                            Встреча с кандидатом <strong>{meeting.candidateName}</strong>, оставшееся
                            время: <strong>{formatRemaining(remainingMs)}</strong>
                        </div>
                    ) : (
                        <div>Встреча с кандидатом <strong>{meeting.candidateName}</strong></div>
                    )}
                    <div className={`connection ${connected ? 'ok' : 'bad'}`}/>
                </div>
            </header>

            {/* Stage */}
            <main className={`stage ${split ? 'split' : ''}`}>
                {ui.page === 'lobby' && (
                    <Lobby
                        greeting={meeting.greeting}
                        micEnabled={micEnabled}
                        onMicToggle={() => setMicEnabled((m) => !m)}
                        camEnabled={camEnabled}
                        onCamToggle={() => setCamEnabled((c) => !c)}
                        level={level}
                        candidateName={meeting.candidateName}
                        status={ui.status}
                        cameraStream={cameraStream}
                        camPerm={camPerm}
                        camRequesting={camRequesting}
                        onCamRetry={() => {
                            setCamEnabled(false);
                            setTimeout(() => setCamEnabled(true), 0)
                        }}
                        onTestSound={handleTestSound}
                    />
                )}

                {ui.page === 'live' && (
                    <LiveView
                        micEnabled={micEnabled}
                        onMicToggle={() => setMicEnabled((m) => !m)}
                        camEnabled={camEnabled}
                        level={level}
                        subtitles={subtitles}
                        speaking={avatarSpeaking}
                        cameraStream={cameraStream}
                    />
                )}
            </main>

            {/* Bottom bar */}
            <footer className="bottombar">
                <div className="bottombar-inner">
                    <ToggleButton
                        active={micEnabled}
                        onClick={() => setMicEnabled((m) => !m)}
                        label={micEnabled ? 'Микрофон включен' : 'Микрофон выключен'}
                        activeClass="mic-active"
                        iconOn={<Mic/>}
                        iconOff={<MicOff/>}
                    />
                    <ToggleButton
                        active={camEnabled}
                        onClick={() => setCamEnabled((c) => !c)}
                        label={camEnabled ? 'Камера включена' : 'Камера выключена'}
                        iconOn={<Video/>}
                        iconOff={<VideoOff/>}
                    />
                    {ui.status !== 'running' ? (
                        <button className="btn primary" onClick={startMeeting}><Play/> Начать</button>
                    ) : (
                        <button className="btn danger" onClick={() => setShowEndConfirm(true)}><Square/> Завершить
                        </button>
                    )}
                </div>
            </footer>

            <ConfirmEndModal
                open={showEndConfirm}
                onCancel={() => setShowEndConfirm(false)}
                onConfirm={() => {
                    setShowEndConfirm(false);
                    endMeeting()
                }}
            />
        </div>
    )
}

// ---------------- Subcomponents ----------------

const ToggleButton: React.FC<{
    active: boolean
    onClick: () => void
    label: string
    activeClass?: string
    iconOn: React.ReactNode
    iconOff: React.ReactNode
}> = ({active, onClick, label, activeClass, iconOn, iconOff}) => (
    <button className={`btn toggle ${active ? activeClass || '' : 'off'}`} onClick={onClick} aria-pressed={active}>
        <span className="icon">{active ? iconOn : iconOff}</span>
        <span>{label}</span>
        {active && <span className="lava" aria-hidden/>}
    </button>
)

const Lobby: React.FC<{
    greeting: string
    micEnabled: boolean
    onMicToggle: () => void
    camEnabled: boolean
    onCamToggle: () => void
    level: number
    candidateName: string
    status: MeetingStatus
    cameraStream: MediaStream | null
    camPerm: CamPermission
    camRequesting: boolean
    onCamRetry: () => void
    onTestSound: () => void
}> = ({
          greeting, micEnabled, onMicToggle, camEnabled, onCamToggle, level,
          candidateName, status, cameraStream, camPerm, camRequesting, onCamRetry, onTestSound
      }) => {
    const videoRef = useRef<HTMLVideoElement | null>(null)
    useEffect(() => {
        if (!videoRef.current) return
        if (cameraStream) {
            videoRef.current.srcObject = cameraStream
            videoRef.current.play().catch(() => {
            })
        } else {
            ;(videoRef.current as HTMLVideoElement).srcObject = null
        }
    }, [cameraStream])

    const camContent = (() => {
        if (!camEnabled) return <span>Камера выключена</span>
        if (camRequesting) return <span>Запрашиваем доступ к камере… Подтвердите в браузере.</span>
        if (camPerm === 'prompt') return <span>Ожидаем подтверждения… Проверьте окно разрешения.</span>
        if (camPerm === 'denied') {
            return (
                <div style={{display: 'grid', gap: 8, placeItems: 'center', padding: 12}}>
                    <span>Доступ к камере заблокирован. Разрешите доступ в настройках сайта и нажмите «Повторить».</span>
                    <button className="btn" onClick={onCamRetry}>Повторить</button>
                </div>
            )
        }
        if (camPerm === 'granted' && cameraStream) {
            return <video ref={videoRef} className="camera-video" muted playsInline/>
        }
        return <span>Камера недоступна (проверьте устройство).</span>
    })()

    return (
        <section className="lobby">
            <div className="lobby-content">
                <div className="welcome">
                    <h1>Привет, {candidateName}!</h1>
                    {status === 'ended'
                        ? <p>Ваша предыдущая сессия завершена. Вы можете начать новую при готовности.</p>
                        : <p className="muted" dangerouslySetInnerHTML={{__html: greeting.replace(/\n/g, '<br/>')}}/>
                    }
                </div>

                <div className="device-check">
                    <h2>Проверка устройств</h2>
                    <div className="device-row">
                        {/* МИКРОФОН */}
                        <div className="device">
                            <div className="device-head"><Mic/> Микрофон</div>
                            <div className="vu">
                                <div className="bar"
                                     style={{transform: `scaleX(${Math.min(1, level * 4 + (micEnabled ? 0.1 : 0))})`}}/>
                            </div>
                            <div className="device-actions" style={{display: 'flex', gap: 8, flexWrap: 'wrap'}}>
                                <button className={`btn toggle ${micEnabled ? 'on' : 'off'}`} onClick={onMicToggle}>
                                    {micEnabled ? <Mic/> : <MicOff/>} {micEnabled ? 'Включен' : 'Выключен'}
                                </button>
                                <button className="btn" onClick={onTestSound}>Тест звука</button>
                            </div>
                        </div>

                        {/* КАМЕРА */}
                        <div className="device">
                            <div className="device-head"><Video/> Камера</div>
                            <div className="camera-preview placeholder"
                                 style={{padding: 0, overflow: 'hidden', minHeight: 120}}>
                                {camContent}
                            </div>
                            <div className="device-actions" style={{display: 'flex', gap: 8}}>
                                <button className={`btn toggle ${camEnabled ? 'on' : 'off'}`} onClick={onCamToggle}>
                                    {camEnabled ? <Video/> : <VideoOff/>} {camEnabled ? 'Включена' : 'Выключена'}
                                </button>
                                {camEnabled && (camPerm === 'denied' || camPerm === 'prompt') && !camRequesting && (
                                    <button className="btn" onClick={onCamRetry}>Повторить</button>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    )
}

const LiveView: React.FC<{
    micEnabled: boolean
    onMicToggle: () => void
    camEnabled: boolean
    level: number
    speaking: boolean
    subtitles: { partial?: string; finals: string[] }
    cameraStream: MediaStream | null
}> = ({micEnabled, onMicToggle, camEnabled, level, speaking, subtitles, cameraStream}) => {
    const videoRef = useRef<HTMLVideoElement | null>(null)
    useEffect(() => {
        if (!videoRef.current) return
        if (cameraStream) {
            videoRef.current.srcObject = cameraStream
            videoRef.current.play().catch(() => {
            })
        } else {
            ;(videoRef.current as HTMLVideoElement).srcObject = null
        }
    }, [cameraStream])

    return (
        <section className="live">
            <div className={camEnabled ? 'panel agent' : 'panel full agent'}>
                <div className="avatar">
                    <div className={`aura ${speaking ? 'active' : 'idle'}`}>
                        <div className={`pulse ${speaking ? 'on' : 'off'}`}/>
                        <div className={`wave ${speaking ? 'animate' : 'frozen'}`}>
                            <div className="shimmer" aria-hidden/>
                        </div>
                    </div>
                </div>
                <div className="subs">
                    {subtitles.finals.slice(-3).map((t, i) => (
                        <div className="line" key={i}>{t}</div>
                    ))}
                    {subtitles.partial && <div className="line partial">{subtitles.partial}</div>}
                </div>
            </div>

            {camEnabled && (
                <div className="panel camera">
                    <video ref={videoRef} className="camera-video" muted playsInline/>
                    <div className="mic-float">
                        <div className="vu small">
                            <div className="bar"
                                 style={{transform: `scaleX(${Math.min(1, level * 4 + (micEnabled ? 0.1 : 0))})`}}/>
                        </div>
                        <button className={`btn ghost ${micEnabled ? 'on' : 'off'}`} onClick={onMicToggle}>
                            {micEnabled ? <Mic/> : <MicOff/>}
                        </button>
                    </div>
                </div>
            )}
        </section>
    )
}

const ConfirmEndModal: React.FC<{ open: boolean; onCancel: () => void; onConfirm: () => void }> = ({
                                                                                                       open,
                                                                                                       onCancel,
                                                                                                       onConfirm
                                                                                                   }) => {
    if (!open) return null
    return (
        <div className="modal">
            <div className="modal-card">
                <h2>Вы уверены, что хотите завершить встречу?</h2>
                <p className="muted">Действие нельзя будет отменить.</p>
                <div className="modal-actions">
                    <button className="btn" onClick={onCancel}><RefreshCw/> Продолжить встречу</button>
                    <button className="btn danger" onClick={onConfirm}><Square/> Завершить</button>
                </div>
            </div>
        </div>
    )
}

export default MeetingApp
