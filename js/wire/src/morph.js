import Alpine from 'alpinejs'
import { trigger } from './hooks.js'
import { findComponentByEl } from './store.js'
import { interceptMessage } from './request/index.js'
import { transitionDomMutation } from './directives/transition.js'

export async function morph(component, el, html) {
    let wrapper = document.createElement(tagNameOf(el.parentElement))

    wrapper.innerHTML = html

    let parent = findComponentByEl(el.parentElement, false)

    if (parent) wrapper.__brew = parent

    let to = wrapper.firstElementChild

    // Inscribe state on the new root so a mismatched morph can re-initialize from it...
    to.setAttribute('wire:snapshot', component.snapshotEncoded)

    let { html: _html, ...effects } = component.effects

    to.setAttribute('wire:effects', JSON.stringify(effects))

    to.__brew = component

    trigger('morph', { el, toEl: to, component })

    // Child components rendered without a snapshot are unchanged; keep the live ones even if a loop lost its wire:key...
    let existing = {}

    el.querySelectorAll('[wire\\:id]').forEach(child => existing[child.getAttribute('wire:id')] = child)

    to.querySelectorAll('[wire\\:id]').forEach(child => {
        if (child.hasAttribute('wire:snapshot')) return

        let live = existing[child.getAttribute('wire:id')]

        if (live) child.replaceWith(live.cloneNode(true))
    })

    let transitionOptions = {}

    if (Object.prototype.hasOwnProperty.call(component.effects, 'transition') && component.effects.transition) {
        transitionOptions = component.effects.transition
    }

    await transitionDomMutation(el, to, () => {
        Alpine.morph(el, to, morphConfig(component))
    }, transitionOptions)

    trigger('morphed', { el, component })
}

function isElement(el) {
    return typeof el.hasAttribute === 'function'
}

function morphConfig(component) {
    return {
        updating: (el, toEl, childrenOnly, skip, skipChildren) => {
            if (! isElement(el)) return

            trigger('morph.updating', { el, toEl, component, skip, childrenOnly, skipChildren })

            if (el.__brew_replace === true) el.innerHTML = toEl.innerHTML

            if (el.__brew_replace_self === true) {
                el.outerHTML = toEl.outerHTML

                return skip()
            }

            if (el.__brew_ignore === true) return skip()
            if (el.__brew_ignore_self === true) childrenOnly()
            if (el.__brew_ignore_children === true) return skipChildren()

            if (el.hasAttribute('wire:id')) {
                // Nested components update themselves...
                if (el.getAttribute('wire:id') !== component.id) return skip()

                toEl.__brew = component
            }
        },

        updated: el => { if (isElement(el)) trigger('morph.updated', { el, component }) },
        removing: (el, skip) => { if (isElement(el)) trigger('morph.removing', { el, component, skip }) },
        removed: el => { if (isElement(el)) trigger('morph.removed', { el, component }) },
        adding: el => { trigger('morph.adding', { el, component }) },
        added: el => { if (isElement(el)) trigger('morph.added', { el }) },

        key: el => {
            if (el.hasAttribute('wire:id')) return el.getAttribute('wire:id')
            if (el.hasAttribute('wire:key')) return el.getAttribute('wire:key')

            return el.id
        },

        lookahead: false,
    }
}

export function tagNameOf(el) {
    let tag = el.tagName.toLowerCase()

    return customElements.get(tag) ? 'div' : tag
}

export function registerMorphEffect() {
    interceptMessage(({ message, onSuccess }) => {
        onSuccess(({ payload, onMorph }) => {
            onMorph(async () => {
                let html = payload.effects.html

                if (html) await morph(message.component, message.component.el, html)
            })
        })
    })
}
