/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web.auth;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * The address a request is rate limited by. {@code X-Forwarded-For} is only trusted when the direct peer is a
 * loopback or private address, which is where a reverse proxy in front of the app (Traefik on the Docker network)
 * connects from. A client that reaches the app directly can not pick its own rate limit bucket with a made-up
 * header.
 */
final class ClientAddress {

    private ClientAddress() {}

    static String of(String peer, String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank() || !isPrivate(peer)) return peer;
        int comma = forwardedFor.indexOf(',');
        String first = (comma >= 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
        return first.isEmpty() ? peer : first;
    }

    static boolean isPrivate(String ip) {
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(ip);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) return true;
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
