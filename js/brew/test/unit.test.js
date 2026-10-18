import { describe, it, expect, vi, beforeEach } from 'vitest'
import Alpine from 'alpinejs'
import {
    WeakBag, dispatch, parsePathSegments, dataGet, dataSet, diffAndConsolidate, diffAndPatchRecursive,
    extractData, isSynthetic, getCsrfToken, getUpdateUri, walkUpwards, deepClone,
} from '../src/utils.js'
import { on, trigger, triggerAsync } from '../src/hooks.js'
import { Directive, extractDirective, matchesForBrewDirective, directive, customDirectiveHasBeenRegistered } from '../src/directives.js'
import { contextualizeExpression, evaluateActionExpression } from '../src/evaluator.js'
import { showHtmlModal } from '../src/modal.js'
import { InterceptorRegistry } from '../src/request/interceptor.js'

beforeEach(() => {
    document.head.innerHTML = ''
    document.body.innerHTML = ''
    delete window.brewScriptConfig
})

describe('WeakBag', () => {
    it('collects values per key', () => {
        let bag = new WeakBag()
        let key = {}
        let seen = []

        expect(bag.get(key)).toEqual([])
        bag.add(key, 1)
        bag.add(key, 2)
        bag.each(key, value => seen.push(value))

        expect(seen).toEqual([1, 2])
        bag.remove(key)
        expect(bag.get(key)).toEqual([])
    })
})

describe('dispatch', () => {
    it('fires a bubbling cancelable custom event', () => {
        let received

        document.addEventListener('brew:test', e => received = e, { once: true })
        dispatch(document.body, 'brew:test', { a: 1 })

        expect(received.detail).toEqual({ a: 1 })
        expect(received.bubbles).toBe(true)
        expect(received.cancelable).toBe(true)
    })

    it('can dispatch without bubbling', () => {
        let received

        document.body.addEventListener('brew:quiet', e => received = e, { once: true })
        dispatch(document.body, 'brew:quiet', undefined, false)

        expect(received.bubbles).toBe(false)
        expect(received.detail).toEqual({})
    })
})

describe('paths', () => {
    it('parses dot and bracket notation', () => {
        expect(parsePathSegments('')).toEqual([])
        expect(parsePathSegments('a.b')).toEqual(['a', 'b'])
        expect(parsePathSegments("a['b'].c")).toEqual(['a', 'b', 'c'])
        expect(parsePathSegments('a[0]')).toEqual(['a', '0'])
        expect(parsePathSegments('["x"].y')).toEqual(['x', 'y'])
    })

    it('reads nested values', () => {
        let data = { a: { b: [1, { c: 2 }] } }

        expect(dataGet(data, '')).toBe(data)
        expect(dataGet(data, 'a.b.1.c')).toBe(2)
        expect(dataGet(data, 'a.missing.deep')).toBeUndefined()
    })

    it('writes nested values, creating containers', () => {
        let data = { list: [1, 2], tags: ['a'] }

        dataSet(data, 'x', 1)
        dataSet(data, 'a.b.c', 2)
        dataSet(data, 'list.2', 3)
        dataSet(data, 'tags.5', 'f')
        dataSet(data, 'list.name', 'n')

        expect(data.x).toBe(1)
        expect(data.a).toEqual({ b: { c: 2 } })
        expect(data.list.slice()).toEqual([1, 2, 3])
        expect(data.list.name).toBe('n')
        expect(data.tags).toEqual({ 0: 'a', 5: 'f' })
    })
})

describe('diffAndConsolidate', () => {
    it('returns nothing for identical trees', () => {
        let same = { a: 1 }

        expect(diffAndConsolidate(same, same)).toEqual({})
        expect(diffAndConsolidate({ a: 1 }, { a: 1 })).toEqual({})
    })

    it('reports leaf and type changes', () => {
        expect(diffAndConsolidate({ a: 1, b: 'x' }, { a: 2, b: 'x' })).toEqual({ a: 2 })
        expect(diffAndConsolidate({ a: '1' }, { a: 1 })).toEqual({ a: 1 })
        expect(diffAndConsolidate({ a: { x: 1 } }, { a: [1] })).toEqual({ a: [1] })
        expect(diffAndConsolidate({ a: [1] }, { a: { x: 1 } })).toEqual({ a: { x: 1 } })
        expect(diffAndConsolidate({ a: null }, { a: { x: 1 } })).toEqual({ a: { x: 1 } })
        expect(diffAndConsolidate({ a: { x: 1 } }, { a: null })).toEqual({ a: null })
    })

    it('keeps granular diffs when an empty or missing value becomes an object', () => {
        expect(diffAndConsolidate({ f: [] }, { f: { one: { value: 'x' } } })).toEqual({ 'f.one.value': 'x' })
        expect(diffAndConsolidate({ a: 1 }, { f: { a: 1, b: 2 } })).toEqual({ 'f.a': 1, 'f.b': 2 })
        expect(diffAndConsolidate({}, { f: { a: 1, b: 2 } })).toEqual({ f: { a: 1, b: 2 } })
    })

    it('sends a nested node whole when its shape changes', () => {
        expect(diffAndConsolidate({ list: [1, 2] }, { list: [1, 2, 3] })).toEqual({ list: [1, 2, 3] })
        expect(diffAndConsolidate({ o: { a: 1, b: 2 } }, { o: { a: 1, c: 2 } })).toEqual({ o: { a: 1, c: 2 } })
        expect(diffAndConsolidate({ o: { a: 1, b: 2 } }, { o: { b: 2, a: 1 } })).toEqual({ o: { b: 2, a: 1 } })
    })

    it('handles root shape changes per key', () => {
        expect(diffAndConsolidate({ a: 1 }, { a: 1, b: 2 })).toEqual({ b: 2 })
        expect(diffAndConsolidate({ a: 1, b: 2 }, { a: 1, c: 3 })).toEqual({ c: 3 })
        expect(diffAndConsolidate({ a: 1, b: 2 }, { b: 2, a: 3 })).toEqual({ a: 3 })
    })

    it('consolidates when every child changed', () => {
        expect(diffAndConsolidate({ o: { a: 1, b: 2 } }, { o: { a: 3, b: 4 } })).toEqual({ o: { a: 3, b: 4 } })
        expect(diffAndConsolidate({ o: { a: 1 } }, { o: { a: 2 } })).toEqual({ 'o.a': 2 })
        expect(diffAndConsolidate({ o: { a: 1, b: 2 } }, { o: { a: 3, b: 2 } })).toEqual({ 'o.a': 3 })
    })

    it('does not double-consolidate above an already consolidated child', () => {
        let left = { o: { list: [1], n: 1 } }
        let right = { o: { list: [1, 2], n: 2 } }

        expect(diffAndConsolidate(left, right)).toEqual({ 'o.list': [1, 2], 'o.n': 2 })
    })
})

describe('diffAndPatchRecursive', () => {
    it('patches only changed leaves and keeps identity', () => {
        let target = { a: { x: 1, y: 2 }, list: [1, 2, 3] }
        let inner = target.a

        diffAndPatchRecursive(deepClone(target), { a: { x: 1, y: 5 }, list: [1, 3], extra: { n: 1 } }, target)

        expect(target.a).toBe(inner)
        expect(target).toEqual({ a: { x: 1, y: 5 }, list: [1, 3], extra: { n: 1 } })
    })

    it('replaces mismatched containers', () => {
        let target = { a: [1], b: 'x' }

        diffAndPatchRecursive({ a: [1], b: 'x' }, { a: { k: 1 }, b: { k: 2 } }, target)

        expect(target).toEqual({ a: { k: 1 }, b: { k: 2 } })
    })

    it('rewrites an object when existing keys shift position', () => {
        let target = { a: 1, b: 2 }

        diffAndPatchRecursive({ a: 1, b: 2 }, { z: 0, a: 1, b: 2 }, target)

        expect(Object.keys(target)).toEqual(['z', 'a', 'b'])
    })

    it('removes array items from the end and non-numeric keys', () => {
        let target = [1, 2, 3, 4]

        diffAndPatchRecursive([1, 2, 3, 4], [1, 2], target)
        expect(target).toEqual([1, 2])

        let object = { a: 1, b: 2, c: 3 }

        diffAndPatchRecursive({ a: 1, b: 2, c: 3 }, { a: 1 }, object)
        expect(object).toEqual({ a: 1 })
    })
})

describe('synthetic tuples', () => {
    it('extracts raw values from nested tuples', () => {
        let payload = { list: [[1, 2], { s: 'arr' }], when: ['2026-01-01', { s: 'dt', type: 'instant' }], plain: [1, 2], pair: [1, { x: 1 }] }

        expect(extractData(payload)).toEqual({ list: [1, 2], when: '2026-01-01', plain: [1, 2], pair: [1, { x: 1 }] })
        expect(isSynthetic([1, { s: 'x' }])).toBe(true)
        expect(isSynthetic([1, null])).toBe(false)
        expect(isSynthetic('x')).toBe(false)
    })
})

describe('csrf and update uri', () => {
    it('prefers a csrf meta tag, then data-csrf, then script config', () => {
        window.brewScriptConfig = { csrf: 'from-config' }
        expect(getCsrfToken()).toBe('from-config')

        document.body.innerHTML = '<script data-csrf="from-script"></script>'
        expect(getCsrfToken()).toBe('from-script')

        document.head.innerHTML = '<meta name="csrf-token" content="from-meta">'
        expect(getCsrfToken()).toBe('from-meta')
    })

    it('throws when no token can be found', () => {
        expect(() => getCsrfToken()).toThrow('No CSRF token')

        window.brewScriptConfig = {}
        expect(() => getCsrfToken()).toThrow('No CSRF token')
    })

    it('resolves the update uri', () => {
        expect(getUpdateUri()).toBe('/brew/update')

        window.brewScriptConfig = { uri: '/cfg/update' }
        expect(getUpdateUri()).toBe('/cfg/update')

        document.body.innerHTML = '<script data-update-uri="/tag/update"></script>'
        expect(getUpdateUri()).toBe('/tag/update')
    })
})

describe('walkUpwards', () => {
    it('follows teleported elements back to their origin', () => {
        document.body.innerHTML = '<section id="origin"><template></template></section><div id="teleported"><span></span></div>'

        let teleported = document.getElementById('teleported')
        teleported._x_teleportBack = document.querySelector('template')

        let ids = []
        let found = walkUpwards(teleported.querySelector('span'), node => {
            ids.push(node.id || node.tagName)

            return node.id === 'origin' ? node : undefined
        })

        expect(found.id).toBe('origin')
        expect(ids).toEqual(['SPAN', 'teleported', 'TEMPLATE', 'origin'])
        expect(walkUpwards(document.body, () => undefined)).toBeUndefined()
    })
})

describe('hooks', () => {
    it('runs listeners and threads finishers', () => {
        let calls = []
        let offA = on('unit.a', value => {
            calls.push(value)

            return result => result + 1
        })
        let offB = on('unit.a', () => () => undefined)

        expect(trigger('unit.a', 'x')(1)).toBe(2)
        expect(calls).toEqual(['x'])

        offA()
        offB()

        expect(trigger('unit.a', 'y')(5)).toBe(5)
        expect(trigger('unit.none')(3)).toBe(3)
    })

    it('awaits async listeners', async () => {
        let off = on('unit.async', async () => result => result * 2)
        on('unit.async', async () => 'not a function')

        let finish = await triggerAsync('unit.async')

        expect(finish(4)).toBe(8)
        expect((await triggerAsync('unit.empty'))(1)).toBe(1)

        off()
    })
})

describe('directive parsing', () => {
    it('recognizes wire: attributes and modifiers', () => {
        document.body.innerHTML = `<button wire:click.prevent.debounce.100ms="save(1, 'a,b', [2, 3]), reset"></button>`
        let el = document.body.firstChild
        let parsed = extractDirective(el, 'wire:click.prevent.debounce.100ms')

        expect(matchesForBrewDirective('wire:model')).toBe(true)
        expect(matchesForBrewDirective('x-on:click')).toBe(false)
        expect(parsed.value).toBe('click')
        expect(parsed.modifiers).toEqual(['prevent', 'debounce', '100ms'])
        expect(parsed.raw).toBe(parsed.rawName)
        expect(parsed.method).toBe('save')
        expect(parsed.params).toEqual([1, 'a,b', [2, 3]])
        expect(parsed.methods).toEqual([{ method: 'save', params: [1, 'a,b', [2, 3]] }, { method: 'reset', params: [] }])
    })

    it('handles escaped quotes, nested calls, and bad params', () => {
        document.body.innerHTML = `<b wire:x="a('it\\'s', fn(1, 2)), b(oops("></b>`
        let el = document.body.firstChild
        let warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

        let methods = new Directive('x', [], 'wire:x', el).methods

        expect(methods[0].method).toBe('a')
        expect(methods[1]).toEqual({ method: 'b', params: [] })
        expect(warn).toHaveBeenCalled()

        warn.mockRestore()
    })

    it('ignores duplicate custom directive registrations', () => {
        let first = vi.fn()

        directive('unit-custom', first)
        directive('unit-custom', vi.fn())

        expect(customDirectiveHasBeenRegistered('unit-custom')).toBe(true)
        expect(customDirectiveHasBeenRegistered('unit-other')).toBe(false)
    })
})

describe('contextualizeExpression', () => {
    let el

    beforeEach(() => {
        document.body.innerHTML = '<div id="outer"><div wire:id="x"><div id="mid"><span id="leaf"></span></div></div></div>'
        document.getElementById('outer')._x_dataStack = [{ outside: 1 }]
        document.getElementById('mid')._x_dataStack = [{ open: true, $magic: 1, label: 'a' }]
        el = document.getElementById('leaf')
        el._x_dataStack = [{ label: 'b' }]
    })

    it('prefixes bare identifiers with $wire', () => {
        expect(contextualizeExpression('save(count, 1)', el)).toBe('$wire.save($wire.count, 1)')
        expect(contextualizeExpression('$refresh', el)).toBe('$wire.$refresh')
        expect(contextualizeExpression('outside', el)).toBe('$wire.outside')
    })

    it('leaves strings, comments, keys, members, and Alpine scope alone', () => {
        expect(contextualizeExpression("go('name', {key: value}) /* c */ // d", el))
            .toBe("$wire.go('name', {key: $wire.value}) /* c */ // d")
        expect(contextualizeExpression('a.b + JSON.stringify($event)', el)).toBe('$wire.a.b + JSON.stringify($event)')
        expect(contextualizeExpression('toggle(open, label)', el)).toBe('$wire.toggle(open, label)')
    })

    it('targets $wire for a leading action named like an Alpine key', () => {
        expect(contextualizeExpression('open()', el)).toBe('$wire.open()')
        expect(contextualizeExpression('  open ; x', el)).toBe('  $wire.open ; $wire.x')
        expect(contextualizeExpression('open', el)).toBe('$wire.open')
        expect(contextualizeExpression('open = false', el)).toBe('open = false')
        expect(contextualizeExpression('label /* c */ ()', el)).toBe('$wire.label /* c */ ()')
    })

    it('evaluates against Alpine and reports errors', () => {
        let warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
        let error = vi.spyOn(console, 'error').mockImplementation(() => {})
        let raw = vi.spyOn(Alpine, 'evaluateRaw')

        raw.mockImplementationOnce(() => 42)
        expect(evaluateActionExpression(el, 'x')).toBe(42)
        expect(evaluateActionExpression(el, '')).toBeUndefined()
        expect(evaluateActionExpression(el, '   ')).toBeUndefined()

        raw.mockImplementationOnce(() => { throw new Error('boom') })
        expect(evaluateActionExpression(el, 'x')).toBeUndefined()
        expect(warn.mock.calls[0][0]).toContain('Brew Expression Error: boom')

        raw.mockRestore()
        warn.mockRestore()
        error.mockRestore()
    })
})

describe('error modal', () => {
    it('renders html in a dialog and cleans up on close', () => {
        HTMLDialogElement.prototype.showModal = vi.fn()
        HTMLDialogElement.prototype.close = function () { this.dispatchEvent(new Event('close')) }

        showHtmlModal('<h1>Oops</h1><a href="/x">link</a>')

        let modal = document.getElementById('brew-error')
        expect(modal.tagName).toBe('DIALOG')
        expect(document.body.style.overflow).toBe('hidden')
        expect(modal.querySelector('iframe').contentWindow.document.querySelector('a').getAttribute('target')).toBe('_top')

        showHtmlModal('<p>Again</p>')
        expect(document.querySelectorAll('#brew-error').length).toBe(1)
        expect(document.getElementById('brew-error').querySelectorAll('iframe').length).toBe(1)

        document.getElementById('brew-error').click()
        expect(document.getElementById('brew-error')).toBeNull()
        expect(document.body.style.overflow).toBe('visible')
    })
})

describe('interceptor registry', () => {
    it('removes registrations once', () => {
        let registry = new InterceptorRegistry()
        let component = {}
        let removeMessage = registry.addMessageInterceptor(() => {})
        let removeRequest = registry.addRequestInterceptor(() => {})
        let removeComponent = registry.addComponentMessageInterceptor(component, () => {})

        removeMessage()
        removeMessage()
        removeRequest()
        removeComponent()

        expect(registry.messageCallbacks).toEqual([])
        expect(registry.requestCallbacks).toEqual([])
        expect(registry.componentMessageCallbacks.get(component)).toEqual([])
    })
})
