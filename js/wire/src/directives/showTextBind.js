import Alpine from 'alpinejs'
import { evaluateReactiveExpression } from '../evaluator.js'

/** `wire:show` → Alpine `x-show` against `$wire`. */
export function registerShowDirective() {
    Alpine.interceptInit(el => {
        for (let i = 0; i < el.attributes.length; i++) {
            if (! el.attributes[i].name.startsWith('wire:show')) continue

            let { name, value } = el.attributes[i]
            let modifierString = name.split('wire:show')[1]
            let expression = value.trim()

            Alpine.bind(el, {
                ['x-show' + modifierString]() {
                    return evaluateReactiveExpression(el, expression)
                },
            })
        }
    })
}

/** `wire:text` → Alpine `x-text` against `$wire`. */
export function registerTextDirective() {
    Alpine.interceptInit(el => {
        for (let i = 0; i < el.attributes.length; i++) {
            if (! el.attributes[i].name.startsWith('wire:text')) continue

            let { name, value } = el.attributes[i]
            let modifierString = name.split('wire:text')[1]
            let expression = value.trim()

            Alpine.bind(el, {
                ['x-text' + modifierString]() {
                    return evaluateReactiveExpression(el, expression)
                },
            })
        }
    })
}

/** `wire:bind:attr` → Alpine `x-bind:attr` against `$wire`. */
export function registerBindDirective() {
    Alpine.interceptInit(el => {
        for (let i = 0; i < el.attributes.length; i++) {
            if (! el.attributes[i].name.startsWith('wire:bind:')) continue

            let { name, value } = el.attributes[i]
            let remainder = name.split('wire:bind')[1]
            let expression = value.trim()

            Alpine.bind(el, {
                ['x-bind' + remainder]() {
                    return evaluateReactiveExpression(el, expression)
                },
            })
        }
    })
}
