package org.konaframework.notifications

import org.konaframework.cadence.DB
import org.konaframework.cadence.Database
import org.konaframework.queue.Bus
import org.konaframework.queue.Job
import org.konaframework.queue.ShouldQueue
import org.konaframework.queue.ShouldQueueAfterCommit
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends notifications through registered channels (`mail`, `database`, `log`).
 *
 * When [notification] implements [ShouldQueue] and a [Bus] is configured, delivery is queued;
 * otherwise channels run immediately. [ShouldQueueAfterCommit] (and an open Cadence transaction)
 * defers send until after the outermost commit.
 */
class NotificationSender @JvmOverloads constructor(
    private val bus: Bus? = null,
) {
    private val channels = ConcurrentHashMap<String, NotificationChannel>()

    fun extend(name: String, channel: NotificationChannel): NotificationSender {
        channels[name] = channel
        return this
    }

    fun channel(name: String): NotificationChannel =
        channels[name] ?: throw IllegalArgumentException("Notification channel [$name] is not defined.")

    fun send(notifiable: Notifiable, notification: Notification) {
        if (shouldDeferUntilCommit(notification)) {
            DB.afterCommit(Runnable { sendImmediately(notifiable, notification) })
            return
        }
        sendImmediately(notifiable, notification)
    }

    /** Deliver on every channel in [Notification.via] without queueing again. */
    fun sendNow(notifiable: Notifiable, notification: Notification) {
        for (name in notification.via(notifiable)) {
            channel(name).send(notifiable, notification)
        }
    }

    private fun sendImmediately(notifiable: Notifiable, notification: Notification) {
        if (notification is ShouldQueue && bus != null) {
            bus.dispatch(SendNotificationJob(this, notifiable, notification))
            return
        }
        sendNow(notifiable, notification)
    }

    private fun shouldDeferUntilCommit(notification: Notification): Boolean {
        if (notification !is ShouldQueueAfterCommit) {
            return false
        }
        if (!Database.hasGlobal()) {
            return false
        }
        return DB.transactionLevel() > 0
    }

    private class SendNotificationJob(
        private val sender: NotificationSender,
        private val notifiable: Notifiable,
        private val notification: Notification,
    ) : Job, ShouldQueue {
        override fun handle() {
            sender.sendNow(notifiable, notification)
        }
    }
}
