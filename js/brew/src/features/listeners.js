import { on } from '../hooks.js'
import { fireAction } from '../request/index.js'

/** Wire `effects.listeners` to window events that call `__dispatch` on the component. */
export function registerListeners() {
    on('effect', ({ component, effects, cleanup }) => {
        let names = effects.listeners || []

        names.forEach(name => {
            let handler = event => {
                if (! event.__brew) return

                fireAction(component, '__dispatch', [name, event.detail || {}])
            }

            window.addEventListener(name, handler)
            cleanup(() => window.removeEventListener(name, handler))
        })
    })
}
