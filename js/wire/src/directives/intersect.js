import { directive } from '../directives.js'
import { fireAction } from '../request/index.js'

/** `wire:intersect` (+ `.once` / `.half` / `.full` / `.margin` / `:enter` / `:leave`). */
export function registerIntersectDirective() {
    let bind = (isLeave) => ({ el, directive: dir, component, cleanup }) => {
        let once = dir.modifiers.includes('once')
        let threshold = dir.modifiers.includes('full') ? 1
            : dir.modifiers.includes('half') ? 0.5
            : 0
        let marginMod = dir.modifiers.find(m => m.startsWith('margin'))
        let rootMargin = marginMod ? marginMod.replace('margin-', '').replace(/-/g, ' ') : '0px'
        let expression = dir.expression || '$refresh'
        let leave = isLeave || dir.rawName.includes(':leave')
        let fired = false

        let observer = new IntersectionObserver(entries => {
            entries.forEach(entry => {
                let hit = leave ? ! entry.isIntersecting : entry.isIntersecting
                if (! hit) return
                if (once && fired) return
                fired = true
                fireAction(component, expression)
                if (once) observer.disconnect()
            })
        }, { threshold, rootMargin })

        observer.observe(el)
        cleanup(() => observer.disconnect())
    }

    directive('intersect', bind(false))
    directive('intersect:enter', bind(false))
    directive('intersect:leave', bind(true))
}
