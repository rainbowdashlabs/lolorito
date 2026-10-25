/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.universalis.ItemNames;
import de.chojo.lolorito.universalis.WorldNames;
import de.chojo.universalis.provider.NameSupplier;

/**
 * The human-readable text of a fired alert, shared by every channel that renders one as a message (the Discord DM
 * and Discord webhooks), so they always say the same thing.
 *
 * @param title       message title, naming the item
 * @param description what happened, with the observed value and the scope
 * @param threshold   the rule's threshold in the unit of its kind
 * @param quality     the rule's quality filter: {@code high}, {@code normal} or {@code either}
 * @param footer      the rule id
 */
public record AlertMessage(String title, String description, String threshold, String quality, String footer) {

    public static AlertMessage of(AlertRule rule, int observed, NameSupplier names) {
        String scope = rule.scope().isWorld()
                ? WorldNames.nameOf(rule.scope().worldId())
                : WorldNames.dataCenterNameOf(rule.scope().dataCenterId());
        return new AlertMessage(
                "Market alert: " + ItemNames.nameOf(names, rule.itemId()),
                describe(rule.kind(), scope, observed),
                thresholdLabel(rule.kind(), rule.threshold()),
                rule.hq() == null ? "either" : (rule.hq() ? "high" : "normal"),
                "Rule " + rule.id());
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
}
