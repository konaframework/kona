import { directive } from '../directives.js'
import { fireAction } from '../request/index.js'

function itemOf(el, target) {
    let node = target
    while (node && node !== el) {
        if (node.hasAttribute && node.hasAttribute('wire:sort:item')) return node
        node = node.parentElement
    }
    return null
}

/** `wire:sort` with `:item` / `:handle` / `:group` / `:ignore` children. */
export function registerSortDirective() {
    directive('sort', ({ el, directive: dir, component, cleanup }) => {
        let method = dir.expression || 'sort'
        let group = el.getAttribute('wire:sort:group') || 'default'
        let dragging = null

        let onDragStart = e => {
            let item = itemOf(el, e.target)
            if (! item) return
            let ignored = false
            let node = e.target
            while (node && node !== item) {
                if (node.hasAttribute && node.hasAttribute('wire:sort:ignore')) ignored = true
                node = node.parentElement
            }
            if (ignored) return
            let handle = null
            for (let child of item.children) {
                if (child.hasAttribute('wire:sort:handle')) handle = child
            }
            if (handle && e.target !== handle && ! handle.contains(e.target)) return
            dragging = item
            if (e.dataTransfer) {
                e.dataTransfer.effectAllowed = 'move'
                e.dataTransfer.setData('text/plain', item.getAttribute('wire:sort:item') || '')
            }
            item.setAttribute('data-sorting', '')
        }

        let onDragOver = e => {
            if (! dragging) return
            e.preventDefault()
            let over = itemOf(el, e.target)
            if (! over || over === dragging) return
            el.insertBefore(dragging, over)
        }

        let onDrop = e => {
            e.preventDefault()
            if (! dragging) return
            dragging.removeAttribute('data-sorting')
            let items = [...el.children]
                .filter(node => node.hasAttribute('wire:sort:item'))
                .map(node => node.getAttribute('wire:sort:item'))
            fireAction(component, method, [items, group])
            dragging = null
        }

        let onDragEnd = () => {
            if (dragging) dragging.removeAttribute('data-sorting')
            dragging = null
        }

        for (let item of el.children) {
            if (item.hasAttribute('wire:sort:item')) item.setAttribute('draggable', 'true')
        }
        el.addEventListener('dragstart', onDragStart)
        el.addEventListener('dragover', onDragOver)
        el.addEventListener('drop', onDrop)
        el.addEventListener('dragend', onDragEnd)
        cleanup(() => {
            el.removeEventListener('dragstart', onDragStart)
            el.removeEventListener('dragover', onDragOver)
            el.removeEventListener('drop', onDrop)
            el.removeEventListener('dragend', onDragEnd)
        })
    })
}
