import { trigger } from '../hooks.js'
import { interceptMessage, interceptRequest } from './index.js'

/**
 * The `request` and `commit` hooks predate interceptors and remain part of
 * the public hook surface.
 */
export function registerLegacyHooks() {
    interceptRequest(({ request, onFailure, onResponse, onError, onSuccess }) => {
        let respond = []
        let succeed = []
        let fail = []

        trigger('request', {
            url: request.uri,
            options: request.options,
            payload: request.options.body,
            respond: cb => respond.push(cb),
            succeed: cb => succeed.push(cb),
            fail: cb => fail.push(cb),
        })

        onResponse(({ response }) => respond.forEach(cb => cb({ status: response.status, response })))
        onSuccess(({ response, json }) => succeed.forEach(cb => cb({ status: response.status, json })))
        onFailure(() => fail.forEach(cb => cb({ status: 503, content: null, preventDefault: () => {} })))
        onError(({ response, body, preventDefault }) => fail.forEach(cb => cb({ status: response.status, content: body, preventDefault })))
    })

    interceptMessage(({ message, onCancel, onError, onSuccess, onFinish }) => {
        let respond = []
        let succeed = []
        let fail = []

        trigger('commit', {
            component: message.component,
            commit: message.payload,
            respond: cb => respond.push(cb),
            succeed: cb => succeed.push(cb),
            fail: cb => fail.push(cb),
        })

        onFinish(() => respond.forEach(cb => cb()))
        onSuccess(({ payload, onRender }) => {
            onRender(() => succeed.forEach(cb => cb({ snapshot: payload.snapshot, effects: payload.effects })))
        })
        onError(() => fail.forEach(cb => cb()))
        onCancel(() => fail.forEach(cb => cb()))
    })
}
