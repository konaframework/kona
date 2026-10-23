import { interceptAction, interceptMessage } from '../request/index.js'

/** Remember and restore scroll when an action carries `.preserve-scroll`. */
export function registerPreserveScroll() {
    interceptAction(({ action }) => {
        if (action.origin?.directive?.modifiers.includes('preserve-scroll')) {
            action.metadata.preserveScroll = true
        }
    })

    interceptMessage(({ message, onSuccess }) => {
        let preserve = [...message.actions].some(action => action.metadata.preserveScroll)

        if (! preserve) return

        let x = window.scrollX
        let y = window.scrollY

        onSuccess(({ onMorphed }) => {
            onMorphed(() => window.scrollTo(x, y))
        })
    })
}
