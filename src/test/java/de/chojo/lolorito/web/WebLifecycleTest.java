/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Life-cycle branches on {@link Web} that aren't hit by JavalinTest —
 * accessors, and {@link Web#stop()} on an app that was never started.
 */
class WebLifecycleTest extends RouteTestBase {

    @Test
    void authServiceAccessorReturnsSameInstance() {
        var web = injector.getInstance(Web.class);
        assertThat(web.authService()).isNotNull();
    }

    @Test
    void stopIsSafeBeforeStart() {
        var web = injector.getInstance(Web.class);
        web.stop();
    }

    /**
     * Boot the real Javalin app and immediately stop it — covers the
     * {@code app.start(host, port)} branch of {@link Web#start()}. Port
     * comes from {@link RouteTestBase} which sets it to 0,
     * so the OS picks a free ephemeral port and we can't collide with a
     * dev backend on 8080.
     */
    @Test
    void startBindsAndStops() {
        var web = injector.getInstance(Web.class);
        web.start();
        try {
            assertThat(web.authService()).isNotNull();
        } finally {
            web.stop();
        }
    }
}
