import { WeakBag } from '../utils.js'

const noop = () => {}

export class MessageInterceptor {
    onSend = noop
    onCancel = noop
    onFailure = noop
    onError = noop
    onSuccess = noop
    onSkipped = noop
    onFinish = noop
    onSync = noop
    onEffect = noop
    onMorph = async () => {}
    onMorphed = noop
    onRender = noop

    constructor(message, callback) {
        callback({
            message,
            cancel: () => {
                // Not yet attached means message.cancel() won't reach this interceptor...
                if (! message.interceptors.includes(this)) this.onCancel()

                message.cancel()
            },
            onSend: cb => this.onSend = cb,
            onCancel: cb => this.onCancel = cb,
            onFailure: cb => this.onFailure = cb,
            onError: cb => this.onError = cb,
            onSuccess: cb => this.onSuccess = cb,
            onSkipped: cb => this.onSkipped = cb,
            onFinish: cb => this.onFinish = cb,
        })
    }
}

export class RequestInterceptor {
    onSend = noop
    onCancel = noop
    onFailure = noop
    onResponse = noop
    onParsed = noop
    onError = noop
    onRedirect = noop
    onSuccess = noop
    onFinish = noop

    constructor(request, callback) {
        callback({
            request,
            onSend: cb => this.onSend = cb,
            onCancel: cb => this.onCancel = cb,
            onFailure: cb => this.onFailure = cb,
            onResponse: cb => this.onResponse = cb,
            onParsed: cb => this.onParsed = cb,
            onError: cb => this.onError = cb,
            onRedirect: cb => this.onRedirect = cb,
            onSuccess: cb => this.onSuccess = cb,
            onFinish: cb => this.onFinish = cb,
        })
    }
}

function remover(list, callback) {
    return () => {
        let index = list.indexOf(callback)

        if (index !== -1) list.splice(index, 1)
    }
}

export class InterceptorRegistry {
    messageCallbacks = []
    componentMessageCallbacks = new WeakBag()
    requestCallbacks = []

    addComponentMessageInterceptor(component, callback) {
        this.componentMessageCallbacks.add(component, callback)

        return remover(this.componentMessageCallbacks.get(component), callback)
    }

    addMessageInterceptor(callback) {
        this.messageCallbacks.push(callback)

        return remover(this.messageCallbacks, callback)
    }

    addRequestInterceptor(callback) {
        this.requestCallbacks.push(callback)

        return remover(this.requestCallbacks, callback)
    }

    messageInterceptorsFor(message) {
        return [...this.componentMessageCallbacks.get(message.component), ...this.messageCallbacks]
            .map(callback => new MessageInterceptor(message, callback))
    }

    requestInterceptorsFor(request) {
        return [...this.requestCallbacks].map(callback => new RequestInterceptor(request, callback))
    }
}
