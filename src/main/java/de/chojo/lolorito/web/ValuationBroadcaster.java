/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.web;

import com.google.inject.Singleton;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Real-time push (WebSocket). Producers — currently
 * {@link de.chojo.lolorito.value.MarketModelWorker} — call
 * {@link #broadcastRefit} whenever a material market-model refit
 * completes. Every subscribed SPA session receives a compact JSON
 * payload and can invalidate its own caches.
 */
@Singleton
public class ValuationBroadcaster {

    private static final Logger log = getLogger(ValuationBroadcaster.class);

    private final Set<WsContext> sessions = ConcurrentHashMap.newKeySet();

    public void addSession(WsContext ctx) {
        sessions.add(ctx);
    }

    public void removeSession(WsContext ctx) {
        sessions.remove(ctx);
    }

    public int sessionCount() {
        return sessions.size();
    }

    public void broadcastRefit(int itemId, int worldId, boolean hq) {
        if (sessions.isEmpty()) return;
        String payload =
                "{\"type\":\"model.refit\",\"itemId\":%d,\"worldId\":%d,\"hq\":%s}".formatted(itemId, worldId, hq);
        for (WsContext ctx : sessions) {
            try {
                if (ctx.session.isOpen()) ctx.send(payload);
            } catch (Exception e) {
                log.debug("Broadcast to session {} failed", ctx.sessionId(), e);
                sessions.remove(ctx);
            }
        }
    }
}
