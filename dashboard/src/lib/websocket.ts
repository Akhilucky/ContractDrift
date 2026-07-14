type MessageHandler = (data: unknown) => void

class SentinelWebSocket {
  private ws: WebSocket | null = null
  private handlers = new Map<string, Set<MessageHandler>>()
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private reconnectAttempts = 0
  private maxReconnectAttempts = 20
  private url: string
  private _connected = false

  constructor() {
    this.url = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`
  }

  get connected(): boolean {
    return this._connected
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }

    try {
      this.ws = new WebSocket(this.url)

      this.ws.onopen = () => {
        this._connected = true
        this.reconnectAttempts = 0
        this.handlers.get('connected')?.forEach((h) => h(null))
      }

      this.ws.onmessage = (event) => {
        this.handleMessage(event)
      }

      this.ws.onclose = () => {
        this._connected = false
        this.handlers.get('disconnected')?.forEach((h) => h(null))
        this.scheduleReconnect()
      }

      this.ws.onerror = () => {
        this._connected = false
        this.ws?.close()
      }
    } catch {
      this._connected = false
      this.scheduleReconnect()
    }
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.reconnectAttempts = this.maxReconnectAttempts
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this._connected = false
  }

  on(event: string, handler: MessageHandler) {
    if (!this.handlers.has(event)) {
      this.handlers.set(event, new Set())
    }
    this.handlers.get(event)!.add(handler)
  }

  off(event: string, handler: MessageHandler) {
    this.handlers.get(event)?.delete(handler)
  }

  private handleMessage(event: MessageEvent) {
    try {
      const msg = JSON.parse(event.data)
      const eventType = msg.event || msg.type
      const data = msg.data ?? msg
      if (eventType && this.handlers.has(eventType)) {
        this.handlers.get(eventType)!.forEach((h) => h(data))
      }
      this.handlers.get('*')?.forEach((h) => h(data))
    } catch {
      // ignore malformed messages
    }
  }

  private scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) return
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000)
    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => {
      this.connect()
    }, delay)
  }
}

export const sentinelWs = new SentinelWebSocket()
