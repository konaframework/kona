import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import {
    bootRuntime, stubDialog, cleanPage, mount, rootHtml, snapshot, settle, echoServer,
} from './helpers.js'
import {
    uploadUri, fireUpload, uploadFiles, cancelUpload, uploadDeps,
} from '../src/features/uploads.js'

let Brew

beforeAll(async () => { Brew = await bootRuntime() })
beforeEach(() => {
    stubDialog()
    window.brewScriptConfig = { csrf: 'token-1', uri: '/brew/update' }
    vi.stubGlobal('fetch', echoServer(() => 'ok'))
    uploadDeps.fireAction = vi.fn(async (c, method) => {
        if (method === '_startUpload') return { uploadUrl: '/brew/upload?expires=1&signature=x', token: 'x' }
        return null
    })
    uploadDeps.fetch = vi.fn(async () => ({ ok: true, json: async () => ({ paths: ['p1'] }) }))
})
afterEach(async () => {
    await cleanPage()
    const { fireAction } = await import('../src/request/index.js')
    uploadDeps.fireAction = fireAction
    delete uploadDeps.fetch
})

describe('M6 uploads intersect sort', () => {
    it('covers upload helpers and file wire:model', async () => {
        expect(uploadUri()).toBe('/brew/upload')
        delete window.brewScriptConfig
        expect(uploadUri()).toBe('/brew/upload')
        window.brewScriptConfig = { uri: '/custom/update' }
        expect(uploadUri()).toBe('/custom/upload')

        fireUpload('start', { property: 'photo' })

        let component = { id: 'c-up' }
        await uploadFiles(component, 'photo', [])
        await uploadFiles(component, 'photo', null)
        let file = {
            name: 'a.png', size: 2, type: 'image/png',
            arrayBuffer: async () => new Uint8Array([1, 2]).buffer,
        }
        let fileNoBuf = { name: 'b.png', size: 1, type: '', arrayBuffer: undefined }
        await uploadFiles(component, 'photo', [file], { multiple: false })
        expect(uploadDeps.fireAction).toHaveBeenCalledWith(component, '_finishUpload', expect.any(Array))
        await uploadFiles(component, 'photo', [file, file], { multiple: true })

        // S3 pre-signed PUT path
        uploadDeps.fireAction = vi.fn(async (c, method) => {
            if (method === '_startUpload') {
                return {
                    isS3: true,
                    uploadUrl: 'https://s3.example/obj',
                    path: 'livewire-tmp/a.png',
                    headers: { 'x-amz-acl': 'private' },
                }
            }
            return null
        })
        uploadDeps.fetch = vi.fn(async () => ({ ok: true, json: async () => ({}) }))
        await uploadFiles(component, 'photo', [file])
        expect(uploadDeps.fetch).toHaveBeenCalledWith(
            'https://s3.example/obj',
            expect.objectContaining({ method: 'PUT' }),
        )
        uploadDeps.fetch = vi.fn(async () => ({ ok: false, json: async () => ({}) }))
        await uploadFiles(component, 'photo', [file])
        uploadDeps.fireAction = vi.fn(async (c, method) => {
            if (method === '_startUpload') return { isS3: true, uploadUrl: 'https://s3.example/obj' }
            return null
        })
        uploadDeps.fetch = vi.fn(async () => ({ ok: true, json: async () => ({}) }))
        await uploadFiles(component, 'photo', [file])
        // S3 with empty Content-Type falls back to octet-stream; no headers map
        let emptyType = { name: 'c.bin', size: 1, type: '', arrayBuffer: async () => new Uint8Array([9]).buffer }
        uploadDeps.fireAction = vi.fn(async (c, method) => {
            if (method === '_startUpload') return { isS3: true, uploadUrl: 'https://s3.example/obj', headers: null }
            return null
        })
        await uploadFiles(component, 'photo', [emptyType])
        uploadDeps.fireAction = vi.fn(async (c, method) => {
            if (method === '_startUpload') return { uploadUrl: '/brew/upload?expires=1&signature=x', token: 'x' }
            return null
        })
        uploadDeps.fetch = vi.fn(async () => ({ ok: true, json: async () => ({ paths: ['p1'] }) }))

        // real fetch fallback when uploadDeps.fetch is unset
        delete uploadDeps.fetch
        vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => ({ paths: ['z'] }) })))
        await uploadFiles(component, 'photo', [file])

        uploadDeps.fetch = vi.fn(async () => ({ ok: false, json: async () => ({}) }))
        await uploadFiles(component, 'photo', [file])

        uploadDeps.fireAction = vi.fn(async (c, method) => {
            if (method === '_startUpload') return {}
            if (method === '_uploadErrored') return Promise.reject(new Error('x'))
            return null
        })
        uploadDeps.fetch = vi.fn(async () => ({ ok: true, json: async () => ({}) }))
        await uploadFiles(component, 'photo', [fileNoBuf])

        cancelUpload(component, 'missing')
        uploadDeps.fireAction = vi.fn(async (c, method) => {
            if (method === '_startUpload') return { uploadUrl: '/brew/upload' }
            return null
        })
        uploadDeps.fetch = vi.fn(async () => {
            cancelUpload(component, 'photo')
            throw new DOMException('Aborted', 'AbortError')
        })
        await uploadFiles(component, 'photo', [file])
        await settle()

        let snap = snapshot({ photo: null })
        await mount(rootHtml(snap, `
            <input type="file" wire:model="photo" />
            <input type="file" wire:model="photos" multiple id="multi" />
        `))
        let instance = Brew.find(snap.memo.id).__instance
        expect(typeof instance.$wire.$upload).toBe('function')
        await instance.$wire.$upload('photo', file)
        await instance.$wire.$upload('photo', null)
        await instance.$wire.$upload('photo', file, { multiple: false })
        await instance.$wire.$uploadMultiple('photo', [file])
        await instance.$wire.$uploadMultiple('photo', null)
        await instance.$wire.$uploadMultiple('photo', [file], { foo: 1 })
        instance.$wire.$cancelUpload('photo')
        await instance.$wire.$removeUpload('photo', 'x')
        let single = document.querySelector('input[type=file]')
        Object.defineProperty(single, 'files', { value: null, configurable: true })
        single.dispatchEvent(new Event('change'))
        Object.defineProperty(single, 'files', { value: [], configurable: true })
        single.dispatchEvent(new Event('change'))
        Object.defineProperty(single, 'files', { value: [file], configurable: true })
        single.dispatchEvent(new Event('change'))
        Object.defineProperty(document.querySelector('#multi'), 'files', {
            value: [file], configurable: true,
        })
        document.querySelector('#multi').dispatchEvent(new Event('change'))
        await settle()
    })

    it('registers intersect directive with modifiers', async () => {
        let observed = []
        class FakeObserver {
            constructor(cb, opts) { this.cb = cb; this.opts = opts }
            observe(el) { observed.push({ el, opts: this.opts, cb: this.cb }) }
            disconnect() {}
        }
        vi.stubGlobal('IntersectionObserver', FakeObserver)

        let snap = snapshot({})
        await mount(rootHtml(snap, `
            <div id="a" wire:intersect.once.half.margin-10px></div>
            <div id="b" wire:intersect:leave.full></div>
            <div id="c" wire:intersect:enter="\$refresh"></div>
            <div id="d" wire:intersect="noop"></div>
        `))
        expect(observed.length).toBe(4)
        observed[0].cb([{ isIntersecting: true, target: observed[0].el }])
        observed[0].cb([{ isIntersecting: true, target: observed[0].el }])
        observed[1].cb([{ isIntersecting: false, target: observed[1].el }])
        observed[1].cb([{ isIntersecting: true, target: observed[1].el }])
        observed[2].cb([{ isIntersecting: true, target: observed[2].el }])
        observed[3].cb([{ isIntersecting: true, target: observed[3].el }])
        await settle()
    })

    it('registers sort directive and reorders items', async () => {
        let snap = snapshot({})
        // defaults: method=sort, group=default
        await mount(rootHtml(snap, `
            <ul wire:sort>
                <li wire:sort:item="1">a</li>
            </ul>
        `))
        expect(document.querySelector('ul')).toBeTruthy()
        document.body.innerHTML = ''

        await mount(rootHtml(snapshot({}, { id: 'sort2' }), `
            <ul wire:sort="reordered" wire:sort:group="g1">
                <li wire:sort:item=""><span wire:sort:handle>h</span>one</li>
                <li wire:sort:item="2">two</li>
                <li wire:sort:item="3"><span wire:sort:ignore>x</span>three</li>
            </ul>
        `))
        let list = document.querySelector('ul')
        let items = [...list.children]
        expect(items.every(c => c.getAttribute('draggable') === 'true')).toBe(true)

        // miss: dragstart on the list itself (itemOf → null)
        let miss = new Event('dragstart', { bubbles: true })
        Object.defineProperty(miss, 'target', { value: list })
        list.dispatchEvent(miss)

        let handle = items[0].children[0]
        let start = new Event('dragstart', { bubbles: true })
        Object.defineProperty(start, 'target', { value: handle })
        Object.defineProperty(start, 'dataTransfer', {
            value: { effectAllowed: '', setData() {}, getData() { return '1' } },
        })
        list.dispatchEvent(start)

        // dragover onto the same dragging item
        let overSelf = new Event('dragover', { bubbles: true, cancelable: true })
        Object.defineProperty(overSelf, 'target', { value: items[0] })
        list.dispatchEvent(overSelf)

        // also without dataTransfer
        let start2 = new Event('dragstart', { bubbles: true })
        Object.defineProperty(start2, 'target', { value: handle })
        list.dispatchEvent(start2)

        let ignoreStart = new Event('dragstart', { bubbles: true })
        Object.defineProperty(ignoreStart, 'target', { value: items[2].children[0] })
        list.dispatchEvent(ignoreStart)

        let badStart = new Event('dragstart', { bubbles: true })
        Object.defineProperty(badStart, 'target', { value: items[0] })
        list.dispatchEvent(badStart)

        let over = new Event('dragover', { bubbles: true, cancelable: true })
        Object.defineProperty(over, 'target', { value: items[1] })
        Object.defineProperty(over, 'clientY', { value: 9999 })
        list.dispatchEvent(over)

        let over2 = new Event('dragover', { bubbles: true, cancelable: true })
        Object.defineProperty(over2, 'target', { value: items[1] })
        Object.defineProperty(over2, 'clientY', { value: 0 })
        list.dispatchEvent(over2)

        list.dispatchEvent(new Event('dragend', { bubbles: true }))
        list.dispatchEvent(new Event('dragover', { bubbles: true, cancelable: true }))

        list.dispatchEvent(start)
        list.dispatchEvent(new Event('drop', { bubbles: true, cancelable: true }))
        list.dispatchEvent(new Event('dragend', { bubbles: true }))
        list.dispatchEvent(new Event('drop', { bubbles: true, cancelable: true }))
        await settle()
    })
})
