import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import { bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, echoServer, settle, tick, reply, jsonResponse } from './helpers.js'

let Brew

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(stubDialog)
afterEach(cleanPage)

function bodies(fetch) {
    return fetch.mock.calls.map(([, options]) => JSON.parse(options.body).components[0])
}

async function component(data, inner, memo = {}) {
    let snap = snapshot(data, memo)
    let host = await mount(rootHtml(snap, inner))

    return { snap, host, wire: Brew.find(snap.memo.id) }
}

function type(el, value, event = 'input') {
    el.value = value
    el.dispatchEvent(new Event(event, { bubbles: true }))
}

describe('wire:<event>', () => {
    it('passes evaluated params and supports key modifiers', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({ num: 7 }, `<input wire:keydown.enter="save(num, 'x')"><button wire:click="remove(1)">r</button>`)

        host.querySelector('input').dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true }))
        host.querySelector('input').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
        await settle()
        host.querySelector('button').click()
        await settle()

        expect(bodies(fetch).map(b => b.calls[0])).toEqual([
            { method: 'save', params: [7, 'x'], metadata: {} },
            { method: 'remove', params: [1], metadata: {} },
        ])
    })

    it('prevents form submission implicitly and records the submitter', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let origins = []
        let off = Brew.interceptAction(({ action }) => origins.push(action.origin.targetEl?.id))
        let { host } = await component({}, `
            <form id="a" wire:submit="save"><button id="sa" type="submit">go</button></form>
            <form id="b" wire:submit.prevent="save2"><input id="sb" type="submit"></form>
            <form id="c" wire:submit="save3"></form>`)

        let events = ['a', 'b', 'c'].map(id => {
            let event = new Event('submit', { bubbles: true, cancelable: true })

            if (id === 'a') Object.defineProperty(event, 'submitter', { value: host.querySelector('#sa') })

            host.querySelector('#' + id).dispatchEvent(event)

            return event
        })
        await settle()

        expect(events.every(event => event.defaultPrevented)).toBe(true)
        expect(origins).toEqual(['sa', 'sb', undefined])
        off()
    })

    it('strips Brew-only modifiers before binding Alpine listeners', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({}, `<button wire:click.prepend.append.self="go">x</button>`)

        host.querySelector('button').click()
        await settle()

        expect(fetch).toHaveBeenCalledTimes(1)
    })

    it('handles custom events with Brew options in the detail', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let intercepted = []
        let { host } = await component({}, `<div id="a" wire:picked="choose"></div><div id="b" wire:picked="choose"></div><div id="c" wire:picked="choose('fixed')"></div><div id="d" wire:picked="choose"></div>`)

        let fire = (id, brew) => host.querySelector('#' + id).dispatchEvent(new CustomEvent('picked', { detail: { brew } }))

        fire('a', { defaultParams: [1, 'two'], interceptAction: ({ action }) => intercepted.push(action.name) })
        await settle()
        fire('b', { defaultParams: 3 })
        await settle()
        fire('c', { defaultParams: 4 })
        await settle()
        fire('d', undefined)
        await settle()

        expect(bodies(fetch).map(b => b.calls[0].params)).toEqual([[1, 'two'], [3], ['fixed'], []])
        expect(intercepted).toEqual(['choose'])
    })

    it('leaves reserved and custom directive names alone', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let custom = []
        Brew.directive('flash', ({ el, directive, component, $wire, cleanup }) => {
            custom.push([el.tagName, directive.expression, component.name, typeof $wire.$refresh])
            cleanup(() => custom.push('cleanup'))
        })
        let { host } = await component({}, `<span wire:flash="hello"></span><span wire:poll="tick"></span>`)

        host.querySelector('[wire\\:flash]').dispatchEvent(new Event('flash'))
        host.querySelector('[wire\\:poll]').dispatchEvent(new Event('poll'))
        host.querySelector('[wire\\:flash]').removeAttribute('wire:flash')
        await settle()

        expect(fetch).not.toHaveBeenCalled()
        expect(custom).toEqual([['SPAN', 'hello', 'counter', 'function'], 'cleanup'])
    })
})

describe('wire:model', () => {
    beforeEach(() => vi.stubGlobal('fetch', echoServer(null)))

    it('defers syncing until the next action by default', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host, wire } = await component({ name: '' }, `<input wire:model="name"><button wire:click="save">s</button>`)

        type(host.querySelector('input'), 'Ana')
        await settle()
        expect(wire.name).toBe('Ana')
        expect(fetch).not.toHaveBeenCalled()

        host.querySelector('button').click()
        await settle()
        expect(bodies(fetch)[0].updates).toEqual({ name: 'Ana' })
    })

    it('reflects server state back into the input', async () => {
        let { host, wire } = await component({ name: 'x' }, `<input wire:model="name">`)

        expect(host.querySelector('input').value).toBe('x')
        wire.name = 'y'
        await tick()
        expect(host.querySelector('input').value).toBe('y')
    })

    it('debounces live text input by 150ms and marks it model.live', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({ q: '' }, `<input wire:model.live="q">`)

        type(host.querySelector('input'), 'a')
        type(host.querySelector('input'), 'ab')
        await tick(60)
        expect(fetch).not.toHaveBeenCalled()
        await tick(150)
        await settle()

        expect(fetch).toHaveBeenCalledTimes(1)
        expect(bodies(fetch)[0]).toMatchObject({ updates: { q: 'ab' }, calls: [{ method: '$commit', metadata: { type: 'model.live' } }] })
    })

    it('honors explicit debounce and throttle durations', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({ a: '', b: '' }, `<input id="a" wire:model.live.debounce.20ms="a"><input id="b" wire:model.live.throttle.50ms="b"><input id="c" wire:model.live.throttle="b">`)

        type(host.querySelector('#a'), 'x')
        await tick(40)
        await settle()
        expect(fetch).toHaveBeenCalledTimes(1)

        type(host.querySelector('#b'), '1')
        type(host.querySelector('#b'), '12')
        await settle()
        expect(fetch).toHaveBeenCalledTimes(2)
        await tick(60)
        type(host.querySelector('#b'), '123')
        await settle()
        expect(fetch).toHaveBeenCalledTimes(3)

        type(host.querySelector('#c'), 'z')
        await settle()
        expect(fetch).toHaveBeenCalledTimes(4)
    })

    it('sends checkbox and select changes immediately when live', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host, wire } = await component({ agree: false, picks: [], size: 's' }, `
            <input id="agree" type="checkbox" wire:model.live="agree">
            <input class="pick" type="checkbox" value="a" wire:model="picks"><input class="pick" type="checkbox" value="b" wire:model="picks">
            <select wire:model.live="size"><option value="s">S</option><option value="m">M</option></select>`)

        host.querySelector('#agree').click()
        await settle()
        host.querySelectorAll('.pick')[1].click()
        let select = host.querySelector('select')
        select.value = 'm'
        select.dispatchEvent(new Event('change', { bubbles: true }))
        await settle()

        expect(wire.picks).toEqual(['b'])
        expect(bodies(fetch).map(b => b.updates)).toEqual([{ agree: true }, { picks: ['b'], size: 'm' }])
    })

    it('syncs on blur, change, and enter network triggers', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({ a: '', b: '', c: '', d: '' }, `
            <input id="a" wire:model.live.blur="a"><input id="b" wire:model.live.change="b">
            <input id="c" wire:model.live.enter="c"><input id="d" wire:model.lazy="d">`)
        let el = id => host.querySelector('#' + id)

        el('a').dispatchEvent(new Event('blur'))
        await settle()
        expect(fetch).toHaveBeenCalledTimes(0)

        type(el('a'), 'x')
        el('a').dispatchEvent(new Event('blur'))
        await settle()
        type(el('b'), 'y')
        el('b').dispatchEvent(new Event('change'))
        await settle()
        type(el('c'), 'z')
        el('c').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
        await settle()
        type(el('d'), 'w')
        el('d').dispatchEvent(new Event('change'))
        await settle()

        expect(bodies(fetch).map(b => b.updates)).toEqual([{ a: 'x' }, { b: 'y' }, { c: 'z' }, { d: 'w' }])
    })

    it('applies local modifiers before .live and supports nested paths', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host, wire } = await component({ form: { age: 0 }, list: [{ title: 'a' }], tags: { x: '' } }, `
            <input id="age" wire:model.number.live.debounce="form.age">
            <input id="title" wire:model.blur.defer="list.0.title">
            <input id="tag" wire:model.deep="['tags'].x">
            <input id="self" wire:model.self.change="tags.x">`)

        type(host.querySelector('#age'), '42')
        await tick(200)
        await settle()
        expect(wire.form.age).toBe(42)
        expect(bodies(fetch)[0].updates).toEqual({ 'form.age': 42 })

        type(host.querySelector('#title'), 'changed')
        expect(wire.list[0].title).toBe('a')
        host.querySelector('#title').dispatchEvent(new Event('blur'))
        expect(wire.list[0].title).toBe('changed')

        type(host.querySelector('#tag'), 't')
        expect(wire.tags.x).toBe('t')
    })

    it('strips debounce and throttle from local x-model modifiers', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host, wire } = await component({ a: '', b: '', c: '' }, `
            <input id="a" wire:model.debounce.30ms="a"><input id="b" wire:model.throttle="b"><input id="c" wire:model.debounce.live="c">`)

        type(host.querySelector('#a'), 'x')
        type(host.querySelector('#b'), 'y')
        await settle()

        expect(wire.a).toBe('x')
        expect(wire.b).toBe('y')
        expect(fetch).not.toHaveBeenCalled()

        type(host.querySelector('#c'), 'z')
        await tick(200)
        await settle()
        expect(bodies(fetch)[0].updates).toEqual({ a: 'x', b: 'y', c: 'z' })
    })

    it('warns about a missing expression or unknown property', async () => {
        let warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

        await component({ name: '' }, `<input wire:model=""><input wire:model="nope.deep">`)

        expect(warn.mock.calls.map(c => c[0])).toEqual([
            'Brew: [wire:model] is missing a value.',
            'Brew: [wire:model="nope.deep"] property does not exist on component: [counter]',
        ])
    })
})

describe('wire:init, wire:ignore, wire:replace', () => {
    it('calls the init action, or $refresh when empty', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)

        await component({}, `<div wire:init="load(5)"></div><div wire:init></div>`)
        await settle()

        expect(bodies(fetch)[0].calls.map(c => [c.method, c.params])).toEqual([['load', [5]], ['$refresh', []]])
    })

    it('protects ignored regions and replaces marked ones during morph', async () => {
        let snap = snapshot({ n: 1 })
        let markup = n => `
            <div id="ignore" wire:ignore>${n}</div>
            <div id="self" wire:ignore.self data-n="${n}"><span>${n}</span></div>
            <div id="children" wire:ignore.children data-n="${n}"><span>${n}</span></div>
            <div id="replace" wire:replace><span>${n}</span></div>
            <div id="replace-self" wire:replace.self data-n="${n}"><span>${n}</span></div>
            <p id="plain">${n}</p>`
        vi.stubGlobal('fetch', echoServer(data => markup(data.n), data => { data.n = 2 }))
        let host = await mount(rootHtml(snap, markup(1)))
        let el = id => host.querySelector('#' + id)
        let replacedChild = el('replace').firstElementChild

        await Brew.first().bump()
        await settle()

        expect(el('ignore').textContent).toBe('1')
        expect(el('self').dataset.n).toBe('1')
        expect(el('self').textContent).toBe('2')
        expect(el('children').dataset.n).toBe('2')
        expect(el('children').textContent).toBe('1')
        expect(el('replace').firstElementChild).not.toBe(replacedChild)
        expect(el('replace').textContent).toBe('2')
        expect(el('replace-self').dataset.n).toBe('2')
        expect(el('plain').textContent).toBe('2')
    })
})

describe('morph', () => {
    it('keeps keyed elements, fires morph hooks, and handles block markers', async () => {
        let snap = snapshot({ items: ['a', 'b'] })
        let list = items => `<!--[if BLOCK]><![endif]-->${items.map(i => `<li wire:key="${i}">${i}</li>`).join('')}<!--[if ENDBLOCK]><![endif]--><p id="byid"></p>`
        vi.stubGlobal('fetch', echoServer(data => `<ul>${list(data.items)}</ul>`, data => { data.items = ['b', 'c'] }))
        let host = await mount(rootHtml(snap, `<ul>${list(['a', 'b'])}</ul><em>gone</em>`))
        let b = host.querySelector('[wire\\:key="b"]')
        let hooks = new Set()
        let offs = ['morph', 'morphed', 'morph.updating', 'morph.updated', 'morph.removing', 'morph.removed', 'morph.adding', 'morph.added']
            .map(name => Brew.hook(name, () => hooks.add(name)))

        await Brew.first().shuffle()
        await settle()

        expect([...host.querySelectorAll('li')].map(li => li.textContent)).toEqual(['b', 'c'])
        expect(host.querySelector('[wire\\:key="b"]')).toBe(b)
        expect(host.querySelector('em')).toBeNull()
        expect([...hooks].sort()).toEqual(['morph', 'morph.added', 'morph.adding', 'morph.removed', 'morph.removing', 'morph.updated', 'morph.updating', 'morphed'])
        offs.forEach(off => off())
    })

    it('skips the morph when the response has no html', async () => {
        vi.stubGlobal('fetch', echoServer(null))
        let morphs = []
        let off = Brew.hook('morph', () => morphs.push(1))
        let { wire } = await component({}, '<b>keep</b>')

        await wire.save()

        expect(morphs).toEqual([])
        off()
    })

    it('leaves nested components to themselves and preserves unchanged children', async () => {
        let child = snapshot({ c: 1 }, { name: 'child' })
        let parent = snapshot({ p: 1 })
        let childHtml = rootHtml(child, '<i>child-1</i>')
        // The server renders an unchanged child as a bare root without a snapshot...
        let bareChild = `<div wire:id="${child.memo.id}"></div>`
        vi.stubGlobal('fetch', echoServer(data => `<b>${data.p}</b>${bareChild}`, data => { data.p = 2 }))
        let host = await mount(rootHtml(parent, `<b>1</b>${childHtml}`))

        await Brew.find(parent.memo.id).bump()
        await settle()

        expect(host.querySelector('b').textContent).toBe('2')
        expect(host.querySelector('i').textContent).toBe('child-1')
        expect(Brew.find(child.memo.id).c).toBe(1)
    })

    it('morphs a nested component inside its parent and inside custom elements', async () => {
        customElements.define('brew-host', class extends HTMLElement {})
        let child = snapshot({ c: 1 }, { name: 'child' })
        let parent = snapshot({})
        vi.stubGlobal('fetch', echoServer(data => `<i>${data.c}</i>`, data => { data.c = 2 }))
        let host = await mount(rootHtml(parent, `<brew-host>${rootHtml(child, '<i>1</i>')}</brew-host>`))

        await Brew.find(child.memo.id).bump()
        await settle()

        expect(host.querySelector('i').textContent).toBe('2')
    })

    it('re-initializes children rendered with a fresh snapshot', async () => {
        let parent = snapshot({ show: false })
        let child = snapshot({ c: 5 }, { name: 'fresh' })
        vi.stubGlobal('fetch', echoServer(data => data.show ? rootHtml(child, '<i>new</i>', { key: 'n' }) : '', data => { data.show = true }))
        await mount(rootHtml(parent, ''))

        await Brew.find(parent.memo.id).open()
        await settle()

        expect(Brew.find(child.memo.id).c).toBe(5)
    })
})

describe('lifecycle', () => {
    it('wires directives added to live elements', async () => {
        let fetch = echoServer(null)
        vi.stubGlobal('fetch', fetch)
        let { host } = await component({}, `<button>b</button>`)

        host.querySelector('button').setAttribute('wire:click', 'late')
        host.querySelector('button').setAttribute('data-x', '1')
        await settle()
        host.querySelector('button').click()
        await settle()

        expect(bodies(fetch)[0].calls[0].method).toBe('late')
    })

    it('ignores wire: attributes outside components', async () => {
        let host = await mount(`<button wire:click="nothing">b</button><template><b wire:click="x"></b></template>`)

        host.querySelector('button').setAttribute('wire:dblclick', 'y')
        await settle()

        expect(Brew.all()).toEqual([])
    })

    it('marks the initial render as finished', () => {
        expect(Brew.initialRenderIsFinished).toBe(true)
    })
})

describe('component internals', () => {
    it('refuses double initialization and missing snapshots', async () => {
        let { Component } = await import('../src/component.js')
        let { initComponent, findComponent, destroyComponent } = await import('../src/store.js')
        let { snap, host } = await component({}, '')
        let el = host.firstElementChild

        expect(() => new Component(el)).toThrow('Component already initialized')

        let clone = el.cloneNode()
        expect(() => initComponent(clone)).toThrow('Component already registered')

        let empty = document.createElement('div')
        empty.setAttribute('wire:snapshot', 'null')
        empty.setAttribute('wire:id', 'zz')
        expect(() => new Component(empty)).toThrow('Snapshot missing on Brew component with id: zz')

        expect(() => findComponent('nope')).toThrow('Component not found: nope')
        expect(findComponent('nope', false)).toBeUndefined()
        expect(findComponent(snap.memo.id).id).toBe(snap.memo.id)
        destroyComponent('nope')
    })

    it('throws when no component encloses an element in strict mode', async () => {
        let { findComponentByEl } = await import('../src/store.js')

        expect(() => findComponentByEl(document.body)).toThrow('Could not find Brew component in DOM tree')
    })

    it('defaults missing effects and links parents and children', async () => {
        let child = snapshot({}, { name: 'child' })
        let parent = snapshot({})
        let host = await mount(rootHtml(parent, `<section>${rootHtml(child, '')}</section>`))
        let bare = document.createElement('div')

        bare.setAttribute('wire:snapshot', JSON.stringify(snapshot({}, { id: 'bare' })))
        bare.setAttribute('wire:id', 'bare')
        host.appendChild(bare)
        await settle()

        let parentComponent = Brew.find(parent.memo.id).__instance
        let childComponent = Brew.find(child.memo.id).__instance

        expect(Brew.find('bare').__instance.effects).toEqual({})
        expect(parentComponent.children).toEqual([childComponent])
        expect(childComponent.parent).toBe(parentComponent)
        expect(parentComponent.parent).toBeUndefined()

        let removed = []
        let remove = childComponent.addCleanup(() => removed.push('kept'))
        remove()
        expect(childComponent.cleanups.length).toBeGreaterThanOrEqual(0)
    })
})
