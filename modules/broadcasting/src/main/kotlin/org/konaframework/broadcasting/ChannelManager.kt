package org.konaframework.broadcasting

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Laravel-style `routes/channels` registry.
 *
 * Patterns use `{param}` placeholders (e.g. `orders.{orderId}`). Callbacks receive the
 * authenticated user (may be null) and extracted string parameters in declaration order.
 */
class ChannelManager {
    fun interface Authorizer {
        fun authorize(user: Any?, parameters: List<String>): Any?
    }

    private data class Entry(val pattern: Pattern, val authorizer: Authorizer)

    private val channels = ConcurrentHashMap<String, Entry>()

    fun channel(name: String, authorizer: Authorizer): ChannelManager {
        channels[name] = Entry(buildPattern(name), authorizer)
        return this
    }

    fun authorize(channelName: String, user: Any?): Any? {
        val stripped = stripPrefix(channelName)
        for ((_, entry) in channels) {
            val matcher = entry.pattern.matcher(stripped)
            if (!matcher.matches()) {
                continue
            }
            val params = (1..matcher.groupCount()).map { matcher.group(it) }
            return entry.authorizer.authorize(user, params)
        }
        // Public channels with no callback are allowed; private/presence require a match.
        return if (channelName.startsWith("private-") || channelName.startsWith("presence-")) {
            false
        } else {
            true
        }
    }

    fun has(name: String): Boolean = channels.containsKey(name)

    fun clear() {
        channels.clear()
    }

    companion object {
        fun stripPrefix(channelName: String): String =
            when {
                channelName.startsWith("private-") -> channelName.removePrefix("private-")
                channelName.startsWith("presence-") -> channelName.removePrefix("presence-")
                else -> channelName
            }

        private fun buildPattern(name: String): Pattern {
            val escaped = StringBuilder("^")
            var i = 0
            while (i < name.length) {
                val ch = name[i]
                if (ch == '{') {
                    val end = name.indexOf('}', i)
                    require(end > i) { "Unclosed channel parameter in [$name]" }
                    escaped.append("([^./]+)")
                    i = end + 1
                } else {
                    if ("\\.[]{}()*+-?^$|".indexOf(ch) >= 0) {
                        escaped.append('\\')
                    }
                    escaped.append(ch)
                    i++
                }
            }
            escaped.append('$')
            return Pattern.compile(escaped.toString())
        }
    }
}
