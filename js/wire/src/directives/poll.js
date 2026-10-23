import { directive, getDirectives } from '../directives.js'
import { setNextActionMetadata, setNextActionOrigin, sessionIsExpired } from '../request/index.js'
import { evaluateActionExpression } from '../evaluator.js'
import { brewIsOffline } from './offline.js'

export function registerPollDirective() {
    directive('poll', ({ el, directive }) => {
        let interval = extractDurationFrom(directive.modifiers, 2000)

        let { start, pauseWhile, throttleWhile, stopWhen } = poll(() => {
            triggerComponentRequest(el, directive)
        }, interval)

        start()

        throttleWhile(() => theTabIsInTheBackground() && theDirectiveIsMissingKeepAlive(directive))
        pauseWhile(() => theDirectiveHasVisible(directive) && theElementIsNotInTheViewport(el))
        pauseWhile(() => theDirectiveIsOffTheElement(el))
        pauseWhile(() => brewIsOffline())
        pauseWhile(() => sessionIsExpired())
        stopWhen(() => theElementIsDisconnected(el))
    })
}

function triggerComponentRequest(el, directive) {
    setNextActionOrigin({ el, directive, targetEl: null })
    setNextActionMetadata({ type: 'poll' })

    evaluateActionExpression(el, directive.expression || '$refresh')
}

export function poll(callback, interval = 2000) {
    let pauseConditions = []
    let throttleConditions = []
    let stopConditions = []

    return {
        start() {
            let clear = synchronizedInterval(interval, () => {
                if (stopConditions.some(i => i())) return clear()
                if (pauseConditions.some(i => i())) return
                if (throttleConditions.some(i => i()) && shouldThrottlePoll()) return

                callback()
            })
        },
        pauseWhile(condition) { pauseConditions.push(condition) },
        throttleWhile(condition) { throttleConditions.push(condition) },
        stopWhen(condition) { stopConditions.push(condition) },
    }
}

let clocks = {}

function synchronizedInterval(ms, callback) {
    if (! clocks[ms]) {
        clocks[ms] = {
            timer: setInterval(() => clocks[ms].callbacks.forEach(i => i()), ms),
            callbacks: new Set(),
        }
    }

    clocks[ms].callbacks.add(callback)

    return () => {
        clocks[ms].callbacks.delete(callback)

        if (clocks[ms].callbacks.size === 0) {
            clearInterval(clocks[ms].timer)
            delete clocks[ms]
        }
    }
}

let inBackground = false

document.addEventListener('visibilitychange', () => { inBackground = document.hidden }, false)

export function theTabIsInTheBackground() { return inBackground }

export function theDirectiveIsOffTheElement(el) { return ! getDirectives(el).has('poll') }

export function theDirectiveIsMissingKeepAlive(directive) { return ! directive.modifiers.includes('keep-alive') }

export function theDirectiveHasVisible(directive) { return directive.modifiers.includes('visible') }

export function theElementIsNotInTheViewport(el) {
    let bounding = el.getBoundingClientRect()

    return ! (
        bounding.top < (window.innerHeight || document.documentElement.clientHeight) &&
        bounding.left < (window.innerWidth || document.documentElement.clientWidth) &&
        bounding.bottom > 0 &&
        bounding.right > 0
    )
}

/** Test helper to flip the document.hidden-driven background flag. */
export function __setPollBackgroundForTests(value) { inBackground = value }

export function theElementIsDisconnected(el) { return el.isConnected === false }

/** Separated so tests can force both throttle branches. */
export function shouldThrottlePoll(random = Math.random) {
    return random() < 0.95
}

export function extractDurationFrom(modifiers, defaultDuration) {
    let ms = modifiers.find(mod => mod.match(/([0-9]+)ms/))
    let s = modifiers.find(mod => mod.match(/([0-9]+)s/))
    let m = modifiers.find(mod => mod.match(/([0-9]+)m/))

    if (ms) return Number(ms.replace('ms', ''))
    if (s) return Number(s.replace('s', '')) * 1000
    if (m) return Number(m.replace('m', '')) * 60 * 1000

    return defaultDuration
}
