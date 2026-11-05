export class Channel {
    constructor(echo, name) {
        this.echo = echo
        this.name = name
        this.handlers = new Map()
    }

    listen(event, callback) {
        const name = event.startsWith('.') ? event.slice(1) : event
        const key = `${this.name}:${name}`
        const stop = this.echo.connector.on(key, callback)
        if (!this.handlers.has(event)) this.handlers.set(event, [])
        this.handlers.get(event).push({ callback, stop })
        return this
    }

    listenForWhisper(event, callback) {
        return this.listen(`client-${event}`, callback)
    }

    stopListening(event, callback) {
        const bucket = this.handlers.get(event) || []
        for (const entry of bucket.filter((e) => !callback || e.callback === callback)) {
            entry.stop()
        }
        this.handlers.set(
            event,
            bucket.filter((e) => callback && e.callback !== callback),
        )
        return this
    }

    subscribed(callback) {
        return this.listen('pusher_internal:subscription_succeeded', callback)
    }

    error(callback) {
        return this.listen('pusher:error', callback)
    }

    whisper(eventName, data = {}) {
        this.echo.connector.send({
            event: `client-${eventName}`,
            channel: this.name,
            data,
        })
        return this
    }

    unsubscribe() {
        this.echo.connector.send({
            event: 'pusher:unsubscribe',
            data: { channel: this.name },
        })
        for (const bucket of this.handlers.values()) {
            for (const entry of bucket) entry.stop()
        }
        this.handlers.clear()
        this.echo.forget(this.name)
    }
}

export class PresenceChannel extends Channel {
    here(callback) {
        return this.subscribed((data) => callback(data?.presence?.ids || []))
    }

    joining(callback) {
        return this.listen('pusher_internal:member_added', callback)
    }

    leaving(callback) {
        return this.listen('pusher_internal:member_removed', callback)
    }
}
