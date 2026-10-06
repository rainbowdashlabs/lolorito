/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.OfferFilterTarget;
import de.chojo.lolorito.repository.Offers;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.lolorito.value.Valuation;
import de.chojo.lolorito.value.ValueEngine;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;

import java.util.Comparator;
import java.util.List;

/**
 * Business logic for {@code GET /api/v1/offers}. Talks to {@link Offers} for
 * raw candidates and to {@link ValueEngine} for scoring; no SQL lives here.
 *
 * <p>Results are memoised in a {@link ResponseCache} keyed by the request
 * shape — a burst of clicks from the SPA (same home world, same TTL) hits
 * the cache instead of re-scoring the region.
 */
@Singleton
public class OffersService {
    private final Offers repo;
    private final NameSupplier itemNames;
    private final de.chojo.lolorito.config.file.elements.Planner planner;
    private final ResponseCache<CacheKey, List<ScoredOffer>> cache;

    @Inject
    public OffersService(File config, Offers repo, NameSupplier itemNames) {
        this.repo = repo;
        this.itemNames = itemNames;
        this.planner = config.planner();
        this.cache = new ResponseCache<>(
                config.value().responseCacheSeconds(), config.value().responseCacheMaxSize());
    }

    /**
     * Top {@code limit} candidates for the given home world / DC, sorted by
     * attention-adjusted gil-per-hour and filtered to positive-EV rows.
     * Defaults to {@link OfferFilterTarget#DATA_CENTER} scope for callers
     * that predate the scope switch.
     */
    public List<ScoredOffer> topOffers(
            int homeWorldId, int homeDataCenterId, int refreshHours, UserPrefs prefs, int limit) {
        return topOffers(
                homeWorldId,
                homeDataCenterId,
                "",
                OfferFilterTarget.DATA_CENTER,
                refreshHours,
                prefs,
                limit,
                Language.ENGLISH);
    }

    /**
     * Same as {@link #topOffers(int, int, int, UserPrefs, int)} but resolves
     * item names in the caller's preferred language. Falls back to English
     * whenever the localized string is missing.
     */
    public List<ScoredOffer> topOffers(
            int homeWorldId,
            int homeDataCenterId,
            String regionName,
            OfferFilterTarget scope,
            int refreshHours,
            UserPrefs prefs,
            int limit,
            Language language) {
        var key = new CacheKey(homeWorldId, homeDataCenterId, regionName, scope, refreshHours, prefs, limit, language);
        return cache.get(
                key,
                k -> compute(
                        k.homeWorldId(),
                        k.homeDataCenterId(),
                        k.regionName(),
                        k.scope(),
                        k.refreshHours(),
                        k.prefs(),
                        k.limit(),
                        k.language()));
    }

    /**
     * Recomputes the top offers ignoring the cache — useful for tests.
     */
    public List<ScoredOffer> topOffersUncached(
            int homeWorldId, int homeDataCenterId, int refreshHours, UserPrefs prefs, int limit) {
        return compute(
                homeWorldId,
                homeDataCenterId,
                "",
                OfferFilterTarget.DATA_CENTER,
                refreshHours,
                prefs,
                limit,
                Language.ENGLISH);
    }

    /**
     * Exposed so admin flows or tests can drop the cache.
     */
    public void invalidateCache() {
        cache.invalidateAll();
    }

    /**
     * Exposed for tests / metrics.
     */
    public ResponseCache<CacheKey, List<ScoredOffer>> cache() {
        return cache;
    }

    private List<ScoredOffer> compute(
            int homeWorldId,
            int homeDataCenterId,
            String regionName,
            OfferFilterTarget scope,
            int refreshHours,
            UserPrefs prefs,
            int limit,
            Language language) {
        var candidates = repo.candidates(homeWorldId, homeDataCenterId, regionName, scope, refreshHours, limit * 4);
        var ranked = candidates.stream()
                .map(c -> score(c, prefs, homeDataCenterId, language))
                .filter(o -> o != null && o.valuation().evPerHour() > 0)
                .sorted(Comparator.comparingDouble(
                                (ScoredOffer o) -> o.valuation().evPerHour())
                        .reversed())
                .toList();
        // One row per (item, hq): every candidate of the same key is scored
        // as if it alone captured the home world's demand, so showing three
        // of them presents triple-counted EV. Keep the best.
        var seen = new java.util.HashSet<Long>();
        var out = new java.util.ArrayList<ScoredOffer>(limit);
        for (var offer : ranked) {
            long key = ((long) offer.itemId() << 1) | (offer.hq() ? 1 : 0);
            if (!seen.add(key)) continue;
            out.add(offer);
            if (out.size() >= limit) break;
        }
        return List.copyOf(out);
    }

    private ScoredOffer score(Offers.Candidate c, UserPrefs prefs, int homeDataCenterId, Language language) {
        // Review 2.7: a cross-DC hop costs more wall-clock than a same-DC
        // one — spread the planner's hop constants onto the candidate's run
        // share instead of pretending every source is 30 s away.
        var source = de.chojo.universalis.worlds.Worlds.worldById(c.sourceWorldId());
        boolean sameDc = source != null
                && source.dataCenter() != null
                && source.dataCenter().id() == homeDataCenterId;
        double hopSeconds = sameDc ? planner.tDcSeconds() : planner.tRegionSeconds();
        var hopPrefs = new UserPrefs(prefs.mbTax(), prefs.attentionFraction(), prefs.tRunShareSeconds() + hopSeconds);

        var v = ValueEngine.value(c.model(), c.buyPrice(), c.quantity(), c.depthAhead(), hopPrefs)
                .orElse(null);
        if (v == null) return null;
        // Adversary flags live on the market model — surface them on the
        // offer so the SPA can render badges + power the "hide bot /
        // ghost" filter without pulling the whole item detail.
        return new ScoredOffer(
                c.sourceWorldId(),
                c.itemId(),
                itemNameOf(c.itemId(), language),
                c.hq(),
                c.quantity(),
                c.buyPrice(),
                c.depthAhead(),
                v,
                c.model().lambdaUndercut(),
                c.model().ghostFraction(),
                c.model().sufficient(),
                c.model().pooled());
    }

    private String itemNameOf(int itemId, Language language) {
        if (itemNames == null) return String.valueOf(itemId);
        var name = itemNames.fromId(itemId);
        if (name == null) return String.valueOf(itemId);
        String localised = name.get(language);
        if (localised != null && !localised.isBlank()) return localised;
        String english = name.get(Language.ENGLISH);
        return english == null ? String.valueOf(itemId) : english;
    }

    /**
     * One scored row — the shape the /offers endpoint serializes.
     * {@code depthAhead} is the home-queue depth the units would join;
     * {@code modelPooled} marks lower-confidence DC-pooled fits.
     */
    public record ScoredOffer(
            int sourceWorldId,
            int itemId,
            String itemName,
            boolean hq,
            int quantity,
            int buyPrice,
            int depthAhead,
            Valuation valuation,
            double lambdaUndercut,
            double ghostFraction,
            boolean modelSufficient,
            boolean modelPooled) {}

    /**
     * Cache key — the full request shape. Records auto-generate equals/hashCode.
     */
    public record CacheKey(
            int homeWorldId,
            int homeDataCenterId,
            String regionName,
            OfferFilterTarget scope,
            int refreshHours,
            UserPrefs prefs,
            int limit,
            Language language) {}
}
