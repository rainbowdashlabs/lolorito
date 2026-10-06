/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.CharacterRetainer;
import de.chojo.lolorito.repository.CharacterRetainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Owns the "match this user to Universalis' listings feed" pipeline.
 *
 * <p>The FFXIV client no longer exposes the retainer's owner id, so
 * Universalis has stopped shipping the {@code sellerID} hash. That
 * removes any way to auto-link sibling retainers to the same account.
 * Users declare every retainer they own; we match by
 * {@code (world_id, retainer_name)} against the ambient listings feed.
 *
 * <p>A retainer needs at least one active listing before Universalis
 * observes it — until then it's declared but "not seen" in the UI.
 */
@Singleton
public class RetainerAttributionService {
    private static final int OWNED_LISTINGS_LIMIT = 200;

    private final CharacterRetainers retainers;

    @Inject
    public RetainerAttributionService(CharacterRetainers retainers) {
        this.retainers = retainers;
    }

    public List<CharacterRetainer> list(long discordUserId) {
        return retainers.list(discordUserId);
    }

    public void put(long discordUserId, int worldId, String retainerName) {
        retainers.put(discordUserId, worldId, retainerName);
    }

    public boolean remove(long discordUserId, int worldId, String retainerName) {
        return retainers.delete(discordUserId, worldId, retainerName);
    }

    /**
     * Every listing whose {@code (world, retainer_name)} matches one the
     * caller has declared. Returned newest first, capped at
     * {@value #OWNED_LISTINGS_LIMIT}.
     */
    public List<OwnedListing> ownedListings(long discordUserId) {
        var declared = retainers.list(discordUserId);
        if (declared.isEmpty()) return List.of();

        var rows = new ArrayList<OwnedListing>();
        // Bounded by the number of retainers a user has (typically ≤ 8),
        // so inlining is fine.
        String retainerIn = declared.stream()
                .map(r -> "(" + r.worldId() + ",'" + r.retainerName().replace("'", "''") + "')")
                .reduce((a, b) -> a + "," + b)
                .orElse("(0,'')");
        String sql = """
                SELECT world, item, hq, unit_price, quantity, review_time, retainer_name
                  FROM listings
                 WHERE (world, retainer_name) IN (%s)
                 ORDER BY review_time DESC
                 LIMIT %d
                """.formatted(retainerIn, OWNED_LISTINGS_LIMIT);

        query(sql)
                .single(call())
                .map(row -> {
                    rows.add(new OwnedListing(
                            row.getInt("world"),
                            row.getInt("item"),
                            row.getBoolean("hq"),
                            row.getInt("unit_price"),
                            row.getInt("quantity"),
                            row.get("review_time", INSTANT_TIMESTAMP),
                            row.getString("retainer_name")));
                    return null;
                })
                .all();
        return rows;
    }

    /**
     * Suggest retainer names to autocomplete in the settings form.
     */
    public List<String> suggestRetainerNames(int worldId, String q) {
        String trimmed = q == null ? "" : q.trim();
        String pattern = "%" + trimmed.toLowerCase() + "%";
        return query("""
                SELECT DISTINCT retainer_name
                  FROM listings
                 WHERE world = :w
                   AND retainer_name IS NOT NULL
                   AND lower(retainer_name) LIKE :p
                 ORDER BY retainer_name ASC
                 LIMIT 25
                """)
                .single(call().bind("w", worldId).bind("p", pattern))
                .map(row -> row.getString("retainer_name"))
                .all();
    }

    /**
     * True if we have at least one recent listing for this retainer —
     * used by the UI to render a "seen / not seen yet" badge.
     */
    public boolean hasBeenSeen(int worldId, String retainerName) {
        return query("""
                SELECT 1 FROM listings
                 WHERE world = :w AND retainer_name = :n
                 LIMIT 1
                """)
                .single(call().bind("w", worldId).bind("n", retainerName))
                .map(row -> Boolean.TRUE)
                .first()
                .orElse(false);
    }

    /** One listing attributed to the caller. */
    public record OwnedListing(
            int worldId,
            int itemId,
            boolean hq,
            int unitPrice,
            int quantity,
            Instant reviewTime,
            String retainerName) {}
}
