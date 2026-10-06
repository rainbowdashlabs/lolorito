/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Redaction helpers for trace-level request / response logging. Keys and
 * header names that are known to carry credentials are matched
 * case-insensitively and their values are replaced with {@code [REDACTED]}.
 * Returns fresh strings / maps — inputs are not modified.
 *
 * <p>Ported from ember's util/LogRedaction; kept small and dependency-free
 * so a TRACE log line can never leak a session cookie or an OAuth token.
 */
public final class LogRedaction {

    public static final String SENTINEL = "[REDACTED]";

    /** Query-string keys whose values may carry a credential. */
    public static final Set<String> REDACTED_QUERY_KEYS =
            Set.of("token", "code", "state", "access_token", "refresh_token");

    /** Header names whose values are credentials or session-binding data. */
    public static final Set<String> REDACTED_HEADERS = Set.of("authorization", "cookie", "set-cookie", "x-session-id");

    private LogRedaction() {}

    public static String redactQueryString(String rawQueryString) {
        if (rawQueryString == null || rawQueryString.isEmpty()) return "";
        String[] pairs = rawQueryString.split("&");
        var out = new StringBuilder(rawQueryString.length());
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) out.append('&');
            String pair = pairs[i];
            int eq = pair.indexOf('=');
            if (eq < 0) {
                out.append(pair);
                continue;
            }
            String key = pair.substring(0, eq);
            String lower = key.toLowerCase(Locale.ROOT);
            if (REDACTED_QUERY_KEYS.contains(lower)) {
                out.append(key).append('=').append(SENTINEL);
            } else {
                out.append(pair);
            }
        }
        return out.toString();
    }

    public static Map<String, String> redactHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return Map.of();
        var out = new LinkedHashMap<String, String>(headers.size());
        for (var e : headers.entrySet()) {
            String key = e.getKey();
            String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
            out.put(key, REDACTED_HEADERS.contains(lower) ? SENTINEL : e.getValue());
        }
        return out;
    }
}
