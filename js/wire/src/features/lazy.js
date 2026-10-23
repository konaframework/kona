import { on } from '../hooks.js'
import { fireAction } from '../request/index.js'

function scheduleLazyLoad(component, defer) {
    let load = () => fireAction(component, '$refresh')

    if (defer) {
        queueMicrotask(load)

        return
    }

    if (typeof IntersectionObserver === 'undefined') {
        load()

        return
    }

    let observer = new IntersectionObserver(entries => {
        if (! entries.some(entry => entry.isIntersecting)) return

        observer.disconnect()
        load()
    })

    observer.observe(component.el)
    component.addCleanup(() => observer.disconnect())
}

/** Finish loading `@Lazy` / `@Defer` components when they enter the viewport / after paint. */
export function registerLazy() {
    on('component.init', ({ component }) => {
        let lazy = component.effects?.lazy || component.snapshot.memo?.lazyLoaded === false
        let defer = component.effects?.defer || component.snapshot.memo?.defer

        if (! lazy && ! defer) return

        scheduleLazyLoad(component, !! defer)
    })
}
