import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import {
    bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, echoServer, settle,
} from './helpers.js'

let Brew

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(stubDialog)
afterEach(cleanPage)

async function mountComponent(data, inner, memo = {}, effects = {}) {
    let snap = snapshot(data, memo)
    let host = await mount(rootHtml(snap, inner, { effects }))

    return { snap, host, wire: Brew.find(snap.memo.id), instance: Brew.find(snap.memo.id).__instance }
}

describe('M4 nesting islands teleport lazy', () => {
    it('exposes $parent and $island', async () => {
        let parentSnap = snapshot({ n: 1 })
        let childSnap = snapshot({ n: 2 }, { id: 'child-1', name: 'child' })
        let html = rootHtml(parentSnap, `
            <div id="p">
                ${rootHtml(childSnap, `<span>c</span>`, { effects: {} }).replace(/^/, '')}
            </div>
        `)
        // nest child markup inside parent manually...
        let host = await mount(`
            <div wire:snapshot='${JSON.stringify(parentSnap).replace(/'/g, "&#39;")}' wire:effects='{}' wire:id="${parentSnap.memo.id}" wire:name="parent">
                <div wire:snapshot='${JSON.stringify(childSnap).replace(/'/g, "&#39;")}' wire:effects='{}' wire:id="child-1" wire:name="child">
                    <span>c</span>
                </div>
            </div>
        `)
        let parent = Brew.find(parentSnap.memo.id)
        let child = Brew.find('child-1')
        expect(child.$parent).toBeTruthy()
        expect(typeof parent.$island).toBe('function')

        vi.stubGlobal('fetch', echoServer(null))
        await parent.$island('panel', 'append')
        await settle()
        expect(host).toBeTruthy()
    })

    it('teleports template contents and applies islands', async () => {
        document.body.insertAdjacentHTML('beforeend', '<div id="portal"></div>')
        let { instance, wire } = await mountComponent({}, `
            <template wire:teleport="#portal"><b>hi</b></template>
            <template wire:teleport="#missing"><b>skip</b></template>
            <template wire:teleport=""><b>empty</b></template>
            <div wire:island="panel">old</div>
        `)

        instance.processEffects({
            islands: { panel: { html: '<i>new</i>' }, missing: { html: '' } },
        })
        expect(document.querySelector('#portal b')?.textContent).toBe('hi')
        expect(instance.el.querySelector('[wire\\:island="panel"]')?.innerHTML).toContain('new')

        instance.__brewIsland = { mode: 'append' }
        instance.processEffects({ islands: { panel: { html: '<u>more</u>' } } })
        instance.__brewIsland = { mode: 'prepend' }
        instance.processEffects({ islands: { panel: { html: '<s>pre</s>' } } })

        // orphan $parent proxy...
        expect(typeof wire.$parent.save).toBe('function')
    })

    it('lazy loads on intersection and defer via microtask', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)

        await mountComponent({}, `<div></div>`, { lazyLoaded: false }, { lazy: true })
        await settle()

        await mountComponent({}, `<div></div>`, { defer: true }, { defer: true })
        await settle()
        expect(fetch.mock.calls.length).toBeGreaterThan(0)

        // IntersectionObserver path...
        let observe = vi.fn()
        let disconnect = vi.fn()
        let cb
        globalThis.IntersectionObserver = class {
            constructor(fn) { cb = fn }
            observe(el) {
                observe(el)
                cb([{ isIntersecting: false }])
                cb([{ isIntersecting: true }])
            }
            disconnect() { disconnect() }
        }
        await mountComponent({}, `<div></div>`, {}, { lazy: true })
        await settle()
        expect(observe).toHaveBeenCalled()
        delete globalThis.IntersectionObserver
    })

    it('marks island metadata from .island modifier', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host } = await mountComponent({}, `
            <button type="button" wire:click.island.panel.append="save">go</button>
            <button type="button" wire:click.island.prepend="save">pre</button>
            <button type="button" wire:click.island="save">def</button>
        `)
        host.querySelectorAll('button')[0].click()
        await settle()
        let body = JSON.parse(fetch.mock.calls.at(-1)[1].body)
        expect(body.components[0].calls[0].metadata.island).toEqual({ name: 'panel', mode: 'append' })

        host.querySelectorAll('button')[1].click()
        await settle()
        body = JSON.parse(fetch.mock.calls.at(-1)[1].body)
        expect(body.components[0].calls[0].metadata.island.mode).toBe('prepend')

        host.querySelectorAll('button')[2].click()
        await settle()
        body = JSON.parse(fetch.mock.calls.at(-1)[1].body)
        expect(body.components[0].calls[0].metadata.island.name).toBe('default')
    })
})
