/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.BotUser;
import de.chojo.lolorito.entity.ItemListing;
import de.chojo.lolorito.entity.ItemStat;
import de.chojo.lolorito.entity.Listing;
import de.chojo.lolorito.entity.Offer;
import de.chojo.lolorito.entity.OfferFilterRow;
import de.chojo.lolorito.entity.OfferListing;
import de.chojo.lolorito.entity.SearchScope;
import de.chojo.lolorito.entity.TopFilter;
import de.chojo.lolorito.entity.WorldListings;
import de.chojo.lolorito.entity.WorldOffers;
import de.chojo.lolorito.repository.Items;
import de.chojo.universalis.entities.Item;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembly logic for the Discord {@code /check}, {@code /offers get}, and
 * {@code /top} handlers. Consumes raw rows from {@link Items}, groups them
 * by world, resolves the per-world {@link ItemStat}, and returns
 * ready-to-embed entities.
 */
@Singleton
public class ItemsService {
    private final Items repo;
    private final FilterService filters;
    private final NameSupplier itemNames;

    @Inject
    public ItemsService(Items repo, FilterService filters, NameSupplier itemNames) {
        this.repo = repo;
        this.filters = filters;
        this.itemNames = itemNames;
    }

    static int scopeIdFor(TopFilter topFilter, World home, OfferFilterRow filter) {
        return switch (topFilter.searchScope()) {
            case WORLD -> home.id();
            case DATACENTER -> home.dataCenter().id();
        };
    }

    /**
     * Build the {@code /check} response for {@code itemId} on the user's home DC.
     */
    public Listing listingFor(BotUser user, int itemId, Boolean hq) {
        var filter = filters.current(user.userId());
        World home = Worlds.worldById(filter.worldId());
        if (home == null || home.dataCenter() == null) {
            return new Listing(ItemStat.empty(home, itemFor(itemId), hq), Map.of());
        }
        var itemListings = repo.currentListings(itemId, home.dataCenter().id(), hq);
        Map<World, WorldListings> byWorld = new HashMap<>();
        for (ItemListing listing : itemListings) {
            byWorld.computeIfAbsent(
                            listing.world(),
                            w -> new WorldListings(repo.stats(w, listing.item(), listing.hq()), new ArrayList<>()))
                    .listings()
                    .add(listing);
        }
        return new Listing(repo.stats(home, itemFor(itemId), hq), byWorld);
    }

    /**
     * Build the {@code /offers get} response for the user's saved filter.
     */
    public List<Offer> bestOffersFor(BotUser user) {
        var filter = filters.current(user.userId());
        World home = Worlds.worldById(filter.worldId());
        if (home == null || home.dataCenter() == null) return Collections.emptyList();

        var offerListings = repo.arbitrageListings(filter, home);
        Map<ItemKey, Map<World, WorldOffers>> byItemAndWorld = new HashMap<>();
        for (OfferListing listing : offerListings) {
            byItemAndWorld
                    .computeIfAbsent(new ItemKey(listing.hq(), listing.item()), i -> new HashMap<>())
                    .computeIfAbsent(
                            listing.world(),
                            w -> new WorldOffers(repo.stats(w, listing.item(), listing.hq()), new ArrayList<>()))
                    .listings()
                    .add(listing);
        }
        var offers = new ArrayList<Offer>();
        for (var entry : byItemAndWorld.entrySet()) {
            ItemStat stats =
                    repo.stats(home, entry.getKey().item(), entry.getKey().hq());
            offers.add(new Offer(stats, entry.getValue()));
        }
        return offers;
    }

    /**
     * Build the {@code /top} response, scoped by the user's saved world/DC.
     */
    public List<ItemStat> topFor(BotUser user, TopFilter topFilter) {
        var filter = filters.current(user.userId());
        World home = Worlds.worldById(filter.worldId());
        if (home == null || home.dataCenter() == null) return Collections.emptyList();
        int scopeId = scopeIdFor(topFilter, home, filter);
        return repo.top(topFilter, scopeId);
    }

    /**
     * Convenience passthrough for callers that want the enum for autocomplete.
     */
    public SearchScope[] scopes() {
        return SearchScope.values();
    }

    private Item itemFor(int itemId) {
        return new Item(itemId, itemNames == null ? null : itemNames.fromId(itemId));
    }

    private record ItemKey(boolean hq, Item item) {}
}
