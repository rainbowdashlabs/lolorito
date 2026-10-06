/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Ported from {@code frontend/scripts/refresh-recipes.mjs}. Walks
 * XIVAPI's {@code /sheet/Recipe} and picks out craft class + level +
 * yield + ingredients. The craft class comes from the sheet's
 * {@code CraftType.row_id} (0..7 → the eight DoH classes).
 */
public final class RecipeCatalogBuilder {

    private static final Logger log = getLogger(RecipeCatalogBuilder.class);

    private static final int PAGE_LIMIT = 500;
    private static final int MAX_PAGES = 50;
    private static final String FIELDS = "CraftType.row_id,AmountResult,ItemResult.row_id,"
            + "RecipeLevelTable.ClassJobLevel,AmountIngredient,Ingredient[].row_id";
    private static final String SHEET = "Recipe";

    private static final Map<Integer, String> CRAFT_CLASS = Map.of(
            0, "carpenter",
            1, "blacksmith",
            2, "armorer",
            3, "goldsmith",
            4, "leatherworker",
            5, "weaver",
            6, "alchemist",
            7, "culinarian");

    private final XivapiClient client;
    private final int pageLimit;
    private final int maxPages;

    public RecipeCatalogBuilder(XivapiClient client) {
        this(client, PAGE_LIMIT, MAX_PAGES);
    }

    RecipeCatalogBuilder(XivapiClient client, int pageLimit, int maxPages) {
        this.client = client;
        this.pageLimit = pageLimit;
        this.maxPages = maxPages;
    }

    /**
     * Return every recipe row XIVAPI knows about, sorted by id. An empty
     * list means the fetch failed or every row was malformed — callers
     * keep the existing seed rather than overwriting with nothing.
     */
    public List<RecipeRecord> build() {
        var out = new ArrayList<RecipeRecord>();
        Integer after = null;
        for (int page = 0; page < maxPages; page++) {
            JsonNode body = client.page(SHEET, FIELDS, pageLimit, after);
            if (body == null) {
                log.warn("RecipeCatalogBuilder: XIVAPI page {} returned no body; stopping", page);
                break;
            }
            JsonNode rows = body.get("rows");
            if (rows == null || !rows.isArray() || rows.isEmpty()) break;

            int lastId = -1;
            for (JsonNode row : rows) {
                int id = row.path("row_id").asInt(-1);
                if (id > 0) lastId = id;
                JsonNode fields = row.path("fields");
                int productId = fields.path("ItemResult").path("row_id").asInt(0);
                if (id <= 0 || productId <= 0) continue;

                var ingredients = extractIngredients(fields);
                if (ingredients.isEmpty()) continue;

                int classId = fields.path("CraftType").path("row_id").asInt(-1);
                String craftClass = CRAFT_CLASS.getOrDefault(classId, "unknown");
                int level = fields.path("RecipeLevelTable")
                        .path("fields")
                        .path("ClassJobLevel")
                        .asInt(0);
                int yield = fields.path("AmountResult").asInt(1);
                if (yield <= 0) yield = 1;

                out.add(new RecipeRecord(id, productId, craftClass, level, yield, ingredients));
            }
            if (rows.size() < pageLimit || lastId <= 0) break;
            after = lastId;
        }
        out.sort((a, b) -> Integer.compare(a.id(), b.id()));
        return out;
    }

    /**
     * XIVAPI stores ingredients as parallel arrays {@code Ingredient[]}
     * and {@code AmountIngredient[]}. The old per-index
     * {@code ItemIngredient0..7} fields were removed on the beta schema.
     */
    private static List<RecipeRecord.Ingredient> extractIngredients(JsonNode fields) {
        var out = new ArrayList<RecipeRecord.Ingredient>();
        JsonNode items = fields.path("Ingredient");
        JsonNode amounts = fields.path("AmountIngredient");
        if (!items.isArray()) return out;
        for (int i = 0; i < items.size(); i++) {
            int item = items.get(i).path("row_id").asInt(0);
            int qty = amounts.isArray() && i < amounts.size() ? amounts.get(i).asInt(0) : 0;
            if (item > 0 && qty > 0) out.add(new RecipeRecord.Ingredient(item, qty));
        }
        return out;
    }
}
