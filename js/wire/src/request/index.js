import Alpine from 'alpinejs'
import { getCsrfToken, getUpdateUri } from '../utils.js'
import { triggerAsync } from '../hooks.js'
import { showHtmlModal } from '../modal.js'
import { browser } from '../browser.js'
import { InterceptorRegistry } from './interceptor.js'
import { MessageBus, scopeOf } from './bus.js'
import { MessageRequest } from './request.js'
import Message from './message.js'
import Action from './action.js'

let nextOrigin = null
let nextMetadata = {}
let nextInterceptors = []
let actionInterceptors = []
let partitionInterceptors = []
let interceptors = new InterceptorRegistry()
let bus = new MessageBus()
let sessionExpired = false

export function sessionIsExpired() { return sessionExpired }

export function messageBus() { return bus }

export function setNextActionOrigin(origin) { nextOrigin = origin }

export function setNextActionMetadata(metadata) { nextMetadata = metadata }

export function setNextActionInterceptor(callback) { nextInterceptors.push(callback) }

function removable(list, callback) {
    list.push(callback)

    return () => {
        let index = list.indexOf(callback)

        if (index !== -1) list.splice(index, 1)
    }
}

export function interceptAction(callback) { return removable(actionInterceptors, callback) }

export function interceptMessage(callback) { return interceptors.addMessageInterceptor(callback) }

export function interceptRequest(callback) { return interceptors.addRequestInterceptor(callback) }

export function interceptPartition(callback) { return removable(partitionInterceptors, callback) }

function scoped(nameOrCallback, maybeCallback) {
    return typeof nameOrCallback === 'string' ? [nameOrCallback, maybeCallback] : [null, nameOrCallback]
}

function hasAction(message, name) {
    return ! name || [...message.actions].some(action => action.name === name)
}

export function interceptComponentAction(component, nameOrCallback, maybeCallback) {
    let [name, callback] = scoped(nameOrCallback, maybeCallback)

    return interceptAction(params => {
        if (params.action.component !== component) return
        if (name && params.action.name !== name) return

        callback(params)
    })
}

export function interceptComponentMessage(component, nameOrCallback, maybeCallback) {
    let [name, callback] = scoped(nameOrCallback, maybeCallback)

    return interceptors.addComponentMessageInterceptor(component, params => {
        if (hasAction(params.message, name)) callback(params)
    })
}

export function interceptComponentRequest(component, nameOrCallback, maybeCallback) {
    let [name, callback] = scoped(nameOrCallback, maybeCallback)

    return interceptRequest(params => {
        let matches = [...params.request.messages].some(message => message.component === component && hasAction(message, name))

        if (matches) callback(params)
    })
}

interceptMessage(({ message, onFinish }) => {
    bus.activeMessages.add(message)

    onFinish(() => bus.activeMessages.delete(message))
})

export function fireAction(component, method, params = [], metadata = {}) {
    let action = constructAction(component, method, params, metadata)

    ;[...actionInterceptors].forEach(callback => callback(action.hooks()))

    if (action.isCancelled() || action.isDeferred()) return action.promise

    return fireActionInstance(action)
}

export function constructAction(component, method, params = [], metadata = {}) {
    let action = new Action(component, method, params, { ...metadata, ...nextMetadata }, nextOrigin)

    action.fireUsing = fireActionInstance

    nextInterceptors.forEach(callback => action.addInterceptor(callback))

    nextOrigin = null
    nextMetadata = {}
    nextInterceptors = []

    return action
}

function fireActionInstance(action) {
    let message = createOrAddToPendingMessage(action)

    bus.buffer(message, sendMessages)

    return action.promise
}

export function createOrAddToPendingMessage(action) {
    let message = bus.findPendingMessageFor(action) || new Message(action.component)

    message.addAction(action)

    bus.pendingMessages.add(message)

    return message
}

function compileRequest(requests, messages) {
    let existing = [...requests].find(request => messages.some(message => request.messages.has(message)))

    if (existing) {
        messages.forEach(message => existing.addMessage(message))

        return existing
    }

    let request = new MessageRequest()

    messages.forEach(message => request.addMessage(message))

    requests.add(request)

    return request
}

function sendMessages() {
    let requests = new Set()

    ;[...bus.pendingMessages].forEach(message => {
        ;[...partitionInterceptors].forEach(callback => callback({
            message,
            compileRequest: messages => compileRequest(requests, messages),
        }))
    })

    bus.takePendingMessages().forEach(message => {
        if ([...requests].some(request => request.messages.has(message))) return

        // One request carries at most one message per component, and isolated requests take no passengers...
        let request = [...requests].find(candidate => ! candidate.isolated && ! [...candidate.messages].some(m => m.component === message.component))

        if (request) {
            request.addMessage(message)
        } else {
            compileRequest(requests, [message])
        }
    })

    requests.forEach(request => {
        request.messages.forEach(message => {
            message.snapshot = message.component.getEncodedSnapshotWithLatestChildrenMergedIn()
            message.updates = message.component.getUpdates()
            message.calls = [...message.actions].map(action => ({
                method: action.name,
                params: action.params,
                metadata: action.metadata,
            }))
            message.payload = { snapshot: message.snapshot, updates: message.updates, calls: message.calls }
            message.scope = scopeOf(message.component)
        })

        request.uri = getUpdateUri()

        Object.defineProperty(request, 'payload', {
            get: () => ({ _token: getCsrfToken(), components: [...request.messages].map(message => message.payload) }),
        })

        let options

        Object.defineProperty(request, 'options', {
            get: () => options ??= {
                method: 'POST',
                body: JSON.stringify(request.payload),
                headers: { 'Content-type': 'application/json', 'X-Brew': '1' },
                signal: request.controller.signal,
            },
        })

        request.initInterceptors(interceptors)

        if (request.hasAllCancelledMessages()) request.cancel()

        sendRequest(request)
    })
}

function preventable() {
    let state = { prevented: false }

    state.preventDefault = () => state.prevented = true

    return state
}

function handleError(request, response, body) {
    let error = preventable()

    request.invokeOnError({ response, body, preventDefault: error.preventDefault })

    if (error.prevented) return

    if (response.status === 419) {
        if (sessionExpired) return

        sessionExpired = true

        if (confirm('This page has expired.\nWould you like to refresh the page?')) browser.reload()

        return
    }

    if (response.ok && body.trim() === '') {
        if (confirm('The server returned an empty response.\nWould you like to refresh the page?')) browser.reload()

        return
    }

    showHtmlModal(body)
}

async function sendRequest(request) {
    let response

    try {
        if (request.isCancelled()) return

        let responsePromise = fetch(request.uri, request.options)

        request.invokeOnSend({ responsePromise })

        response = await responsePromise
    } catch (error) {
        if (request.isCancelled()) return

        request.invokeOnFailure({ error })
        request.each('onFinish')

        return
    }

    request.each('onResponse', { response })

    let body = await response.text()

    if (request.isCancelled()) return

    request.each('onParsed', { response, body })

    let json = null

    if (response.ok && ! response.redirected && body.trim() !== '') {
        try { json = JSON.parse(body) } catch (e) { console.error(e) }
    }

    if (response.redirected && response.ok) {
        let redirect = preventable()

        request.each('onRedirect', { url: response.url, preventDefault: redirect.preventDefault })

        if (! redirect.prevented) browser.visit(response.url)

        // Settled so a prevented redirect doesn't leave the component's queue blocked...
        request.messages.forEach(message => {
            message.resolveActionPromises([], {})
            message.each('onFinish')
        })
    } else if (json === null) {
        handleError(request, response, body)
    } else {
        request.each('onSuccess', { response, body, json })

        await triggerAsync('payload.intercept', json)

        applyResponse(request, json)
    }

    request.each('onFinish')
}

function applyResponse(request, json) {
    request.messages.forEach(message => {
        json.components.forEach(payload => {
            if (message.isCancelled()) return

            if (payload.skip) {
                if (payload.id !== message.component.id || discardIfSuperseded(message)) return

                supersedeOlderMessages(message)

                message.responsePayload = payload
                message.skipped = true
                message.invokeOnSkipped()
                message.resolveActionPromises([], [])
                message.each('onFinish')

                return
            }

            let { snapshot: encoded, effects } = payload
            let snapshot = JSON.parse(encoded)

            if (snapshot.memo.id !== message.component.id || discardIfSuperseded(message)) return

            supersedeOlderMessages(message)

            message.responsePayload = { snapshot, effects }
            message.invokeOnSuccess()

            if (message.isCancelled()) return

            let morphed = false

            Alpine.transaction(async () => {
                message.component.mergeNewSnapshot(encoded, effects, message.updates)
                message.each('onSync')

                if (message.isCancelled()) return

                message.component.processEffects(effects, request)
                message.each('onEffect')

                if (message.isCancelled()) return

                await message.invokeOnMorph()

                morphed = true

                message.each('onMorphed')
            }).finally(() => {
                if (! message.isCancelled()) {
                    message.resolveActionPromises(message.pendingReturns, message.pendingReturnsMeta)
                    message.each('onFinish')
                }

                if (! morphed) return

                requestAnimationFrame(() => {
                    if (! message.isCancelled()) message.each('onRender')
                })
            })
        })
    })
}

function discardIfSuperseded(message) {
    if (! message.superseded) return false

    message.resolveActionPromises([], [])
    message.each('onFinish')

    return true
}

function supersedeOlderMessages(message) {
    message.actions.forEach(action => action.supersedes.forEach(older => older.superseded = true))
}
