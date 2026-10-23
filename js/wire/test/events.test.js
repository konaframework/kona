import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import {
    bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, echoServer, settle, manualFetch, reply, jsonResponse,
} from './helpers.js'
import {
    dispatch, dispatchGlobal, dispatchSelf, dispatchTo, dispatchEl, dispatchRef,
} from '../src/events.js'
import { browser } from '../src/browser.js'

let Brew

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(stubDialog)
afterEach(cleanPage)

async function mountComponent(data, inner, memo = {}, effects = {}) {
    let snap = snapshot(data, memo)
    let host = await mount(rootHtml(snap, inner, { effects }))

    return { snap, host, wire: Brew.find(snap.memo.id), instance: Brew.find(snap.memo.id).__instance }
}

function okFor(call, effects = {}) {
    return jsonResponse({ components: call.body.components.map(c => reply(JSON.parse(c.snapshot), effects)), assets: [] })
}

describe('events + dispatches', () => {
    it('dispatches CustomEvents with __brew and listens via Brew.on', async () => {
        let seen = []
        let off = Brew.on('saved', detail => seen.push(detail))
        window.dispatchEvent(new CustomEvent('saved', { detail: { ignored: true } }))
        expect(seen).toEqual([])
        dispatchGlobal('saved', { id: 1 })
        expect(seen).toEqual([{ id: 1 }])
        off()

        let { host, instance, wire } = await mountComponent({}, `<span wire:ref="box" id="box"></span><div class="t"></div>`)
        let local = []
        wire.$on('local', d => local.push(d))
        wire.$dispatch('local', { a: 1 })
        expect(local).toEqual([{ a: 1 }])

        wire.$dispatchSelf('local', { b: 2 })
        dispatchEl(instance, '.t', 'el-evt', { c: 3 })
        dispatchRef(instance, 'box', 'ref-evt', { d: 4 })
        dispatchTo('counter', 'to-evt', { e: 5 })
        dispatch(instance, 'bubbled', 'x')
        dispatchSelf(instance, 'self-only', { f: 6 })
        expect(host.querySelector('#box')).toBeTruthy()
    })

    it('fires server dispatches after morph and applies streams', async () => {
        let seen = []

        vi.stubGlobal('fetch', echoServer(
            () => `<div wire:stream="log" id="log">start</div>`,
            () => ({
                dispatches: [{ name: 'toast', params: { ok: true }, self: true }],
                streams: [{ content: '<em>s</em>', to: 'log', replace: true }],
            }),
        ))

        let { host, wire, instance } = await mountComponent({ count: 1 }, `
            <div wire:stream="log" id="log">start</div>
        `)

        instance.el.addEventListener('toast', e => { if (e.__brew) seen.push(e.detail) })

        await wire.save()
        await settle()

        expect(seen).toEqual([{ ok: true }])
        expect(host.querySelector('#log')?.innerHTML || '').toContain('s')
    })

    it('follows redirect effects', async () => {
        let visit = vi.spyOn(browser, 'visit').mockImplementation(() => {})
        vi.stubGlobal('fetch', echoServer(null, () => ({
            redirect: { url: '/next', navigate: true },
        })))

        let { wire } = await mountComponent({}, ``)
        await wire.save()
        await settle()
        expect(visit).toHaveBeenCalledWith('/next')

        vi.stubGlobal('fetch', echoServer(null, () => ({
            redirect: { default: '/fallback' },
        })))
        await wire.save()
        await settle()
        expect(visit).toHaveBeenCalledWith('/fallback')

        vi.stubGlobal('fetch', echoServer(null, () => ({
            redirect: {},
        })))
        await wire.save()
        await settle()
        expect(visit).toHaveBeenCalledWith('/')

        vi.stubGlobal('fetch', echoServer(null, () => ({})))
        await wire.save()
        await settle()
        visit.mockRestore()
    })

    it('runs xjs effects after morph', async () => {
        vi.stubGlobal('fetch', echoServer(data => `<span>${data.count}</span>`, () => ({
            xjs: ['$wire.count = 42'],
        })))

        let { wire } = await mountComponent({ count: 1 }, `<span>1</span>`)
        await wire.save()
        await settle()
        expect(wire.count).toBe(42)
    })

    it('dispatches targeted variants and streams without to', async () => {
        let { instance, host } = await mountComponent({}, `
            <span wire:ref="box" id="box"></span>
            <div class="item"></div>
            <div wire:stream id="all">x</div>
            <div wire:stream.replace="r" id="r">old</div>
        `, {}, { listeners: ['from-window'] })

        dispatchTo('counter', 'to-me', {})
        dispatchRef(instance, 'box', 'ref-me', {})
        dispatchEl(instance, '.item', 'el-me', {})
        dispatchSelf(instance, 'self-me', {})

        vi.stubGlobal('fetch', echoServer(() => `<div wire:stream id="all">x</div><div wire:stream.replace="r" id="r">old</div>`, () => ({
            streams: [
                { content: '<i>a</i>', replace: false },
                { content: 'new', to: 'r', replace: true },
            ],
            dispatches: [
                { name: 'c', component: 'counter' },
                { name: 'r', ref: 'box' },
                { name: 'e', el: '.item' },
                { name: 'plain' },
            ],
        })))
        await instance.$wire.save()
        await settle()
        expect(host.querySelector('#all')?.innerHTML || '').toContain('a')

        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        window.dispatchEvent(Object.assign(new CustomEvent('from-window', { detail: { z: 1 } }), { __brew: true }))
        await settle()
        expect(fetch).toHaveBeenCalled()

        // non-brew events are ignored; missing detail uses {}...
        window.dispatchEvent(new CustomEvent('from-window', { detail: undefined }))
        window.dispatchEvent(Object.assign(new CustomEvent('from-window'), { __brew: true }))
        await settle()

        await mountComponent({}, `<div></div>`, {}, { dispatches: [] })
    })

    it('runs mount-time dispatches after microtasks', async () => {
        let seen = []
        let off = Brew.on('boot', d => seen.push(d))
        await mountComponent({}, `<div></div>`, {}, {
            dispatches: [{ name: 'boot', params: { v: 1 } }],
        })
        await new Promise(r => queueMicrotask(() => queueMicrotask(() => queueMicrotask(r))))
        await settle()
        expect(seen).toEqual([{ v: 1 }])
        off()
    })
})
