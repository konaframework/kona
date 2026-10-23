import { directive } from '../directives.js'

/**
 * `wire:navigate` is handled globally in features/navigate.js via click capture.
 * Registering the directive prevents the wildcard from binding it as an Alpine event.
 */
export function registerNavigateDirective() {
    directive('navigate', () => {})
}
