import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import {
    bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, settle, tick,
} from './helpers.js'
import { resolveHref } from '../src/features/current.js'

let Brew

function stubNavFetch(html = '<html><head><title>T</title></head><body><div>ok</div></body></html>') {
    return vi.fn(async () => ({ text: async () => html }))
}

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(() => {
    stubDialog()
    history.replaceState({}, '', '/')
    window.wireScriptConfig = {
        navigate: { show_progress_bar: true, progress_bar_color: '#f00', view_transitions: false },
    }
    vi.stubGlobal('fetch', stubNavFetch())
})
afterEach(async () => {
    // Drain in-flight navigate/prefetch before cleanPage restores the real fetch.
    await settle()
    await tick(50)
    await cleanPage()
    vi.stubGlobal('fetch', stubNavFetch())
})

describe('M5 navigate current url', () => {
    it('exposes Brew.navigate and applies url/title effects', async () => {
        expect(typeof Brew.navigate).toBe('function')

        document.head.insertAdjacentHTML('beforeend', '<script src="/brew.js" data-navigate-once></script>')
        document.head.insertAdjacentHTML('beforeend', '<script data-navigate-track="t1"></script>')

        vi.stubGlobal('fetch', vi.fn(async () => ({
            text: async () => `<html><head><title>Next</title>
                <script src="/brew.js" data-navigate-once></script>
                <script data-navigate-track="t1"></script>
                <script>window.__nav = 1</script>
            </head><body><div id="x">ok</div><div wire:persist="nav">new</div></body></html>`,
        })))

        document.body.innerHTML = '<a href="/next" wire:navigate>Go</a><div wire:persist="nav">keep</div>'
        document.querySelector('a').dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
        await settle()
        await tick(50)
        expect(document.title).toBe('Next')
        expect(document.body.textContent).toContain('keep')

        // persist without matching target in the next document...
        document.body.innerHTML = '<div wire:persist="solo">alone</div>'
        vi.stubGlobal('fetch', vi.fn(async () => ({
            text: async () => '<html><head><title>T</title></head><body><p>no-persist</p></body></html>',
        })))
        await Brew.navigate('/nop')
        await settle()
        expect(document.body.textContent).toContain('no-persist')

        // blank persist attr uses default key...
        document.body.innerHTML = '<div wire:persist="">blank</div>'
        document.querySelector('[wire\\:persist]')._brewPersistKey = 'default'
        vi.stubGlobal('fetch', vi.fn(async () => ({
            text: async () => '<html><head><title>U</title></head><body><div wire:persist="default">x</div></body></html>',
        })))
        await Brew.navigate('/blank-persist')
        await settle()
        expect(document.body.textContent).toContain('blank')

        // replace + view transitions path...
        window.wireScriptConfig.navigate.view_transitions = true
        document.startViewTransition = (fn) => { fn(); return { finished: Promise.resolve() } }
        await Brew.navigate('/again', { replace: true })
        await settle()
        delete document.startViewTransition
        window.wireScriptConfig.navigate.view_transitions = false

        // default progress color...
        delete window.wireScriptConfig.navigate.progress_bar_color
        await Brew.navigate('/default-color')
        await settle()
        await tick(250)

        // progress bar disabled (hideProgress early-return when never shown)...
        window.wireScriptConfig.navigate.show_progress_bar = false
        document.querySelectorAll('[data-brew-progress]').forEach(el => el.remove())
        await Brew.navigate('/quiet')
        await settle()
        await tick(250)
        delete window.wireScriptConfig
        window.brewScriptConfig = { navigate: { show_progress_bar: false } }
        await Brew.navigate('/legacy-quiet')
        await settle()
        await tick(250)
        delete window.brewScriptConfig
        window.wireScriptConfig = {
            navigate: { show_progress_bar: true, progress_bar_color: '#f00', view_transitions: false },
        }

        let snap = snapshot({})
        let host = await mount(rootHtml(snap, `<a href="/posts" wire:current>Posts</a>`))
        expect(host.querySelector('a')).toBeTruthy()

        let instance = Brew.find(snap.memo.id).__instance
        instance.processEffects({
            url: { q: { value: 'tea', history: true }, page: { value: 2 }, blank: { value: '' } },
            title: 'Tea',
            disableBackButtonCache: true,
        })
        expect(document.title).toBe('Tea')
        expect(location.search).toContain('q=tea')

        instance.processEffects({ url: { q: { value: null } } })
    })

    it('prefetches on hover and handles current exact/strict', async () => {
        let fetch = vi.fn(async () => ({ text: async () => '<html><body></body></html>' }))
        vi.stubGlobal('fetch', fetch)
        document.body.innerHTML = '<a href="/p" wire:navigate.hover>P</a>'
        document.querySelector('a').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))
        await settle()
        // second hover hits cache...
        document.querySelector('a').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))
        await settle()
        expect(fetch.mock.calls.length).toBe(1)

        history.replaceState({}, '', '/')
        let snap = snapshot({})
        await mount(rootHtml(snap, `
            <a href="/" wire:current.exact>Home</a>
            <a href="/elsewhere" wire:current>Else</a>
            <a href="/x" wire:current.strict>X</a>
            <a href="http://localhost/abs" wire:current>Abs</a>
            <a href="http://localhost" wire:current>HostOnly</a>
        `))
        expect(document.querySelectorAll('[data-current]').length).toBeGreaterThan(0)

        history.replaceState({}, '', '/posts/1')
        await mount(rootHtml(snapshot({}, { id: 'c2' }), `
            <a href="/posts" wire:current>Posts</a>
            <a href="/" wire:current.exact>Home</a>
            <span wire:current="/posts">via-expr</span>
            <a wire:current.exact>no-href</a>
        `))
        expect(document.querySelectorAll('[data-current]').length).toBeGreaterThan(0)

        expect(resolveHref({ getAttribute: () => '/a' }, { expression: '' })).toBe('/a')
        expect(resolveHref({ getAttribute: () => null }, { expression: '/b' })).toBe('/b')
        expect(resolveHref({ getAttribute: () => '' }, { expression: '' })).toBe('')
        expect(resolveHref({ getAttribute: () => null }, { expression: '' })).toBe('')

        // ignore modified clicks...
        document.body.innerHTML = '<a href="/z" wire:navigate>Z</a><div wire:navigate.hover></div>'
        document.querySelector('a').dispatchEvent(new MouseEvent('click', { bubbles: true, metaKey: true }))
        document.querySelector('a').dispatchEvent(new MouseEvent('click', { bubbles: true, ctrlKey: true }))
        document.querySelector('a').dispatchEvent(new MouseEvent('click', { bubbles: true, shiftKey: true }))
        document.querySelector('a').dispatchEvent(new MouseEvent('click', { bubbles: true, altKey: true }))
        document.querySelector('a').dispatchEvent(new MouseEvent('click', { bubbles: true, button: 1 }))
        document.querySelector('[wire\\:navigate\\.hover]').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))
        // hash / mailto / missing href ignored (no navigation fetch)...
        document.body.innerHTML = '<a href="#x" wire:navigate>H</a><a href="mailto:a@b.c" wire:navigate>M</a><a wire:navigate>N</a>'
        document.querySelectorAll('a').forEach(a => {
            a.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
        })

        window.dispatchEvent(new PopStateEvent('popstate'))
        await settle()
        await tick(50)

        // url effect without history + plain value + clear all...
        let snap2 = snapshot({})
        await mount(rootHtml(snap2, `<span>u</span>`))
        let inst = Brew.find(snap2.memo.id).__instance
        inst.processEffects({ url: { plain: 'v' } })
        inst.processEffects({ url: { plain: { value: '' } } })

        // failed fetch is swallowed (empty document swap)...
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('fetch failed') }))
        await Brew.navigate('/broken')
        await settle()
    })
})
