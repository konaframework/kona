import Alpine from 'alpinejs'
import morph from '@alpinejs/morph'
import intersect from '@alpinejs/intersect'
import collapse from '@alpinejs/collapse'
import focus from '@alpinejs/focus'
import persist from '@alpinejs/persist'
import anchor from '@alpinejs/anchor'
import mask from '@alpinejs/mask'
import sort from '@alpinejs/sort'
import resize from '@alpinejs/resize'
import { destroyComponent, findComponentByEl, hasComponent, initComponent } from './store.js'
import { extractDirective, matchesForBrewDirective } from './directives.js'
import { trigger } from './hooks.js'
import { dispatch } from './utils.js'

function initDirectives(el, component, names) {
    names.map(name => extractDirective(el, name)).forEach(directive => {
        trigger('directive.init', {
            el,
            component,
            directive,
            cleanup: callback => Alpine.onAttributeRemoved(el, directive.raw, callback),
        })
    })
}

export function start() {
    setTimeout(warnIfScriptIsMisplaced)

    dispatch(document, 'wire:init')
    dispatch(document, 'brew:init')
    dispatch(document, 'wire:initializing')
    dispatch(document, 'brew:initializing')

    ;[morph, intersect, sort, resize, collapse, anchor, focus, persist, mask].forEach(plugin => Alpine.plugin(plugin))

    Alpine.addRootSelector(() => '[wire\\:id]')

    // Directives added to an already-initialized element (e.g. by a morph) still need wiring...
    Alpine.onAttributesAdded((el, attributes) => {
        let names = [...attributes].map(attribute => attribute.name).filter(matchesForBrewDirective)

        if (names.length === 0 || ! el._x_marker) return

        let component = findComponentByEl(el, false)

        if (component) initDirectives(el, component, names)
    })

    Alpine.interceptInit(Alpine.skipDuringClone(el => {
        let names = el.getAttributeNames().filter(matchesForBrewDirective)

        if (names.length === 0) return

        if (el.hasAttribute('wire:id') && ! el.__brew && ! hasComponent(el.getAttribute('wire:id'))) {
            let component = initComponent(el)

            Alpine.onAttributeRemoved(el, 'wire:id', () => destroyComponent(component.id))
        }

        let component = findComponentByEl(el, false)

        if (! component) return

        trigger('element.init', { el, component })

        initDirectives(el, component, names)
    }))

    Alpine.start()

    setTimeout(() => window.Wire.initialRenderIsFinished = true)

    dispatch(document, 'wire:initialized')
    dispatch(document, 'brew:initialized')
}

function warnIfScriptIsMisplaced() {
    let script = document.querySelector('script[data-update-uri][data-csrf]')

    if (script?.closest('[wire\\:id]')) {
        console.warn('Wire: missing closing tags found. Ensure your template elements contain matching closing tags.', script.closest('[wire\\:id]'))
    }
}
