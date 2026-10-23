import Alpine from 'alpinejs'
import { on } from './hooks.js'

let customDirectiveNames = new Set()

export function matchesForBrewDirective(attributeName) {
    return attributeName.startsWith('wire:')
}

export function extractDirective(el, name) {
    let [value, ...modifiers] = name.slice('wire:'.length).split('.')

    return new Directive(value, modifiers, name, el)
}

/**
 * Register a custom `wire:<name>` directive. Registering the same name twice
 * is ignored so feature modules can be imported more than once.
 */
export function directive(name, callback) {
    if (customDirectiveNames.has(name)) return

    customDirectiveNames.add(name)

    on('directive.init', ({ el, component, directive, cleanup }) => {
        if (directive.value === name) callback({ el, directive, component, $wire: component.$wire, cleanup })
    })
}

export function customDirectiveHasBeenRegistered(name) {
    return customDirectiveNames.has(name)
}

/** All `wire:*` directives on an element. */
export function getDirectives(el) {
    return new DirectiveManager(el)
}

class DirectiveManager {
    constructor(el) {
        this.el = el
        this.directives = this.extractTypeModifiersAndValue()
    }

    all() { return this.directives }

    has(value) { return this.directives.map(directive => directive.value).includes(value) }

    missing(value) { return ! this.has(value) }

    get(value) { return this.directives.find(directive => directive.value === value) }

    extractTypeModifiersAndValue() {
        return Array.from(this.el.getAttributeNames()
            .filter(name => matchesForBrewDirective(name))
            .map(name => extractDirective(this.el, name)))
    }
}

export class Directive {
    constructor(value, modifiers, rawName, el) {
        this.rawName = this.raw = rawName
        this.el = el
        this.value = value
        this.modifiers = modifiers
        this.expression = el.getAttribute(rawName)
    }

    get method() { return this.methods[0].method }

    get params() { return this.methods[0].params }

    get methods() {
        return splitMethods(this.expression).map(({ method, paramString }) => ({ method, params: evaluateParams(paramString) }))
    }
}

function evaluateParams(paramString) {
    if (paramString === '') return []

    try {
        return Alpine.evaluateRaw(document, `__brewArgs(${paramString})`, { scope: { __brewArgs: (...args) => args } })
    } catch (error) {
        console.warn('Failed to parse parameters:', paramString, error)

        return []
    }
}

/**
 * Split `a(1, 'x,y'), b` on top-level commas, respecting nested parens and
 * quoted strings.
 */
function splitMethods(expression) {
    let methods = []
    let current = ''
    let depth = 0
    let quote = null
    let source = expression.trim()

    for (let i = 0; i < source.length; i++) {
        let char = source[i]

        if (quote) {
            if (char === quote && source[i - 1] !== '\\') quote = null
        } else if (char === '"' || char === "'") {
            quote = char
        } else if (char === '(') {
            depth++
        } else if (char === ')') {
            depth--
        } else if (char === ',' && depth === 0) {
            methods.push(parseCall(current.trim()))
            current = ''

            continue
        }

        current += char
    }

    if (current.trim() !== '') methods.push(parseCall(current.trim()))

    return methods
}

function parseCall(call) {
    let match = call.match(/^([^(]+)\(/)

    if (! match) return { method: call, paramString: '' }

    return { method: match[1].trim(), paramString: call.slice(match[0].length, call.lastIndexOf(')')).trim() }
}
