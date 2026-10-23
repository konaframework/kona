import Alpine from 'alpinejs'
import { dataGet, dataSet } from './utils.js'
import { on } from './hooks.js'
import { findComponentByEl } from './store.js'
import { fireAction, interceptComponentAction, interceptComponentMessage, interceptComponentRequest } from './request/index.js'
import { getErrorsObject } from './features/errors.js'
import { findRefEl } from './features/refs.js'
import { checkDirty } from './directives/dirty.js'
import { isEvaluatingReactiveExpression } from './evaluator.js'
import { dispatch, dispatchSelf, dispatchTo, dispatchEl, dispatchRef, listen } from './events.js'
import { entangle } from './features/entangle.js'

let properties = {}

const aliases = {
    el: '$el',
    id: '$id',
    get: '$get',
    set: '$set',
    call: '$call',
    hook: '$hook',
    watch: '$watch',
    dirty: '$dirty',
    commit: '$commit',
    errors: '$errors',
    refs: '$refs',
    on: '$on',
    dispatch: '$dispatch',
    dispatchTo: '$dispatchTo',
    dispatchSelf: '$dispatchSelf',
    dispatchEl: '$dispatchEl',
    dispatchRef: '$dispatchRef',
    intercept: '$intercept',
    interceptAction: '$interceptAction',
    interceptMessage: '$interceptMessage',
    interceptRequest: '$interceptRequest',
    js: '$js',
    entangle: '$entangle',
}

/**
 * Register a `$wire.<name>` property. Features in later slices extend the
 * object through this function.
 */
export function wireProperty(name, callback) {
    properties[name] = callback
}

function callServer(component, method, params) {
    // `@click="save"` passes the DOM event as the only argument; it is not a server parameter...
    if (params.length === 1 && params[0] instanceof Event) params = []

    if (isEvaluatingReactiveExpression()) {
        console.warn(`Brew: Cannot call server method "${method}" from a reactive binding like wire:text, wire:show, or wire:bind.`, component.el)

        return Promise.resolve()
    }

    return fireAction(component, method, params)
}

export function generateWireObject(component, state) {
    return new Proxy({}, {
        get(target, property) {
            if (property === '__instance') return component

            let name = Object.hasOwn(aliases, property) ? aliases[property] : property

            if (Object.hasOwn(properties, name)) return properties[name](component)
            if (property in state) return state[property]
            if (property === 'toJSON') return () => component.toJSON()
            if (typeof property === 'symbol' || property === 'then') return undefined

            return (...params) => callServer(component, property, params)
        },

        set(target, property, value) {
            if (property in state) state[property] = value

            return true
        },
    })
}

function watcher(component, cleanup) {
    return (path, callback) => {
        let unwatch = Alpine.watch(() => dataGet(component.reactive, path), callback)

        if (cleanup) {
            cleanup(unwatch)

            return unwatch
        }

        let removeCleanup = component.addCleanup(unwatch)

        return () => {
            removeCleanup()
            unwatch()
        }
    }
}

function safely(callback) {
    try {
        return callback()
    } catch (error) {
        return Promise.reject(error)
    }
}

wireProperty('$get', component => (path, reactive = true) => dataGet(reactive ? component.reactive : component.ephemeral, path))

wireProperty('$el', component => component.el)

wireProperty('$id', component => component.id)

wireProperty('$set', component => (path, value, live = true) => {
    dataSet(component.reactive, path, value)

    if (! live) return Promise.resolve()

    // Queued so the server applies it first, even if the input was reverted locally meanwhile...
    component.queueUpdate(path, value)

    return fireAction(component, '$set')
})

wireProperty('$toggle', component => (path, live = true) => component.$wire.$set(path, ! component.$wire.$get(path), live))

wireProperty('$call', component => (method, ...params) => component.$wire[method](...params))

wireProperty('$refresh', component => () => safely(() => fireAction(component, '$refresh')))

wireProperty('$commit', component => () => safely(() => fireAction(component, '$commit')))

wireProperty('$watch', component => watcher(component))

wireProperty('$hook', component => (name, callback) => {
    let unhook = on(name, ({ component: hooked, ...params }) => {
        if (hooked === undefined) return callback(params)

        if (hooked.id === component.id) return callback({ component: hooked, ...params })
    })

    component.addCleanup(unhook)

    return unhook
})

wireProperty('$intercept', component => (name, callback) => interceptComponentAction(component, name, callback))
wireProperty('$interceptAction', component => (name, callback) => interceptComponentAction(component, name, callback))
wireProperty('$interceptMessage', component => (name, callback) => interceptComponentMessage(component, name, callback))
wireProperty('$interceptRequest', component => (name, callback) => interceptComponentRequest(component, name, callback))

wireProperty('$errors', component => getErrorsObject(component))

wireProperty('$refs', component => {
    let fn = name => findRefEl(component, name)

    return new Proxy(fn, {
        get(target, property) {
            if (property in target) return target[property]

            return fn(property)
        },
    })
})

wireProperty('$dirty', component => property => {
    let reactive = Alpine.reactive({ dirty: false })

    interceptComponentMessage(component, ({ onFinish }) => {
        onFinish(() => {
            queueMicrotask(() => { reactive.dirty = checkDirty(component, property) })
        })
    })

    Alpine.effect(() => { reactive.dirty = checkDirty(component, property) })

    return reactive.dirty
})

wireProperty('$on', component => (name, callback) => {
    let off = listen(component, name, callback)

    component.addCleanup(off)

    return off
})

wireProperty('$dispatch', component => (name, params) => dispatch(component, name, params))
wireProperty('$dispatchSelf', component => (name, params) => dispatchSelf(component, name, params))
wireProperty('$dispatchTo', () => (name, event, params) => dispatchTo(name, event, params))
wireProperty('$dispatchEl', component => (selector, name, params) => dispatchEl(component, selector, name, params))
wireProperty('$dispatchRef', component => (ref, name, params) => dispatchRef(component, ref, name, params))

wireProperty('$js', component => component.$js ??= {})

wireProperty('$entangle', component => (name, live = false) => entangle(component, name, live))

wireProperty('$parent', component => {
    let parent = component.parent

    return parent ? parent.$wire : new Proxy({}, { get: () => () => {} })
})

/**
 * `$wire` inside Alpine expressions resolves its component lazily, and
 * `$watch` registered from an element is torn down with that element.
 */
export function registerWireMagic() {
    Alpine.magic('wire', (el, { cleanup }) => {
        let component

        let resolve = () => component ??= findComponentByEl(el, false)

        return new Proxy({}, {
            get(target, property) {
                if (! resolve()) return () => {}

                if (property === '$watch' || property === 'watch') return watcher(component, cleanup)

                return component.$wire[property]
            },

            set(target, property, value) {
                if (resolve()) component.$wire[property] = value

                return true
            },
        })
    })

}
