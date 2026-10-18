import Alpine from 'alpinejs'

const SKIP = ['JSON', 'true', 'false', 'null', 'undefined', 'this', '$wire', '$event']

/**
 * Keys an Alpine `x-data` declares between this element and the component
 * root. Only each element's own scope is read so parent scopes outside the
 * component never leak in.
 */
function alpineScopeKeys(el) {
    let keys = []

    for (let current = el; current; current = current.parentElement) {
        let own = current._x_dataStack?.[0] || {}

        Object.keys(own).forEach(key => {
            if (! key.startsWith('$') && ! keys.includes(key)) keys.push(key)
        })

        if (current.hasAttribute('wire:id')) break
    }

    return keys
}

function reportError(error, expression, el) {
    console.warn(`Brew Expression Error: ${error.message}\n\nExpression: "${expression}"\n\n`, el)
    console.error(error)
}

let reactiveExpressionEvaluationDepth = 0

export function isEvaluatingReactiveExpression() {
    return reactiveExpressionEvaluationDepth > 0
}

export function evaluateReactiveExpression(el, expression, options = {}) {
    reactiveExpressionEvaluationDepth++

    try {
        return evaluateActionExpression(el, expression, options)
    } finally {
        reactiveExpressionEvaluationDepth--
    }
}

export function evaluateActionExpression(el, expression, options = {}) {
    if (! expression || expression.trim() === '') return

    try {
        return Alpine.evaluateRaw(el, contextualizeExpression(expression, el), options)
    } catch (error) {
        reportError(error, expression, el)
    }
}

/**
 * Prefix bare identifiers with `$wire.` so `save(1)` calls the component
 * action while strings, comments, object keys, member accesses, and Alpine
 * scope keys are left untouched. A leading action call (`save` or `save(...)`)
 * always targets `$wire`, even when an Alpine key shares its name.
 */
export function contextualizeExpression(expression, el) {
    let scopeKeys = alpineScopeKeys(el)
    let skip = [...SKIP, ...scopeKeys]
    let protectedParts = []
    let target = actionTargetOffset(expression)

    let result = expression.replace(/(["'`])(?:(?!\1)[^\\]|\\.)*\1|\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, match => {
        protectedParts.push(match)

        return `___${protectedParts.length - 1}___`
    })

    result = result.replace(/(^|[^.\w$])(\$?[a-zA-Z_]\w*)/g, (match, pre, ident, offset) => {
        let isActionTarget = scopeKeys.includes(ident) && offset + pre.length === target

        if ((skip.includes(ident) && ! isActionTarget) || /^___\d+___$/.test(ident)) return pre + ident
        if (result[offset + match.length] === ':') return pre + ident

        return pre + '$wire.' + ident
    })

    return result.replace(/___(\d+)___/g, (match, i) => protectedParts[i])
}

function actionTargetOffset(expression) {
    let match = expression.match(/^(\s*)([a-zA-Z_]\w*)/)

    if (! match) return null

    let rest = expression.slice(match[0].length).replace(/^(?:\s+|\/\*[\s\S]*?\*\/|\/\/[^\n]*(?:\n|$))*/, '')

    if (rest !== '' && ! rest.startsWith('(') && ! rest.startsWith(';')) return null

    return match[1].length
}
