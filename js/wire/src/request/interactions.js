import { constructAction, createOrAddToPendingMessage, interceptAction, interceptPartition, messageBus } from './index.js'

function allActions(message, predicate) {
    return [...message.actions].every(predicate)
}

function hasBoundChildren(component) {
    let found = false

    component.getDeepChildrenWithBindings(() => found = true)

    return found
}

export function coordinateNetworkInteractions() {
    // Isolated components always travel in their own request...
    interceptPartition(({ message, compileRequest }) => {
        if (message.component.isIsolated) compileRequest([message]).isolated = true
    })

    // Children bound through reactive/modelable props ride along with their parent...
    interceptPartition(({ message, compileRequest }) => {
        let bundled = []

        message.component.getDeepChildrenWithBindings(child => {
            bundled.push(createOrAddToPendingMessage(constructAction(child, '$commit')))
        })

        if (bundled.length > 0) compileRequest([message, ...bundled])
    })

    interceptAction(({ action }) => {
        let modifiers = action.origin?.directive?.modifiers || []

        if (modifiers.includes('renderless')) action.metadata.renderless = true
        if (modifiers.includes('async')) action.metadata.async = true
        if (modifiers.includes('preserve-scroll')) action.metadata.preserveScroll = true

        let active = messageBus().activeMessagesFor(action)
        let message = active[0]

        if (! message) return

        if (message.isAsync() || action.isAsync()) return

        if (action.metadata.type === 'poll') return action.cancel()

        if (allActions(message, i => i.metadata.type === 'poll')) return message.cancel()

        let bothModelLive = action.metadata.type === 'model.live' && allActions(message, i => i.metadata.type === 'model.live')

        // Overlapping model.live requests run in parallel unless bound children would share a stale parent snapshot...
        if (bothModelLive && ! hasBoundChildren(action.component) && ! [...message.actions].some(i => hasBoundChildren(i.component))) {
            active
                .filter(older => allActions(older, i => i.metadata.type === 'model.live'))
                .forEach(older => action.supersedes.add(older))

            return
        }

        action.defer()

        message.addInterceptor(({ onFinish }) => onFinish(() => action.fire()))
    })
}
