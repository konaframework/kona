package config

import org.konaframework.core.ConfigFile

/**
 * HTTP layer (`config/http` — trusted proxies / hosts).
 *
 * Use `"*"` for [trusted_proxies] when the app sits behind a load balancer
 * or ingress that always sets `X-Forwarded-*`. Leave [trusted_hosts] empty
 * to accept any Host header; otherwise list exact hosts or `*.example.com`.
 */
object Http : ConfigFile {
    override fun key(): String = "http"

    override fun config(): Map<String, Any?> = mapOf(
        /*
        |--------------------------------------------------------------------------
        | Trusted Proxies
        |--------------------------------------------------------------------------
        |
        | These IP addresses (or "*") are trusted to set X-Forwarded-* headers.
        | Bootstrap middleware may call trustProxies() with the same list.
        |
        */
        "trusted_proxies" to listOf("*"),

        /*
        |--------------------------------------------------------------------------
        | Trusted Hosts
        |--------------------------------------------------------------------------
        |
        | When non-empty, requests whose Host header is not in this list are
        | rejected. Use exact names or wildcard suffixes such as "*.example.com".
        |
        */
        "trusted_hosts" to emptyList<String>(),
    )
}
