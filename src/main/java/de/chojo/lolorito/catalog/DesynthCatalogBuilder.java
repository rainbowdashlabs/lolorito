/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Ported from {@code frontend/scripts/refresh-desynth.mjs}. Merges
 * three sources of desynth data, then backfills class/level per row
 * from XIVAPI's Item sheet ({@code ClassJobRepair} + {@code LevelItem}).
 *
 * <ol>
 *   <li>Operator telemetry (not implemented in Java yet; the pipeline is
 *       recipe + Teamcraft + XIVAPI backfill).</li>
 *   <li>Recipe-derived: crystals guaranteed at their recipe qty, non-
 *       crystal materials share one drop per attempt over the first 4,
 *       everything normalised by the recipe yield.</li>
 *   <li>Teamcraft's community desynth map for non-craftable sources.</li>
 * </ol>
 *
 * <p>Rows that XIVAPI confirms as non-desynthable ({@code
 * ClassJobRepair == 0} — crystals, ingredients, treasure maps) get
 * dropped from the seed rather than shipped as always-visible
 * null-class entries.
 */
public final class DesynthCatalogBuilder {

    private static final Logger log = getLogger(DesynthCatalogBuilder.class);

    private static final int CRYSTAL_MIN = 2;
    private static final int CRYSTAL_MAX = 19;
    private static final int MAX_NON_CRYSTAL_MATERIALS = 4;

    private static final String TEAMCRAFT_URL =
            "https://raw.githubusercontent.com/ffxiv-teamcraft/ffxiv-teamcraft/staging/libs/data/src/lib/json/desynth.json";
    private static final int BACKFILL_CONCURRENCY = 8;

    /** ClassJob row_id → canonical crafter name. DoH rows start at 8. */
    private static final Map<Integer, String> CLASS_JOB_TO_CRAFT_CLASS = Map.of(
            8, "carpenter",
            9, "blacksmith",
            10, "armorer",
            11, "goldsmith",
            12, "leatherworker",
            13, "weaver",
            14, "alchemist",
            15, "culinarian");

    private final XivapiClient xivapi;
    private final HttpClient teamcraftHttp;

    public DesynthCatalogBuilder(XivapiClient xivapi) {
        this.xivapi = xivapi;
        this.teamcraftHttp =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * Convenience entry point: build recipe-derived rows, merge with a
     * Teamcraft fetch, then run the XIVAPI backfill pass. Returns the
     * finished seed sorted by source id.
     */
    public List<DesynthSource> build(List<RecipeRecord> recipes) {
        var recipeRows = deriveFromRecipes(recipes);
        var teamcraftRows = fetchTeamcraft();
        var merged = mergeLayers(recipeRows, teamcraftRows);
        return backfillFromXivapi(merged);
    }

    /**
     * Derive one desynth row per recipe product. Crystals are guaranteed
     * drops at {@code recipeQty / yield}; up to four non-crystal
     * materials share a single drop between them, scaled by yield. When
     * multiple recipes share a product the lowest-level recipe wins on
     * (class, level) and the max avg_qty per component survives.
     */
    List<DesynthSource> deriveFromRecipes(List<RecipeRecord> recipes) {
        var byProduct = new LinkedHashMap<Integer, Map<Integer, Double>>();
        var gateByProduct = new HashMap<Integer, Gate>();

        for (var r : recipes) {
            var components = componentsFromRecipe(r);
            if (components.isEmpty()) continue;
            var existing = byProduct.computeIfAbsent(r.productItemId(), k -> new LinkedHashMap<>());
            for (var c : components) {
                existing.merge(c.componentItemId(), c.avgQty(), Math::max);
            }
            Gate prev = gateByProduct.get(r.productItemId());
            if (prev == null || r.level() < prev.level) {
                gateByProduct.put(r.productItemId(), new Gate(r.craftClass(), r.level()));
            }
        }

        var out = new ArrayList<DesynthSource>(byProduct.size());
        for (var e : byProduct.entrySet()) {
            var comps = e.getValue().entrySet().stream()
                    .map(x -> new DesynthSource.Component(x.getKey(), roundQty(x.getValue())))
                    .sorted(Comparator.comparingDouble(DesynthSource.Component::avgQty)
                            .reversed())
                    .toList();
            Gate gate = gateByProduct.getOrDefault(e.getKey(), new Gate(null, 0));
            out.add(new DesynthSource(e.getKey(), gate.className, gate.level > 0 ? gate.level : null, comps));
        }
        out.sort(Comparator.comparingInt(DesynthSource::sourceItemId));
        return out;
    }

    /**
     * Best-effort Teamcraft fetch. Returns an empty list on failure so
     * the pipeline stays green when the CDN is unavailable.
     */
    List<DesynthSource> fetchTeamcraft() {
        try {
            var req = HttpRequest.newBuilder(URI.create(TEAMCRAFT_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            var res = teamcraftHttp.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) {
                log.warn("Teamcraft fetch returned HTTP {}; skipping non-craftable layer", res.statusCode());
                return List.of();
            }
            var tree = JsonMapper.builder().build().readTree(res.body());
            return parseTeamcraft(tree);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Teamcraft fetch failed ({}); non-craftable layer skipped", e.getMessage());
            return List.of();
        }
    }

    static List<DesynthSource> parseTeamcraft(JsonNode tree) {
        var out = new ArrayList<DesynthSource>();
        if (tree == null || !tree.isObject()) return out;
        for (var entry : tree.properties()) {
            int source;
            try {
                source = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (source <= 0) continue;
            JsonNode arr = entry.getValue();
            if (arr == null || !arr.isArray() || arr.isEmpty()) continue;
            var ids = new java.util.LinkedHashSet<Integer>();
            for (var id : arr) ids.add(id.asInt(0));
            ids.removeIf(id -> id == null || id <= 0);
            if (ids.isEmpty()) continue;
            double share = roundQty(1.0 / ids.size());
            var comps = new ArrayList<DesynthSource.Component>(ids.size());
            for (Integer id : ids) comps.add(new DesynthSource.Component(id, share));
            out.add(new DesynthSource(source, null, null, comps));
        }
        return out;
    }

    /**
     * Merge recipe-derived + Teamcraft: recipes win on collisions,
     * Teamcraft fills the gaps.
     */
    static List<DesynthSource> mergeLayers(List<DesynthSource> recipeRows, List<DesynthSource> teamcraftRows) {
        var merged = new LinkedHashMap<Integer, DesynthSource>();
        for (var r : teamcraftRows) merged.put(r.sourceItemId(), r);
        for (var r : recipeRows) merged.put(r.sourceItemId(), r);
        return merged.values().stream()
                .sorted(Comparator.comparingInt(DesynthSource::sourceItemId))
                .toList();
    }

    /**
     * Backfill class + level for rows still lacking either, dropping
     * rows XIVAPI confirms as non-desynthable ({@code
     * ClassJobRepair == 0}). Runs a small worker pool against the
     * single-row Item endpoint.
     */
    List<DesynthSource> backfillFromXivapi(List<DesynthSource> rows) {
        var targets = new ArrayList<DesynthSource>();
        for (var r : rows) {
            if (r.desynthClass() == null || r.desynthLevel() == null) targets.add(r);
        }
        if (targets.isEmpty()) return rows;

        log.info("XIVAPI backfill: probing {} rows for class + level", targets.size());
        var dropIds =
                java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());
        var fillMap = new java.util.concurrent.ConcurrentHashMap<Integer, Gate>();
        var filled = new AtomicInteger();
        var failed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(BACKFILL_CONCURRENCY, r -> {
            var t = new Thread(r, "desynth-backfill");
            t.setDaemon(true);
            return t;
        });
        try {
            var futures = new ArrayList<CompletableFuture<Void>>(targets.size());
            for (var row : targets) {
                futures.add(CompletableFuture.runAsync(
                        () -> {
                            var gate = fetchDesynthGate(row.sourceItemId());
                            if (gate == null) {
                                failed.incrementAndGet();
                            } else if (gate.className == null || gate.level <= 0) {
                                dropIds.add(row.sourceItemId());
                            } else {
                                fillMap.put(row.sourceItemId(), gate);
                                filled.incrementAndGet();
                            }
                        },
                        pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdown();
        }

        log.info(
                "XIVAPI backfill: filled {}, dropped {} (non-desynthable confirmed), failed {} (fetch)",
                filled.get(),
                dropIds.size(),
                failed.get());

        var out = new ArrayList<DesynthSource>(rows.size() - dropIds.size());
        for (var r : rows) {
            if (dropIds.contains(r.sourceItemId())) continue;
            Gate gate = fillMap.get(r.sourceItemId());
            if (gate != null) {
                out.add(new DesynthSource(r.sourceItemId(), gate.className, gate.level, r.components()));
            } else {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * Three outcomes:
     *   {@code new Gate(cls, level)}   — real desynthable row.
     *   {@code new Gate(null, 0)}      — XIVAPI says not desynthable.
     *   {@code null}                   — fetch failed; leave row alone.
     */
    private Gate fetchDesynthGate(int itemId) {
        JsonNode body = xivapi.row("Item", itemId, "ClassJobRepair.row_id,LevelItem.row_id");
        if (body == null) return null;
        int jobId = body.path("fields").path("ClassJobRepair").path("row_id").asInt(0);
        String cls = CLASS_JOB_TO_CRAFT_CLASS.get(jobId);
        if (cls == null) return new Gate(null, 0);
        int level = body.path("fields").path("LevelItem").path("row_id").asInt(0);
        if (level <= 0) return new Gate(null, 0);
        return new Gate(cls, level);
    }

    private static List<DesynthSource.Component> componentsFromRecipe(RecipeRecord recipe) {
        int yieldN = recipe.yield() > 0 ? recipe.yield() : 1;
        var crystals = new ArrayList<DesynthSource.Component>();
        var materials = new ArrayList<RecipeRecord.Ingredient>();
        for (var ing : recipe.ingredients()) {
            if (ing.itemId() <= 0 || ing.quantity() <= 0) continue;
            if (ing.itemId() >= CRYSTAL_MIN && ing.itemId() <= CRYSTAL_MAX) {
                crystals.add(new DesynthSource.Component(ing.itemId(), roundQty((double) ing.quantity() / yieldN)));
            } else if (materials.size() < MAX_NON_CRYSTAL_MATERIALS) {
                materials.add(ing);
            }
        }
        double share = materials.isEmpty() ? 0.0 : 1.0 / materials.size();
        var out = new ArrayList<DesynthSource.Component>(crystals.size() + materials.size());
        out.addAll(crystals);
        for (var ing : materials) {
            out.add(new DesynthSource.Component(ing.itemId(), roundQty(share / yieldN)));
        }
        return out;
    }

    /** Four decimals is enough resolution against gil-priced components without inflating the JSON. */
    private static double roundQty(double n) {
        return Math.round(n * 10_000.0) / 10_000.0;
    }

    /** Internal helper — desynth class + level pair (level=0 sentinels an unknown). */
    private record Gate(String className, int level) {
        Set<String> classAsSet() {
            return className == null ? Set.of() : Set.of(className);
        }
    }

    Set<String> supportedClasses() {
        return new HashSet<>(CLASS_JOB_TO_CRAFT_CLASS.values());
    }
}
