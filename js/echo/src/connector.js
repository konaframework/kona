/**
 * Minimal Pusher-protocol websocket connector for Kona Reverb.
 */
export class Connector {
    constructor(options = {}) {
        this.options = {
            key: options.key || 'kona-key',
            wsHost: options.wsHost || options.host || '127.0.0.1',
            wsPort: options.wsPort || options.port || 8080,
            wssPort: options.wssPort || options.port || 443,
            forceTLS: options.forceTLS ?? options.scheme === 'https',
            authEndpoint: options.authEndpoint || '/broadcasting/auth',
            auth: options.auth || {},
            ...options,
        }
        this.socket = null
        this.socketId = null
        this.listeners = new Map()
        this.subscriptions = new Map()
        this.queue = []
        this.connected = false
    }

    connect() {
        if (this.socket) return this
        const scheme = this.options.forceTLS ? 'wss' : 'ws'
        const port = this.options.forceTLS ? this.options.wssPort : this.options.wsPort
        const url = `${scheme}://${this.options.wsHost}:${port}/app/${this.options.key}`
        this.socket = this.options.WebSocket
            ? new this.options.WebSocket(url)
            : new WebSocket(url)
        this.socket.addEventListener('message', (event) => this.#onMessage(event.data))
        this.socket.addEventListener('close', () => {
            this.connected = false
            this.socket = null
        })
        return this
    }

    disconnect() {
        this.socket?.close()
        this.socket = null
        this.connected = false
        this.socketId = null
    }

    on(event, handler) {
        if (!this.listeners.has(event)) this.listeners.set(event, new Set())
        this.listeners.get(event).add(handler)
        return () => this.listeners.get(event)?.delete(handler)
    }

    send(payload) {
        const raw = typeof payload === 'string' ? payload : JSON.stringify(payload)
        if (this.connected && this.socket?.readyState === 1) {
            this.socket.send(raw)
        } else {
            this.queue.push(raw)
        }
    }

    async authorize(channel, socketId) {
        const body = new URLSearchParams({
            socket_id: socketId,
            channel_name: channel,
        })
        const headers = {
            'Content-Type': 'application/x-www-form-urlencoded',
            Accept: 'application/json',
            ...(this.options.auth.headers || {}),
        }
        const response = await fetch(this.options.authEndpoint, {
            method: 'POST',
            headers,
            body,
            credentials: 'same-origin',
        })
        if (!response.ok) {
            throw new Error(`Broadcast auth failed (${response.status})`)
        }
        return response.json()
    }

    #emit(event, data) {
        for (const handler of this.listeners.get(event) || []) {
            handler(data)
        }
        for (const handler of this.listeners.get('*') || []) {
            handler({ event, data })
        }
    }

    #onMessage(raw) {
        let message
        try {
            message = JSON.parse(raw)
        } catch {
            return
        }
        const event = message.event
        let data = message.data
        if (typeof data === 'string') {
            try {
                data = JSON.parse(data)
            } catch {
                // keep string
            }
        }
        if (event === 'pusher:connection_established') {
            this.socketId = data.socket_id
            this.connected = true
            while (this.queue.length) {
                this.socket.send(this.queue.shift())
            }
            this.#emit('connected', data)
            return
        }
        if (event === 'pusher:pong') {
            this.#emit('pong', data)
            return
        }
        if (message.channel) {
            this.#emit(`${message.channel}:${event}`, data)
        }
        this.#emit(event, { channel: message.channel, data })
    }
}
