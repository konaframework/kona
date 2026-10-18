import { toggleBooleanStateDirective } from './shared.js'
import { directive } from '../directives.js'

let offlineHandlers = new Set()
let onlineHandlers = new Set()
let isOffline = ! navigator.onLine

window.addEventListener('offline', () => {
    isOffline = true
    offlineHandlers.forEach(i => i())
})

window.addEventListener('online', () => {
    isOffline = false
    onlineHandlers.forEach(i => i())
})

export function brewIsOffline() {
    return isOffline
}

export function registerOfflineDirective() {
    directive('offline', ({ el, directive, cleanup }) => {
        let setOffline = () => toggleBooleanStateDirective(el, directive, true)
        let setOnline = () => toggleBooleanStateDirective(el, directive, false)

        offlineHandlers.add(setOffline)
        onlineHandlers.add(setOnline)

        if (isOffline) setOffline()

        cleanup(() => {
            offlineHandlers.delete(setOffline)
            onlineHandlers.delete(setOnline)
        })
    })
}
