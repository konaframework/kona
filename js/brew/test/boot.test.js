import { describe, it, expect, vi, beforeAll } from 'vitest'
import { settle, snapshot, rootHtml, response } from './helpers.js'

let warnings = []
let started = []

beforeAll(async () => {
    vi.spyOn(console, 'warn').mockImplementation((...args) => warnings.push(args[0]))

    window.Brew = { stale: true }
    window.Alpine = { stale: true }

    document.body.innerHTML = `<div><script data-csrf="boot-token"></script></div>`
        + rootHtml(snapshot({ n: 1 }, { id: 'boot' }), '<script data-update-uri="/x" data-csrf="y"></script>')

    document.addEventListener('brew:init', () => started.push('init'))
    document.addEventListener('brew:initialized', () => started.push('initialized'))

    await import('../src/index.js')

    // Simulate a second Alpine bundle replacing ours before the page finished loading...
    let ours = window.Alpine
    window.Alpine = {}
    document.dispatchEvent(new Event('DOMContentLoaded'))
    window.Alpine = ours

    await settle()
})

describe('booting from a script tag', () => {
    it('warns about duplicate Brew and Alpine instances', () => {
        expect(warnings.filter(w => w === 'Detected multiple instances of Brew running')).toHaveLength(1)
        expect(warnings.filter(w => w === 'Detected multiple instances of Alpine running')).toHaveLength(2)
    })

    it('starts automatically on DOMContentLoaded and announces it', () => {
        expect(started).toEqual(['init', 'initialized'])
        expect(window.Brew.find('boot').n).toBe(1)
    })

    it('warns when the script tag ended up inside a component', () => {
        expect(warnings).toContain('Brew: missing closing tags found. Ensure your template elements contain matching closing tags.')
    })

    it('keeps the page when the reload prompts are declined', async () => {
        let confirm = vi.fn(() => false)
        vi.stubGlobal('confirm', confirm)
        let { browser } = await import('../src/browser.js')
        let reload = vi.spyOn(browser, 'reload')

        vi.stubGlobal('fetch', vi.fn(async () => response('', { status: 419 })))
        await window.Brew.find('boot').save().catch(() => {})
        vi.stubGlobal('fetch', vi.fn(async () => response('')))
        await window.Brew.find('boot').save().catch(() => {})

        expect(confirm).toHaveBeenCalledTimes(2)
        expect(reload).not.toHaveBeenCalled()
        vi.unstubAllGlobals()
    })

    it('navigates through the browser helpers', async () => {
        let { browser } = await import('../src/browser.js')
        let errors = vi.spyOn(console, 'error').mockImplementation(() => {})

        browser.visit('#moved')
        browser.reload()

        expect(window.location.hash).toBe('#moved')
        errors.mockRestore()
    })
})
