import { Connector } from './connector.js'
import { Channel, PresenceChannel } from './channel.js'

/**
 * Laravel Echo-shaped client for Kona Reverb / Pusher-compatible servers.
 */
export class Echo {
    constructor(options = {}) {
        this.options = options
        this.connector = new Connector(options)
        this.channels = new Map()
        this.connector.connect()
    }

    socketId() {
        return this.connector.socketId
    }

    channel(name) {
        return this.#subscribe(name, Channel, false)
    }

    private(name) {
        return this.#subscribe(`private-${name}`, Channel, true)
    }

    join(name) {
        return this.#subscribe(`presence-${name}`, PresenceChannel, true)
    }

    leave(name) {
        for (const key of [`${name}`, `private-${name}`, `presence-${name}`]) {
            this.channels.get(key)?.unsubscribe()
        }
    }

    leaveChannel(name) {
        this.channels.get(name)?.unsubscribe()
    }

    disconnect() {
        for (const channel of [...this.channels.values()]) {
            channel.unsubscribe()
        }
        this.connector.disconnect()
    }

    forget(name) {
        this.channels.delete(name)
    }

    #subscribe(name, ChannelType, privateChannel) {
        if (this.channels.has(name)) {
            return this.channels.get(name)
        }
        const channel = new ChannelType(this, name)
        this.channels.set(name, channel)

        const subscribe = async () => {
            const payload = { channel: name }
            if (privateChannel) {
                const socketId = await this.#waitForSocketId()
                const auth = await this.connector.authorize(name, socketId)
                payload.auth = auth.auth
                if (auth.channel_data) payload.channel_data = auth.channel_data
            }
            this.connector.send({ event: 'pusher:subscribe', data: payload })
        }

        if (this.connector.connected) {
            subscribe().catch(() => {})
        } else {
            this.connector.on('connected', () => {
                subscribe().catch(() => {})
            })
        }
        return channel
    }

    #waitForSocketId() {
        if (this.connector.socketId) {
            return Promise.resolve(this.connector.socketId)
        }
        return new Promise((resolve) => {
            const stop = this.connector.on('connected', (data) => {
                stop()
                resolve(data.socket_id)
            })
        })
    }
}
