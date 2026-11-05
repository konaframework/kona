import { getCurrentInstance, onUnmounted, shallowRef } from 'vue'

function resolveEcho(echo) {
    if (echo) return echo
    if (typeof window !== 'undefined' && window.Echo) return window.Echo
    throw new Error('Echo instance not found. Pass one to useEcho() or set window.Echo.')
}

/**
 * Subscribe to a private channel for the lifetime of the current component.
 */
export function useEcho(channelName, listeners = {}, echo = null) {
    const instance = resolveEcho(echo)
    const channel = shallowRef(null)
    let active = null

    Promise.resolve(instance.private(channelName)).then((ch) => {
        channel.value = ch
        active = ch
        for (const [event, handler] of Object.entries(listeners)) {
            ch.listen(event, handler)
        }
    })

    if (getCurrentInstance()) {
        onUnmounted(() => active?.unsubscribe())
    }

    return channel
}

/** Subscribe to a public channel. */
export function useEchoPublic(channelName, listeners = {}, echo = null) {
    const instance = resolveEcho(echo)
    const channel = shallowRef(null)
    let active = null

    Promise.resolve(instance.channel(channelName)).then((ch) => {
        channel.value = ch
        active = ch
        for (const [event, handler] of Object.entries(listeners)) {
            ch.listen(event, handler)
        }
    })

    if (getCurrentInstance()) {
        onUnmounted(() => active?.unsubscribe())
    }

    return channel
}

/** Join a presence channel. */
export function useEchoPresence(channelName, listeners = {}, echo = null) {
    const instance = resolveEcho(echo)
    const channel = shallowRef(null)
    let active = null

    Promise.resolve(instance.join(channelName)).then((ch) => {
        channel.value = ch
        active = ch
        for (const [event, handler] of Object.entries(listeners)) {
            if (event === 'here' && typeof ch.here === 'function') ch.here(handler)
            else if (event === 'joining' && typeof ch.joining === 'function') ch.joining(handler)
            else if (event === 'leaving' && typeof ch.leaving === 'function') ch.leaving(handler)
            else ch.listen(event, handler)
        }
    })

    if (getCurrentInstance()) {
        onUnmounted(() => active?.unsubscribe())
    }

    return channel
}
