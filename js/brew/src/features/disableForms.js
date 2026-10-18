import Alpine from 'alpinejs'
import { on } from '../hooks.js'
import { interceptMessage } from '../request/index.js'
import { WeakBag } from '../utils.js'

let cleanups = new WeakBag()

/**
 * While a `wire:submit` request is in flight, disable / readonly form controls
 * (`SupportDisablingFormsDuringRequest`).
 */
export function registerDisableFormsDuringRequest() {
    on('directive.init', ({ el, directive, component }) => setTimeout(() => {
        if (directive.value !== 'submit') return

        el.addEventListener('submit', () => {
            let cleanup = disableForm(el)

            cleanups.add(component, cleanup)
        })
    }))

    interceptMessage(({ message, onFinish }) => {
        onFinish(() => {
            cleanups.each(message.component, cleanup => cleanup())
            cleanups.remove(message.component)
        })
    })
}

function disableForm(formEl) {
    let undos = []

    Alpine.walk(formEl, (el, skip) => {
        if (el.hasAttribute('wire:ignore')) return skip()

        if (shouldMarkDisabled(el)) undos.push(markDisabled(el))
        else if (shouldMarkReadOnly(el)) undos.push(markReadOnly(el))
    })

    return () => {
        while (undos.length > 0) undos.shift()()
    }
}

function shouldMarkDisabled(el) {
    let tag = el.tagName.toLowerCase()
    let inputTypesThatDontSupportReadonly = [
        'hidden', 'range', 'color', 'checkbox', 'radio',
        'file', 'submit', 'image', 'reset', 'button',
    ]

    if (tag === 'select') return true
    if (tag === 'button' && el.type === 'submit') return true
    if (tag === 'input' && inputTypesThatDontSupportReadonly.includes(el.type)) return true

    return false
}

function shouldMarkReadOnly(el) {
    return ['input', 'textarea'].includes(el.tagName.toLowerCase())
}

function markDisabled(el) {
    let undo = el.disabled ? () => {} : () => { el.disabled = false }

    el.disabled = true

    return undo
}

function markReadOnly(el) {
    let undo = el.readOnly ? () => {} : () => { el.readOnly = false }

    el.readOnly = true

    return undo
}
