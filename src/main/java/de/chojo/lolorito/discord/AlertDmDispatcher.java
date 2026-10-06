/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.discord;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.core.Discord;
import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.service.AlertDispatcher;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.Worlds;
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
        String itemName = nameOf(rule.itemId());
        String scope = rule.scope().isWorld()
                ? worldName(rule.scope().worldId())
                : "DC " + rule.scope().dataCenterId();
        return new EmbedBuilder()
                .setTitle("Market alert: " + itemName)
                .setDescription(describe(rule.kind(), scope, observed))
                .addField("Threshold", thresholdLabel(rule.kind(), rule.threshold()), true)
                .addField("HQ", rule.hq() == null ? "either" : (rule.hq() ? "high" : "normal"), true)
                .setFooter("Rule " + rule.id())
                .build();
    }

    private static String describe(AlertKind kind, String scope, int observed) {
        return switch (kind) {
            case PRICE_BELOW -> "Cheapest listing on **%s** dropped to **%,d gil**.".formatted(scope, observed);
            case PRICE_ABOVE -> "Cheapest listing on **%s** climbed to **%,d gil**.".formatted(scope, observed);
            case SALE_VOLUME_SPIKE ->
                "Sales on **%s** in the last 24 h are at **%,d %%** of the usual daily volume."
                        .formatted(scope, observed);
            case LISTING_COUNT_DROP -> "Only **%,d** listing(s) left on **%s**.".formatted(observed, scope);
        };
    }

    private static String thresholdLabel(AlertKind kind, int threshold) {
        return switch (kind) {
            case PRICE_BELOW, PRICE_ABOVE -> "%,d gil".formatted(threshold);
            case SALE_VOLUME_SPIKE -> "%,d %% of usual".formatted(threshold);
            case LISTING_COUNT_DROP -> "%,d listings".formatted(threshold);
        };
    }

    private String nameOf(int itemId) {
        if (nameSupplier == null) return String.valueOf(itemId);
        var name = nameSupplier.fromId(itemId);
        return name == null ? String.valueOf(itemId) : name.get(Language.ENGLISH);
    }

    private static String worldName(int worldId) {
        var w = Worlds.worldById(worldId);
        return w == null ? String.valueOf(worldId) : w.name();
    }
}
