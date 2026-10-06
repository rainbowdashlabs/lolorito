/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.api;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Cheap unit checks for the query-param helpers. Contexts are stubbed so we
 * don't need Javalin's full request pipeline for a bit of parsing.
 */
class QueryParamsTest {

    private static Context ctxWith(String name, String value) {
        var ctx = Mockito.mock(Context.class);
        Mockito.when(ctx.queryParam(name)).thenReturn(value);
        return ctx;
    }

    @Test
    void optIntReturnsNullOnAbsent() {
        assertNull(QueryParams.optInt(ctxWith("x", null), "x"));
    }

    @Test
    void optIntReturnsNullOnBlank() {
        assertNull(QueryParams.optInt(ctxWith("x", "  "), "x"));
    }

    @Test
    void optIntReturnsNullOnGarbage() {
        assertNull(QueryParams.optInt(ctxWith("x", "banana"), "x"));
    }

    @Test
    void optIntParsesNumber() {
        assertEquals(42, QueryParams.optInt(ctxWith("x", "42"), "x"));
    }

    @Test
    void intOrClampsBelowLow() {
        assertEquals(1, QueryParams.intOr(ctxWith("x", "-5"), "x", 10, 1, 100));
    }

    @Test
    void intOrClampsAboveHigh() {
        assertEquals(100, QueryParams.intOr(ctxWith("x", "500"), "x", 10, 1, 100));
    }

    @Test
    void intOrUsesFallbackOnGarbage() {
        assertEquals(10, QueryParams.intOr(ctxWith("x", "banana"), "x", 10, 1, 100));
    }

    @Test
    void doubleOrParsesValue() {
        assertEquals(0.5, QueryParams.doubleOr(ctxWith("x", "0.5"), "x", 1.0), 1e-9);
    }

    @Test
    void doubleOrUsesFallbackOnGarbage() {
        assertEquals(1.0, QueryParams.doubleOr(ctxWith("x", "banana"), "x", 1.0), 1e-9);
    }

    @Test
    void doubleOrUsesFallbackOnBlank() {
        assertEquals(2.0, QueryParams.doubleOr(ctxWith("x", ""), "x", 2.0), 1e-9);
    }
}
