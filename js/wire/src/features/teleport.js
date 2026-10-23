import { on } from '../hooks.js'

function applyTeleports(root) {
    root.querySelectorAll('template[wire\\:teleport]').forEach(template => {
        let selector = template.getAttribute('wire:teleport')

        if (! selector) return

        let target = document.querySelector(selector)

        if (! target) return

        target.append(...template.content.childNodes)
        template.remove()
    })
}

/** Move `@teleport` template contents to the target selector. */
export function registerTeleport() {
    on('effect', ({ component }) => applyTeleports(component.el))
}
