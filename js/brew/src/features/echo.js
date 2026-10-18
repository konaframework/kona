import { on } from '../hooks.js'
import { fireAction } from '../request/index.js'

/**
 * Wire `effects.echo` listeners to `window.Echo` when present
 * (`echo:channel,Event`, `echo-private:`, `echo-presence:`).
 */
export function registerEcho() {
    on('effect', ({ component, effects, cleanup }) => {
        let echo = effects.echo || {}
        let Echo = window.Echo

        if (! Echo || Object.keys(echo).length === 0) return

        Object.entries(echo).forEach(([spec, method]) => {
            let [prefix, rest] = spec.split(/:(.+)/)
            let [channel, event] = (rest || '').split(',')

            if (! channel || ! event) return

            let channelRef

            if (prefix === 'echo-private') channelRef = Echo.private(channel)
            else if (prefix === 'echo-presence') channelRef = Echo.join(channel)
            else channelRef = Echo.channel(channel)

            let handler = payload => fireAction(component, method, [payload])

            channelRef.listen(event, handler)
            cleanup(() => {
                if (typeof channelRef.stopListening !== 'function') return

                channelRef.stopListening(event, handler)
            })
        })
    })
}
