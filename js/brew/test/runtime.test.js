import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import Alpine from 'alpinejs'
import { mount, rootHtml, snapshot, serverReply, echoServer, manualFetch, reply, settle, tick, jsonResponse, response } from './helpers.js'

let Brew

beforeAll(async () => {
    window.brewScriptConfig = { csrf: 'token-1', uri: '/brew/update' }
    await import('../src/index.js')
    Brew = window.Brew
    Brew.start()
    await settle()
})

beforeEach(() => {
    HTMLDialogElement.prototype.showModal = vi.fn()
    HTMLDialogElement.prototype.close = function () { this.dispatchEvent(new Event('close')) }
})

afterEach(async () => {
    document.body.innerHTML = ''
    await settle()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
})

const counter = (data = { count: 1 }, memo = {}) => snapshot(data, memo)

describe('store', () => {
    it('finds components by id, name, and order', async () => {
        let a = counter({ count: 1 }, { name: 'counter' })
        let b = counter({ count: 2 }, { name: 'other' })

        await mount(rootHtml(a, '<span></span>') + rootHtml(b, '<span></span>'))

        expect(Brew.find(a.memo.id).count).toBe(1)
        expect(Brew.find('missing')).toBeUndefined()
        expect(Brew.first().count).toBe(1)
        expect(Brew.getByName('other').map(wire => wire.count)).toEqual([2])
        expect(Brew.all().map(component => component.id)).toEqual([a.memo.id, b.memo.id])
    })

    it('forgets components removed from the page', async () => {
        let snap = counter()
        let host = await mount(rootHtml(snap, ''))

        host.remove()
        await settle()

        expect(Brew.find(snap.memo.id)).toBeUndefined()
        expect(Brew.first()).toBeUndefined()
    })

    it('fires component.init and element.init hooks', async () => {
        let seen = []
        let offA = Brew.hook('component.init', ({ component, cleanup }) => {
            seen.push('component:' + component.name)
            cleanup(() => seen.push('cleaned'))
        })
        let offB = Brew.hook('element.init', ({ el }) => seen.push('element:' + el.tagName))

        let host = await mount(rootHtml(counter(), '<button wire:click="go"></button>'))
        host.remove()
        await settle()

        expect(seen).toEqual(['component:counter', 'element:DIV', 'element:BUTTON', 'cleaned'])
        offA()
        offB()
    })

    it('runs mount effects through the effect hook', async () => {
        let effects = []
        let off = Brew.hook('effect', ({ effects: e }) => effects.push(e))

        await mount(rootHtml(counter(), '', { effects: { custom: 1 } }))

        expect(effects).toEqual([{ custom: 1 }])
        off()
    })
})

describe('$wire', () => {
    let wire

    beforeEach(async () => {
        await mount(rootHtml(counter({ count: 1, user: { name: 'Ana' }, tags: ['a'] }), '<span></span>'))
        wire = Brew.first()
    })

    it('reads and writes state', () => {
        expect(wire.count).toBe(1)
        expect(wire.$get('user.name')).toBe('Ana')
        expect(wire.get('count')).toBe(1)
        expect(wire.$el).toBe(document.querySelector('[wire\\:id]'))
        expect(wire.el).toBe(wire.$el)
        expect(wire.$id).toBe(wire.id)

        wire.count = 5
        wire.notState = 1

        expect(wire.count).toBe(5)
        expect(wire.$get('count', false)).toBe(5)
        expect(wire.__instance.ephemeral.notState).toBeUndefined()
        expect(JSON.parse(JSON.stringify(wire))).toMatchObject({ name: 'counter', data: { count: 5 } })
        expect(wire[Symbol.iterator]).toBeUndefined()
        expect(wire.then).toBeUndefined()
    })

    it('$set without live only changes local state', async () => {
        let fetch = vi.fn()
        vi.stubGlobal('fetch', fetch)

        await wire.$set('user.name', 'Bo', false)
        await settle()

        expect(wire.user.name).toBe('Bo')
        expect(fetch).not.toHaveBeenCalled()
    })

    it('$set queues the update ahead of local changes and calls $set', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)

        wire.user.name = 'typed'
        let promise = wire.set('user', { name: 'Queued' })
        await settle()
        await promise

        let message = JSON.parse(fetch.mock.calls[0][1].body).components[0]
        expect(message.updates).toEqual({ user: { name: 'Queued' } })
        expect(message.calls[0].method).toBe('$set')
        expect(wire.user.name).toBe('Queued')
    })

    it('$toggle, $call, $refresh, and $commit send actions', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)

        wire.$toggle('flag')
        await settle()
        wire.$call('save', 1, 2)
        await settle()
        wire.$refresh()
        await settle()
        wire.commit()
        await settle()

        let calls = fetch.mock.calls.map(([, options]) => JSON.parse(options.body).components[0])
        expect(calls[0].updates).toEqual({ flag: true })
        expect(calls[1].calls[0]).toMatchObject({ method: 'save', params: [1, 2] })
        expect(calls[2].calls[0].method).toBe('$refresh')
        expect(calls[3].calls[0].method).toBe('$commit')
    })

    it('strips a lone DOM event argument from method calls', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)

        wire.save(new Event('click'))
        await settle()

        expect(JSON.parse(fetch.mock.calls[0][1].body).components[0].calls[0].params).toEqual([])
    })

    it('returns rejected promises when firing throws synchronously', async () => {
        let off = Brew.interceptAction(() => { throw new Error('nope') })

        await expect(wire.$refresh()).rejects.toThrow('nope')
        await expect(wire.$commit()).rejects.toThrow('nope')

        off()
    })

    it('$watch observes a path until unwatched', async () => {
        let values = []
        let unwatch = wire.$watch('user.name', value => values.push(value))

        wire.user.name = 'X'
        await tick()
        unwatch()
        wire.user.name = 'Y'
        await tick()

        expect(values).toEqual(['X'])
    })

    it('$hook scopes component hooks and passes request hooks through', async () => {
        let seen = []

        wire.$hook('unit.scoped', params => seen.push(params.value))

        Brew.trigger('unit.scoped', { component: wire.__instance, value: 'mine' })
        Brew.trigger('unit.scoped', { component: { id: 'other' }, value: 'theirs' })
        Brew.trigger('unit.scoped', { value: 'global' })

        expect(seen).toEqual(['mine', 'global'])
    })

    it('scoped interceptors only see their component and action', async () => {
        vi.stubGlobal('fetch', echoServer(null))

        let actions = []
        let messages = []
        let requests = []

        wire.$intercept('save', ({ action }) => actions.push(action.name))
        wire.intercept(({ action }) => actions.push('any:' + action.name))
        wire.$interceptAction('other', () => actions.push('never'))
        wire.$interceptMessage('save', ({ message }) => messages.push([...message.actions].map(a => a.name)))
        wire.interceptMessage(() => messages.push('any'))
        wire.$interceptRequest('save', () => requests.push('save'))
        wire.interceptRequest(() => requests.push('any'))
        wire.$interceptRequest('nothing', () => requests.push('never'))

        wire.save()
        await settle()
        wire.load()
        await settle()

        expect(actions).toEqual(['save', 'any:save', 'any:load'])
        expect(messages).toEqual([['save'], 'any', 'any'])
        expect(requests).toEqual(['save', 'any', 'any'])
    })
})

describe('$wire magic in Alpine', () => {
    it('resolves the enclosing component lazily and cleans up watchers', async () => {
        let snap = counter({ count: 1 })
        let host = await mount(rootHtml(snap, `
            <div x-data="{ seen: [] }" x-init="let off = $wire.$watch('count', v => seen.push(v))">
                <span x-text="$wire.count"></span>
            </div>
            <b x-data x-init="$wire.count = 2"></b>`))

        expect(Brew.first().count).toBe(2)

        Brew.first().count = 3
        await tick()

        expect(host.querySelector('span').textContent).toBe('3')
        expect(host.querySelector('[x-data]')._x_dataStack[0].seen).toContain(3)

        let scope = host.querySelector('[x-data]')._x_dataStack[0]

        host.querySelector('[x-data]').remove()
        await tick()
        Brew.first().count = 4
        await tick()

        expect(scope.seen).not.toContain(4)
    })

    it('is inert outside a component', async () => {
        let host = await mount(`<div x-data="{ r: null }" x-init="r = typeof $wire.save; $wire.anything = 1; r = r + ':' + typeof $wire.save()"><b x-text="r"></b></div>`)

        expect(host.querySelector('b').textContent).toBe('function:undefined')
    })
})

describe('request bundling', () => {
    it('bundles calls in the same tick into one message', async () => {
        let fetch = echoServer(null, () => ({ returns: ['a', 'b'] }))
        vi.stubGlobal('fetch', fetch)
        await mount(rootHtml(counter(), ''))

        let wire = Brew.first()
        let results = await Promise.all([wire.one(1), wire.two(2)])

        expect(fetch).toHaveBeenCalledTimes(1)
        expect(JSON.parse(fetch.mock.calls[0][1].body).components[0].calls.map(c => c.method)).toEqual(['one', 'two'])
        expect(results).toEqual(['a', 'b'])
    })

    it('squashes identical calls and resolves every caller', async () => {
        let fetch = echoServer(null, () => ({ returns: ['done'] }))
        vi.stubGlobal('fetch', fetch)
        await mount(rootHtml(counter(), ''))

        let wire = Brew.first()
        let results = await Promise.all([wire.save(1), wire.save(1)])

        expect(JSON.parse(fetch.mock.calls[0][1].body).components[0].calls).toHaveLength(1)
        expect(results).toEqual(['done', 'done'])
    })

    it('bundles different components into one request', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let a = counter()
        let b = counter()
        await mount(rootHtml(a, '') + rootHtml(b, ''))

        Brew.find(a.memo.id).go()
        Brew.find(b.memo.id).go()
        await settle()

        expect(fetch).toHaveBeenCalledTimes(1)
        expect(JSON.parse(fetch.mock.calls[0][1].body).components).toHaveLength(2)
    })

    it('sends async actions in their own message and request', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        await mount(rootHtml(counter({}, { async: ['track'] }), ''))

        let wire = Brew.first()
        wire.save()
        wire.track()
        Brew.fireAction(wire, 'ping', [], { async: true })
        await settle()

        expect(fetch).toHaveBeenCalledTimes(3)
    })

    it('isolates components that ask for it', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let a = counter({}, { isolate: true })
        let b = counter()
        await mount(rootHtml(a, '') + rootHtml(b, ''))

        Brew.find(a.memo.id).go()
        Brew.find(b.memo.id).go()
        await settle()

        expect(fetch).toHaveBeenCalledTimes(2)
    })

    it('carries bound children along with their parent', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let child = counter({ value: 1 }, { props: ['value'] })
        let plain = counter({})
        let parent = counter({ value: 1 })
        await mount(rootHtml(parent, rootHtml(child, '', { key: 'k1' }) + rootHtml(plain, '', { key: 'k2' })))

        Brew.find(parent.memo.id).save()
        await settle()

        let body = JSON.parse(fetch.mock.calls[0][1].body)
        expect(fetch).toHaveBeenCalledTimes(1)
        expect(body.components.map(c => c.calls[0].method)).toEqual(['save', '$commit'])
        expect(JSON.parse(body.components[0].snapshot).memo.children).toEqual({
            k1: ['div', child.memo.id],
            k2: ['div', plain.memo.id],
        })
    })

    it('merges overlapping partitions into one request', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let child = counter({}, { bindings: { value: 'v' } })
        let parent = counter({}, { isolate: true })
        await mount(rootHtml(parent, rootHtml(child, '')))

        Brew.find(parent.memo.id).save()
        await settle()

        expect(fetch).toHaveBeenCalledTimes(1)
        expect(JSON.parse(fetch.mock.calls[0][1].body).components).toHaveLength(2)
    })

    it('exposes Brew.fireAction for components and $wire', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        await mount(rootHtml(counter(), ''))

        Brew.fireAction(Brew.all()[0], 'a')
        await settle()
        Brew.fireAction(Brew.first(), 'b', [1], { x: 1 })
        await settle()

        let calls = fetch.mock.calls.map(([, o]) => JSON.parse(o.body).components[0].calls[0])
        expect(calls).toEqual([{ method: 'a', params: [], metadata: {} }, { method: 'b', params: [1], metadata: { x: 1 } }])
    })
})
