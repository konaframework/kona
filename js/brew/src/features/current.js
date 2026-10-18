import { directive } from '../directives.js'

function resolveHref(el, dir) {
    let href = el.getAttribute('href')
    if (href) return href
    if (dir.expression) return dir.expression
    return ''
}

function pathOf(href) {
    if (! href.includes('://')) return href
    let afterHost = href.indexOf('/', href.indexOf('://') + 3)
    return afterHost < 0 ? '/' : href.slice(afterHost)
}

/** `wire:current` — adds `data-current` when the link matches the location. */
export function registerCurrentDirective() {
    directive('current', ({ el, directive: dir }) => {
        let href = resolveHref(el, dir)
        let path = location.pathname
        let target = pathOf(href)
        let exact = dir.modifiers.includes('exact') || dir.modifiers.includes('strict')
        let matches = path === target
        if (! exact && ! matches && target.length > 1) matches = path.startsWith(target)
        el.toggleAttribute('data-current', matches)
    })
}

export { resolveHref }
