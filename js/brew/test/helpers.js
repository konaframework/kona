import { vi } from 'vitest'

/** Load the runtime once per test file (each file gets a fresh module graph) and start it. */
export async function bootRuntime() {
    window.brewScriptConfig = { csrf: 'token-1', uri: '/brew/update' }

    await import('../src/index.js')

    window.Brew.start()

    await settle()

    return window.Brew
}

export function stubDialog() {
    HTMLDialogElement.prototype.showModal = vi.fn()
    HTMLDialogElement.prototype.close = function () { this.dispatchEvent(new Event('close')) }
}

export async function cleanPage() {
    document.body.innerHTML = ''

    await settle()

    vi.unstubAllGlobals()
    vi.restoreAllMocks()
}

export const tick = (ms = 0) => new Promise(resolve => setTimeout(resolve, ms))

/** Let Alpine's mutation observer, the 5ms message buffer, and the response pipeline settle. */
export async function settle() {
    for (let i = 0; i < 4; i++) await tick(10)

    await new Promise(resolve => requestAnimationFrame(resolve))
    await tick()
}

let nextId = 0

/** Server-shaped snapshot for a component root. */
export function snapshot(data, memo = {}) {
    let id = memo.id || 'c' + ++nextId

    return {
        data,
        memo: { id, name: 'counter', path: '', method: 'GET', children: {}, scripts: [], assets: [], errors: [], locale: 'en', ...memo },
        checksum: 'abc',
    }
}

export function escape(value) {
    return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;')
}

export function rootHtml(snap, inner, { effects = [], tag = 'div', key } = {}) {
    let keyAttr = key ? ` wire:key="${key}"` : ''

    return `<${tag} wire:snapshot="${escape(JSON.stringify(snap))}" wire:effects="${escape(JSON.stringify(effects))}" wire:id="${snap.memo.id}" wire:name="${snap.memo.name}"${keyAttr}>${inner}</${tag}>`
}

/** Mount markup into the page and wait for Alpine to initialize it. */
export async function mount(html) {
    let host = document.createElement('div')

    host.innerHTML = html
    document.body.appendChild(host)

    await settle()

    return host
}

export function jsonResponse(json, extra = {}) {
    return response(JSON.stringify(json), extra)
}

export function response(body, { status = 200, redirected = false, url = 'http://localhost/brew/update' } = {}) {
    return { ok: status >= 200 && status < 300, status, redirected, url, text: async () => body }
}

/** Build a server reply for each message in a request body, using `respond(component, body)`. */
export function serverReply(respond) {
    return vi.fn(async (uri, options) => {
        let body = JSON.parse(options.body)

        return jsonResponse({
            components: body.components.map(component => respond(component, body)),
            assets: [],
        })
    })
}

/**
 * A stand-in server: applies `updates` to the posted snapshot data, lets
 * `handle(data, calls, snap)` mutate it and return effects, and re-renders.
 */
export function echoServer(render, handle = () => ({})) {
    return serverReply(component => {
        let snap = JSON.parse(component.snapshot)

        Object.entries(component.updates).forEach(([key, value]) => {
            let segments = key.split('.')
            let target = snap.data

            segments.slice(0, -1).forEach(segment => target = target[segment] ??= {})
            target[segments.at(-1)] = value
        })

        let effects = handle(snap.data, component.calls, snap) || {}
        let html = render ? rootHtml(snap, render(snap.data, snap)) : undefined

        return reply(snap, html ? { html, ...effects } : effects)
    })
}

/** A fetch whose responses the test releases by hand, to hold requests in flight. */
export function manualFetch() {
    let calls = []
    let fetch = vi.fn((uri, options) => new Promise((resolve, reject) => {
        calls.push({ uri, options, body: JSON.parse(options.body), resolve, reject })
    }))

    return { fetch, calls }
}

export function reply(snap, effects = {}) {
    return { snapshot: JSON.stringify(snap), effects }
}
