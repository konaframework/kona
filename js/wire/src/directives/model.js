import Alpine from 'alpinejs'
import { directive } from '../directives.js'
import { dataGet, dataSet, deeplyEqual } from '../utils.js'
import { setNextActionMetadata, setNextActionOrigin } from '../request/index.js'

/**
 * `wire:model` binds through Alpine's `x-model` against `$wire`. Modifiers
 * before `.live` shape when the local value syncs; modifiers after `.live`
 * shape when the network request is sent.
 */
export function registerModelDirective() {
    directive('model', ({ el, directive, component }) => {
        let { expression } = directive
        let modifiers = directive.modifiers.filter(modifier => modifier !== 'renderless')

        if (! expression) return console.warn('Wire: [wire:model] is missing a value.', el)

        if (el.type === 'file') {
            el.addEventListener('change', () => {
                let files = el.files
                if (! files || files.length === 0) return
                if (el.multiple) component.$wire.$uploadMultiple(expression, files)
                else component.$wire.$upload(expression, files[0])
            })
            return
        }

        if (! Object.hasOwn(component.canonical, baseProperty(expression))) {
            return console.warn(`Brew: [wire:model="${expression}"] property does not exist on component: [${component.name}]`, el)
        }

        let liveIndex = modifiers.indexOf('live')
        let isLive = liveIndex !== -1
        // `.lazy` without `.live` keeps its historical meaning: sync and send on change...
        let lazyOnly = modifiers.includes('lazy') && ! isLive
        let sendsNetwork = isLive || lazyOnly

        let local = isLive ? modifiers.slice(0, liveIndex) : modifiers.filter(modifier => modifier !== 'lazy')
        let network = isLive ? modifiers.slice(liveIndex + 1) : lazyOnly ? ['change'] : []

        if (! local.includes('deep') && ! network.includes('deep') && ! local.includes('self')) local.push('self')

        let onBlur = network.includes('blur')
        let onChange = network.includes('change')
        let onEnter = network.includes('enter')
        let hasTriggers = onBlur || onChange || onEnter
        let debounced = network.includes('debounce')
        let throttled = network.includes('throttle')

        let update = () => {
            setNextActionOrigin({ el, directive })

            if (isLive) setNextActionMetadata({ type: 'model.live' })

            component.$wire.$commit()
        }

        let send = update

        if (debounced || (sendsNetwork && ! hasTriggers && ! throttled && isRealtimeInput(el))) {
            send = debounce(update, duration(network, 'debounce') ?? 150)
        }

        if (throttled) send = throttle(update, duration(network, 'throttle') ?? 150)

        let bindings = {}

        // A microtask lets a local `.blur` sync land before deciding whether anything changed...
        if (onBlur) bindings['@blur'] = () => queueMicrotask(() => {
            if (! deeplyEqual(component.canonical, component.ephemeral)) update()
        })
        if (onChange) bindings['@change'] = () => update()
        if (onEnter) bindings['@keydown.enter'] = () => update()

        bindings['x-model' + modifierTail(local)] = () => ({
            get: () => dataGet(component.$wire, expression),
            set: value => {
                dataSet(component.$wire, expression, value)

                if (sendsNetwork && ! hasTriggers) send()
            },
        })

        Alpine.bind(el, bindings)
    })
}

function baseProperty(expression) {
    return (expression.match(/^\[['"]?([^\]'"]+)['"]?\]/) || expression.match(/^([^.[]+)/))[1]
}

function isRealtimeInput(el) {
    return ['INPUT', 'TEXTAREA'].includes(el.tagName) && ! ['checkbox', 'radio'].includes(el.type)
}

function modifierTail(modifiers) {
    let kept = []

    for (let i = 0; i < modifiers.length; i++) {
        let modifier = modifiers[i]

        if (modifier === 'debounce' || modifier === 'throttle') {
            if (duration(modifiers, modifier) !== undefined) i++

            continue
        }

        if (modifier !== 'defer') kept.push(modifier)
    }

    // Never empty: `self` or `deep` is always present...
    return '.' + kept.join('.')
}

function duration(modifiers, key) {
    let next = modifiers[modifiers.indexOf(key) + 1] || ''
    let value = parseInt(next)

    return /^\d+(ms)?$/.test(next) ? value : undefined
}

function debounce(callback, wait) {
    let timeout

    return () => {
        clearTimeout(timeout)

        timeout = setTimeout(callback, wait)
    }
}

function throttle(callback, limit) {
    let waiting = false

    return () => {
        if (waiting) return

        callback()

        waiting = true

        setTimeout(() => waiting = false, limit)
    }
}
