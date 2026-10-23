import Alpine from 'alpinejs'
import { interceptMessage } from '../request/index.js'

/** Run `effects.xjs` expressions against the component root after morph. */
export function registerJsEvaluation() {
    interceptMessage(({ message, onSuccess }) => {
        onSuccess(({ payload, onMorphed }) => {
            onMorphed(() => {
                let expressions = payload.effects?.xjs || []

                expressions.forEach(expression => {
                    Alpine.evaluate(message.component.el, expression)
                })
            })
        })
    })
}
