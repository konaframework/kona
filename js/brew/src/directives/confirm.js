import { directive } from '../directives.js'

export function registerConfirmDirective() {
    directive('confirm', ({ el, directive }) => {
        let message = (directive.expression || '').replaceAll('\\n', '\n')
        let shouldPrompt = directive.modifiers.includes('prompt')

        if (message === '') message = 'Are you sure?'

        el.__brew_confirm = (action, instead) => {
            if (shouldPrompt) {
                let [question, expected] = message.split('|')

                if (! expected) {
                    console.warn('Brew: Must provide expectation with wire:confirm.prompt')
                } else {
                    let input = prompt(question)

                    if (input === expected) action()
                    else instead()
                }
            } else if (confirm(message)) {
                action()
            } else {
                instead()
            }
        }
    })
}
