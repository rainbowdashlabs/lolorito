/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.discord.AlertDmDispatcher;
import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.entity.AlertScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeAlertDispatcherTest {

    private static AlertRule rule() {
        return new AlertRule(
                UUID.randomUUID(),
                1L,
                100,
                AlertScope.forWorld(66),
                null,
                AlertKind.PRICE_BELOW,
                500,
                true,
                60,
                null,
                Instant.EPOCH);
    }

    @Test
    void bothSinksFireOnDispatch() {
        var discordCalls = new AtomicInteger();
        var webhookCalls = new AtomicInteger();
        var dispatcher = new CompositeAlertDispatcher(new StubDm(discordCalls), new StubWebhook(webhookCalls));
        dispatcher.dispatch(rule(), 250);
        assertEquals(1, discordCalls.get());
        assertEquals(1, webhookCalls.get());
    }

    @Test
    void discordFailureDoesNotBlockWebhook() {
        var webhookCalls = new AtomicInteger();
        var dispatcher = new CompositeAlertDispatcher(new ThrowingDm(), new StubWebhook(webhookCalls));
        dispatcher.dispatch(rule(), 250);
        assertEquals(1, webhookCalls.get(), "webhook fires even if the discord sink throws");
    }

    @Test
    void webhookFailureDoesNotBlockDiscord() {
        var discordCalls = new AtomicInteger();
        var dispatcher = new CompositeAlertDispatcher(new StubDm(discordCalls), new ThrowingWebhook());
        dispatcher.dispatch(rule(), 250);
        assertEquals(1, discordCalls.get(), "discord fires even if the webhook sink throws");
    }

    // -- Stubs -----------------------------------------------------------

    private static final class StubDm extends AlertDmDispatcher {
        private final AtomicInteger calls;

        StubDm(AtomicInteger calls) {
            super(null, null);
            this.calls = calls;
        }

        @Override
        public void dispatch(AlertRule rule, int observed) {
            calls.incrementAndGet();
        }
    }

    private static final class ThrowingDm extends AlertDmDispatcher {
        ThrowingDm() {
            super(null, null);
        }

        @Override
        public void dispatch(AlertRule rule, int observed) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class StubWebhook extends WebhookAlertDispatcher {
        private final AtomicInteger calls;

        StubWebhook(AtomicInteger calls) {
            super(new File(), null);
            this.calls = calls;
        }

        @Override
        public void dispatch(AlertRule rule, int observed) {
            calls.incrementAndGet();
        }
    }

    private static final class ThrowingWebhook extends WebhookAlertDispatcher {
        ThrowingWebhook() {
            super(new File(), null);
        }

        @Override
        public void dispatch(AlertRule rule, int observed) {
            throw new IllegalStateException("boom");
        }
    }
}
