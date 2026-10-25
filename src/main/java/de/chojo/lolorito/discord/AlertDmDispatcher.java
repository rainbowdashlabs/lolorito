/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.core.Discord;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.service.AlertDispatcher;
import de.chojo.lolorito.service.AlertMessage;
import de.chojo.universalis.provider.NameSupplier;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * {@link AlertDispatcher} that opens a DM with the rule's owner and sends
 * a short embed. Failures are logged — a Discord outage should never
 * block the scanner loop.
 */
@Singleton
public class AlertDmDispatcher implements AlertDispatcher {

    private static final Logger log = getLogger(AlertDmDispatcher.class);

    private final Discord discord;
    private final NameSupplier nameSupplier;

    @Inject
    public AlertDmDispatcher(Discord discord, NameSupplier nameSupplier) {
        this.discord = discord;
        this.nameSupplier = nameSupplier;
    }

    @Override
    public void dispatch(AlertRule rule, int observed) {
        var shardManager = discord.shardManager();
        if (shardManager == null) {
            log.warn("Alert {} fired but shard manager is not ready — skipping DM", rule.id());
            return;
        }
        try {
            shardManager
                    .retrieveUserById(rule.userId())
                    .queue(
                            user -> user.openPrivateChannel()
                                    .queue(
                                            channel -> channel.sendMessageEmbeds(buildEmbed(rule, observed))
                                                    .queue(
                                                            ok -> {},
                                                            err -> log.warn(
                                                                    "Alert {} DM send failed for user {}",
                                                                    rule.id(),
                                                                    rule.userId(),
                                                                    err)),
                                            err -> log.warn(
                                                    "Alert {} could not open DM with user {}",
                                                    rule.id(),
                                                    rule.userId(),
                                                    err)),
                            err -> log.warn("Alert {} could not resolve user {}", rule.id(), rule.userId(), err));
        } catch (Exception e) {
            log.warn("Alert {} dispatch failed", rule.id(), e);
        }
    }

    private MessageEmbed buildEmbed(AlertRule rule, int observed) {
        var message = AlertMessage.of(rule, observed, nameSupplier);
        return new EmbedBuilder()
                .setTitle(message.title())
                .setDescription(message.description())
                .addField("Threshold", message.threshold(), true)
                .addField("HQ", message.quality(), true)
                .setFooter(message.footer())
                .build();
    }
}
