/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.AlertKind;
import de.chojo.lolorito.entity.AlertRule;
import de.chojo.lolorito.entity.AlertScope;
import de.chojo.lolorito.repository.AlertRules;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD for {@link AlertRule}s. Enforces ownership on writes and clamps
 * thresholds / cooldowns to sane ranges. Firing decisions live in
 * {@link AlertMatcher}; scanning + dispatch in {@link AlertScanner}.
 */
@Singleton
public class AlertService {

    private static final int DEFAULT_COOLDOWN_MINUTES = 60;
    private static final int MIN_COOLDOWN_MINUTES = 0;
    private static final int MAX_COOLDOWN_MINUTES = 60 * 24 * 7; // one week

    private final AlertRules repo;
    private final AlertDispatcher dispatcher;

    @Inject
    public AlertService(AlertRules repo, AlertDispatcher dispatcher) {
        this.repo = repo;
        this.dispatcher = dispatcher;
    }

    public AlertRule create(long userId, CreateRequest req) {
        if (req.itemId == null || req.itemId <= 0) {
            throw new IllegalArgumentException("itemId is required");
        }
        AlertScope scope = resolveScope(req.worldId, req.dataCenterId);
        AlertKind kind = AlertKind.fromWire(req.kind);
        int threshold = Math.max(1, req.thresholdPrice == null ? 1 : req.thresholdPrice);
        int cooldown = clampCooldown(req.cooldownMinutes);
        var rule = new AlertRule(
                UUID.randomUUID(),
                userId,
                req.itemId,
                scope,
                req.hq,
                kind,
                threshold,
                true,
                cooldown,
                null,
                Instant.now());
        repo.insert(rule);
        return rule;
    }

    public List<AlertRule> listByUser(long userId) {
        return repo.listByUser(userId);
    }

    public Optional<AlertRule> findOwn(UUID id, long callerUserId) {
        return repo.findById(id).filter(r -> r.userId() == callerUserId);
    }

    public boolean delete(UUID id, long callerUserId) {
        return findOwn(id, callerUserId).map(r -> repo.delete(r.id())).orElse(false);
    }

    /**
     * Owner-only synthetic dispatch — sends the alert now with a fake
     * observed price of {@code thresholdPrice - 1} (or {@code + 1} for
     * price_above kinds) so the user can verify the plumbing without
     * waiting for the market to move. Returns {@code true} when the
     * rule was found and the dispatch was attempted; {@code false} for
     * unknown ids or non-owner callers.
     */
    public boolean test(UUID id, long callerUserId) {
        var rule = findOwn(id, callerUserId).orElse(null);
        if (rule == null) return false;
        int observed = rule.kind() == AlertKind.PRICE_ABOVE
                ? rule.thresholdPrice() + 1
                : Math.max(1, rule.thresholdPrice() - 1);
        dispatcher.dispatch(rule, observed);
        return true;
    }

    /** Owner-only enable/disable toggle. Returns empty iff the rule doesn't exist or belongs to someone else. */
    public Optional<AlertRule> setEnabled(UUID id, long callerUserId, boolean enabled) {
        var existing = findOwn(id, callerUserId).orElse(null);
        if (existing == null) return Optional.empty();
        repo.updateEnabled(existing.id(), enabled);
        return repo.findById(existing.id());
    }

    // -- Helpers -----------------------------------------------------------

    private static AlertScope resolveScope(Integer worldId, Integer dataCenterId) {
        if (worldId != null && worldId > 0 && (dataCenterId == null || dataCenterId <= 0)) {
            return AlertScope.forWorld(worldId);
        }
        if (dataCenterId != null && dataCenterId > 0 && (worldId == null || worldId <= 0)) {
            return AlertScope.forDataCenter(dataCenterId);
        }
        throw new IllegalArgumentException("exactly one of worldId / dataCenterId must be set");
    }

    private static int clampCooldown(Integer cooldown) {
        int c = cooldown == null ? DEFAULT_COOLDOWN_MINUTES : cooldown;
        return Math.clamp(c, MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES);
    }

    /** Wire body — every field nullable so validation lives in {@link #create}. */
    public record CreateRequest(
            Integer itemId,
            Integer worldId,
            Integer dataCenterId,
            Boolean hq,
            String kind,
            Integer thresholdPrice,
            Integer cooldownMinutes) {}
}
