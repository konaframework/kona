/** Resolve `[wire:ref="name"]` inside a component root. */
export function findRefEl(component, name) {
    let refEl = component.el.querySelector(`[wire\\:ref="${name}"]`)

    if (! refEl) {
        console.error(`Ref "${name}" not found in component "${component.id}"`)

        return
    }

    return refEl
}
