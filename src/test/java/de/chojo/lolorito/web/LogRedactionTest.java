/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class LogRedactionTest {

    @Test
    void tokenParamIsRedactedRegardlessOfCasing() {
        String out = LogRedaction.redactQueryString("id=42&Token=abc&next=/planner");
        assertThat(out).isEqualTo("id=42&Token=[REDACTED]&next=/planner");
    }

    @Test
    void emptyOrBareQueryStringSurvives() {
        assertThat(LogRedaction.redactQueryString(null)).isEmpty();
        assertThat(LogRedaction.redactQueryString("")).isEmpty();
        assertThat(LogRedaction.redactQueryString("noequals")).isEqualTo("noequals");
    }

    @Test
    void codeAndStateAreRedacted() {
        String out = LogRedaction.redactQueryString("code=xyz&state=abc&other=keepme");
        assertThat(out).isEqualTo("code=[REDACTED]&state=[REDACTED]&other=keepme");
    }

    @Test
    void authorizationCookieAndSetCookieHeadersAreRedacted() {
        var input = new LinkedHashMap<String, String>();
        input.put("Authorization", "Bearer abc");
        input.put("Cookie", "session=xyz");
        input.put("Set-Cookie", "session=xyz; HttpOnly");
        input.put("Accept", "application/json");
        var out = LogRedaction.redactHeaders(input);
        assertThat(out.get("Authorization")).isEqualTo("[REDACTED]");
        assertThat(out.get("Cookie")).isEqualTo("[REDACTED]");
        assertThat(out.get("Set-Cookie")).isEqualTo("[REDACTED]");
        assertThat(out.get("Accept")).isEqualTo("application/json");
    }

    @Test
    void emptyOrNullHeaderMapYieldsEmpty() {
        assertThat(LogRedaction.redactHeaders(null)).isEmpty();
        assertThat(LogRedaction.redactHeaders(new LinkedHashMap<>())).isEmpty();
    }
}
