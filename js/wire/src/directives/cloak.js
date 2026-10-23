import Alpine from 'alpinejs'

/** Remove `wire:cloak` once Alpine initializes the element (CSS hides it until then). */
export function registerCloakDirective() {
    Alpine.interceptInit(el => {
        if (el.hasAttribute('wire:cloak')) {
            Alpine.mutateDom(() => el.removeAttribute('wire:cloak'))
        }
    })
}
