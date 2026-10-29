package org.konaframework.firefly.notifications

import org.konaframework.http.RequestSession

/**
 * Default resource toast flashes (create / save / delete).
 *
 * Java: `FireflyToasts.created(session);`
 */
object FireflyToasts {
    /** Flash a success toast titled "Saved". */
    @JvmStatic
    fun saved(session: RequestSession) {
        FireflyNotification.make("saved").title("Saved").success().send(session)
    }

    /** Flash a success toast titled "Created". */
    @JvmStatic
    fun created(session: RequestSession) {
        FireflyNotification.make("created").title("Created").success().send(session)
    }

    /** Flash a success toast titled "Deleted". */
    @JvmStatic
    fun deleted(session: RequestSession) {
        FireflyNotification.make("deleted").title("Deleted").success().send(session)
    }
}
