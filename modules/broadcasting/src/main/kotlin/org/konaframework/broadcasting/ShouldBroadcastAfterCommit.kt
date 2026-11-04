package org.konaframework.broadcasting

/**
 * Marker: when a Cadence database transaction is open, [BroadcastManager.broadcast] defers
 * delivery until after the outermost commit.
 */
interface ShouldBroadcastAfterCommit
