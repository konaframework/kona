import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import {
    bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, echoServer, settle,
} from './helpers.js'
import { entangle } from '../src/features/entangle.js'

let Brew

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(stubDialog)
afterEach(cleanPage)

async function mountComponent(data, inner, memo = {}, effects = {}) {
    let snap = snapshot(data, memo)
    let host = await mount(rootHtml(snap, inner, { effects }))

    return { snap, host, wire: Brew.find(snap.memo.id), instance: Brew.find(snap.memo.id).__instance }
}

describe('M3 downloads scripts echo preserve-scroll entangle', () => {
    it('downloads a file from effects.download', async () => {
        let click = vi.fn()
        let create = document.createElement.bind(document)

        document.createElement = tag => {
            let el = create(tag)

            if (tag === 'a') el.click = click

            return el
        }

        globalThis.URL = globalThis.URL || function URL() {}
        globalThis.URL.createObjectURL = vi.fn(() => 'blob:test')
        globalThis.URL.revokeObjectURL = vi.fn()

        vi.stubGlobal('fetch', echoServer(null, () => ({
            download: { content: btoa('hi') },
        })))

        let { wire } = await mountComponent({}, `<div></div>`)

        await wire.save()
        await settle()
        expect(click).toHaveBeenCalled()

        vi.stubGlobal('fetch', echoServer(null, () => ({
            download: { name: 'n.txt', contentType: 'text/plain', content: btoa('x') },
        })))
        await wire.save()
        await settle()

        // no download effect is a no-op...
        vi.stubGlobal('fetch', echoServer(null, () => ({})))
        await wire.save()
        await settle()
        document.createElement = create
    })

    it('preserves scroll for preserve-scroll actions', async () => {
        Object.defineProperty(window, 'scrollX', { configurable: true, get: () => 0 })
        Object.defineProperty(window, 'scrollY', { configurable: true, get: () => 40 })
        let scrolled = vi.spyOn(window, 'scrollTo').mockImplementation(() => {})

        vi.stubGlobal('fetch', echoServer(data => `<span>${data.count}</span>`))

        let { host } = await mountComponent({ count: 1 }, `
            <button type="button" wire:click.preserve-scroll="save">go</button>
        `)

        host.querySelector('button').click()
        await settle()
        expect(scrolled).toHaveBeenCalled()
        scrolled.mockRestore()
    })

    it('marks async metadata from .async modifier', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)

        let { host } = await mountComponent({}, `
            <button type="button" wire:click.async="parallel">async</button>
        `, { async: ['parallel'] })

        host.querySelector('button').click()
        await settle()
        let body = JSON.parse(fetch.mock.calls.at(-1)[1].body)
        expect(body.components[0].calls[0].metadata.async).toBe(true)
    })

    it('runs scripts and binds $js', async () => {
        window.__scriptRan = false
        let { wire, instance } = await mountComponent({}, `<div></div>`)

        instance.processEffects({
            js: { beep: 'window.__beep = 1' },
            scripts: ['window.__scriptRan = true'],
        })
        expect(window.__scriptRan).toBe(true)
        expect(typeof wire.$js.beep).toBe('function')
        wire.$js.beep()
        expect(window.__beep).toBe(1)
        // second js binding hits component.$js already set...
        instance.processEffects({ js: { other: 'window.__other = 1' } })
        expect(typeof wire.$js.other).toBe('function')
        // scripts already ran → early return...
        instance.processEffects({ scripts: ['window.__scriptRan = false'] })
        expect(window.__scriptRan).toBe(true)
        // empty scripts returns early...
        let { instance: other } = await mountComponent({}, `<div></div>`)
        other.processEffects({ js: { x: '1' } })
    })

    it('injects assets once and wires Echo channels', async () => {
        let listened = []
        window.Echo = {
            channel(name) {
                return {
                    listen(event, handler) { listened.push(['public', name, event, handler]) },
                    stopListening() {},
                }
            },
            private(name) {
                return {
                    listen(event, handler) { listened.push(['private', name, event, handler]) },
                    stopListening() {},
                }
            },
            join(name) {
                return {
                    listen(event, handler) { listened.push(['presence', name, event, handler]) },
                    stopListening() {},
                }
            },
        }

        let { instance, host } = await mountComponent({}, `<div></div>`)

        instance.processEffects({
            assets: ['<meta name="brew-asset" content="1">', '', 1, 'plain text'],
            echo: {
                'echo:orders,OrderShipped': 'onEchoOrder',
                'echo-private:room,Message': 'onMsg',
                'echo-presence:lobby,Joined': 'onJoin',
                'bad': 'x',
            },
        })
        // cleanup without stopListening...
        window.Echo.channel = () => ({ listen() {} })
        instance.processEffects({ echo: { 'echo:x,Y': 'm' } })
        instance.cleanup()
        expect(document.querySelector('meta[name="brew-asset"]')).toBeTruthy()
        // second pass hits the already-injected asset guard...
        let before = document.querySelectorAll('meta[name="brew-asset"]').length
        let { instance: again } = await mountComponent({}, `<div></div>`)
        again.processEffects({ assets: ['<meta name="brew-asset" content="1">'] })
        expect(document.querySelectorAll('meta[name="brew-asset"]').length).toBe(before)
        expect(listened.map(i => i[0]).slice(0, 3)).toEqual(['public', 'private', 'presence'])
        // invoke echo handler...
        listened[0][3]({ id: 1 })
        await settle()
        delete window.Echo
        expect(host).toBeTruthy()
    })

    it('entangles state with optional live sync', async () => {
        let { instance, wire } = await mountComponent({ title: 'a' }, `<div></div>`)
        let bound = entangle(instance, 'title')
        expect(bound.get()).toBe('a')
        bound.set('b')
        expect(wire.title).toBe('b')

        let live = entangle(instance, 'title').live
        expect(live.get()).toBe('b')
        vi.stubGlobal('fetch', echoServer(null))
        live.set('c')
        await settle()
        expect(wire.title).toBe('c')
    })
})
