/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Static per-item metadata the runtime can't derive from Universalis or
 * the DB — currently the max stack size for each item.
 *
 * <p>Load order at boot:
 * <ol>
 *   <li>Read the bundled {@code /item/stack-sizes.json} written at build
 *       time by {@link de.chojo.lolorito.catalog.CatalogRefreshCli} (the
 *       Gradle {@code refreshCatalog} task).</li>
 *   <li>If that file is missing OR the map is trivially small (a
 *       barely-populated resource shipped from dev), kick off a fetch
 *       against {@code beta.xivapi.com} on a background thread. Slot
 *       accounting keeps using {@link #DEFAULT_STACK_SIZE} until the
 *       fetch lands.</li>
 *   <li>At runtime, {@link de.chojo.lolorito.catalog.CatalogRefreshWorker}
 *       calls {@link #ingest} every {@code catalog.intervalHours} hours
 *       to merge freshly-fetched entries without a restart.</li>
 * </ol>
 *
 * <p>Falls back to {@link #DEFAULT_STACK_SIZE} for anything not in the
 * map. The planner's slot accounting keys off of this — see
 * {@link de.chojo.lolorito.planner.Candidate#slotsConsumed()}.
 */
@Singleton
public class ItemCatalog {
    private static final Logger log = getLogger(ItemCatalog.class);

    /**
     * When the runtime catalog is missing an entry we assume the item is
     * stackable at FFXIV's maximum (999). Non-stackable gear is the
     * minority — over-counting slots for a handful of equipment picks is
     * a smaller mistake than under-counting slots for every stackable
     * material.
     */
    private static final int DEFAULT_STACK_SIZE = 999;

    /** Below this we assume the bundled file was a dev stub and refresh live. */
    private static final int TRUSTED_ENTRY_THRESHOLD = 1_000;

    private static final String XIVAPI_ENDPOINT = "https://beta.xivapi.com/api/1/sheet/Item";
    private static final int XIVAPI_PAGE_SIZE = 500;
    private static final int XIVAPI_MAX_PAGES = 200;

    private final Map<Integer, Integer> stackSizeById;
    /** iconId is what the XIVAPI /asset endpoint uses to key icon PNGs. */
    private final Map<Integer, Integer> iconIdById = new ConcurrentHashMap<>();
    /** Item-level (ilvl). Zero for consumables and materials that don't carry one. */
    private final Map<Integer, Integer> ilvlById = new ConcurrentHashMap<>();
    /** Freeform in-game flavor text. Empty string when not on file. */
    private final Map<Integer, String> descriptionById = new ConcurrentHashMap<>();
    /** UI category name ("Metal", "Reagent", "Culinarian's Primary Tool"…). Empty when unknown. */
    private final Map<Integer, String> categoryById = new ConcurrentHashMap<>();
    /** Ids with an HQ variant in game — absent means NQ-only. */
    private final java.util.Set<Integer> canBeHqIds = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean fetchStarted = new AtomicBoolean();

    public ItemCatalog() {
        var loaded = load();
        this.stackSizeById = new ConcurrentHashMap<>(loaded);
        loadItemsJson();
        log.info(
                "ItemCatalog loaded {} stack-size, {} icon, {} ilvl, {} category, {} description entries",
                stackSizeById.size(),
                iconIdById.size(),
                ilvlById.size(),
                categoryById.size(),
                descriptionById.size());
        if (stackSizeById.size() < TRUSTED_ENTRY_THRESHOLD) {
            triggerLiveRefresh();
        }
    }

    /** Max units that fit in one inventory / retainer slot for {@code itemId}. */
    public int stackSize(int itemId) {
        return stackSizeById.getOrDefault(itemId, DEFAULT_STACK_SIZE);
    }

    /** True when the item exists in an HQ variant in game. */
    public boolean canBeHq(int itemId) {
        return canBeHqIds.contains(itemId);
    }

    /** XIVAPI iconId for the item, or {@code -1} if unknown. */
    public int iconIdFor(int itemId) {
        return iconIdById.getOrDefault(itemId, -1);
    }

    /** Item level (ilvl), or {@code 0} for items that don't carry one. */
    public int ilvlFor(int itemId) {
        return ilvlById.getOrDefault(itemId, 0);
    }

    /**
     * Highest item level in the catalog — the game's desynthesis skill
     * cap tracks this (desynth level can rise to the highest ilvl, not
     * to the job-level cap of 100). Falls back to 999 when the catalog
     * hasn't loaded ilvls, so the clamp never locks users out.
     */
    public int maxItemLevel() {
        return ilvlById.values().stream().mapToInt(Integer::intValue).max().orElse(999);
    }

    /** Freeform in-game description, or {@code ""} when not on file. */
    public String descriptionFor(int itemId) {
        return descriptionById.getOrDefault(itemId, "");
    }

    /** UI category name for the item, or {@code ""} when not on file. */
    public String categoryFor(int itemId) {
        return categoryById.getOrDefault(itemId, "");
    }

    /** Every id → {icon, ilvl, stackSize} entry we know about. Used by the /item-catalog endpoint. */
    public Map<Integer, CatalogEntry> allEntries() {
        var out = new HashMap<Integer, CatalogEntry>(iconIdById.size());
        for (var e : iconIdById.entrySet()) {
            int id = e.getKey();
            out.put(id, new CatalogEntry(e.getValue(), ilvlFor(id), stackSize(id)));
        }
        return out;
    }

    /** Public catalog row — one per known item id. */
    public record CatalogEntry(int icon, int ilvl, int stackSize) {}

    /** Exposed for tests / /admin endpoints. */
    public int size() {
        return stackSizeById.size();
    }

    /**
     * Merge a freshly-built catalog map into the runtime maps. Called
     * by the {@link de.chojo.lolorito.catalog.CatalogRefreshWorker}
     * after each successful refresh so subsequent requests see the new
     * icon / ilvl / stack size values without a restart.
     */
    public int ingest(java.util.Map<Integer, de.chojo.lolorito.catalog.ItemSheetEntry> entries) {
        if (entries == null || entries.isEmpty()) return 0;
        int added = 0;
        for (var e : entries.values()) {
            if (e.id() <= 0) continue;
            if (e.icon() > 0) iconIdById.put(e.id(), e.icon());
            if (e.ilvl() > 0) ilvlById.put(e.id(), e.ilvl());
            if (e.stackSize() > 0) {
                Integer prev = stackSizeById.put(e.id(), e.stackSize());
                if (prev == null) added++;
            }
            if (e.category() != null && !e.category().isEmpty()) categoryById.put(e.id(), e.category());
            if (e.description() != null && !e.description().isEmpty()) descriptionById.put(e.id(), e.description());
            if (e.canBeHq()) canBeHqIds.add(e.id());
        }
        log.info(
                "ItemCatalog ingested {} entries ({} new stackSize); totals now stackSize={} category={} description={}",
                entries.size(),
                added,
                stackSizeById.size(),
                categoryById.size(),
                descriptionById.size());
        return added;
    }

    private void triggerLiveRefresh() {
        if (!fetchStarted.compareAndSet(false, true)) return;
        log.info(
                "ItemCatalog is thin ({} entries) — kicking off a live XIVAPI refresh in the background",
                stackSizeById.size());
        var t = new Thread(this::liveRefresh, "item-catalog-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void liveRefresh() {
        try {
            var http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            var mapper = JsonMapper.builder().build();
            Integer cursor = null;
            int added = 0;
            for (int page = 0; page < XIVAPI_MAX_PAGES; page++) {
                String url = XIVAPI_ENDPOINT + "?fields=StackSize,Icon,LevelItem&limit=" + XIVAPI_PAGE_SIZE
                        + (cursor == null ? "" : "&after=" + cursor);
                var req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                var res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 != 2) {
                    log.warn("XIVAPI refresh returned HTTP {} on page {}; giving up", res.statusCode(), page);
                    return;
                }
                XivPage body = mapper.readValue(res.body(), XivPage.class);
                if (body == null || body.rows == null || body.rows.isEmpty()) break;
                int lastId = -1;
                for (XivRow row : body.rows) {
                    if (row.rowId <= 0) continue;
                    lastId = row.rowId;
                    int stack = row.fields == null ? 0 : row.fields.stackSize;
                    if (stack > 0) {
                        stackSizeById.put(row.rowId, stack);
                        added++;
                    }
                    if (row.fields != null && row.fields.icon != null && row.fields.icon.id > 0) {
                        iconIdById.put(row.rowId, row.fields.icon.id);
                    }
                    if (row.fields != null && row.fields.levelItem != null && row.fields.levelItem.value > 0) {
                        ilvlById.put(row.rowId, row.fields.levelItem.value);
                    }
                }
                if (lastId <= 0 || body.rows.size() < XIVAPI_PAGE_SIZE) break;
                cursor = lastId;
            }
            log.info("ItemCatalog live refresh added {} entries (total now {})", added, stackSizeById.size());
        } catch (Exception e) {
            log.warn("ItemCatalog live refresh failed", e);
        }
    }

    /** XIVAPI response envelope for the {@code /sheet/Item} endpoint we hit. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class XivPage {
        public java.util.List<XivRow> rows;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class XivRow {
        @JsonProperty("row_id")
        public int rowId;

        public XivFields fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class XivFields {
        @JsonProperty("StackSize")
        public int stackSize;

        @JsonProperty("Icon")
        public XivIcon icon;

        @JsonProperty("LevelItem")
        public XivLevelItem levelItem;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class XivIcon {
        public int id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class XivLevelItem {
        public int value;
    }

    /**
     * Load {@code /item/items.json} into {@link #iconIdById} + {@link #ilvlById}.
     * File shape: {@code { "<itemId>": {"icon": 1234, "ilvl": 5, "stackSize": 999} }}.
     * Missing or malformed rows are skipped silently.
     */
    private void loadItemsJson() {
        log.info("Loading item catalog");
        try (InputStream in = ItemCatalog.class.getResourceAsStream("/item/items.json")) {
            if (in == null) return;
            var tree = JsonMapper.builder().build().readTree(in.readAllBytes());
            for (var entry : tree.properties()) {
                try {
                    int id = Integer.parseInt(entry.getKey());
                    var value = entry.getValue();
                    int icon = value.path("icon").asInt(-1);
                    int ilvl = value.path("ilvl").asInt(0);
                    String description = value.path("description").asString("");
                    String category = value.path("category").asString("");
                    if (id > 0 && icon > 0) iconIdById.put(id, icon);
                    if (id > 0 && ilvl > 0) ilvlById.put(id, ilvl);
                    if (id > 0 && !description.isEmpty()) descriptionById.put(id, description);
                    if (id > 0 && !category.isEmpty()) categoryById.put(id, category);
                    if (id > 0 && value.path("canBeHq").asBoolean(false)) canBeHqIds.add(id);
                } catch (NumberFormatException ignored) {
                    // Non-numeric key, skip.
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load /item/items.json", e);
        }
    }

    private static Map<Integer, Integer> load() {
        var out = new HashMap<Integer, Integer>();
        try (InputStream in = ItemCatalog.class.getResourceAsStream("/item/stack-sizes.json")) {
            if (in == null) return out;
            var tree = JsonMapper.builder().build().readTree(in.readAllBytes());
            for (var entry : tree.properties()) {
                try {
                    int id = Integer.parseInt(entry.getKey());
                    int stack = entry.getValue().asInt();
                    if (id > 0 && stack > 0) out.put(id, stack);
                } catch (NumberFormatException ignored) {
                    // Non-numeric key, skip.
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load /item/stack-sizes.json", e);
        }
        return out;
    }
}
