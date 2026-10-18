import { findRefEl } from './features/refs.js'
import { componentsByName } from './store.js'

export function dispatch(component, name, params) {
    dispatchEvent(component.el, name, params)
}

export function dispatchGlobal(name, params) {
    dispatchEvent(window, name, params)
}

export function dispatchSelf(component, name, params) {
    dispatchEvent(component.el, name, params, false)
}

export function dispatchEl(component, selector, name, params) {
    component.el.querySelectorAll(selector).forEach(target => {
        dispatchEvent(target, name, params, false)
    })
}

export function dispatchTo(componentName, name, params) {
    componentsByName(componentName).forEach(target => {
        dispatchEvent(target.el, name, params, false)
    })
}

export function dispatchRef(component, ref, name, params) {
    let el = findRefEl(component, ref)

    if (el) dispatchEvent(el, name, params, false)
}

export function listen(component, name, callback) {
    let handler = e => callback(e.detail)

    component.el.addEventListener(name, handler)

    return () => component.el.removeEventListener(name, handler)
}

export function on(eventName, callback) {
    let handler = e => {
        if (! e.__brew) return

        callback(e.detail)
    }

    window.addEventListener(eventName, handler)

    return () => window.removeEventListener(eventName, handler)
}

function dispatchEvent(target, name, params, bubbles = true) {
    if (typeof params === 'string') params = [params]

    let event = new CustomEvent(name, { bubbles, detail: params, composed: true })

    event.__brew = { name, params, receivedBy: [] }
    target.dispatchEvent(event)
}
