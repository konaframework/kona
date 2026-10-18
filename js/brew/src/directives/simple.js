import { directive } from '../directives.js'
import { setNextActionOrigin } from '../request/index.js'
import { evaluateActionExpression } from '../evaluator.js'

export function registerSimpleDirectives() {
    directive('init', ({ el, directive }) => {
        setNextActionOrigin({ el, directive })

        evaluateActionExpression(el, directive.expression || '$refresh')
    })

    directive('ignore', ({ el, directive }) => {
        if (directive.modifiers.includes('self')) {
            el.__brew_ignore_self = true
        } else if (directive.modifiers.includes('children')) {
            el.__brew_ignore_children = true
        } else {
            el.__brew_ignore = true
        }
    })

    directive('replace', ({ el, directive }) => {
        if (directive.modifiers.includes('self')) {
            el.__brew_replace_self = true
        } else {
            el.__brew_replace = true
        }
    })
}
