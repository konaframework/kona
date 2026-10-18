import Alpine from 'alpinejs'
import { on } from '../hooks.js'
import { customDirectiveHasBeenRegistered } from '../directives.js'
import { setNextActionInterceptor, setNextActionOrigin } from '../request/index.js'
import { evaluateActionExpression } from '../evaluator.js'

const RESERVED = [
    'snapshot', 'effects', 'model', 'init', 'loading', 'poll', 'ignore', 'id', 'data',
    'key', 'target', 'dirty', 'sort', 'name', 'cloak', 'show', 'text', 'bind', 'ref',
    'transition', 'offline', 'confirm', 'navigate', 'current', 'persist', 'scroll',
]
const BREW_ONLY_MODIFIERS = ['async', 'renderless', 'preserve-scroll', 'prepend', 'append', 'island']

/**
 * Any other `wire:<event>` becomes an Alpine `x-on` listener (keeping every
 * key and event modifier) whose expression runs against `$wire`.
 */
export function registerWildcardDirective() {
    on('directive.init', ({ el, directive, cleanup, component }) => {
        if (RESERVED.includes(directive.value) || customDirectiveHasBeenRegistered(directive.value)) return

        let modifiers = []

        for (let i = 0; i < directive.modifiers.length; i++) {
            let modifier = directive.modifiers[i]

            if (BREW_ONLY_MODIFIERS.includes(modifier)) {
                // Drop the island name token that follows `.island`...
                if (modifier === 'island') {
                    let next = directive.modifiers[i + 1]

                    if (next && ! BREW_ONLY_MODIFIERS.includes(next) && ! ['prevent', 'stop', 'window', 'document', 'outside', 'once', 'debounce', 'throttle', 'self', 'camel', 'dot', 'passive', 'capture'].includes(next)) {
                        i++
                    }
                }

                continue
            }

            modifiers.push(modifier)
        }

        if (directive.value === 'submit' && ! modifiers.includes('prevent')) modifiers.push('prevent')

        let attribute = ['x-on:' + directive.value, ...modifiers].join('.')

        let unbind = Alpine.bind(el, {
            [attribute](event) {
                directive.eventContext = event
                directive.wire = component.$wire

                let execute = () => {
                    let targetEl = directive.value === 'submit'
                        ? event.submitter || el.querySelector('button[type="submit"], input[type="submit"]')
                        : undefined

                    setNextActionOrigin(targetEl === undefined ? { el, directive } : { el, directive, targetEl })

                    let options = event.detail?.brew

                    if (options?.interceptAction) setNextActionInterceptor(options.interceptAction)

                    let expression = directive.expression

                    // A bare method name receives the event's default params, if the dispatcher supplied any...
                    if (options?.defaultParams !== undefined && ! expression.includes('(')) {
                        let params = Array.isArray(options.defaultParams) ? options.defaultParams : [options.defaultParams]

                        expression += '(' + params.map(param => JSON.stringify(param)).join(', ') + ')'
                    }

                    evaluateActionExpression(el, expression, { scope: { $event: event } })
                }

                if (el.__brew_confirm) {
                    el.__brew_confirm(execute, () => event.stopImmediatePropagation())
                } else {
                    execute()
                }
            },
        })

        cleanup(unbind)
    })
}
