import { describe, expect, it, vi, beforeEach } from 'vitest'
import { Echo } from '../src/Echo.js'
import { Connector } from '../src/connector.js'

class FakeSocket {
    static instances = []
    constructor(url) {
        this.url = url
        this.readyState = 0
        this.listeners = {}
        FakeSocket.instances.push(this)
        queueMicrotask(() => {
            this.readyState = 1
            this.listeners.open?.forEach((fn) => fn())
            this.emit(
                'message',
                JSON.stringify({
                    event: 'pusher:connection_established',
                    data: JSON.stringify({ socket_id: '1.1', activity_timeout: 120 }),
                }),
            )
        })
    }
    addEventListener(event, fn) {
        ;(this.listeners[event] ||= []).push(fn)
    }
    send(raw) {
        this.lastSent = raw
        const message = JSON.parse(raw)
        if (message.event === 'pusher:subscribe') {
            this.emit(
                'message',
                JSON.stringify({
                    event: 'pusher_internal:subscription_succeeded',
                    channel: message.data.channel,
                    data: '{}',
                }),
            )
        }
    }
    close() {
        this.readyState = 3
        this.listeners.close?.forEach((fn) => fn())
    }
    emit(type, data) {
        this.listeners[type]?.forEach((fn) => fn({ data }))
    }
}

describe('Echo', () => {
    beforeEach(() => {
        FakeSocket.instances = []
        global.fetch = vi.fn(async () => ({
            ok: true,
            json: async () => ({ auth: 'key:sig', channel_data: '{"user_id":"1"}' }),
        }))
    })

    it('connects and listens on public channels', async () => {
        const echo = new Echo({ key: 'app', wsHost: 'localhost', wsPort: 8080, WebSocket: FakeSocket })
        await vi.waitFor(() => expect(echo.socketId()).toBe('1.1'))
        const hits = []
        echo.channel('orders').listen('OrderShipped', (payload) => hits.push(payload))
        FakeSocket.instances[0].emit(
            'message',
            JSON.stringify({
                event: 'OrderShipped',
                channel: 'orders',
                data: JSON.stringify({ id: 7 }),
            }),
        )
        expect(hits).toEqual([{ id: 7 }])
        echo.leave('orders')
        echo.disconnect()
    })

    it('authorizes private and presence channels', async () => {
        const echo = new Echo({ key: 'app', WebSocket: FakeSocket, authEndpoint: '/broadcasting/auth' })
        await vi.waitFor(() => expect(echo.socketId()).toBe('1.1'))
        echo.private('user.1')
        echo.join('room')
        await vi.waitFor(() => expect(global.fetch).toHaveBeenCalled())
        expect(FakeSocket.instances[0].lastSent).toContain('pusher:subscribe')
        echo.leaveChannel('private-user.1')
    })

    it('connector queues messages until connected', () => {
        const connector = new Connector({ key: 'k', WebSocket: FakeSocket })
        connector.connect()
        connector.send({ event: 'pusher:subscribe', data: { channel: 'x' } })
        expect(connector.queue.length).toBe(1)
    })
})
