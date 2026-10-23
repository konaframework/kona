import Alpine from 'alpinejs'
import { directive, getDirectives } from '../directives.js'
import { toggleBooleanStateDirective } from './shared.js'
import { dataGet, deeplyEqual, WeakBag } from '../utils.js'
import { on } from '../hooks.js'

let refreshDirtyStatesByComponent = new WeakBag()

export function checkDirty(component, targets) {
    if (targets === undefined) return ! deeplyEqual(component.canonical, component.reactive)

    if (Array.isArray(targets)) {
        return targets.some(target =>
            ! deeplyEqual(dataGet(component.canonical, target), dataGet(component.reactive, target)))
    }

    return ! deeplyEqual(dataGet(component.canonical, targets), dataGet(component.reactive, targets))
}

export function registerDirtyDirective() {
    on('commit', ({ component, succeed }) => {
        succeed(() => {
            setTimeout(() => refreshDirtyStatesByComponent.each(component, i => i(false)))
        })
    })

    directive('dirty', ({ el, directive, component }) => {
        let targets = dirtyTargets(el)
        let oldIsDirty = false
        let initialDisplay = el.style.display

        let refreshDirtyState = isDirty => {
            toggleBooleanStateDirective(el, directive, isDirty, initialDisplay)
            oldIsDirty = isDirty
        }

        refreshDirtyStatesByComponent.add(component, refreshDirtyState)

        Alpine.effect(() => {
            let isDirty = checkDirty(component, targets.length === 0 ? undefined : targets)

            if (oldIsDirty !== isDirty) refreshDirtyState(isDirty)

            oldIsDirty = isDirty
        })
    })
}

function dirtyTargets(el) {
    let directives = getDirectives(el)
    let targets = []

    if (directives.has('model')) targets.push(directives.get('model').expression)

    if (directives.has('target')) {
        targets = targets.concat(
            directives.get('target').expression.split(',').map(s => s.trim()).filter(Boolean),
        )
    }

    return targets
}
