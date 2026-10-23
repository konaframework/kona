import { interceptMessage } from '../request/index.js'

/** Mark action origin elements with `data-loading` while their request is in flight. */
export function registerDataLoading() {
    interceptMessage(({ message, onSend, onFinish }) => {
        let undos = []

        onSend(() => {
            message.actions.forEach(action => {
                let origin = action.origin

                if (! origin) return

                let el = Object.prototype.hasOwnProperty.call(origin, 'targetEl') ? origin.targetEl : origin.el

                if (! el) return

                el.setAttribute('data-loading', 'true')
                undos.push(() => el.removeAttribute('data-loading'))
            })
        })

        onFinish(() => undos.forEach(undo => undo()))
    })
}
