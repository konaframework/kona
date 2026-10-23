import Alpine from 'alpinejs'
import { on } from '../hooks.js'

/** Run `@script` / `effects.scripts` once per component, and bind `@Js` methods on `$wire.$js`. */
export function registerScripts() {
    on('effect', ({ component, effects }) => {
        let js = effects.js || {}

        Object.entries(js).forEach(([name, body]) => {
            if (! component.$js) component.$js = {}

            component.$js[name] = (...params) => Alpine.evaluate(component.el, body, {
                scope: { $wire: component.$wire, params },
            })
        })

        let scripts = effects.scripts || []

        if (scripts.length === 0) return

        if (component.__wireScriptsRan) return

        component.__wireScriptsRan = true

        scripts.forEach(source => {
            Alpine.evaluate(component.el, source, { scope: { $wire: component.$wire, $js: component.$js } })
        })
    })
}
