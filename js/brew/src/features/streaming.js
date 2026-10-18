import { interceptMessage } from '../request/index.js'

/** Apply `effects.streams` into `wire:stream` targets. */
export function registerStreaming() {
    interceptMessage(({ message, onSuccess }) => {
        onSuccess(({ payload, onMorphed }) => {
            onMorphed(() => {
                let streams = payload.effects?.streams || []

                streams.forEach(({ content, to, replace }) => {
                    let targets = to
                        ? message.component.el.querySelectorAll(`[wire\\:stream="${to}"], [wire\\:stream\\.replace="${to}"]`)
                        : message.component.el.querySelectorAll('[wire\\:stream], [wire\\:stream\\.replace]')

                    targets.forEach(el => {
                        let shouldReplace = replace || el.hasAttribute('wire:stream.replace')

                        if (shouldReplace) el.innerHTML = content
                        else el.insertAdjacentHTML('beforeend', content)
                    })
                })
            })
        })
    })
}