export class MessageRequest {
    messages = new Set()
    controller = new AbortController()
    interceptors = []
    cancelled = false
    isolated = false
    uri = null

    addMessage(message) {
        message.request = this

        this.messages.add(message)
    }

    initInterceptors(registry) {
        this.interceptors = registry.requestInterceptorsFor(this)

        this.messages.forEach(message => {
            // Interceptors added while the message waited (e.g. deferred actions) stay attached...
            message.interceptors = [...registry.messageInterceptorsFor(message), ...message.interceptors]
        })
    }

    cancel() {
        if (this.cancelled) return

        this.cancelled = true

        this.controller.abort()

        this.each('onCancel')

        this.messages.forEach(message => message.cancel())
    }

    hasAllCancelledMessages() {
        return [...this.messages].every(message => message.isCancelled())
    }

    isCancelled() { return this.cancelled }

    each(name, ...args) {
        this.interceptors.forEach(interceptor => interceptor[name](...args))
    }

    invokeOnSend(args) {
        this.each('onSend', args)
        this.messages.forEach(message => message.invokeOnSend())
    }

    invokeOnFailure({ error }) {
        this.each('onFailure', { error })
        this.messages.forEach(message => message.invokeOnFailure(error))
    }

    invokeOnError(args) {
        this.each('onError', args)
        this.messages.forEach(message => message.invokeOnError(args))
    }
}
