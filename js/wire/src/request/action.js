const LIFECYCLE = ['Send', 'Cancel', 'Success', 'Error', 'Failure', 'Finish']

export default class Action {
    squashedActions = new Set()
    supersedes = new Set()
    message = null
    cancelled = false
    deferred = false
    callbacks = Object.fromEntries(LIFECYCLE.map(name => [name, []]))

    constructor(component, name, params = [], metadata = {}, origin = null) {
        this.component = component
        this.name = name
        this.params = params
        this.metadata = metadata
        this.origin = origin

        this.promise = new Promise((resolve, reject) => {
            this.resolution = { resolve, reject }
        })

        // Failures already surface through the error modal and interceptors, so a
        // discarded promise must not also raise an unhandled rejection...
        this.promise.catch(() => {})
    }

    hooks() {
        return {
            action: this,
            onSend: cb => this.callbacks.Send.push(cb),
            onCancel: cb => this.callbacks.Cancel.push(cb),
            onSuccess: cb => this.callbacks.Success.push(cb),
            onError: cb => this.callbacks.Error.push(cb),
            onFailure: cb => this.callbacks.Failure.push(cb),
            onFinish: cb => this.callbacks.Finish.push(cb),
        }
    }

    addInterceptor(callback) {
        callback(this.hooks())
    }

    invoke(name, ...args) {
        this.callbacks[name].forEach(cb => cb(...args))
        this.squashedActions.forEach(action => action.invoke(name, ...args))
    }

    cancel() {
        if (this.cancelled) return

        this.cancelled = true

        this.invoke('Cancel')
        this.invoke('Finish')
        this.reject({ status: null, body: null, json: null, errors: null })

        this.message?.removeAction(this)
    }

    isCancelled() { return this.cancelled }

    defer() { this.deferred = true }

    isDeferred() { return this.deferred }

    fire() { this.fireUsing(this) }

    get fingerprint() {
        return JSON.stringify([this.component.id, this.name, this.params, this.metadata])
    }

    isAsync() {
        let marked = this.component.snapshot.memo.async || []

        return marked.includes(this.name)
            || !! this.origin?.directive?.modifiers.includes('async')
            || !! this.metadata.async
    }

    addSquashedAction(action) {
        this.squashedActions.add(action)
    }

    resolve(value) {
        this.squashedActions.forEach(action => action.resolution.resolve(value))
        this.resolution.resolve(value)
    }

    reject(error) {
        this.squashedActions.forEach(action => action.resolution.reject(error))
        this.resolution.reject(error)
    }
}
