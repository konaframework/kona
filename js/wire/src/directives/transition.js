import { directive } from '../directives.js'

let defaultName = 'match-element'
let assignedTransitionNames = new WeakSet()

/** No-op registration so `wire:transition` is reserved from the wildcard. */
export function registerTransitionDirective() {
    directive('transition', () => {})
}

export function setTransitionNames(root, options = {}) {
    let attribute = options.attribute ?? 'wire:transition'
    let hasUsedDefaultName = false

    elementsForAttribute(root, attribute, options.includeRoot).forEach(el => {
        if (el.style.viewTransitionName) return

        let name = el.getAttribute(attribute)

        if (! name && options.defaultName) {
            if (hasUsedDefaultName) {
                console.warn(`Brew: Only one unnamed [${attribute}] element can be transitioned per page. Give additional elements unique names.`)

                return
            }

            name = options.defaultName
            hasUsedDefaultName = true
        }

        if (! name && options.type) return

        el.style.viewTransitionName = name || defaultName
        assignedTransitionNames.add(el)
    })
}

export function clearTransitionNames(root, options = {}) {
    let attribute = options.attribute ?? 'wire:transition'

    elementsForAttribute(root, attribute, options.includeRoot).forEach(el => {
        if (! assignedTransitionNames.has(el)) return

        el.style.viewTransitionName = ''
        assignedTransitionNames.delete(el)
    })
}

function selectorForAttribute(attribute) {
    return `[${attribute.replace(/[:.]/g, '\\$&')}]`
}

function elementsForAttribute(root, attribute, includeRoot = false) {
    let selector = selectorForAttribute(attribute)
    let elements = Array.from(root.querySelectorAll(selector))

    if (includeRoot && root.matches?.(selector)) elements.unshift(root)

    return elements
}

export function startViewTransition(update, options = {}) {
    let transitionConfig = { update }

    if (options.type) transitionConfig.types = [options.type]

    try {
        return document.startViewTransition(transitionConfig)
    } catch (e) {
        return document.startViewTransition(update)
    }
}

export function skipTransitionWhenTopLayerOpens(transition) {
    transition.ready.catch(() => {})

    let onBeforeToggle = event => {
        if (event.newState === 'open' && event.target.matches?.('dialog, [popover]')) {
            transition.skipTransition()
        }
    }

    document.addEventListener('beforetoggle', onBeforeToggle, true)

    let observer = new MutationObserver(() => {
        if (document.querySelector('dialog:modal')) {
            transition.skipTransition()
            observer.disconnect()
        }
    })

    observer.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['open'],
        subtree: true,
    })

    transition.finished.finally(() => {
        observer.disconnect()
        document.removeEventListener('beforetoggle', onBeforeToggle, true)
    }).catch(() => {})
}

/** True when an open modal dialog or visible popover would hide the transition. */
export function topLayerBlocksTransitions() {
    if (document.querySelector('dialog:modal')) return true

    for (let popover of document.querySelectorAll(':popover-open')) {
        for (let el of popover.querySelectorAll('*')) {
            if (typeof el.checkVisibility === 'function' ? el.checkVisibility() : false) return true
        }
    }

    return false
}

export async function transitionDomMutation(fromEl, toEl, callback, options = {}) {
    if (options.skip) return callback()

    if (! fromEl.querySelector?.('[wire\\:transition]') && ! toEl.querySelector?.('[wire\\:transition]')) {
        return callback()
    }

    if (typeof document.startViewTransition !== 'function') return callback()

    if (topLayerBlocksTransitions()) return callback()

    setTransitionNames(fromEl, options)

    let style = document.createElement('style')

    style.textContent = `
        @media (prefers-reduced-motion: reduce) {
            ::view-transition-group(*), ::view-transition-old(*), ::view-transition-new(*) {
                animation: none !important;
            }
        }
        ::view-transition-old(root) { animation: none !important; opacity: 0 !important; }
        ::view-transition-new(root) { animation: none !important; opacity: 1 !important; }
    `

    document.head.appendChild(style)

    let update = () => {
        callback()
        setTransitionNames(fromEl, options)
    }

    let cleanup = () => {
        style.remove()
        clearTransitionNames(fromEl)
    }

    let transition = startViewTransition(update, { type: options.type })

    skipTransitionWhenTopLayerOpens(transition)
    transition.finished.finally(cleanup).catch(() => {})

    await transition.updateCallbackDone
}
