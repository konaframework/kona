import { isFunction } from './utils.js'

let listeners = {}

/**
 * Listen for an internal lifecycle event. Returns an "off" callback.
 * A listener may return a function; it becomes a finisher that receives
 * (and may replace) the value passed to the trigger's returned callback.
 */
export function on(name, callback) {
    if (! listeners[name]) listeners[name] = []

    listeners[name].push(callback)

    return () => {
        listeners[name] = listeners[name].filter(i => i !== callback)
    }
}

export function trigger(name, ...params) {
    let finishers = (listeners[name] || []).map(callback => callback(...params)).filter(isFunction)

    return result => runFinishers(finishers, result)
}

export async function triggerAsync(name, ...params) {
    let finishers = []

    for (let callback of listeners[name] || []) {
        let finisher = await callback(...params)

        if (isFunction(finisher)) finishers.push(finisher)
    }

    return result => runFinishers(finishers, result)
}

function runFinishers(finishers, result) {
    return finishers.reduce((latest, finisher) => {
        let next = finisher(latest)

        return next === undefined ? latest : next
    }, result)
}
