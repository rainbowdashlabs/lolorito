/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Ported from {@code frontend/scripts/refresh-icons.mjs}. Walks
 * XIVAPI's {@code /sheet/Item} until the cursor stalls, extracting
 * {@code (icon, ilvl, stackSize)} per item id.
 */
public final class IconCatalogBuilder {

    private static final Logger log = getLogger(IconCatalogBuilder.class);

    private static final int PAGE_LIMIT = 500;
    private static final int MAX_PAGES = 200;
    private static final String FIELDS = "Icon,LevelItem,StackSize,Description,ItemUICategory.Name,CanBeHq";
    private static final String SHEET = "Item";

    private final XivapiClient client;
    private final int pageLimit;
    private final int maxPages;

    public IconCatalogBuilder(XivapiClient client) {
        this(client, PAGE_LIMIT, MAX_PAGES);
    }

    IconCatalogBuilder(XivapiClient client, int pageLimit, int maxPages) {
        this.client = client;
        this.pageLimit = pageLimit;
        this.maxPages = maxPages;
    }

    /**
     * Return every item row XIVAPI knows about, keyed by item id. Items
     * without a positive icon id are skipped — those are internal test
     * items with no useful catalog entry. An empty map means the fetch
     * failed or the sheet was empty; callers keep the existing seed.
     */
    public Map<Integer, ItemSheetEntry> build() {
        var out = new LinkedHashMap<Integer, ItemSheetEntry>();
        Integer after = null;
        for (int page = 0; page < maxPages; page++) {
            JsonNode body = client.page(SHEET, FIELDS, pageLimit, after);
            if (body == null) {
                log.warn("IconCatalogBuilder: XIVAPI page {} returned no body; stopping", page);
                break;
            }
            JsonNode rows = body.get("rows");
            if (rows == null || !rows.isArray() || rows.isEmpty()) break;

            int lastId = -1;
            for (JsonNode row : rows) {
                int id = row.path("row_id").asInt(-1);
                if (id > 0) lastId = id;
                JsonNode fields = row.path("fields");
                int icon = fields.path("Icon").path("id").asInt(-1);
                if (id <= 0 || icon <= 0) continue;
                int ilvl = fields.path("LevelItem").path("value").asInt(0);
                int stack = fields.path("StackSize").asInt(1);
                if (stack <= 0) stack = 1;
                String category = fields.path("ItemUICategory")
                        .path("fields")
                        .path("Name")
                        .asString("");
                String description = fields.path("Description").asString("");
                boolean canBeHq = fields.path("CanBeHq").asBoolean(false);
                out.put(id, new ItemSheetEntry(id, icon, ilvl, stack, category, description, canBeHq));
            }
            if (rows.size() < pageLimit || lastId <= 0) break;
            after = lastId;
        }
        return out;
    }
}
