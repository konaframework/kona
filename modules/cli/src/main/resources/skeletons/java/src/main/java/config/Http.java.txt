package config;

import org.konaframework.core.ConfigFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP layer (trusted proxies / hosts).
 *
 * <p>Use {@code "*"} for trusted proxies when the app sits behind a load balancer
 * or ingress that always sets {@code X-Forwarded-*}. Leave trusted hosts empty
 * to accept any Host header.
 */
public final class Http implements ConfigFile {
    @Override
    public String key() {
        return "http";
    }

    @Override
    public Map<String, Object> config() {
        Map<String, Object> values = new LinkedHashMap<>();
        /*
        |--------------------------------------------------------------------------
        | Trusted Proxies
        |--------------------------------------------------------------------------
        |
        | These IP addresses (or "*") are trusted to set X-Forwarded-* headers.
        |
        */
        values.put("trusted_proxies", List.of("*"));

        /*
        |--------------------------------------------------------------------------
        | Trusted Hosts
        |--------------------------------------------------------------------------
        |
        | When non-empty, requests whose Host header is not in this list are
        | rejected. Use exact names or wildcard suffixes such as "*.example.com".
        |
        */
        values.put("trusted_hosts", List.of());
        return values;
    }
}
