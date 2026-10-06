/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.catalog.CatalogRefreshWorker;
import de.chojo.lolorito.catalog.DesynthSource;
import de.chojo.lolorito.catalog.RecipeRecord;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.queries.configuration.ConnectedQueryConfigurationImpl;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Bulk-load curated recipe and desynth data. Two entry paths:
 * <ul>
 *   <li>{@link #loadAll()} — reads the classpath seed ({@code
 *       resource/recipe/recipes.json} + {@code resource/desynth/
 *       desynth_results.json}), which the Gradle {@code refreshCatalog}
 *       task or the runtime {@link
 *       CatalogRefreshWorker} keep fresh.</li>
 *   <li>{@link #load(List, List)} — reload from records the worker
 *       just built in memory, no JSON round-trip.</li>
 * </ul>
 * Both paths use {@code ON CONFLICT DO UPDATE} so a reload keeps the
 * DB in lock-step with whichever source last wrote.
 */
public final class CraftDesynthLoader {
    private static final Logger log = getLogger(CraftDesynthLoader.class);
    private static final String RECIPE_RESOURCE = "recipe/recipes.json";
    private static final String DESYNTH_RESOURCE = "desynth/desynth_results.json";

    private CraftDesynthLoader() {}

    /**
     * Boot-time cold load from the bundled JSON seed. Silent no-op when either file is absent.
     */
    public static void loadAll() {
        log.info("Loading recipes");
        var recipes = readRecipes();
        if (recipes != null) loadRecipes(recipes);
        log.info("Loaded recipes");
        log.info("Loading desynth results");
        var desynth = readDesynth();
        if (desynth != null) loadDesynth(desynth);
        log.info("Loaded desynth results");
    }

    /**
     * Reload from freshly-built records — used by the async refresh
     * worker so a live pull doesn't have to bounce through a temp file.
     */
    public static void load(List<RecipeRecord> recipes, List<DesynthSource> desynth) {
        if (recipes != null) loadRecipes(recipes);
        if (desynth != null) loadDesynth(desynth);
    }

    private static void loadRecipes(List<RecipeRecord> recipes) {
        int loaded = 0, ingredients = 0;
        try (ConnectedQueryConfigurationImpl query =
                QueryConfiguration.getDefault().withSingleTransaction()) {
            for (var r : recipes) {
                query.query("""
                             INSERT INTO recipe (id, product_item_id, craft_class, level, yield_qty)
                             VALUES (:id, :product, :cls, :lvl, :yield)
                             ON CONFLICT (id) DO UPDATE SET
                                 product_item_id = excluded.product_item_id,
                                 craft_class     = excluded.craft_class,
                                 level           = excluded.level,
                                 yield_qty       = excluded.yield_qty
                             """)
                        .single(call().bind("id", r.id())
                                .bind("product", r.productItemId())
                                .bind("cls", r.craftClass())
                                .bind("lvl", r.level())
                                .bind("yield", r.yield() > 0 ? r.yield() : 1))
                        .insert();
                loaded++;

                query.query("DELETE FROM recipe_ingredient WHERE recipe_id = :id")
                        .single(call().bind("id", r.id()))
                        .delete();
                for (var i : r.ingredients()) {
                    query.query("""
                                 INSERT INTO recipe_ingredient (recipe_id, item_id, quantity)
                                 VALUES (:rid, :item, :qty)
                                 """)
                            .single(call().bind("rid", r.id())
                                    .bind("item", i.itemId())
                                    .bind("qty", i.quantity()))
                            .insert();
                    ingredients++;
                }
            }
        }
        log.info("Loaded {} recipes ({} ingredient rows)", loaded, ingredients);
    }

    private static void loadDesynth(List<DesynthSource> desynth) {
        int loaded = 0;
        for (var r : desynth) {
            // Wipe-then-load so removed components disappear on reload.
            query("DELETE FROM desynth_result WHERE source_item_id = :id")
                    .single(call().bind("id", r.sourceItemId()))
                    .delete();
            if (r.components() == null) continue;
            query("""
                    INSERT INTO desynth_result (source_item_id, component_item_id, avg_qty,
                                                desynth_class, desynth_level)
                    VALUES (:src, :comp, :qty, :cls, :lvl)
                    """)
                    .batch(r.components().stream().map(c -> call().bind("src", r.sourceItemId())
                            .bind("comp", c.componentItemId())
                            .bind("qty", c.avgQty())
                            .bind("cls", r.desynthClass())
                            .bind("lvl", r.desynthLevel())))
                    .insert();
            loaded += r.components().size();
        }
        log.info("Loaded {} desynth component rows", loaded);
    }

    // -- JSON parsing (classpath / cold seed) ------------------------------

    private static List<RecipeRecord> readRecipes() {
        JsonNode root = readTree(RECIPE_RESOURCE);
        if (root == null) return null;
        JsonNode arr = root.get("recipes");
        if (arr == null || !arr.isArray()) return List.of();
        var out = new ArrayList<RecipeRecord>(arr.size());
        for (JsonNode r : arr) {
            int id = r.path("id").asInt(-1);
            int productId = r.path("product_item_id").asInt(-1);
            if (id <= 0 || productId <= 0) continue;
            String cls = r.path("craft_class").asString();
            int level = r.path("level").asInt(0);
            int yield = r.path("yield").asInt(1);
            if (yield <= 0) yield = 1;
            var ings = new ArrayList<RecipeRecord.Ingredient>();
            JsonNode ing = r.path("ingredients");
            if (ing.isArray()) {
                for (JsonNode i : ing) {
                    int itemId = i.path("item_id").asInt(0);
                    int qty = i.path("quantity").asInt(0);
                    if (itemId > 0 && qty > 0) ings.add(new RecipeRecord.Ingredient(itemId, qty));
                }
            }
            out.add(new RecipeRecord(id, productId, cls, level, yield, ings));
        }
        return out;
    }

    private static List<DesynthSource> readDesynth() {
        JsonNode root = readTree(DESYNTH_RESOURCE);
        if (root == null) return null;
        JsonNode arr = root.get("results");
        if (arr == null || !arr.isArray()) return List.of();
        var out = new ArrayList<DesynthSource>(arr.size());
        for (JsonNode r : arr) {
            int source = r.path("source_item_id").asInt(-1);
            if (source <= 0) continue;
            String cls = r.hasNonNull("desynth_class") ? r.get("desynth_class").asString() : null;
            Integer level =
                    r.hasNonNull("desynth_level") ? r.get("desynth_level").asInt() : null;
            var comps = new ArrayList<DesynthSource.Component>();
            JsonNode c = r.path("components");
            if (c.isArray()) {
                for (JsonNode e : c) {
                    int cid = e.path("component_item_id").asInt(0);
                    double qty = e.path("avg_qty").asDouble(0.0);
                    if (cid > 0 && qty > 0) comps.add(new DesynthSource.Component(cid, qty));
                }
            }
            out.add(new DesynthSource(source, cls, level, comps));
        }
        return out;
    }

    private static JsonNode readTree(String resource) {
        try (InputStream in = CraftDesynthLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("Craft/desynth seed missing on classpath: {}", resource);
                return null;
            }
            return JsonMapper.builder().build().readTree(in.readAllBytes());
        } catch (IOException e) {
            log.warn("Failed to load craft/desynth seed {}", resource, e);
            return null;
        }
    }
}
