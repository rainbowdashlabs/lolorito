/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.AlertRule;

/**
 * Notification sink for a fired alert. The default binding is
 * {@link de.chojo.lolorito.discord.AlertDmDispatcher} which sends a
 * Discord DM. Tests substitute an in-memory list.
 */
public interface AlertDispatcher {
    /**
     * Called by the scanner when {@link AlertMatcher} says a rule should
     * fire. Implementations must be side-effect-safe under duplicate
     * calls — the scanner records {@code last_triggered_at} <em>after</em>
     * a successful dispatch, so a retry could re-invoke this method for
     * the same trigger.
     *
     * @param rule the fired rule
     * @param observed the value that crossed the threshold: cheapest price,
     *                 spike percent, or listing count, depending on the kind
     */
    void dispatch(AlertRule rule, int observed);
}
