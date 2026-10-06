/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Read side for {@code lolorito.recipe} + {@code lolorito.recipe_ingredient}.
 * Each accessor issues at most two queries per node — recipe head first, then
 * ingredients — because sadu's row mapper is single-row.
 *
 * <p>Loading is transitive: every {@link Ingredient} carries a nullable
 * {@link Ingredient#recipeToCraftIt} that points at the first recipe whose
 * product is the ingredient itself. That gives the item DAG walkers a
 * ready-to-consume graph — the solver stays one-level for
 * now, but the loader no longer stands in the way of "buy raw → craft
 * intermediate → craft product" chains.
 *
 * <p>The recursion uses a visited-id set to guard against cycles (FFXIV
 * shouldn't have them, but a defensive check is cheap).
 */
@Singleton
public class Recipes {
    @Inject
    public Recipes(DataSource dataSource) {
        // sadu 2 uses the default QueryConfiguration
    }

    public List<Recipe> findByProduct(int productItemId) {
        var heads = query("""
                SELECT id, product_item_id, craft_class, level, yield_qty
                  FROM recipe
                 WHERE product_item_id = :item
                """)
                .single(call().bind("item", productItemId))
                .map(row -> new Head(
                        row.getInt("id"),
                        row.getInt("product_item_id"),
                        row.getString("craft_class"),
                        row.getInt("level"),
                        row.getInt("yield_qty")))
                .all();
        return heads.stream().map(h -> materialiseChain(h, new HashSet<>())).toList();
    }

    public Optional<Recipe> find(int recipeId) {
        return findHead(recipeId).map(h -> materialiseChain(h, new HashSet<>()));
    }

    /**
     * Every recipe head (id + craft_class + level) — cheap enumeration
     * used by the planner's CRAFT-candidate synthesis. Doesn't inflate
     * the ingredient DAG; the caller materialises the recipes it
     * actually wants to price.
     */
    public List<Head> allHeads() {
        return query("""
                SELECT id, product_item_id, craft_class, level, yield_qty
                  FROM recipe
                """)
                .single(call())
                .map(row -> new Head(
                        row.getInt("id"),
                        row.getInt("product_item_id"),
                        row.getString("craft_class"),
                        row.getInt("level"),
                        row.getInt("yield_qty")))
                .all();
    }

    private Optional<Head> findHead(int recipeId) {
        return query("""
                SELECT id, product_item_id, craft_class, level, yield_qty
                  FROM recipe WHERE id = :id
                """)
                .single(call().bind("id", recipeId))
                .map(row -> new Head(
                        row.getInt("id"),
                        row.getInt("product_item_id"),
                        row.getString("craft_class"),
                        row.getInt("level"),
                        row.getInt("yield_qty")))
                .first();
    }

    /**
     * Materialise the ingredient list, recursively resolving each
     * ingredient's own recipe (if any). {@code visited} tracks recipe ids
     * we're currently unrolling — if we re-enter one, we return {@code
     * null} instead of an infinite chain, and the caller records a null
     * {@code recipeToCraftIt} for that ingredient. Top-level entries
     * always call in with an empty {@code visited}, so this method never
     * returns null to public callers.
     */
    private Recipe materialiseChain(Head h, Set<Integer> visited) {
        if (!visited.add(h.id)) return null;
        var flat = loadIngredients(h.id);
        var deep = new ArrayList<Ingredient>(flat.size());
        for (var ing : flat) {
            var subHead = firstRecipeHeadForProduct(ing.itemId());
            var subRecipe = subHead == null ? null : materialiseChain(subHead, visited);
            deep.add(new Ingredient(ing.itemId(), ing.quantity(), subRecipe));
        }
        visited.remove(h.id);
        return new Recipe(h.id, h.productItemId, h.craftClass, h.level, h.yield, deep);
    }

    private List<Ingredient> loadIngredients(int recipeId) {
        return query("""
                SELECT item_id, quantity FROM recipe_ingredient
                 WHERE recipe_id = :id
                """)
                .single(call().bind("id", recipeId))
                .map(row -> new Ingredient(row.getInt("item_id"), row.getInt("quantity"), null))
                .all();
    }

    /**
     * First recipe head whose product matches — arbitrary tie-break, sub-recipe picker for the chain walker.
     */
    private Head firstRecipeHeadForProduct(int itemId) {
        return query("""
                SELECT id, product_item_id, craft_class, level, yield_qty
                  FROM recipe
                 WHERE product_item_id = :item
                 ORDER BY id ASC
                 LIMIT 1
                """)
                .single(call().bind("item", itemId))
                .map(row -> new Head(
                        row.getInt("id"),
                        row.getInt("product_item_id"),
                        row.getString("craft_class"),
                        row.getInt("level"),
                        row.getInt("yield_qty")))
                .first()
                .orElse(null);
    }

    public record Head(int id, int productItemId, String craftClass, int level, int yield) {}

    public record Recipe(
            int id, int productItemId, String craftClass, int level, int yield, List<Ingredient> ingredients) {}

    /**
     * One recipe ingredient. When {@link #recipeToCraftIt} is non-null the
     * ingredient can itself be crafted — walkers use this to expand the
     * DAG one level deeper without another repo round-trip.
     */
    public record Ingredient(int itemId, int quantity, Recipe recipeToCraftIt) {}
}
