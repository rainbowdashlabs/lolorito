/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.discord.AlertDmDispatcher;
import de.chojo.lolorito.entity.AlertRule;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Fans an alert out to every configured sink. A single failing sink is
 * logged and swallowed so a broken webhook can't stop the Discord DM
 * (or vice-versa) from firing.
 */
@Singleton
public class CompositeAlertDispatcher implements AlertDispatcher {

    private static final Logger log = getLogger(CompositeAlertDispatcher.class);

    private final AlertDmDispatcher discordDm;
    private final WebhookAlertDispatcher webhook;

    @Inject
    public CompositeAlertDispatcher(AlertDmDispatcher discordDm, WebhookAlertDispatcher webhook) {
        this.discordDm = discordDm;
        this.webhook = webhook;
    }

    @Override
    public void dispatch(AlertRule rule, int observed) {
        safe("discord-dm", () -> discordDm.dispatch(rule, observed));
        safe("webhook", () -> webhook.dispatch(rule, observed));
    }

    private static void safe(String name, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("Alert sink '{}' threw", name, e);
        }
    }
}
