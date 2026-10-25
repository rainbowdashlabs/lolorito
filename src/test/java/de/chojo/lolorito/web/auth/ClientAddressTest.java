/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAddressTest {

    @Test
    void proxyOnThePrivateNetworkIsTrusted() {
        assertEquals("203.0.113.7", ClientAddress.of("172.18.0.5", "203.0.113.7"));
        assertEquals("203.0.113.7", ClientAddress.of("127.0.0.1", "203.0.113.7, 10.0.0.2"));
    }

    @Test
    void directClientCanNotChooseItsKey() {
        assertEquals("198.51.100.9", ClientAddress.of("198.51.100.9", "1.2.3.4"));
    }

    @Test
    void missingHeaderUsesThePeer() {
        assertEquals("172.18.0.5", ClientAddress.of("172.18.0.5", null));
        assertEquals("172.18.0.5", ClientAddress.of("172.18.0.5", " "));
        assertEquals("172.18.0.5", ClientAddress.of("172.18.0.5", ", 1.2.3.4"));
    }

    @Test
    void privateRangesAreRecognised() {
        assertTrue(ClientAddress.isPrivate("10.1.2.3"));
        assertTrue(ClientAddress.isPrivate("192.168.0.1"));
        assertTrue(ClientAddress.isPrivate("::1"));
        assertTrue(ClientAddress.isPrivate("fd00::1"));
        assertFalse(ClientAddress.isPrivate("8.8.8.8"));
        assertFalse(ClientAddress.isPrivate("2001:db8::1"));
        assertFalse(ClientAddress.isPrivate("not an ip"));
    }
}
