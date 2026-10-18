export class WeakBag {
    constructor() { this.map = new WeakMap() }

    add(key, value) {
        if (! this.map.has(key)) this.map.set(key, [])

        this.map.get(key).push(value)
    }

    get(key) { return this.map.get(key) || [] }

    each(key, callback) { this.get(key).forEach(callback) }

    remove(key) { this.map.delete(key) }
}

export function dispatch(target, name, detail = {}, bubbles = true) {
    target.dispatchEvent(new CustomEvent(name, { detail, bubbles, composed: true, cancelable: true }))
}

export function isObjecty(subject) { return typeof subject === 'object' && subject !== null }
export function isObject(subject) { return isObjecty(subject) && ! isArray(subject) }
export function isArray(subject) { return Array.isArray(subject) }
export function isFunction(subject) { return typeof subject === 'function' }
export function isPrimitive(subject) { return typeof subject !== 'object' || subject === null }
export function deepClone(value) { return JSON.parse(JSON.stringify(value)) }
export function deeplyEqual(a, b) { return JSON.stringify(a) === JSON.stringify(b) }

export function parsePathSegments(path) {
    if (path === '') return []

    return path
        .replace(/\[(['"]?)(.+?)\1\]/g, '.$2')
        .replace(/^\./, '')
        .split('.')
}

export function dataGet(object, key) {
    if (key === '') return object

    return parsePathSegments(key).reduce((carry, segment) => carry?.[segment], object)
}

export function dataSet(object, key, value) {
    let segments = parsePathSegments(key)

    if (segments.length === 1) {
        object[segments[0]] = value

        return
    }

    let first = segments.shift()
    let next = segments[0]

    if (object[first] === undefined) object[first] = {}

    // Assigning a far index on an array would back-fill it with nulls, so promote to an object...
    if (isArray(object[first]) && ! isNaN(parseInt(next)) && parseInt(next) > object[first].length) {
        object[first] = { ...object[first] }
    }

    dataSet(object[first], segments.join('.'), value)
}

/**
 * A flat, dot-notated diff of two data trees. When every child of a node
 * changes, or keys are added/removed/reordered, the node is sent whole.
 */
export function diffAndConsolidate(left, right) {
    let diffs = {}

    diffNode(left, right, '', diffs, right)

    return diffs
}

function diffNode(left, right, path, diffs, root) {
    if (left === right) return { changed: false, consolidated: false }

    let converted = false

    if (typeof left !== typeof right || (isObject(left) && isArray(right)) || (isArray(left) && isObject(right))) {
        if (isObject(right) && (left === undefined || (isArray(left) && left.length === 0))) {
            left = {}
            converted = true
        } else {
            diffs[path] = right

            return { changed: true, consolidated: false }
        }
    }

    if (isPrimitive(left) || isPrimitive(right)) {
        diffs[path] = right

        return { changed: true, consolidated: false }
    }

    let leftKeys = Object.keys(left)
    let rightKeys = Object.keys(right)
    let whole = () => {
        diffs[path] = dataGet(root, path)

        return { changed: true, consolidated: true }
    }

    if (! converted) {
        let reshaped = leftKeys.length !== rightKeys.length
            || ! leftKeys.every(key => rightKeys.includes(key))
            || (isObject(left) && leftKeys.some((key, i) => key !== rightKeys[i]))

        if (reshaped && path !== '') return whole()

        if (reshaped && leftKeys.length !== rightKeys.length) {
            rightKeys.forEach(key => {
                if (! deeplyEqual(left[key], right[key])) diffs[key] = right[key]
            })

            return { changed: true, consolidated: true }
        }
    }

    let childDiffs = {}
    let changed = 0
    let consolidated = 0

    rightKeys.forEach(key => {
        let result = diffNode(left[key], right[key], path === '' ? key : `${path}.${key}`, childDiffs, root)

        if (result.changed) changed++
        if (result.consolidated) consolidated++
    })

    if (path !== '' && rightKeys.length > 1 && changed === rightKeys.length && consolidated === 0 && ! converted) {
        return whole()
    }

    Object.assign(diffs, childDiffs)

    return { changed: changed > 0, consolidated: consolidated > 0 || converted }
}

/**
 * Patch the difference between two trees onto a target, preserving object
 * identity where possible so Alpine reactivity only fires for real changes.
 */
export function diffAndPatchRecursive(left, right, target) {
    let leftKeys = new Set(Object.keys(left))
    let rightKeys = Object.keys(right)

    if (! isArray(target) && [...leftKeys].some((key, i) => key !== rightKeys[i])) {
        Object.keys(target).forEach(key => delete target[key])
        rightKeys.forEach(key => target[key] = right[key])

        return
    }

    rightKeys.forEach(key => {
        leftKeys.delete(key)

        if (deeplyEqual(left[key], right[key])) return

        if (isObjecty(left[key]) && isObjecty(right[key]) && isObjecty(target[key]) && isArray(right[key]) === isArray(target[key])) {
            diffAndPatchRecursive(left[key], right[key], target[key])
        } else {
            target[key] = right[key]
        }
    })

    // An object losing keys always fails the position check above, so only arrays reach here...
    if (isArray(target)) {
        [...leftKeys].map(Number).sort((a, b) => b - a).forEach(index => target.splice(index, 1))
    }
}

/**
 * Snapshot data travels as nested `[value, {s: key}]` tuples. This unwraps
 * the raw values into a plain JavaScript tree.
 */
export function extractData(payload) {
    let value = isSynthetic(payload) ? payload[0] : payload

    if (isObjecty(value)) {
        Object.entries(value).forEach(([key, item]) => {
            value[key] = extractData(item)
        })
    }

    return value
}

export function isSynthetic(subject) {
    return isArray(subject)
        && subject.length === 2
        && isObject(subject[1])
        && Object.keys(subject[1]).includes('s')
}

function scriptConfig() {
    return window.brewScriptConfig || {}
}

export function getCsrfToken() {
    let meta = document.querySelector('meta[name="csrf-token"]')

    if (meta) return meta.getAttribute('content')

    let tagged = document.querySelector('[data-csrf]')

    if (tagged) return tagged.getAttribute('data-csrf')

    if (scriptConfig().csrf) return scriptConfig().csrf

    throw 'Brew: No CSRF token detected'
}

export function getUpdateUri() {
    return document.querySelector('[data-update-uri]')?.getAttribute('data-update-uri')
        ?? scriptConfig().uri
        ?? '/brew/update'
}

export function walkUpwards(el, callback) {
    let current = el

    while (current) {
        let found = callback(current)

        if (found !== undefined) return found

        current = current._x_teleportBack || current.parentElement
    }
}
