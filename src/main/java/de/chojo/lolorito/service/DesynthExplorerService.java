/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.repository.DesynthResults;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.MarketModels;
import de.chojo.lolorito.value.UserPrefs;
import de.chojo.lolorito.value.ValueEngine;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ranks every desynth source we know about by expected profit-per-hour
 * for a given home world. Buy the source item at its cheapest price
 * within the chosen scope (home DC by default — DC hops are cheap;
 * region-wide on request, since travelling between data centers is
 * substantial effort), desynth it, sell every component at the home
 * world's expected price.
 *
 * <p>Sources whose required crafter class + level exceed the caller's
 * skill map are dropped. The seed carries class + level for every row
 * as of the XIVAPI backfill pass, so the null-safe fallback below is
 * defensive against a partial refresh — a row that somehow still lacks
 * either field passes the gate rather than being hidden.
 *
 * <p>Deliberately lightweight — walks {@link DesynthResults#allSources()}
 * once, does one home-model + one regional-listings lookup per source,
 * and returns the top {@code limit} by EV/hour. Cheap enough to run
 * without a cache; expensive enough that we cap the walk to avoid
 * timing out on cold JVMs.
 */
@Singleton
public class DesynthExplorerService {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(DesynthExplorerService.class);

    private static final int MAX_SOURCES = 500;
    private static final int DEFAULT_LIMIT = 50;

    private final File config;
    private final DesynthResults results;
    private final ItemDetail repo;
    private final MarketModels models;
    private final NameSupplier itemNames;
    private final UserSkillLevelService skills;
    private final UserPreferencesService preferences;

    @Inject
    public DesynthExplorerService(
            File config,
            DesynthResults results,
            ItemDetail repo,
            MarketModels models,
            NameSupplier itemNames,
            UserSkillLevelService skills,
            UserPreferencesService preferences) {
        this.config = config;
        this.results = results;
        this.repo = repo;
        this.models = models;
        this.itemNames = itemNames;
        this.skills = skills;
        this.preferences = preferences;
    }

    /** Convenience for callers with no user context (tests). No skill gating applied, DC scope. */
    public List<Candidate> topCandidates(int homeWorldId, int limit) {
        return topCandidatesFor(homeWorldId, limit, Language.ENGLISH, Map.of(), Set.of(), 0, false, 0.25);
    }

    /**
     * Same as {@link #topCandidates(int, int)} but resolves item names
     * in the caller's preferred language and filters candidates against
     * the caller's per-class desynth levels, plus optional caller-
     * supplied {@code classFilter} (empty = all classes) and {@code
     * minLevel} (0 = no lower bound). Buy probe stays on the home DC.
     */
    public List<Candidate> topCandidatesFor(
            int homeWorldId, int limit, Language language, long discordUserId, Set<String> classFilter, int minLevel) {
        return topCandidatesFor(homeWorldId, limit, language, discordUserId, classFilter, minLevel, false);
    }

    /**
     * @param regionWide widen the buy-price probe from the home DC to the
     *                   whole region — the caller accepts DC-hop travel
     */
    public List<Candidate> topCandidatesFor(
            int homeWorldId,
            int limit,
            Language language,
            long discordUserId,
            Set<String> classFilter,
            int minLevel,
            boolean regionWide) {
        return topCandidatesFor(
                homeWorldId,
                limit,
                language,
                skills.desynthLevels(discordUserId),
                classFilter,
                minLevel,
                regionWide,
                preferences.attentionFractionFor(discordUserId, 0.25));
    }

    List<Candidate> topCandidatesFor(
            int homeWorldId,
            int limit,
            Language language,
            Map<String, Integer> skillLevels,
            Set<String> classFilter,
            int minLevel,
            boolean regionWide,
            double attentionFraction) {
        Set<String> normalisedClasses = classFilter.stream()
                .map(c -> c == null ? "" : c.trim().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
        int cap = Math.max(1, Math.min(DEFAULT_LIMIT * 4, limit <= 0 ? DEFAULT_LIMIT : limit));
        World home = Worlds.worldById(homeWorldId);
        if (home == null || home.dataCenter() == null) return List.of();
        int dcId = home.dataCenter().id();
        String regionName = home.dataCenter().region().name();
        var prefs = new UserPrefs(config.value().mbTaxFor(dcId), attentionFraction, 30.0);

        // Pass 1 — cheap in-memory filters, collect the eligible sources.
        var eligible = new ArrayList<DesynthResults.Source>();
        int skippedByCap = 0;
        for (DesynthResults.Source src : results.allSources()) {
            if (!classFilterPasses(src, normalisedClasses)) continue;
            if (!minLevelPasses(src, minLevel)) continue;
            if (!skillGatePasses(src, skillLevels)) continue;
            if (eligible.size() >= MAX_SOURCES) {
                skippedByCap++;
                continue;
            }
            eligible.add(src);
        }
        if (skippedByCap > 0) {
            log.warn(
                    "Desynth explorer source cap hit: {} eligible sources not evaluated (cap {})",
                    skippedByCap,
                    MAX_SOURCES);
        }

        // Pass 2 — two batched round trips: scope-cheapest for every
        // source, home models for every component. Replaces the per-source
        // N+1 that made this walk quadratic in practice.
        int freshHours = config.value().listingFreshnessHours();
        var sourceIds = eligible.stream().map(DesynthResults.Source::itemId).toList();
        // Probe both qualities and buy whichever board is cheaper — gear
        // sources are frequently listed HQ-only, and the components come
        // out the same either way.
        var buyPricesNq = regionWide
                ? repo.cheapestByItemRegion(regionName, sourceIds, false, freshHours)
                : repo.cheapestByItem(dcId, sourceIds, false, freshHours);
        var buyPricesHq = regionWide
                ? repo.cheapestByItemRegion(regionName, sourceIds, true, freshHours)
                : repo.cheapestByItem(dcId, sourceIds, true, freshHours);

        var componentsBySource = new java.util.HashMap<Integer, List<DesynthResults.Component>>();
        var componentIds = new java.util.HashSet<Integer>();
        for (var src : eligible) {
            if (!buyPricesNq.containsKey(src.itemId()) && !buyPricesHq.containsKey(src.itemId())) continue;
            var components = results.findBySource(src.itemId());
            if (components.isEmpty()) continue;
            componentsBySource.put(src.itemId(), components);
            for (var c : components) componentIds.add(c.componentItemId());
        }
        var componentModels = models.findAll(homeWorldId, componentIds, false);
        // Resale comparison — buy→sell and buy→desynth→sell belong on
        // one axis. Batch the source items' own home models.
        var sourceModels = models.findAll(homeWorldId, componentsBySource.keySet(), false);

        var out = new ArrayList<Candidate>();
        for (var src : eligible) {
            var components = componentsBySource.get(src.itemId());
            if (components == null) continue;
            Integer nqBuy = buyPricesNq.get(src.itemId());
            Integer hqBuy = buyPricesHq.get(src.itemId());
            boolean hqSource = hqBuy != null && hqBuy > 0 && (nqBuy == null || nqBuy <= 0 || hqBuy < nqBuy);
            Integer probeBuy = hqSource ? hqBuy : nqBuy;
            if (probeBuy == null || probeBuy <= 0) continue;

            var pricings = new ArrayList<de.chojo.lolorito.value.ComponentPricing>();
            int priced = 0;
            for (var c : components) {
                var m = componentModels.get(c.componentItemId());
                if (m != null && m.sufficient()) priced++;
                pricings.add(new de.chojo.lolorito.value.ComponentPricing(c.componentItemId(), c.avgQty(), m, null));
            }
            var v = ValueEngine.valueDesynth(probeBuy, 1, pricings, prefs).orElse(null);
            if (v == null || v.evPerHour() <= 0) continue;

            // Plain resale of the same source at the same buy price — null
            // when the source has no sufficient home model.
            var sourceModel = sourceModels.get(src.itemId());
            Double resaleEvPerHour = sourceModel == null
                    ? null
                    : ValueEngine.value(sourceModel, probeBuy, 1, prefs)
                            .map(de.chojo.lolorito.value.Valuation::evPerHour)
                            .orElse(null);

            out.add(new Candidate(
                    src.itemId(),
                    itemNameOf(src.itemId(), language),
                    src.desynthClass(),
                    src.desynthLevel(),
                    probeBuy,
                    hqSource,
                    v.expectedNet(),
                    v.evGross(),
                    v.evPerHour(),
                    v.expectedTimeOnShelfHours(),
                    priced / (double) components.size(),
                    resaleEvPerHour));
        }
        out.sort(Comparator.comparingDouble(Candidate::evPerHour).reversed());
        if (out.size() > cap) return out.subList(0, cap);
        return out;
    }

    private static boolean skillGatePasses(DesynthResults.Source src, Map<String, Integer> skillLevels) {
        // No stored desynth skills → no gating. A new user must see the
        // opportunity surface (requirements are shown per row); hiding
        // 95% of the catalog behind an un-filled settings form made the
        // explorer look permanently empty. Mirrors the craft-scan
        // fallback semantics.
        if (skillLevels.isEmpty()) return true;
        if (src.desynthClass() == null || src.desynthLevel() == null) return true;
        Integer callerLevel = skillLevels.get(src.desynthClass());
        return callerLevel != null && callerLevel >= src.desynthLevel();
    }

    /**
     * Empty filter → allow everything. Filter set → only rows whose
     * class is in the set survive; rows with unknown class drop, since
     * the caller is explicitly asking for specific classes.
     */
    private static boolean classFilterPasses(DesynthResults.Source src, Set<String> filter) {
        if (filter.isEmpty()) return true;
        return src.desynthClass() != null && filter.contains(src.desynthClass());
    }

    /**
     * Rows without a known level pass unconditionally — we can't rank
     * an unknown level against the threshold, so we don't hide it.
     */
    private static boolean minLevelPasses(DesynthResults.Source src, int minLevel) {
        if (minLevel <= 0) return true;
        return src.desynthLevel() == null || src.desynthLevel() >= minLevel;
    }

    private String itemNameOf(int itemId, Language language) {
        if (itemNames == null) return String.valueOf(itemId);
        var n = itemNames.fromId(itemId);
        if (n == null) return String.valueOf(itemId);
        String localised = n.get(language);
        if (localised != null && !localised.isBlank()) return localised;
        String english = n.get(Language.ENGLISH);
        return english == null ? String.valueOf(itemId) : english;
    }

    /**
     * One desynth candidate — the top-N list on the /desynth page renders
     * these. {@code pricedComponentFraction} < 1 means some components had
     * no sufficient model and contributed zero — the EV is a lower bound,
     * not a verdict; the UI shows partial pricing instead of hiding it.
     */
    public record Candidate(
            int itemId,
            String itemName,
            String desynthClass,
            Integer desynthLevel,
            /** Cheapest fresh listing within the requested scope (home DC or region). */
            int cheapestBuy,
            /** True when the cheaper source listing is on the HQ board. */
            boolean hqSource,
            double expectedNet,
            double evGross,
            double evPerHour,
            double expectedTimeOnShelfHours,
            double pricedComponentFraction,
            /**
             * EV/hour of just reselling the source at the same buy price,
             * or null when the source has no sufficient home model. Lets
             * the UI show "desynth beats resale by X".
             */
            Double resaleEvPerHour) {}
}
