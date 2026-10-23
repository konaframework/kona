import { Component } from './component.js'
import { trigger } from './hooks.js'
import { walkUpwards } from './utils.js'

let components = {}

export function initComponent(el) {
    let component = new Component(el)

    if (components[component.id]) throw 'Component already registered'

    trigger('component.init', { component, cleanup: i => component.addCleanup(i) })

    components[component.id] = component

    // Mount-time effects run after registration so they can resolve $wire...
    component.processEffects(component.effects)

    trigger('component.initialized', { component })

    return component
}

export function destroyComponent(id) {
    let component = components[id]

    if (! component) return

    component.cleanup()

    delete components[id]
}

export function hasComponent(id) {
    return !! components[id]
}

export function findComponent(id, strict = true) {
    let component = components[id]

    if (! component && strict) throw 'Component not found: ' + id

    return component
}

export function findComponentByEl(el, strict = true) {
    let id = walkUpwards(el, node => node.__brew?.id)
    let component = id && components[id]

    if (! component && strict) throw 'Could not find Brew component in DOM tree'

    return component || undefined
}

export function componentsByName(name) {
    return Object.values(components).filter(component => component.name === name)
}

export function getByName(name) {
    return componentsByName(name).map(component => component.$wire)
}

export function find(id) {
    return components[id]?.$wire
}

export function first() {
    return Object.values(components)[0]?.$wire
}

export function all() {
    return Object.values(components)
}
