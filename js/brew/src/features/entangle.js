import { dataGet, dataSet } from '../utils.js'

/** Alpine-entangled Brew state: `$wire.$entangle('prop')` / `@entangle('prop')`. */
export function entangle(component, name, live = false) {
    let options = { live }

    let api = {
        get() {
            return dataGet(component.reactive, name)
        },
        set(value) {
            dataSet(component.reactive, name, value)

            if (options.live) component.$wire.$set(name, value)
        },
    }

    Object.defineProperty(api, 'live', {
        get() {
            options.live = true

            return api
        },
    })

    return api
}
