package org.konaframework.inertia

/**
 * Inertia protocol header names (Inertia 2 / Laravel `inertia-laravel` 2.x).
 */
object InertiaHeaders {
    const val INERTIA: String = "X-Inertia"
    const val VERSION: String = "X-Inertia-Version"
    const val LOCATION: String = "X-Inertia-Location"
    const val PARTIAL_COMPONENT: String = "X-Inertia-Partial-Component"
    const val PARTIAL_ONLY: String = "X-Inertia-Partial-Data"
    const val PARTIAL_EXCEPT: String = "X-Inertia-Partial-Except"
    const val RESET: String = "X-Inertia-Reset"
    const val ERROR_BAG: String = "X-Inertia-Error-Bag"
}
