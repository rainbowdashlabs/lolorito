/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.repository.ListingEpisodes;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.entities.Listing;
import de.chojo.universalis.entities.Sale;
import de.chojo.universalis.worlds.World;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The listing-lifecycle primitive.
 *
 * <p>Universalis delivers the <em>full</em> current listing set per
 * (world, item) on every refresh, never diffs. This service compares each
 * snapshot against the open episodes in {@code listing_episode} and records
 * the transitions:
 *
 * <ul>
 *   <li>identity present in snapshot, no open episode → <b>POSTED</b> (open
 *       a new episode; if it lands below the previous floor, also record an
 *       undercut event — the measured basis for {@code lambda_undercut})</li>
 *   <li>identity present in both → still on shelf (touch {@code last_seen})</li>
 *   <li>open episode missing from snapshot → <b>ended</b>: SOLD when a
 *       matching sale exists since it appeared, DELISTED otherwise; sale
 *       events that arrive late flip DELISTED → SOLD via
 *       {@link #onSales}</li>
 * </ul>
 *
 * <p>Identity: Universalis' {@code listingId} when present (rare — the
 * field is almost always null client-side), otherwise
 * {@code retainerId|hq|price|qty} with an occurrence suffix so two
 * identical stacks from the same retainer stay distinct episodes.
 */
@Singleton
public class ListingDiffer {

    private final ListingEpisodes episodes;

    @Inject
    public ListingDiffer(ListingEpisodes episodes) {
        this.episodes = episodes;
    }

    public void onSnapshot(World world, Item item, List<Listing> listings, Instant now) {
        var snapshot = identities(listings);
        var open = episodes.openFor(world.id(), item.id());

        Map<String, ListingEpisodes.OpenEpisode> openByIdentity = new HashMap<>();
        for (var e : open) openByIdentity.put(e.identity(), e);

        List<Long> touched = new ArrayList<>();
        List<ListingEpisodes.NewEpisode> posted = new ArrayList<>();
        for (var entry : snapshot.entrySet()) {
            var existing = openByIdentity.remove(entry.getKey());
            if (existing != null) {
                touched.add(existing.id());
            } else {
                posted.add(entry.getValue());
            }
        }
        // Whatever's left in openByIdentity vanished from the snapshot.
        List<Long> gone = openByIdentity.values().stream()
                .map(ListingEpisodes.OpenEpisode::id)
                .toList();

        recordUndercuts(world, item, open, posted, now);

        episodes.touch(touched, now);
        episodes.open(world.id(), item.id(), posted, now);
        episodes.close(world.id(), item.id(), gone, now);
    }

    /** Late-arriving sales heal DELISTED endings into SOLD. */
    public void onSales(World world, int itemId, Collection<Sale> sales) {
        for (Sale sale : sales) {
            episodes.reclassifySold(
                    world.id(),
                    itemId,
                    sale.hq(),
                    sale.price().pricePerUnit(),
                    sale.price().quantity(),
                    sale.timestamp());
        }
    }

    /**
     * A fresh listing strictly below the previous open floor of its quality
     * bucket is an undercut. One event per quality per snapshot — the
     * lowest new price against the old floor is the meaningful step.
     */
    private void recordUndercuts(
            World world,
            Item item,
            List<ListingEpisodes.OpenEpisode> openBefore,
            List<ListingEpisodes.NewEpisode> posted,
            Instant now) {
        for (boolean hq : new boolean[] {false, true}) {
            int oldFloor = openBefore.stream()
                    .filter(e -> e.hq() == hq)
                    .mapToInt(ListingEpisodes.OpenEpisode::unitPrice)
                    .min()
                    .orElse(Integer.MAX_VALUE);
            if (oldFloor == Integer.MAX_VALUE) continue; // nothing to undercut
            int newFloor = posted.stream()
                    .filter(e -> e.hq() == hq)
                    .mapToInt(ListingEpisodes.NewEpisode::unitPrice)
                    .min()
                    .orElse(Integer.MAX_VALUE);
            if (newFloor < oldFloor) {
                episodes.recordUndercut(world.id(), item.id(), hq, now, newFloor, oldFloor);
            }
        }
    }

    /**
     * Snapshot listings keyed by stable identity. LinkedHashMap-free —
     * order doesn't matter, only stability of the occurrence suffix, which
     * the incoming listing order provides (Universalis sends price-sorted).
     */
    private static Map<String, ListingEpisodes.NewEpisode> identities(List<Listing> listings) {
        Map<String, Integer> seen = new HashMap<>();
        Map<String, ListingEpisodes.NewEpisode> out = new HashMap<>();
        for (Listing l : listings) {
            String base = baseIdentity(l);
            int occurrence = seen.merge(base, 1, Integer::sum);
            String identity = occurrence == 1 ? base : base + "#" + occurrence;
            var retainer = l.retainer();
            out.put(
                    identity,
                    new ListingEpisodes.NewEpisode(
                            identity,
                            l.meta().hq(),
                            l.price().pricePerUnit(),
                            l.price().quantity(),
                            retainer == null ? null : retainer.id()));
        }
        return out;
    }

    private static String baseIdentity(Listing l) {
        String listingId = l.listingId();
        if (listingId != null && !listingId.isBlank()) return "lid:" + listingId;
        var retainer = l.retainer();
        String retainerId = retainer == null || retainer.id() == null ? "?" : retainer.id();
        return retainerId + "|" + (l.meta().hq() ? "hq" : "nq") + "|"
                + l.price().pricePerUnit() + "|" + l.price().quantity();
    }
}
