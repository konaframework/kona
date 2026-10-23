import { on } from '../hooks.js'

let progressEl = null
let prefetchCache = new Map()

function config() {
    return (window.wireScriptConfig && window.wireScriptConfig.navigate)
        || (window.brewScriptConfig && window.brewScriptConfig.navigate)
        || {}
}

function showProgress() {
    if (config().show_progress_bar === false) return
    if (! progressEl) {
        progressEl = document.createElement('div')
        progressEl.setAttribute('data-wire-progress', '')
        progressEl.setAttribute('data-brew-progress', '')
        progressEl.style.cssText = `position:fixed;top:0;left:0;height:3px;width:0;background:${config().progress_bar_color || '#FF9A00'};z-index:9999;transition:width .2s`
        document.body.appendChild(progressEl)
    }
    requestAnimationFrame(() => { if (progressEl) progressEl.style.width = '70%' })
}

function hideProgress() {
    if (! progressEl) return
    progressEl.style.width = '100%'
    setTimeout(() => {
        if (progressEl) progressEl.remove()
        progressEl = null
    }, 200)
}

function fire(name, detail = {}) {
    document.dispatchEvent(new CustomEvent('wire:' + name, { detail, bubbles: true }))
    document.dispatchEvent(new CustomEvent('brew:' + name, { detail, bubbles: true }))
    document.dispatchEvent(new CustomEvent('livewire:' + name, { detail, bubbles: true }))
}

function persistSnapshot() {
    document.querySelectorAll('[wire\\:persist]').forEach(el => {
        el._brewPersistKey = el.getAttribute('wire:persist') || 'default'
    })
}

function restorePersist(doc) {
    document.querySelectorAll('[wire\\:persist]').forEach(el => {
        let next = doc.querySelector(`[wire\\:persist="${el._brewPersistKey}"]`)
        if (next) next.replaceWith(el)
    })
}

function applyScripts(doc) {
    doc.querySelectorAll('script').forEach(script => {
        let skipOnce = script.hasAttribute('data-navigate-once') && script.src !== '' && document.querySelector(`script[src="${script.src}"][data-navigate-once]`)
        let skipTrack = script.hasAttribute('data-navigate-track') && document.querySelector(`script[data-navigate-track="${script.getAttribute('data-navigate-track')}"]`)
        if (skipOnce || skipTrack) return
        let clone = document.createElement('script')
        ;[...script.attributes].forEach(attr => clone.setAttribute(attr.name, attr.value))
        clone.textContent = script.textContent
        document.body.appendChild(clone)
    })
}

async function fetchPage(url, { prefetch = false } = {}) {
    if (prefetch && prefetchCache.has(url)) return prefetchCache.get(url)
    let promise = Promise.resolve()
        .then(() => fetch(url, { headers: { 'X-Brew-Navigate': '1', Accept: 'text/html' } }))
        .then(r => r.text())
        .catch(() => '<html><head></head><body></body></html>')
    if (prefetch) prefetchCache.set(url, promise)
    return promise
}

export async function navigateTo(url, { replace = false } = {}) {
    fire('navigate', { url })
    fire('navigating', { url })
    showProgress()
    persistSnapshot()

    let html = await fetchPage(url)
    let doc = new DOMParser().parseFromString(html, 'text/html')

    let swap = () => {
        restorePersist(doc)
        document.title = doc.title
        document.body.replaceWith(doc.body)
        applyScripts(doc)
        history[replace ? 'replaceState' : 'pushState']({}, '', url)
        hideProgress()
        fire('navigated', { url })
        try { window.Wire.start() } catch (_) {}
    }

    if (config().view_transitions && document.startViewTransition) document.startViewTransition(swap)
    else swap()
}

export function registerNavigate() {
    document.addEventListener('click', e => {
        let link = e.target.closest('[wire\\:navigate]')
        if (! link || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button) return
        e.preventDefault()
        let href = link.getAttribute('href')
        if (! href || href.startsWith('#') || href.startsWith('mailto:')) return
        navigateTo(href)
    }, true)

    document.addEventListener('mouseover', e => {
        let link = e.target.closest('[wire\\:navigate\\.hover]')
        let href = link && link.getAttribute('href')
        if (href) fetchPage(href, { prefetch: true })
    }, { capture: true })

    window.addEventListener('popstate', () => navigateTo(location.href, { replace: true }))

    on('effect', ({ effects }) => {
        if (effects.disableBackButtonCache) {
            window.addEventListener('unload', () => {})
            history.scrollRestoration = 'manual'
        }
        if (effects.url) {
            let params = new URLSearchParams(location.search)
            Object.entries(effects.url).forEach(([key, meta]) => {
                let value = meta && Object.prototype.hasOwnProperty.call(meta, 'value') ? meta.value : meta
                if (value == null || value === '') params.delete(key)
                else params.set(key, String(value))
            })
            let qs = params.toString()
            let next = location.pathname + (qs === '' ? '' : '?' + qs)
            if (Object.values(effects.url).some(m => m && m.history === true)) history.pushState({}, '', next)
            else history.replaceState({}, '', next)
        }
        if (effects.title) document.title = effects.title
    })

    window.Wire = window.Wire || {}
    window.Wire.navigate = navigateTo
}
