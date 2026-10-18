import { MessageInterceptor } from './interceptor.js'

const ABORTED = { status: null, body: null, json: null, errors: null }

export default class Message {
    actions = new Set()
    snapshot = null
    updates = null
    calls = null
    payload = null
    responsePayload = null
    pendingReturns = []
    pendingReturnsMeta = {}
    interceptors = []
    cancelled = false
    skipped = false
    superseded = false
    request = null
    scope = null

    constructor(component) {
        this.component = component
    }

    addAction(action) {
        let twin = [...this.actions].find(existing => existing.fingerprint === action.fingerprint)

        if (twin) {
            twin.addSquashedAction(action)

            return
        }

        action.message = this
        this.actions.add(action)
    }

    removeAction(action) {
        this.actions.delete(action)
        action.message = null

        if (this.actions.size === 0) this.cancel()
    }

    addInterceptor(callback) {
        this.interceptors.push(new MessageInterceptor(this, callback))
    }

    cancel() {
        if (this.cancelled) return

        this.cancelled = true

        this.invokeOnCancel()

        if (this.request?.hasAllCancelledMessages()) this.request.cancel()
    }

    isCancelled() { return this.cancelled }

    isAsync() {
        return [...this.actions].every(action => action.isAsync())
    }

    each(name, ...args) {
        this.interceptors.forEach(interceptor => interceptor[name](...args))
    }

    eachAction(name, ...args) {
        this.actions.forEach(action => action.invoke(name, ...args))
    }

    settleRejected(error) {
        this.actions.forEach(action => action.reject(error))
        this.eachAction('Finish')
        this.each('onFinish')
    }

    invokeOnSend() {
        this.each('onSend', { payload: this.payload })

        ;[...this.actions].forEach((action, index) => action.invoke('Send', { call: this.calls[index] }))
    }

    invokeOnCancel() {
        this.each('onCancel')
        this.settleRejected(ABORTED)
    }

    invokeOnFailure(error) {
        this.each('onFailure', { error })
        this.eachAction('Failure', { error })
        this.settleRejected(ABORTED)
    }

    invokeOnError({ response, body, preventDefault }) {
        this.each('onError', { response, body, preventDefault })
        this.eachAction('Error', { response, body, preventDefault })

        let json = null

        try { json = JSON.parse(body) } catch (e) {}

        this.settleRejected({ status: response.status, body, json, errors: null })
    }

    invokeOnSuccess() {
        this.interceptors.forEach(interceptor => {
            interceptor.onSuccess({
                payload: this.responsePayload,
                onSync: cb => interceptor.onSync = cb,
                onEffect: cb => interceptor.onEffect = cb,
                onMorph: cb => interceptor.onMorph = cb,
                onMorphed: cb => interceptor.onMorphed = cb,
                onRender: cb => interceptor.onRender = cb,
            })
        })

        let effects = this.responsePayload.effects

        this.pendingReturns = effects.returns || []
        this.pendingReturnsMeta = effects.returnsMeta || {}
    }

    invokeOnSkipped() {
        this.each('onSkipped')
    }

    async invokeOnMorph() {
        for (let interceptor of this.interceptors) {
            await interceptor.onMorph()
        }
    }

    resolveActionPromises(returns, returnsMeta) {
        ;[...this.actions].forEach((action, index) => {
            let meta = returnsMeta[index]

            if (meta?.errors || meta?.status) {
                action.reject({ status: meta.errors ? 422 : meta.status, body: null, json: null, errors: meta.errors || null })
            } else {
                action.invoke('Success', returns[index])
                action.resolve(returns[index])
            }

            action.invoke('Finish')
        })
    }
}
