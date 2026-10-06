/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.Basket;
import de.chojo.lolorito.entity.BasketItem;
import de.chojo.lolorito.entity.BasketVisibility;
import de.chojo.lolorito.repository.Baskets;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the "save this basket / share this basket" flow. Enforces the
 * visibility rules from {@link BasketVisibility}:
 *
 * <ul>
 *   <li>the owner always sees their own baskets,</li>
 *   <li>{@code AUTHENTICATED} baskets require any signed-in caller,</li>
 *   <li>{@code PUBLIC} baskets are readable without a session.</li>
 * </ul>
 *
 * <p>Share tokens are 24 bytes of {@link SecureRandom} base64url-encoded —
 * long enough that guessing one is not a realistic threat.
 */
@Singleton
public class BasketService {

    /**
     * Bytes of {@link SecureRandom} that back a share token — 24 bytes → 32 chars base64url.
     */
    private static final int SHARE_TOKEN_BYTES = 24;

    private final Baskets repo;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public BasketService(Baskets repo) {
        this.repo = repo;
    }

    // -- Create / update ---------------------------------------------------

    /**
     * Access check used by {@link #findShared}. Kept package-private so tests can hit it.
     */
    static boolean canRead(Basket basket, boolean sessionPresent) {
        return switch (basket.visibility()) {
            case PUBLIC -> true;
            case AUTHENTICATED -> sessionPresent;
            case PRIVATE -> false;
        };
    }

    /**
     * Trim + clamp the name so a naughty client can't blow up embeds later.
     */
    private static String sanitise(String name) {
        String n = Objects.requireNonNullElse(name, "Untitled").strip();
        if (n.isEmpty()) n = "Untitled";
        return n.length() > 120 ? n.substring(0, 120) : n;
    }

    public Basket create(long ownerUserId, String name, BasketVisibility visibility, List<BasketItem> items) {
        var now = Instant.now();
        var basket = new Basket(
                UUID.randomUUID(),
                ownerUserId,
                mintShareToken(),
                sanitise(name),
                visibility == null ? BasketVisibility.PRIVATE : visibility,
                now,
                now,
                items == null ? List.of() : items);
        repo.insert(basket);
        return basket;
    }

    // -- Reads -------------------------------------------------------------

    /**
     * Replace {@code items} + metadata on {@code basketId}. Ownership is
     * enforced — a non-owner call returns empty even if the basket exists.
     */
    public Optional<Basket> update(
            UUID basketId, long callerUserId, String name, BasketVisibility visibility, List<BasketItem> items) {
        var existing = repo.findById(basketId).orElse(null);
        if (existing == null || existing.ownerUserId() != callerUserId) return Optional.empty();
        var now = Instant.now();
        repo.replaceItems(basketId, items == null ? List.of() : items, now);
        repo.updateMeta(
                basketId,
                name == null ? existing.name() : sanitise(name),
                visibility == null ? existing.visibility() : visibility,
                now);
        return repo.findById(basketId);
    }

    public boolean delete(UUID basketId, long callerUserId) {
        var existing = repo.findById(basketId).orElse(null);
        if (existing == null || existing.ownerUserId() != callerUserId) return false;
        return repo.delete(basketId);
    }

    /**
     * List the caller's own baskets, newest first.
     */
    public List<Basket> listOwn(long ownerUserId) {
        return repo.listByOwner(ownerUserId);
    }

    /**
     * Owner-only read — used when the SPA opens {@code /baskets/:id}.
     */
    public Optional<Basket> findOwn(UUID basketId, long callerUserId) {
        return repo.findById(basketId).filter(b -> b.ownerUserId() == callerUserId);
    }

    // -- Helpers -----------------------------------------------------------

    /**
     * Public / authenticated share read.
     *
     * @param sessionPresent whether the caller has an active session at all
     * @return the basket iff its visibility permits this caller
     */
    public Optional<Basket> findShared(String shareToken, boolean sessionPresent) {
        return repo.findByShareToken(shareToken).filter(b -> canRead(b, sessionPresent));
    }

    private String mintShareToken() {
        byte[] buf = new byte[SHARE_TOKEN_BYTES];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
