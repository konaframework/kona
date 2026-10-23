import { browser } from '../browser.js'
import { interceptMessage } from '../request/index.js'

/** Follow `effects.redirect` after a successful response. */
export function registerRedirects() {
    interceptMessage(({ message, onSuccess }) => {
        onSuccess(({ payload }) => {
            let redirect = payload.effects?.redirect

            if (! redirect) return

            // `navigate` is reserved for M5 wire:navigate; both paths use a full visit for now...
            browser.visit(redirect.url || redirect.default || '/')
        })
    })
}
