package org.konaframework.auth

import java.time.Instant

/**
 * Contract for users that must verify their email before accessing verified routes
 * (Laravel `MustVerifyEmail`).
 */
interface MustVerifyEmail {
    /** True when [emailVerifiedAt] is non-null. */
    fun hasVerifiedEmail(): Boolean = emailVerifiedAt() != null

    /** Instant the email was verified, or null. */
    fun emailVerifiedAt(): Instant?

    /** Mark the email as verified and persist. */
    fun markEmailAsVerified()

    /** Email address that receives the verification notice. */
    fun getEmailForVerification(): String
}
