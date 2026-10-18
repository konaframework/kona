import Alpine from 'alpinejs'
import { all, find, first, getByName } from './store.js'
import { start } from './lifecycle.js'
import { on as hook, trigger, triggerAsync } from './hooks.js'
import { directive } from './directives.js'
import { fireAction, interceptAction, interceptMessage, interceptRequest } from './request/index.js'
import { coordinateNetworkInteractions } from './request/interactions.js'
import { registerLegacyHooks } from './request/legacy.js'
import { registerWireMagic } from './wire.js'
import { registerMorphEffect } from './morph.js'
import { registerWildcardDirective } from './directives/wildcard.js'
import { registerModelDirective } from './directives/model.js'
import { registerSimpleDirectives } from './directives/simple.js'
import { registerLoadingDirective } from './directives/loading.js'
import { registerDirtyDirective } from './directives/dirty.js'
import { registerOfflineDirective } from './directives/offline.js'
import { registerPollDirective } from './directives/poll.js'
import { registerConfirmDirective } from './directives/confirm.js'
import { registerCloakDirective } from './directives/cloak.js'
import { registerShowDirective, registerTextDirective, registerBindDirective } from './directives/showTextBind.js'
import { registerTransitionDirective } from './directives/transition.js'
import { registerDataLoading } from './features/dataLoading.js'
import { registerDisableFormsDuringRequest } from './features/disableForms.js'
import { registerDispatches } from './features/dispatches.js'
import { registerListeners } from './features/listeners.js'
import { registerJsEvaluation } from './features/jsEvaluation.js'
import { registerRedirects } from './features/redirects.js'
import { registerStreaming } from './features/streaming.js'
import { registerDownloads } from './features/downloads.js'
import { registerPreserveScroll } from './features/preserveScroll.js'
import { registerScripts } from './features/scripts.js'
import { registerAssets } from './features/assets.js'
import { registerEcho } from './features/echo.js'
import { registerTeleport } from './features/teleport.js'
import { registerIslands } from './features/islands.js'
import { registerLazy } from './features/lazy.js'
import { registerNavigate, navigateTo } from './features/navigate.js'
import { registerCurrentDirective } from './features/current.js'
import { registerNavigateDirective } from './directives/navigate.js'
import { registerUploads } from './features/uploads.js'
import { registerIntersectDirective } from './directives/intersect.js'
import { registerSortDirective } from './directives/sort.js'
import { dispatchGlobal, dispatchTo, on as listenGlobal } from './events.js'

let Brew = {
    directive,
    interceptAction: callback => interceptAction(callback),
    interceptMessage: callback => interceptMessage(callback),
    interceptRequest: callback => interceptRequest(callback),
    fireAction: (component, method, params = [], metadata = {}) => fireAction(component.__instance || component, method, params, metadata),
    start,
    first,
    find,
    getByName,
    all,
    hook,
    trigger,
    triggerAsync,
    dispatch: dispatchGlobal,
    dispatchTo,
    on: listenGlobal,
    navigate: navigateTo,
}

let warnAboutMultipleInstancesOf = entity => console.warn(`Detected multiple instances of ${entity} running`)

if (window.Brew) warnAboutMultipleInstancesOf('Brew')
if (window.Alpine) warnAboutMultipleInstancesOf('Alpine')

registerWireMagic()
registerLegacyHooks()
registerMorphEffect()
registerModelDirective()
registerSimpleDirectives()
registerLoadingDirective()
registerDirtyDirective()
registerOfflineDirective()
registerPollDirective()
registerConfirmDirective()
registerCloakDirective()
registerShowDirective()
registerTextDirective()
registerBindDirective()
registerTransitionDirective()
registerDataLoading()
registerDisableFormsDuringRequest()
registerDispatches()
registerListeners()
registerJsEvaluation()
registerRedirects()
registerStreaming()
registerDownloads()
registerPreserveScroll()
registerScripts()
registerAssets()
registerEcho()
registerTeleport()
registerIslands()
registerLazy()
registerNavigate()
registerNavigateDirective()
registerCurrentDirective()
registerUploads()
registerIntersectDirective()
registerSortDirective()
registerWildcardDirective()

// Deferred so feature code can register action interceptors ahead of the built-in coordination...
queueMicrotask(coordinateNetworkInteractions)

window.Brew = Brew
window.Alpine = Alpine

// With `@brewScriptConfig` the application bundles Brew itself and calls Brew.start()...
if (window.brewScriptConfig === undefined) {
    Alpine.__fromBrew = true

    document.addEventListener('DOMContentLoaded', () => {
        // Another Alpine bundle replaced ours on window after this script ran...
        if (window.Alpine.__fromBrew === undefined) warnAboutMultipleInstancesOf('Alpine')

        Brew.start()
    })
}

export { Brew, Alpine }
