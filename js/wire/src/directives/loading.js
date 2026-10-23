import { toggleBooleanStateDirective } from './shared.js'
import { directive, getDirectives } from '../directives.js'
import { interceptMessage } from '../request/index.js'

export function registerLoadingDirective() {
    directive('loading', ({ el, directive, component, cleanup }) => {
        let { targets, inverted } = getTargets(el)
        let [delay, abortDelay] = applyDelay(directive)
        let restoreLoadingState = () => toggleBooleanStateDirective(el, directive, false)
        let activeLoadingCount = 0

        let startLoading = () => {
            if (activeLoadingCount === 0) {
                if (directive.modifiers.includes('class')) {
                    let classes = directive.expression.split(' ').filter(String)
                    let classStates = classes.map(className => [className, el.classList.contains(className)])

                    restoreLoadingState = () => classStates.forEach(([className, wasPresent]) => {
                        el.classList.toggle(className, wasPresent)
                    })
                } else if (directive.modifiers.includes('attr')) {
                    let attribute = directive.expression
                    let value = el.getAttribute(attribute)

                    restoreLoadingState = value === null
                        ? () => el.removeAttribute(attribute)
                        : () => el.setAttribute(attribute, value)
                }

                delay(() => toggleBooleanStateDirective(el, directive, true))
            }

            activeLoadingCount++
        }

        let endLoading = () => {
            activeLoadingCount--

            if (activeLoadingCount === 0) abortDelay(restoreLoadingState)
        }

        cleanup(whenTargetsArePartOfRequest(component, targets, inverted, [startLoading, endLoading]))
    })
}

export function applyDelay(directive) {
    if (! directive.modifiers.includes('delay') || directive.modifiers.includes('none')) {
        return [i => i(), i => i()]
    }

    let duration = 200
    let delayModifiers = {
        shortest: 50, shorter: 100, short: 150, default: 200,
        long: 300, longer: 500, longest: 1000,
    }

    Object.keys(delayModifiers).some(key => {
        if (directive.modifiers.includes(key)) {
            duration = delayModifiers[key]

            return true
        }
    })

    let timeout
    let started = false

    return [
        callback => {
            timeout = setTimeout(() => {
                callback()
                started = true
            }, duration)
        },
        async callback => {
            if (started) {
                await callback()
                started = false
            } else {
                clearTimeout(timeout)
            }
        },
    ]
}

function whenTargetsArePartOfRequest(component, targets, inverted, [startLoading, endLoading]) {
    return interceptMessage(({ message, onSend, onSuccess, onFinish }) => {
        if (component !== message.component) return

        if (targets.length === 0 && ! [...message.actions].some(action => action.metadata.island === undefined)) return

        let matches = true
        let cleared = false

        let finishLoading = () => {
            if (! matches || cleared) return

            endLoading()
            cleared = true
        }

        onSend(({ payload }) => {
            if (targets.length > 0 && containsTargets(payload, targets) === inverted) {
                matches = false
            }

            if (! matches) return

            startLoading()
        })

        onSuccess(({ onEffect }) => onEffect(() => finishLoading()))
        onFinish(() => finishLoading())
    })
}

export function containsTargets(payload, targets) {
    let { updates, calls } = payload

    return targets.some(({ target, params }) => {
        if (params) {
            return calls.some(({ method, params: methodParams }) =>
                target === method && params === quickHash(JSON.stringify(methodParams)))
        }

        let hasMatchingUpdate = Object.keys(updates).some(property => {
            if (property.includes('.')) {
                if (property.split('.')[0] === target) return true
            }

            return property === target
        })

        if (hasMatchingUpdate) return true

        return calls.map(i => i.method).includes(target)
    })
}

function getTargets(el) {
    let directives = getDirectives(el)
    let targets = []
    let inverted = false

    if (directives.has('target')) {
        let directive = directives.get('target')

        if (directive.modifiers.includes('except')) inverted = true

        directive.methods.forEach(({ method, params }) => {
            targets.push({
                target: method,
                params: params && params.length > 0 ? quickHash(JSON.stringify(params)) : undefined,
            })
        })
    } else {
        let nonAction = ['init', 'dirty', 'offline', 'navigate', 'target', 'loading', 'poll', 'ignore', 'key', 'id', 'cloak', 'show', 'text', 'bind', 'ref', 'transition', 'confirm']

        directives.all()
            .filter(i => ! nonAction.includes(i.value))
            .map(i => i.expression.split('(')[0])
            .forEach(target => targets.push({ target }))
    }

    return { targets, inverted }
}

function quickHash(subject) {
    return btoa(encodeURIComponent(subject))
}
