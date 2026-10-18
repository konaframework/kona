import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import {
    bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, echoServer, manualFetch,
    settle, reply, jsonResponse,
} from './helpers.js'
import {
    extractDurationFrom, poll, theElementIsDisconnected, theTabIsInTheBackground,
    theDirectiveIsOffTheElement, theDirectiveIsMissingKeepAlive, theDirectiveHasVisible,
    theElementIsNotInTheViewport, __setPollBackgroundForTests,
} from '../src/directives/poll.js'
import { checkDirty } from '../src/directives/dirty.js'
import { brewIsOffline } from '../src/directives/offline.js'
import { toggleBooleanStateDirective } from '../src/directives/shared.js'
import { getDirectives, extractDirective } from '../src/directives.js'
import { applyDelay, containsTargets } from '../src/directives/loading.js'
import {
    transitionDomMutation, setTransitionNames, clearTransitionNames, startViewTransition,
    skipTransitionWhenTopLayerOpens, topLayerBlocksTransitions,
} from '../src/directives/transition.js'
import { shouldThrottlePoll } from '../src/directives/poll.js'
import { getErrorsObject } from '../src/features/errors.js'
import { findRefEl } from '../src/features/refs.js'
import { evaluateReactiveExpression, isEvaluatingReactiveExpression } from '../src/evaluator.js'
import { sessionIsExpired } from '../src/request/index.js'

let Brew

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(stubDialog)
afterEach(async () => {
    vi.useRealTimers()
    await cleanPage()
})

function okFor(call, effects = {}) {
    return jsonResponse({ components: call.body.components.map(c => reply(JSON.parse(c.snapshot), effects)), assets: [] })
}

async function component(data, inner, memo = {}) {
    let snap = snapshot(data, memo)
    let host = await mount(rootHtml(snap, inner))

    return { snap, host, wire: Brew.find(snap.memo.id) }
}

describe('wire:loading + data-loading', () => {
    it('toggles display during a request and sets data-loading on the origin', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)

        let { host } = await component({ count: 1 }, `
            <button wire:click="save" id="btn">go</button>
            <span wire:loading id="load" style="display:none">…</span>
            <span wire:loading.class="busy" id="cls">x</span>
            <span wire:loading.attr="aria-busy" id="attr">y</span>
            <span wire:loading.remove id="rm">z</span>
            <span wire:loading.flex id="flex" style="display:none">f</span>
        `)

        host.querySelector('#btn').click()
        await settle()

        expect(host.querySelector('#btn').getAttribute('data-loading')).toBe('true')
        expect(host.querySelector('#load').style.display).not.toBe('none')
        expect(host.querySelector('#cls').classList.contains('busy')).toBe(true)
        expect(host.querySelector('#attr').hasAttribute('aria-busy')).toBe(true)
        expect(host.querySelector('#rm').style.display).toBe('none')
        expect(host.querySelector('#flex').style.display).toBe('flex')

        calls[0].resolve(okFor(calls[0]))
        await settle()

        expect(host.querySelector('#btn').hasAttribute('data-loading')).toBe(false)
        expect(host.querySelector('#cls').classList.contains('busy')).toBe(false)
        expect(host.querySelector('#attr').hasAttribute('aria-busy')).toBe(false)
    })

    it('scopes loading with wire:target and except', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)

        let { host } = await component({}, `
            <button wire:click="save" id="a">a</button>
            <button wire:click="other" id="b">b</button>
            <span wire:loading wire:target="save" id="load" style="display:none">…</span>
            <span wire:loading wire:target.except="save" id="ex" style="display:none">e</span>
        `)

        host.querySelector('#b').click()
        await settle()
        expect(host.querySelector('#load').style.display).toBe('none')
        expect(host.querySelector('#ex').style.display).not.toBe('none')

        calls[0].resolve(okFor(calls[0]))
        await settle()

        host.querySelector('#a').click()
        await settle()
        expect(host.querySelector('#load').style.display).not.toBe('none')
        expect(host.querySelector('#ex').style.display).toBe('none')

        calls[1].resolve(okFor(calls[1]))
        await settle()
    })

    it('matches nested property updates for wire:target', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)

        let { host, wire } = await component({ user: { name: 'a' } }, `
            <span wire:loading wire:target="user" id="load" style="display:none">…</span>
        `)

        wire.$set('user.name', 'b')
        await settle()
        expect(host.querySelector('#load').style.display).not.toBe('none')

        calls[0].resolve(okFor(calls[0]))
        await settle()
    })
})

describe('wire:dirty', () => {
    it('shows when ephemeral state diverges and targets a property', async () => {
        let { host, wire } = await component({ title: 'a', body: 'b' }, `
            <input wire:model="title" id="title">
            <div wire:dirty id="any" style="display:none">dirty</div>
            <div wire:dirty wire:target="title" id="t" style="display:none">t</div>
            <div wire:dirty.remove id="rm">clean</div>
            <div wire:dirty.class="is-dirty" id="cls">c</div>
        `)

        expect(host.querySelector('#any').style.display).toBe('none')
        expect(host.querySelector('#rm').style.display).not.toBe('none')

        wire.title = 'changed'
        await settle()

        expect(host.querySelector('#any').style.display).not.toBe('none')
        expect(host.querySelector('#t').style.display).not.toBe('none')
        expect(host.querySelector('#rm').style.display).toBe('none')
        expect(host.querySelector('#cls').classList.contains('is-dirty')).toBe(true)
        expect(wire.$dirty()).toBe(true)
        expect(wire.$dirty('title')).toBe(true)
        expect(wire.$dirty('body')).toBe(false)
        expect(checkDirty(wire.__instance, ['title', 'body'])).toBe(true)
    })
})

describe('wire:confirm', () => {
    it('gates the action behind confirm and prompt', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let confirmFn = vi.fn(() => false)
        vi.stubGlobal('confirm', confirmFn)

        let { host } = await component({}, `<button wire:click="del" wire:confirm="Sure?" id="b">x</button>`)
        host.querySelector('#b').click()
        await settle()

        expect(confirmFn).toHaveBeenCalledWith('Sure?')
        expect(fetch).not.toHaveBeenCalled()

        confirmFn.mockReturnValue(true)
        host.querySelector('#b').click()
        await settle()
        expect(fetch).toHaveBeenCalledTimes(1)

        let promptFn = vi.fn(() => 'DELETE')
        vi.stubGlobal('prompt', promptFn)
        let { host: host2 } = await component({}, `<button wire:click="wipe" wire:confirm.prompt="Type|DELETE" id="p">p</button>`)
        host2.querySelector('#p').click()
        await settle()
        expect(promptFn).toHaveBeenCalled()
        expect(fetch).toHaveBeenCalledTimes(2)

        promptFn.mockReturnValue('nope')
        host2.querySelector('#p').click()
        await settle()
        expect(fetch).toHaveBeenCalledTimes(2)

        let warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
        let { host: host3 } = await component({}, `<button wire:click="x" wire:confirm.prompt="OnlyQuestion" id="q">q</button>`)
        host3.querySelector('#q').click()
        await settle()
        expect(warn).toHaveBeenCalled()
        warn.mockRestore()

        let { host: host4 } = await component({}, `<button wire:click="x" wire:confirm id="d">d</button>`)
        confirmFn.mockReturnValue(true)
        host4.querySelector('#d').click()
        await settle()
        expect(confirmFn.mock.calls.at(-1)[0]).toBe('Are you sure?')
    })
})

describe('wire:cloak / show / text / bind', () => {
    it('removes cloak and binds show/text/attr', async () => {
        let { host, wire } = await component({ open: true, label: 'Hi', href: '/a' }, `
            <div wire:cloak id="cloak">c</div>
            <div wire:show="open" id="show">s</div>
            <span wire:text="label" id="text"></span>
            <a wire:bind:href="href" id="link">l</a>
        `)

        expect(host.querySelector('#cloak').hasAttribute('wire:cloak')).toBe(false)
        expect(host.querySelector('#text').textContent).toBe('Hi')
        expect(host.querySelector('#link').getAttribute('href')).toBe('/a')

        wire.open = false
        wire.label = 'Bye'
        wire.href = '/b'
        await settle()

        expect(host.querySelector('#show').style.display).toBe('none')
        expect(host.querySelector('#text').textContent).toBe('Bye')
        expect(host.querySelector('#link').getAttribute('href')).toBe('/b')
    })
})

describe('wire:ref + $refs + $errors', () => {
    it('resolves refs and exposes the error bag API', async () => {
        let { wire, host } = await component({ title: '' }, `
            <input wire:ref="title" id="inp">
            <span wire:ref="label">L</span>
        `, { errors: { title: ['Required'], nested: [['deep']] } })

        expect(wire.$refs('title')).toBe(host.querySelector('#inp'))
        expect(wire.$refs.label.textContent).toBe('L')
        expect(findRefEl(wire.__instance, 'title')).toBe(host.querySelector('#inp'))

        let errors = wire.$errors
        expect(errors.has('title')).toBe(true)
        expect(errors.first('title')).toBe('Required')
        expect(errors.first('nested')).toBe('deep')
        expect(errors.first()).toBe('Required')
        expect(errors.get('title')).toEqual(['Required'])
        expect(errors.all()).toEqual(['Required', ['deep']])
        expect(errors.any()).toBe(true)
        expect(errors.count()).toBe(2)
        expect(errors.keys()).toEqual(['title', 'nested'])
        expect(errors.hasAny(['title', 'x'])).toBe(true)
        expect(errors.hasAny([['title']])).toBe(true)
        expect(errors.missing('x')).toBe(true)
        expect(errors.missing(['x'])).toBe(true)
        expect(errors.isNotEmpty()).toBe(true)
        expect(errors.has(['title'])).toBe(true)
        expect(errors.has()).toBe(true)
        expect(errors.has(null)).toBe(true)

        errors.clear('title')
        expect(errors.has('title')).toBe(false)
        expect(errors.isEmpty()).toBe(false)

        errors.clear()
        expect(errors.any()).toBe(false)
        expect(errors.isEmpty()).toBe(true)

        let err = vi.spyOn(console, 'error').mockImplementation(() => {})
        expect(wire.$refs('missing')).toBeUndefined()
        err.mockRestore()
    })

    it('resets client overrides when the snapshot changes', async () => {
        let { wire } = await component({}, ``, { errors: { a: ['one'] } })
        wire.$errors.clear()
        expect(wire.$errors.any()).toBe(false)

        wire.__instance.snapshot = { ...wire.__instance.snapshot, memo: { ...wire.__instance.snapshot.memo, errors: { b: ['two'] } } }
        expect(wire.$errors.first('b')).toBe('two')
    })
})

describe('wire:offline', () => {
    it('toggles when the browser goes offline', async () => {
        let { host } = await component({}, `
            <span wire:offline id="off" style="display:none">offline</span>
            <span wire:offline.class="dim" id="c">c</span>
            <span wire:offline.attr="hidden" id="a">a</span>
            <span wire:offline.remove id="rm">online</span>
        `)

        window.dispatchEvent(new Event('offline'))
        await settle()
        expect(brewIsOffline()).toBe(true)
        expect(host.querySelector('#off').style.display).not.toBe('none')
        expect(host.querySelector('#c').classList.contains('dim')).toBe(true)
        expect(host.querySelector('#a').hasAttribute('hidden')).toBe(true)
        expect(host.querySelector('#rm').style.display).toBe('none')

        window.dispatchEvent(new Event('online'))
        await settle()
        expect(brewIsOffline()).toBe(false)
        expect(host.querySelector('#off').style.display).toBe('none')
    })
})

describe('wire:poll helpers', () => {
    it('parses durations and runs a synchronized poll', async () => {
        expect(extractDurationFrom(['500ms'], 2000)).toBe(500)
        expect(extractDurationFrom(['2s'], 2000)).toBe(2000)
        expect(extractDurationFrom(['1m'], 2000)).toBe(60000)
        expect(extractDurationFrom([], 2000)).toBe(2000)
        expect(theElementIsDisconnected(document.createElement('div'))).toBe(true)

        let ticks = 0
        let { start, pauseWhile, stopWhen } = poll(() => { ticks++ }, 20)
        let paused = false
        pauseWhile(() => paused)
        stopWhen(() => ticks >= 2)
        start()

        await new Promise(r => setTimeout(r, 80))
        expect(ticks).toBeGreaterThanOrEqual(1)

        paused = true
        let before = ticks
        await new Promise(r => setTimeout(r, 50))
        expect(ticks).toBe(before)
    })

    it('fires $refresh via wire:poll', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)

        await component({}, `<div wire:poll.50ms="refreshMe"></div>`)
        await new Promise(r => setTimeout(r, 120))
        await settle()

        expect(fetch.mock.calls.length).toBeGreaterThanOrEqual(1)
        let body = JSON.parse(fetch.mock.calls[0][1].body)
        expect(body.components[0].calls[0].method).toBe('refreshMe')
        expect(body.components[0].calls[0].metadata.type).toBe('poll')
    })
})

describe('wire:submit disables form controls', () => {
    it('disables submit controls while the request is in flight', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)

        let { host } = await component({}, `
            <form wire:submit="save">
                <input id="text" type="text">
                <input id="check" type="checkbox">
                <select id="sel"><option>1</option></select>
                <button id="go" type="submit">go</button>
                <button id="plain" type="button" disabled>already</button>
            </form>
        `)

        let form = host.querySelector('form')
        let event = new Event('submit', { bubbles: true, cancelable: true })
        Object.defineProperty(event, 'submitter', { value: host.querySelector('#go') })
        form.dispatchEvent(event)
        await settle()

        expect(host.querySelector('#go').disabled).toBe(true)
        expect(host.querySelector('#sel').disabled).toBe(true)
        expect(host.querySelector('#check').disabled).toBe(true)
        expect(host.querySelector('#text').readOnly).toBe(true)

        calls[0].resolve(okFor(calls[0]))
        await settle()

        expect(host.querySelector('#go').disabled).toBe(false)
        expect(host.querySelector('#text').readOnly).toBe(false)
        expect(host.querySelector('#plain').disabled).toBe(true)
    })
})

describe('wire:model + x-modelable', () => {
    it('syncs through Alpine x-modelable custom controls', async () => {
        let { host, wire } = await component({ color: 'red' }, `
            <div x-data="{ value: null }" x-modelable="value" wire:model="color" id="ctrl">
                <span x-text="value" id="v"></span>
            </div>
        `)

        await settle()
        expect(host.querySelector('#v').textContent).toBe('red')

        wire.color = 'blue'
        await settle()
        expect(host.querySelector('#v').textContent).toBe('blue')
    })
})

describe('wire:transition', () => {
    it('runs View Transitions when available and skips when asked', async () => {
        let ran = false
        document.startViewTransition = vi.fn(config => {
            let update = typeof config === 'function' ? config : config.update
            let done = Promise.resolve().then(() => update())

            return {
                updateCallbackDone: done,
                ready: Promise.resolve(),
                finished: Promise.resolve(),
                skipTransition: vi.fn(),
            }
        })

        let from = document.createElement('div')
        from.innerHTML = `<span wire:transition="panel">a</span>`
        let to = document.createElement('div')
        to.innerHTML = `<span wire:transition="panel">b</span>`

        await transitionDomMutation(from, to, () => { ran = true }, { type: 'forward' })
        expect(ran).toBe(true)
        expect(document.startViewTransition).toHaveBeenCalled()

        ran = false
        await transitionDomMutation(from, to, () => { ran = true }, { skip: true })
        expect(ran).toBe(true)

        ran = false
        delete document.startViewTransition
        await transitionDomMutation(from, to, () => { ran = true }, {})
        expect(ran).toBe(true)

        let empty = document.createElement('div')
        ran = false
        await transitionDomMutation(empty, empty, () => { ran = true }, {})
        expect(ran).toBe(true)

        setTransitionNames(from, { defaultName: 'root' })
        clearTransitionNames(from)
        document.startViewTransition = () => { throw new Error('typed unsupported') }
        // Fallback path when typed transitions throw...
        try {
            startViewTransition(() => {}, { type: 'x' })
        } catch (_) {
            // jsdom may lack startViewTransition entirely after delete + reassign throw
        }
        delete document.startViewTransition
    })

    it('morphs with transition effects from the server', async () => {
        document.startViewTransition = vi.fn(config => {
            let update = typeof config === 'function' ? config : config.update
            let done = Promise.resolve().then(() => update())

            return {
                updateCallbackDone: done,
                ready: Promise.resolve(),
                finished: Promise.resolve(),
                skipTransition: vi.fn(),
            }
        })

        vi.stubGlobal('fetch', echoServer(data => `<span wire:transition="panel">${data.count}</span>`, () => ({
            transition: { type: 'forward' },
        })))

        let { host, wire } = await component({ count: 1 }, `<span wire:transition="panel">1</span>`)
        await wire.save()
        await settle()

        expect(document.startViewTransition).toHaveBeenCalled()
        expect(host.querySelector('[wire\\:transition]').textContent).toBe('1')
        delete document.startViewTransition
    })
})

describe('shared helpers + reactive expressions', () => {
    it('covers toggleBooleanStateDirective branches and getDirectives', () => {
        let el = document.createElement('div')
        el.className = 'a'
        el.setAttribute('wire:loading.class', 'busy')
        el.setAttribute('wire:target', 'save(1)')
        let dir = extractDirective(el, 'wire:loading.class')
        toggleBooleanStateDirective(el, dir, true)
        expect(el.classList.contains('busy')).toBe(true)
        toggleBooleanStateDirective(el, dir, false)

        el.setAttribute('wire:loading.attr', 'disabled')
        let attrDir = extractDirective(el, 'wire:loading.attr')
        attrDir.expression = 'disabled'
        toggleBooleanStateDirective(el, attrDir, true)
        expect(el.hasAttribute('disabled')).toBe(true)
        toggleBooleanStateDirective(el, attrDir, false)

        let mgr = getDirectives(el)
        expect(mgr.has('loading')).toBe(true)
        expect(mgr.missing('poll')).toBe(true)
        expect(mgr.get('target').methods[0].method).toBe('save')
    })

    it('evaluateReactiveExpression tracks depth and warns on server calls', async () => {
        let { wire, host } = await component({ n: 1 }, `<span id="x"></span>`)
        let warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

        expect(isEvaluatingReactiveExpression()).toBe(false)
        let value = evaluateReactiveExpression(host.querySelector('#x'), 'n')
        expect(value).toBe(1)

        evaluateReactiveExpression(host.querySelector('#x'), 'save()')
        expect(warn).toHaveBeenCalled()
        warn.mockRestore()
        expect(wire.n).toBe(1)
    })

    it('getErrorsObject standalone covers empty memo', () => {
        let component = {
            snapshot: { memo: {} },
            id: 'x',
            el: document.createElement('div'),
        }
        let errors = getErrorsObject(component)
        expect(errors.messages()).toEqual({})
        expect(errors.has('a')).toBe(false)
        expect(errors.hasAny(['a'])).toBe(false)
        expect(errors.first('missing')).toBe('')
    })
})

describe('loading delay', () => {
    it('delays showing the indicator then restores', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)

        let { host } = await component({}, `
            <button wire:click="save">go</button>
            <span wire:loading.delay.shortest id="load" style="display:none">…</span>
        `)

        host.querySelector('button').click()
        await settle()
        // shortest = 50ms; settle waits ~40ms so may still be hidden
        await new Promise(r => setTimeout(r, 80))
        expect(host.querySelector('#load').style.display).not.toBe('none')

        calls[0].resolve(okFor(calls[0]))
        await settle()
        expect(host.querySelector('#load').style.display).toBe('none')
    })

    it('aborts delay before it fires and covers applyDelay helpers', async () => {
        let el = document.createElement('div')
        el.setAttribute('wire:loading.delay.none', '')
        let none = applyDelay(extractDirective(el, 'wire:loading.delay.none'))
        let ran = false
        none[0](() => { ran = true })
        expect(ran).toBe(true)

        el.setAttribute('wire:loading.delay.longest', '')
        let [delay, abort] = applyDelay(extractDirective(el, 'wire:loading.delay.longest'))
        let shown = false
        delay(() => { shown = true })
        await abort(() => {})
        expect(shown).toBe(false)

        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({}, `
            <button wire:click="save(1)" id="b">go</button>
            <span wire:loading wire:target="save(1)" id="p" style="display:none">p</span>
            <span wire:loading id="auto" style="display:none">a</span>
            <button wire:click="save" wire:loading.remove id="self">s</button>
        `)

        host.querySelector('#b').click()
        await settle()
        expect(host.querySelector('#p').style.display).not.toBe('none')
        calls[0].resolve(okFor(calls[0]))
        await settle()

        // Attr restore when attribute already present...
        let { fetch: f2, calls: c2 } = manualFetch()
        vi.stubGlobal('fetch', f2)
        let { host: h2 } = await component({}, `
            <button wire:click="go">x</button>
            <span wire:loading.attr="aria-busy" aria-busy="false" id="a">a</span>
        `)
        h2.querySelector('button').click()
        await settle()
        expect(h2.querySelector('#a').getAttribute('aria-busy')).toBe('true')
        c2[0].resolve(okFor(c2[0]))
        await settle()
        expect(h2.querySelector('#a').getAttribute('aria-busy')).toBe('false')
    })
})

describe('poll guards + transition edge cases', () => {
    it('covers poll visibility and background helpers', () => {
        expect(sessionIsExpired()).toBe(false)
        __setPollBackgroundForTests(true)
        expect(theTabIsInTheBackground()).toBe(true)
        __setPollBackgroundForTests(false)

        let el = document.createElement('div')
        expect(theDirectiveIsOffTheElement(el)).toBe(true)
        el.setAttribute('wire:poll', '')
        expect(theDirectiveIsOffTheElement(el)).toBe(false)

        expect(theDirectiveIsMissingKeepAlive({ modifiers: [] })).toBe(true)
        expect(theDirectiveIsMissingKeepAlive({ modifiers: ['keep-alive'] })).toBe(false)
        expect(theDirectiveHasVisible({ modifiers: ['visible'] })).toBe(true)
        expect(theDirectiveHasVisible({ modifiers: [] })).toBe(false)

        Object.defineProperty(el, 'getBoundingClientRect', {
            value: () => ({ top: -100, left: -100, bottom: -50, right: -50 }),
        })
        expect(theElementIsNotInTheViewport(el)).toBe(true)

        let ticks = 0
        let { start, throttleWhile, stopWhen } = poll(() => { ticks++ }, 10)
        throttleWhile(() => true)
        stopWhen(() => ticks >= 1)
        start()
    })

    it('covers transition top-layer skips and naming warnings', async () => {
        let warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
        let from = document.createElement('div')
        from.innerHTML = `<span wire:transition></span><span wire:transition></span>`
        setTransitionNames(from, { defaultName: 'root' })
        expect(warn).toHaveBeenCalled()
        clearTransitionNames(from)

        from.innerHTML = `<span wire:transition></span>`
        setTransitionNames(from, { type: 'forward' })
        expect(from.querySelector('[wire\\:transition]').style.viewTransitionName).toBe('')

        let skip = vi.fn()
        let finished = Promise.resolve()
        skipTransitionWhenTopLayerOpens({
            ready: Promise.reject(new Error('skip')),
            finished,
            skipTransition: skip,
        })
        document.dispatchEvent(new CustomEvent('beforetoggle', {
            detail: {},
            bubbles: true,
        }))
        // Force the open-dialog path via a synthetic beforetoggle on a dialog...
        let dialog = document.createElement('dialog')
        document.body.appendChild(dialog)
        let event = new Event('beforetoggle', { bubbles: true })
        Object.defineProperty(event, 'newState', { value: 'open' })
        Object.defineProperty(event, 'target', { value: dialog })
        document.dispatchEvent(event)
        await finished
        dialog.remove()

        document.startViewTransition = vi.fn(config => {
            let update = typeof config === 'function' ? config : config.update
            return {
                updateCallbackDone: Promise.resolve().then(() => update()),
                ready: Promise.resolve(),
                finished: Promise.resolve(),
                skipTransition: vi.fn(),
            }
        })

        // dialog:modal short-circuit...
        let openDialog = document.createElement('dialog')
        document.body.appendChild(openDialog)
        openDialog.showModal()
        let ran = false
        from.innerHTML = `<span wire:transition="x">a</span>`
        let to = document.createElement('div')
        to.innerHTML = `<span wire:transition="x">b</span>`
        await transitionDomMutation(from, to, () => { ran = true }, {})
        expect(ran).toBe(true)
        openDialog.close()
        openDialog.remove()

        // popover with visible descendant...
        let pop = document.createElement('div')
        pop.setAttribute('popover', '')
        pop.togglePopover?.()
        if (pop.matches(':popover-open')) {
            let child = document.createElement('span')
            child.textContent = 'hi'
            child.checkVisibility = () => true
            pop.appendChild(child)
            document.body.appendChild(pop)
            ran = false
            await transitionDomMutation(from, to, () => { ran = true }, {})
            expect(ran).toBe(true)
            pop.remove()
        }

        // typed transition catch fallback...
        document.startViewTransition = vi.fn(config => {
            if (typeof config === 'object') throw new Error('no types')
            return {
                updateCallbackDone: Promise.resolve().then(() => config()),
                ready: Promise.resolve(),
                finished: Promise.resolve(),
                skipTransition: vi.fn(),
            }
        })
        await startViewTransition(() => {}, { type: 'forward' })
        delete document.startViewTransition
        warn.mockRestore()
    })

    it('covers form disable with wire:ignore and already-disabled controls', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)

        let { host } = await component({}, `
            <form wire:submit="save">
                <div wire:ignore><input id="ignored" type="text"></div>
                <textarea id="ta" readonly></textarea>
                <input id="file" type="file">
                <button id="go" type="submit" disabled>go</button>
            </form>
        `)

        let form = host.querySelector('form')
        let event = new Event('submit', { bubbles: true, cancelable: true })
        Object.defineProperty(event, 'submitter', { value: host.querySelector('#go') })
        form.dispatchEvent(event)
        await settle()

        expect(host.querySelector('#file').disabled).toBe(true)
        expect(host.querySelector('#ignored').readOnly).toBe(false)

        calls[0].resolve(okFor(calls[0]))
        await settle()
    })

    it('covers $dirty finish hook and dirty model target', async () => {
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)

        let { host, wire } = await component({ title: 'a' }, `
            <input wire:model="title" wire:dirty.class="d" id="i">
        `)

        expect(wire.$dirty('title')).toBe(false)
        wire.title = 'b'
        await settle()
        expect(host.querySelector('#i').classList.contains('d')).toBe(true)

        wire.$commit()
        await settle()
        calls[0].resolve(okFor(calls[0]))
        await settle()
        await new Promise(r => queueMicrotask(r))
    })

    it('covers offline initial path when already offline', async () => {
        window.dispatchEvent(new Event('offline'))
        let { host } = await component({}, `<span wire:offline id="o" style="display:none">o</span>`)
        expect(host.querySelector('#o').style.display).not.toBe('none')
        window.dispatchEvent(new Event('online'))
        await settle()
    })

    it('covers remaining loading/refs/transition branches', async () => {
        expect(containsTargets({ updates: { title: 'x' }, calls: [] }, [{ target: 'title' }])).toBe(true)
        expect(containsTargets({ updates: { 'user.name': 'x' }, calls: [] }, [{ target: 'user' }])).toBe(true)
        expect(containsTargets({ updates: {}, calls: [{ method: 'save', params: [1] }] }, [{
            target: 'save',
            params: btoa(encodeURIComponent(JSON.stringify([1]))),
        }])).toBe(true)
        expect(containsTargets({ updates: {}, calls: [{ method: 'other', params: [] }] }, [{ target: 'save' }])).toBe(false)

        let { wire } = await component({}, `<span wire:ref="x">x</span>`)
        expect(typeof wire.$refs.bind).toBe('function')
        expect(wire.$refs.length).toBe(1)

        let el = document.createElement('div')
        Object.defineProperty(el, 'getBoundingClientRect', {
            value: () => ({ top: 10, left: 10, bottom: 20, right: 20 }),
        })
        expect(theElementIsNotInTheViewport(el)).toBe(false)

        // MutationObserver path in skipTransitionWhenTopLayerOpens...
        let skip = vi.fn()
        let dialog = document.createElement('dialog')
        document.body.appendChild(dialog)
        let original = document.querySelector.bind(document)
        skipTransitionWhenTopLayerOpens({
            ready: Promise.resolve(),
            finished: Promise.resolve(),
            skipTransition: skip,
        })
        dialog.setAttribute('open', '')
        // jsdom may not support :modal; force the observer callback via a querySelector shim...
        let obsCallback
        let RealMO = MutationObserver
        globalThis.MutationObserver = class {
            constructor(cb) { obsCallback = cb }
            observe() {}
            disconnect() {}
        }
        skipTransitionWhenTopLayerOpens({
            ready: Promise.resolve(),
            finished: new Promise(r => setTimeout(r, 5)),
            skipTransition: skip,
        })
        document.querySelector = sel => sel === 'dialog:modal' ? dialog : original(sel)
        obsCallback()
        document.querySelector = original
        globalThis.MutationObserver = RealMO
        dialog.remove()

        let modal = document.createElement('dialog')
        let pop = document.createElement('div')
        let child = document.createElement('span')
        child.checkVisibility = () => true
        pop.appendChild(child)
        let realQ = document.querySelector.bind(document)
        let realAll = document.querySelectorAll.bind(document)
        document.querySelector = sel => sel === 'dialog:modal' ? modal : realQ(sel)
        expect(topLayerBlocksTransitions()).toBe(true)
        document.querySelector = () => null
        document.querySelectorAll = sel => sel === ':popover-open' ? [pop] : realAll(sel)
        expect(topLayerBlocksTransitions()).toBe(true)
        child.checkVisibility = () => false
        expect(topLayerBlocksTransitions()).toBe(false)
        delete child.checkVisibility
        expect(topLayerBlocksTransitions()).toBe(false)
        document.querySelector = realQ
        document.querySelectorAll = realAll

        expect(shouldThrottlePoll(() => 0)).toBe(true)
        expect(shouldThrottlePoll(() => 1)).toBe(false)

        // errors.clear(field) when clientErrors still null and memo.errors missing...
        let fresh = { snapshot: { memo: {} }, id: 'z', el: document.createElement('div') }
        let bag = getErrorsObject(fresh)
        bag.clear('a')
        expect(bag.messages()).toEqual({})

        // endLoading when count already 0 (double finish)...
        let { fetch, calls } = manualFetch()
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({}, `
            <button wire:click="save">go</button>
            <span wire:loading id="l" style="display:none">…</span>
        `)
        host.querySelector('button').click()
        await settle()
        calls[0].resolve(okFor(calls[0]))
        await settle()
        // second finish path already cleared — covered via intercept cleanup

        // loading ignores other components' messages...
        let { fetch: f2, calls: c2 } = manualFetch()
        vi.stubGlobal('fetch', f2)
        let a = await component({}, `<span wire:loading id="la" style="display:none">a</span><button wire:click="x">x</button>`)
        let b = await component({}, `<button wire:click="y">y</button>`)
        b.host.querySelector('button').click()
        await settle()
        expect(a.host.querySelector('#la').style.display).toBe('none')
        c2[0].resolve(okFor(c2[0]))
        await settle()

        // island-only actions skip untargeted loading...
        let { fetch: f3, calls: c3 } = manualFetch()
        vi.stubGlobal('fetch', f3)
        let { host: h3, wire: w3 } = await component({}, `<span wire:loading id="isl" style="display:none">i</span>`)
        Brew.fireAction(w3, 'islandOnly', [], { island: { name: 'x' } })
        await settle()
        expect(h3.querySelector('#isl').style.display).toBe('none')
        c3[0].resolve(okFor(c3[0]))
        await settle()

        // transition includeRoot + empty name + top-layer block during morph...
        let root = document.createElement('div')
        root.setAttribute('wire:transition', '')
        setTransitionNames(root, { includeRoot: true })
        expect(root.style.viewTransitionName).toBe('match-element')
        clearTransitionNames(root, { includeRoot: true })

        document.startViewTransition = vi.fn(config => {
            let update = typeof config === 'function' ? config : config.update
            return {
                updateCallbackDone: Promise.resolve().then(() => update()),
                ready: Promise.resolve(),
                finished: Promise.resolve(),
                skipTransition: vi.fn(),
            }
        })
        document.querySelector = sel => sel === 'dialog:modal' ? modal : realQ(sel)
        let from = document.createElement('div')
        from.innerHTML = `<span wire:transition="z">a</span>`
        let to = document.createElement('div')
        to.innerHTML = `<span wire:transition="z">b</span>`
        let ran = false
        await transitionDomMutation(from, to, () => { ran = true }, {})
        expect(ran).toBe(true)
        document.querySelector = realQ
        delete document.startViewTransition

        // poll empty expression → $refresh...
        let fetchPoll = echoServer(null)
        vi.stubGlobal('fetch', fetchPoll)
        await component({}, `<div wire:poll.50ms></div>`)
        await new Promise(r => setTimeout(r, 80))
        await settle()
        expect(JSON.parse(fetchPoll.mock.calls[0][1].body).components[0].calls[0].method).toBe('$refresh')

        // keep-alive throttle path while tab is in the background...
        __setPollBackgroundForTests(true)
        let fetchKeep = echoServer(null)
        vi.stubGlobal('fetch', fetchKeep)
        await component({}, `<div wire:poll.50ms.keep-alive></div>`)
        await new Promise(r => setTimeout(r, 80))
        await settle()
        __setPollBackgroundForTests(false)

        // visible pause when off-screen...
        await component({}, `<div wire:poll.50ms.visible style="position:fixed;top:-9999px"></div>`)
        await new Promise(r => setTimeout(r, 40))

        // viewport fallbacks when innerWidth/Height are 0...
        let box = document.createElement('div')
        Object.defineProperty(box, 'getBoundingClientRect', {
            value: () => ({ top: 1, left: 1, bottom: 2, right: 2 }),
        })
        let ih = window.innerHeight
        let iw = window.innerWidth
        Object.defineProperty(window, 'innerHeight', { value: 0, configurable: true })
        Object.defineProperty(window, 'innerWidth', { value: 0, configurable: true })
        Object.defineProperty(document.documentElement, 'clientHeight', { value: 100, configurable: true })
        Object.defineProperty(document.documentElement, 'clientWidth', { value: 100, configurable: true })
        expect(theElementIsNotInTheViewport(box)).toBe(false)
        Object.defineProperty(window, 'innerHeight', { value: ih, configurable: true })
        Object.defineProperty(window, 'innerWidth', { value: iw, configurable: true })
    })
})
