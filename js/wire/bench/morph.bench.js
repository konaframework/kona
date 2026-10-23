import { bench, describe } from 'vitest'
import { bootRuntime, mount, rootHtml, snapshot } from '../test/helpers.js'
import { morph } from '../src/morph.js'
import { findComponent } from '../src/store.js'

await bootRuntime()

function rows(count, toggled) {
    let items = Array.from({ length: count }, (_, i) =>
        `<!--[if BLOCK]><![endif]--><li wire:key="${i}"><input type="checkbox"${(i % 3 === 0) !== (i === toggled) ? ' checked' : ''}> Row ${i}</li><!--[if ENDBLOCK]><![endif]-->`)

    return `<ul>${items.join('')}</ul>`
}

async function mounted(count) {
    let snap = snapshot({ rows: count }, { id: 'bench-' + count, name: 'rows' })

    await mount(rootHtml(snap, rows(count, -1)))

    return { component: findComponent(snap.memo.id), snap }
}

const small = await mounted(100)
const large = await mounted(1000)

let flip = 0

describe('morph (keyed list, one row changes)', () => {
    bench('100 rows', async () => {
        flip = (flip + 1) % 100
        await morph(small.component, small.component.el, rootHtml(small.snap, rows(100, flip)))
    })

    bench('1000 rows', async () => {
        flip = (flip + 1) % 1000
        await morph(large.component, large.component.el, rootHtml(large.snap, rows(1000, flip)))
    })
})
