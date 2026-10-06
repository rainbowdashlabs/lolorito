/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class UserServiceTest {

    @Test
    void ofReturnsBotUserWithMatchingId() {
        var service = new UserService(null);
        var user = service.of(42L);
        assertEquals(42L, user.userId());
    }

    @Test
    void ofReturnsCachedInstanceForRepeatedIds() {
        var service = new UserService(null);
        var a = service.of(1L);
        var b = service.of(1L);
        assertSame(a, b, "repeat lookups for the same id must return the cached BotUser");
    }

    @Test
    void ofReturnsDistinctInstanceForDifferentIds() {
        var service = new UserService(null);
        var a = service.of(1L);
        var b = service.of(2L);
        assertNotSame(a, b);
        assertEquals(1L, a.userId());
        assertEquals(2L, b.userId());
    }
}
