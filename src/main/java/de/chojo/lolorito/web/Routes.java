/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import io.javalin.router.JavalinDefaultRoutingApi;

/**
 * Contract every route class implements. Guice multibinds these; the
 * bootstrap iterates over the set and calls {@link #register(JavalinDefaultRoutingApi)}
 * on each one. Ported from Ember's {@code api.Routes}.
 */
public interface Routes {
    void register(JavalinDefaultRoutingApi routes);
}
