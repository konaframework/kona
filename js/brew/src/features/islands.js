import { on } from '../hooks.js'
import { fireAction, interceptAction } from '../request/index.js'
import { wireProperty } from '../wire.js'

/** Island targeting via `.island` modifier and `$wire.$island(name)`. */
export function registerIslands() {
    interceptAction(({ action }) => {
        let modifiers = action.origin?.directive?.modifiers || []
        let islandIdx = modifiers.indexOf('island')

        if (islandIdx === -1) return

        let name = modifiers[islandIdx + 1] || 'default'
        let mode = modifiers.includes('append') ? 'append' : modifiers.includes('prepend') ? 'prepend' : 'morph'

        action.metadata.island = { name, mode }
    })

    wireProperty('$island', component => (name = 'default', mode = 'morph') => {
        component.__brewIsland = { name, mode }

        return fireAction(component, '$refresh')
    })

    on('effect', ({ component, effects }) => {
        let islands = effects.islands || {}

        Object.entries(islands).forEach(([name, payload]) => {
            let el = component.el.querySelector(`[wire\\:island="${name}"]`)

            if (! el || ! payload?.html) return

            let mode = component.__brewIsland?.mode || 'morph'

            if (mode === 'append') el.insertAdjacentHTML('beforeend', payload.html)
            else if (mode === 'prepend') el.insertAdjacentHTML('afterbegin', payload.html)
            else el.innerHTML = payload.html
        })
    })
}
