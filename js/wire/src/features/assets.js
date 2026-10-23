import { on } from '../hooks.js'

const injected = new Set()

function assetKey(html) {
    return html.trim().slice(0, 40).replace(/[^a-zA-Z0-9_-]/g, '')
}

/** Inject `@assets` HTML once per page from `effects.assets`. */
export function registerAssets() {
    on('effect', ({ effects }) => {
        let assets = effects.assets || []

        assets.forEach(html => {
            if (typeof html !== 'string' || ! html.trim()) return

            let key = assetKey(html)

            if (injected.has(key)) return

            injected.add(key)

            let wrapper = document.createElement('div')

            wrapper.innerHTML = html
            ;[...wrapper.childNodes].forEach(node => {
                if (node.nodeType === 1) document.head.appendChild(node)
            })
        })
    })
}
