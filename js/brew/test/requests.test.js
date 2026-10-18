import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import { bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, echoServer, manualFetch, reply, settle, tick, jsonResponse, response } from './helpers.js'
import { browser } from '../src/browser.js'

let Brew

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(stubDialog)
afterEach(cleanPage)

async function component(data = { count: 1 }, memo = {}, inner = '') {
    let snap = snapshot(data, memo)

    await mount(rootHtml(snap, inner))

    return { snap, wire: Brew.find(snap.memo.id) }
}

function okFor(call, effects = {}) {
    return jsonResponse({ components: call.body.components.map(c => reply(JSON.parse(c.snapshot), effects)), assets: [] })
}

describe('return values', () => {
    it('resolves returns and rejects per-call errors and statuses', async () => {
        vi.stubGlobal('fetch', echoServer(null, () => ({
            returns: [1, null, null],
            returnsMeta: { 1: { errors: { name: ['required'] } }, 2: { status: 403 } },
        })))
        let { wire } = await component()

        let [one, two, three] = await Promise.allSettled([wire.a(), wire.b(), wire.c()])

        expect(one).toEqual({ status: 'fulfilled', value: 1 })
        expect(two.reason).toEqual({ status: 422, body: null, json: null, errors: { name: ['required'] } })
        expect(three.reason).toEqual({ status: 403, body: null, json: null, errors: null })
    })

    it('resolves undefined when the server returns nothing', async () => {
        vi.stubGlobal('fetch', echoServer(null))
        let { wire } = await component()

        await expect(wire.save()).resolves.toBeUndefined()
    })
})

describe('interactions while a request is in flight', () => {
    it('marks renderless actions from the .renderless modifier', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        await component({}, {}, '<button wire:click.renderless="track">t</button>')

        document.querySelector('button').click()
        await settle()

        expect(JSON.parse(fetch.mock.calls[0][1].body).components[0].calls[0].metadata).toEqual({ renderless: true })
    })

    it('defers new actions until the in-flight message finishes', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { wire } = await component()

        let first = wire.first()
        await settle()
        let second = wire.second()
        await settle()

        expect(calls).toHaveLength(1)

        calls[0].resolve(okFor(calls[0]))
        await first
        await settle()

        expect(calls).toHaveLength(2)
        expect(calls[1].body.components[0].calls[0].method).toBe('second')

        calls[1].resolve(okFor(calls[1]))
        await second
    })

    it('lets async actions through while a message is in flight', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { wire } = await component({}, {}, '<button wire:click.async="ping">p</button>')

        wire.slow()
        await settle()
        document.querySelector('button').click()
        await settle()
        Brew.fireAction(wire, 'other', [], { async: true })
        await settle()

        expect(calls).toHaveLength(3)
        calls.forEach(call => call.resolve(okFor(call)))
        await settle()

        wire.later()
        await settle()
        expect(calls).toHaveLength(4)
        calls[3].resolve(okFor(calls[3]))
    })

    it('lets new actions through behind an async-only message', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { wire } = await component({}, { async: ['track'] })

        wire.track()
        await settle()
        wire.save()
        await settle()

        expect(calls).toHaveLength(2)
        calls.forEach(call => call.resolve(okFor(call)))
    })

    it('drops new polls behind any request and cancels in-flight polls', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { wire } = await component()

        let save = wire.save()
        await settle()
        let poll = Brew.fireAction(wire, '$refresh', [], { type: 'poll' })

        await expect(poll).rejects.toEqual({ status: null, body: null, json: null, errors: null })
        calls[0].resolve(okFor(calls[0]))
        await save
        await settle()

        let inflightPoll = Brew.fireAction(wire, '$refresh', [], { type: 'poll' })
        await settle()
        let action = wire.act()

        await expect(inflightPoll).rejects.toBeTruthy()
        await settle()
        expect(calls).toHaveLength(3)
        calls[2].resolve(okFor(calls[2]))
        await action
    })

    it('runs overlapping model.live requests in parallel and drops superseded responses', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { wire } = await component({ q: '' })

        wire.q = 'a'
        let first = Brew.fireAction(wire, '$commit', [], { type: 'model.live' })
        await settle()
        wire.q = 'ab'
        let second = Brew.fireAction(wire, '$commit', [], { type: 'model.live' })
        await settle()

        expect(calls).toHaveLength(2)

        let snapFor = (call, q) => ({ ...JSON.parse(call.body.components[0].snapshot), data: { q } })
        calls[1].resolve(jsonResponse({ components: [reply(snapFor(calls[1], 'ab'))] }))
        await second
        calls[0].resolve(jsonResponse({ components: [reply(snapFor(calls[0], 'a'))] }))
        await first
        await settle()

        expect(wire.q).toBe('ab')
    })

    it('discards superseded skip payloads too', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { wire, snap } = await component({ q: '' })

        Brew.fireAction(wire, '$commit', [], { type: 'model.live' })
        await settle()
        Brew.fireAction(wire, '$commit', [], { type: 'model.live' })
        await settle()

        calls[1].resolve(jsonResponse({ components: [{ skip: true, id: snap.memo.id }] }))
        await settle()
        calls[0].resolve(jsonResponse({ components: [{ skip: true, id: snap.memo.id }] }))
        await settle()
    })

    it('serializes model.live requests when bound children share the snapshot', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let child = snapshot({ v: 1 }, { props: ['v'] })
        let parent = snapshot({ v: 1 })
        await mount(rootHtml(parent, rootHtml(child, '')))
        let wire = Brew.find(parent.memo.id)

        Brew.fireAction(wire, '$commit', [], { type: 'model.live' })
        await settle()
        Brew.fireAction(wire, '$commit', [], { type: 'model.live' })
        await settle()
        expect(calls).toHaveLength(1)

        let childOnly = Brew.find(child.memo.id)
        calls[0].resolve(okFor(calls[0]))
        await settle()
        expect(calls).toHaveLength(2)

        Brew.fireAction(childOnly, '$commit', [], { type: 'model.live' })
        await settle()
        calls.slice(1).forEach(call => call.resolve(okFor(call)))
        await settle()
    })
})

describe('server errors', () => {
    it('shows the error page in a modal and rejects with the response', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => response('<h1>Server Error</h1>', { status: 500 })))
        let { wire } = await component()

        let error = await wire.save().catch(e => e)

        expect(error).toEqual({ status: 500, body: '<h1>Server Error</h1>', json: null, errors: null })
        expect(document.getElementById('brew-error')).not.toBeNull()
    })

    it('parses JSON error bodies for the rejection', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => response('{"message":"nope"}', { status: 422 })))
        let { wire } = await component()

        expect((await wire.save().catch(e => e)).json).toEqual({ message: 'nope' })
    })

    it('lets request interceptors prevent the default error handling', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => response('bad', { status: 500 })))
        let off = Brew.interceptRequest(({ onError }) => onError(({ preventDefault }) => preventDefault()))
        let { wire } = await component()

        await wire.save().catch(() => {})

        expect(document.getElementById('brew-error')).toBeNull()
        off()
    })

    it('asks to reload once when the session expired', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => response('expired', { status: 419 })))
        let confirm = vi.fn(() => true)
        vi.stubGlobal('confirm', confirm)
        let reload = vi.spyOn(browser, 'reload').mockImplementation(() => {})
        let { wire } = await component()

        await wire.save().catch(() => {})
        await settle()
        await wire.again().catch(() => {})

        expect(confirm).toHaveBeenCalledTimes(1)
        expect(confirm.mock.calls[0][0]).toBe('This page has expired.\nWould you like to refresh the page?')
        expect(reload).toHaveBeenCalledTimes(1)
    })

    it('asks to reload on an empty response', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => response('  ')))
        let confirm = vi.fn(() => true)
        vi.stubGlobal('confirm', confirm)
        let reload = vi.spyOn(browser, 'reload').mockImplementation(() => {})
        let { wire } = await component()

        await wire.save().catch(() => {})

        expect(confirm.mock.calls[0][0]).toBe('The server returned an empty response.\nWould you like to refresh the page?')
        expect(reload).toHaveBeenCalled()
    })

    it('treats an unparseable success body as an error page', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => response('<html>not json</html>')))
        let logged = vi.spyOn(console, 'error').mockImplementation(() => {})
        let { wire } = await component()

        await wire.save().catch(() => {})

        expect(logged).toHaveBeenCalled()
        expect(document.getElementById('brew-error')).not.toBeNull()
    })

    it('reports network failures to interceptors and legacy hooks', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
        let failures = []
        let legacy = []
        let off = Brew.interceptRequest(({ onFailure }) => onFailure(({ error }) => failures.push(error.message)))
        let offHook = Brew.hook('request', ({ fail }) => fail(details => legacy.push(details.status)))
        let { wire } = await component()

        let error = await wire.save().catch(e => e)

        expect(error).toEqual({ status: null, body: null, json: null, errors: null })
        expect(failures).toEqual(['offline'])
        expect(legacy).toEqual([503])
        off()
        offHook()
    })

    it('follows redirects unless prevented', async () => {
        let visit = vi.spyOn(browser, 'visit').mockImplementation(() => {})
        vi.stubGlobal('fetch', vi.fn(async () => response('<html></html>', { redirected: true, url: 'http://localhost/login' })))
        let { wire } = await component()

        await wire.save()
        expect(visit).toHaveBeenCalledWith('http://localhost/login')

        let off = Brew.interceptRequest(({ onRedirect }) => onRedirect(({ preventDefault }) => preventDefault()))
        await wire.again()
        expect(visit).toHaveBeenCalledTimes(1)
        off()
    })
})

describe('cancellation', () => {
    it('cancels a message from an interceptor before it is sent', async () => {
        let fetch = vi.fn()
        vi.stubGlobal('fetch', fetch)
        let cancelled = []
        let off = Brew.interceptMessage(({ cancel, onCancel }) => {
            onCancel(() => cancelled.push('message'))
            cancel()
        })
        let { wire } = await component()

        await expect(wire.save()).rejects.toEqual({ status: null, body: null, json: null, errors: null })
        await settle()

        expect(fetch).not.toHaveBeenCalled()
        expect(cancelled).toContain('message')
        off()
    })

    it('cancels actions from action interceptors', async () => {
        let fetch = vi.fn()
        vi.stubGlobal('fetch', fetch)
        let events = []
        let off = Brew.interceptAction(({ action, onCancel, onFinish }) => {
            onCancel(() => events.push('cancel'))
            onFinish(() => events.push('finish'))
            action.cancel()
            action.cancel()
        })
        let { wire } = await component()

        await wire.save().catch(() => {})
        await settle()

        expect(events).toEqual(['cancel', 'finish'])
        expect(fetch).not.toHaveBeenCalled()
        off()
    })

    it('drops one action from a bundled message and keeps the rest', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let off = Brew.interceptAction(({ action }) => {
            if (action.name === 'drop') queueMicrotask(() => action.cancel())
        })
        let { wire } = await component()

        wire.keep()
        wire.drop().catch(() => {})
        await settle()

        expect(JSON.parse(fetch.mock.calls[0][1].body).components[0].calls.map(c => c.method)).toEqual(['keep'])
        off()
    })

    it('stops quietly when the request is cancelled mid-flight', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let requests = []
        let off = Brew.interceptRequest(({ request }) => requests.push(request))
        let { wire } = await component()

        wire.a().catch(() => {})
        await settle()
        requests[0].cancel()
        requests[0].cancel()
        calls[0].resolve(okFor(calls[0]))
        await settle()

        wire.b().catch(() => {})
        await settle()
        requests[1].cancel()
        calls[1].reject(new DOMException('aborted', 'AbortError'))
        await settle()

        off()
    })

    it('cancels an attached interceptor through the message', async () => {
        vi.stubGlobal('fetch', echoServer(null))
        let off = Brew.interceptMessage(({ cancel, onSend }) => onSend(() => cancel()))
        let { wire } = await component()

        await expect(wire.save()).rejects.toBeTruthy()
        off()
    })
})

describe('interceptor hooks', () => {
    it('reports action, message, and request lifecycles on success', async () => {
        vi.stubGlobal('fetch', echoServer(null, () => ({ returns: ['ok'] })))
        let events = []
        let offAction = Brew.interceptAction(({ onSend, onSuccess, onFinish }) => {
            onSend(({ call }) => events.push('action:send:' + call.method))
            onSuccess(value => events.push('action:success:' + value))
            onFinish(() => events.push('action:finish'))
        })
        let offRequest = Brew.interceptRequest(({ onSend, onResponse, onParsed, onSuccess, onFinish }) => {
            onSend(({ responsePromise }) => events.push('request:send:' + (responsePromise instanceof Promise)))
            onResponse(({ response }) => events.push('request:response:' + response.status))
            onParsed(({ body }) => events.push('request:parsed:' + typeof body))
            onSuccess(({ json }) => events.push('request:success:' + json.components.length))
            onFinish(() => events.push('request:finish'))
        })
        let { wire } = await component()

        await wire.save()
        await settle()

        expect(events).toEqual([
            'request:send:true', 'action:send:save', 'request:response:200', 'request:parsed:string',
            'request:success:1', 'request:finish', 'action:success:ok', 'action:finish',
        ])
        offAction()
        offRequest()
    })

    it('reports errors and failures at every level', async () => {
        let events = []
        let offAction = Brew.interceptAction(({ onError, onFailure }) => {
            onError(({ response }) => events.push('action:error:' + response.status))
            onFailure(({ error }) => events.push('action:failure:' + error.message))
        })
        let offMessage = Brew.interceptMessage(({ onFailure }) => onFailure(({ error }) => events.push('message:failure:' + error.message)))
        let offHook = Brew.hook('request', ({ fail }) => fail(({ preventDefault }) => preventDefault()))
        let { wire } = await component()

        vi.stubGlobal('fetch', vi.fn(async () => response('{}', { status: 404 })))
        await wire.a().catch(() => {})
        vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('down') }))
        await wire.b().catch(() => {})

        expect(events).toEqual(['action:error:404', 'message:failure:down', 'action:failure:down'])
        offAction()
        offMessage()
        offHook()
    })

    it('reports request cancellation', async () => {
        let events = []
        let offMessage = Brew.interceptMessage(({ cancel }) => cancel())
        let offRequest = Brew.interceptRequest(({ onCancel }) => onCancel(() => events.push('request:cancel')))
        let { wire } = await component()

        await wire.save().catch(() => {})
        await settle()

        expect(events).toEqual(['request:cancel'])
        offMessage()
        offRequest()
    })

    it('cancels the message when its only action is cancelled while pending', async () => {
        let fetch = vi.fn()
        vi.stubGlobal('fetch', fetch)
        let off = Brew.interceptAction(({ action }) => queueMicrotask(() => action.cancel()))
        let { wire } = await component()

        await wire.save().catch(() => {})
        await settle()

        expect(fetch).not.toHaveBeenCalled()
        off()
    })

    it('ignores later payloads once a message was cancelled', async () => {
        let a = snapshot({})
        let b = snapshot({})
        await mount(rootHtml(a, '') + rootHtml(b, ''))
        vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ components: [reply(a), reply(b)] })))
        let off = Brew.interceptMessage(({ message, cancel, onSuccess }) => onSuccess(() => {
            if (message.component.id === a.memo.id) cancel()
        }))

        let results = await Promise.allSettled([Brew.find(a.memo.id).go(), Brew.find(b.memo.id).go()])

        expect(results.map(r => r.status)).toEqual(['rejected', 'fulfilled'])
        off()
    })

    it('lets effect listeners register component cleanups', async () => {
        let cleaned = []
        let off = Brew.hook('effect', ({ cleanup }) => cleanup(() => cleaned.push(true)))
        let { wire } = await component()

        off()
        document.body.innerHTML = ''
        await settle()

        expect(cleaned).toEqual([true])
        expect(wire.__instance.el.__brew).toBeUndefined()
    })
})

describe('response pipeline', () => {
    it('runs message hooks in order and renders on the next frame', async () => {
        vi.stubGlobal('fetch', echoServer(data => `<b>${data.count}</b>`, data => { data.count = 9 }))
        let order = []
        let off = Brew.interceptMessage(({ onSend, onSuccess, onFinish }) => {
            onSend(({ payload }) => order.push('send:' + payload.calls[0].method))
            onSuccess(({ onSync, onEffect, onMorph, onMorphed, onRender }) => {
                order.push('success')
                onSync(() => order.push('sync'))
                onEffect(() => order.push('effect'))
                onMorph(async () => order.push('morph'))
                onMorphed(() => order.push('morphed'))
                onRender(() => order.push('render'))
            })
            onFinish(() => order.push('finish'))
        })
        let { wire } = await component()

        await wire.save()
        await settle()

        expect(order).toEqual(['send:save', 'success', 'sync', 'effect', 'morph', 'morphed', 'finish', 'render'])
        expect(document.querySelector('b').textContent).toBe('9')
        off()
    })

    it('stops at each stage when a message is cancelled', async () => {
        for (let stage of ['success', 'sync', 'effect', 'beforeRender']) {
            vi.stubGlobal('fetch', echoServer(null))
            let after = []
            let off = Brew.interceptMessage(({ cancel, onSuccess, onFinish }) => {
                onSuccess(({ onSync, onEffect, onMorph, onRender }) => {
                    if (stage === 'success') cancel()
                    onSync(() => stage === 'sync' && cancel())
                    onEffect(() => stage === 'effect' && cancel())
                    onMorph(async () => after.push('morph'))
                    onRender(() => after.push('render'))
                })
                onFinish(() => stage === 'beforeRender' && after.length === 1 && cancel())
            })
            let { wire } = await component()

            await wire.save().catch(() => {})
            await settle()

            expect(after).toEqual(stage === 'beforeRender' ? ['morph'] : [])
            off()
            await cleanPage()
        }
    })

    it('settles promises even when a morph throws', async () => {
        vi.stubGlobal('fetch', echoServer(null))
        let rendered = []
        let off = Brew.interceptMessage(({ onSuccess }) => onSuccess(({ onMorph, onRender }) => {
            onMorph(async () => { throw new Error('morph failed') })
            onRender(() => rendered.push(true))
        }))
        let unhandled = vi.fn()
        process.on('unhandledRejection', unhandled)
        let { wire } = await component()

        await wire.save()
        await settle()

        expect(rendered).toEqual([])
        process.off('unhandledRejection', unhandled)
        off()
    })

    it('handles skipped payloads for the matching component only', async () => {
        let skipped = []
        let off = Brew.interceptMessage(({ message, onSkipped }) => onSkipped(() => skipped.push(message.component.id)))
        let a = snapshot({})
        let b = snapshot({})
        await mount(rootHtml(a, '') + rootHtml(b, ''))
        vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ components: [{ skip: true, id: a.memo.id }, reply(b)] })))

        await Promise.all([Brew.find(a.memo.id).go(), Brew.find(b.memo.id).go()])

        expect(skipped).toEqual([a.memo.id])
        off()
    })

    it('runs legacy request and commit hooks', async () => {
        vi.stubGlobal('fetch', echoServer(data => 'ok'))
        let events = []
        let offRequest = Brew.hook('request', ({ url, payload, respond, succeed }) => {
            events.push('request:' + url + ':' + JSON.parse(payload).components.length)
            respond(({ status }) => events.push('respond:' + status))
            succeed(({ status, json }) => events.push('succeed:' + status + ':' + json.components.length))
        })
        let offCommit = Brew.hook('commit', ({ component, commit, respond, succeed }) => {
            events.push('commit:' + component.name + ':' + commit.calls[0].method)
            respond(() => events.push('commit-respond'))
            succeed(({ effects }) => events.push('commit-succeed:' + typeof effects.html))
        })
        let { wire } = await component()

        await wire.save()
        await settle()

        expect(events).toEqual([
            'request:/brew/update:1', 'commit:counter:save', 'respond:200', 'succeed:200:1', 'commit-respond', 'commit-succeed:string',
        ])
        offRequest()
        offCommit()
    })

    it('reports errors and cancellations to legacy hooks', async () => {
        let events = []
        let offRequest = Brew.hook('request', ({ fail }) => fail(({ status, content, preventDefault }) => {
            events.push('request-fail:' + status + ':' + content)
            preventDefault()
        }))
        let offCommit = Brew.hook('commit', ({ fail }) => fail(() => events.push('commit-fail')))
        vi.stubGlobal('fetch', vi.fn(async () => response('boom', { status: 500 })))
        let { wire } = await component()

        await wire.save().catch(() => {})
        expect(events).toEqual(['request-fail:500:boom', 'commit-fail'])
        expect(document.getElementById('brew-error')).toBeNull()

        events.length = 0
        let off = Brew.interceptMessage(({ onSend, cancel }) => onSend(() => cancel()))
        vi.stubGlobal('fetch', echoServer(null))
        await wire.again().catch(() => {})
        expect(events).toContain('commit-fail')

        off()
        offRequest()
        offCommit()
    })
})
