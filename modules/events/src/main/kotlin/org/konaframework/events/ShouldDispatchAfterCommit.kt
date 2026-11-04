package org.konaframework.events

/**
 * Marker: when a Cadence database transaction is open, [EventDispatcher.dispatch] defers
 * delivery until after the outermost commit.
 */
interface ShouldDispatchAfterCommit
