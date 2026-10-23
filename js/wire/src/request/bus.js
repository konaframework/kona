let componentScopes = new WeakMap()

export function scopeOf(component) {
    if (! componentScopes.has(component)) componentScopes.set(component, Symbol())

    return componentScopes.get(component)
}

export class MessageBus {
    pendingMessages = new Set()
    activeMessages = new Set()
    bufferingMessages = new Set()

    /**
     * Hold a message open for 5ms so actions fired in the same tick (and
     * other components' messages) are bundled into a single request.
     */
    buffer(message, callback) {
        if (this.bufferingMessages.has(message)) return

        this.bufferingMessages.add(message)

        setTimeout(() => {
            callback()

            this.bufferingMessages.delete(message)
        }, 5)
    }

    findPendingMessageFor(action) {
        if (action.isAsync()) return null

        return [...this.pendingMessages].find(message => message.component === action.component)
    }

    activeMessagesFor(action) {
        let scope = scopeOf(action.component)

        return [...this.activeMessages].filter(message => message.scope === scope)
    }

    takePendingMessages() {
        let messages = [...this.pendingMessages]

        this.pendingMessages.clear()

        return messages
    }
}
