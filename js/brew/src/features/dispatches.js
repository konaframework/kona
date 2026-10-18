import { dispatch, dispatchEl, dispatchRef, dispatchSelf, dispatchTo } from '../events.js'
import { on } from '../hooks.js'
import { interceptMessage } from '../request/index.js'

export function registerDispatches() {
    interceptMessage(({ message, onSuccess }) => {
        onSuccess(({ payload, onMorphed }) => {
            onMorphed(() => {
                dispatchEvents(message.component, payload.effects?.dispatches || [])
            })
        })
    })

    on('component.init', ({ component }) => {
        let dispatches = component.effects?.dispatches || []

        if (dispatches.length === 0) return

        queueMicrotask(() => {
            queueMicrotask(() => {
                queueMicrotask(() => dispatchEvents(component, dispatches))
            })
        })
    })
}

function dispatchEvents(component, dispatches) {
    dispatches.forEach(({ name, params = {}, self = false, component: componentName, ref, el }) => {
        if (self) dispatchSelf(component, name, params)
        else if (componentName) dispatchTo(componentName, name, params)
        else if (ref) dispatchRef(component, ref, name, params)
        else if (el) dispatchEl(component, el, name, params)
        else dispatch(component, name, params)
    })
}
