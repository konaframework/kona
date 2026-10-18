package org.konaframework.brew.hydration

import org.konaframework.brew.CorruptComponentPayloadException

/**
 * Classes that may be instantiated from snapshot metadata (`SecurityPolicy`). Checksums already prevent tampering;
 * this is defense in depth against gadget classes.
 */
class SecurityPolicy {
    private val denied = mutableListOf("java.", "javax.", "jdk.", "sun.", "com.sun.", "kotlin.", "groovy.", "org.codehaus.")

    /** Deny an extra package/class prefix. */
    fun deny(prefix: String): SecurityPolicy {
        denied += prefix
        return this
    }

    fun isAllowed(className: String): Boolean = denied.none { className.startsWith(it) }

    /** Resolve [className] for hydration, enforcing the policy. */
    fun load(className: String?, loader: ClassLoader): Class<*> {
        if (className.isNullOrBlank() || !isAllowed(className)) {
            throw CorruptComponentPayloadException("Class [$className] is not allowed in Brew snapshots")
        }
        return try {
            Class.forName(className, false, loader)
        } catch (e: ClassNotFoundException) {
            throw CorruptComponentPayloadException("Class [$className] referenced by snapshot does not exist")
        }
    }
}
