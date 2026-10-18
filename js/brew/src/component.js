import Alpine from 'alpinejs'
import { dataSet, deepClone, diffAndConsolidate, diffAndPatchRecursive, extractData } from './utils.js'
import { generateWireObject } from './wire.js'
import { findComponentByEl } from './store.js'
import { trigger } from './hooks.js'

export class Component {
    constructor(el) {
        if (el.__brew) throw 'Component already initialized'

        el.__brew = this

        this.el = el
        this.id = el.getAttribute('wire:id')
        this.key = el.getAttribute('wire:key')
        this.snapshotEncoded = el.getAttribute('wire:snapshot')
        this.snapshot = JSON.parse(this.snapshotEncoded)

        if (! this.snapshot) throw 'Snapshot missing on Brew component with id: ' + this.id

        this.name = this.snapshot.memo.name
        this.effects = JSON.parse(el.getAttribute('wire:effects') || '{}')

        // "canonical" is the last state the server confirmed; "ephemeral" is what the user has changed since...
        this.canonical = extractData(deepClone(this.snapshot.data))
        this.ephemeral = extractData(deepClone(this.snapshot.data))
        this.reactive = Alpine.reactive(this.ephemeral)
        this.queuedUpdates = {}
        this.cleanups = []

        this.$wire = generateWireObject(this, this.reactive)

        el.$wire = this.$wire
    }

    mergeNewSnapshot(snapshotEncoded, effects, updates = {}) {
        let snapshot = JSON.parse(snapshotEncoded)
        let previous = deepClone(this.canonical)

        for (let key in updates) dataSet(previous, key, updates[key])

        this.snapshotEncoded = snapshotEncoded
        this.snapshot = snapshot
        this.effects = effects
        this.canonical = extractData(deepClone(snapshot.data))

        diffAndPatchRecursive(previous, extractData(deepClone(snapshot.data)), this.reactive)
    }

    queueUpdate(path, value) {
        this.queuedUpdates[path] = value
    }

    getUpdates() {
        let diff = diffAndConsolidate(this.canonical, this.ephemeral)

        // Queued updates (from $set) win over any ephemeral change beneath the same path...
        Object.entries(this.queuedUpdates).forEach(([path, value]) => {
            Object.keys(diff).forEach(key => {
                if (key === path || key.startsWith(path + '.')) delete diff[key]
            })

            diff[path] = value
        })

        this.queuedUpdates = {}

        return diff
    }

    processEffects(effects, request) {
        trigger('effects', this, effects)

        trigger('effect', { component: this, effects, cleanup: i => this.addCleanup(i), request })
    }

    get children() {
        return [...this.el.querySelectorAll('[wire\\:id]')]
            .filter(el => el.parentElement.closest('[wire\\:id]') === this.el && el.__brew)
            .map(el => el.__brew)
    }

    get parent() {
        return findComponentByEl(this.el.parentElement, false)
    }

    get isIsolated() {
        return !! this.snapshot.memo.isolate
    }

    getDeepChildren(callback) {
        this.children.forEach(child => {
            callback(child)

            child.getDeepChildren(callback)
        })
    }

    getDeepChildrenWithBindings(callback) {
        this.getDeepChildren(child => {
            if (child.snapshot.memo.props || child.snapshot.memo.bindings) callback(child)
        })
    }

    getEncodedSnapshotWithLatestChildrenMergedIn() {
        let children = {}

        this.children.forEach(child => {
            children[child.key] = [child.el.tagName.toLowerCase(), child.id]
        })

        return this.snapshotEncoded.replace(/"children":\{[^}]*\}/, `"children":${JSON.stringify(children)}`)
    }

    toJSON() {
        return { id: this.id, name: this.name, key: this.key, data: { ...this.ephemeral } }
    }

    addCleanup(cleanup) {
        this.cleanups.push(cleanup)

        return () => {
            this.cleanups = this.cleanups.filter(i => i !== cleanup)
        }
    }

    cleanup() {
        delete this.el.__brew

        while (this.cleanups.length > 0) this.cleanups.pop()()
    }
}
