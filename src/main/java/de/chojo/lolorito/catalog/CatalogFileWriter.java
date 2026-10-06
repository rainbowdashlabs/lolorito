/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Writes the five catalog JSON files the build seeds against:
 * <ul>
 *   <li>{@code item/icons.json}       — {@code {"id": iconId}}</li>
 *   <li>{@code item/items.json}       — {@code {"id": {icon, ilvl, stackSize}}}</li>
 *   <li>{@code item/stack-sizes.json} — {@code {"id": stackSize}}</li>
 *   <li>{@code recipe/recipes.json}   — recipe records array</li>
 *   <li>{@code desynth/desynth_results.json} — desynth source records array</li>
 * </ul>
 * File shapes match what the Node scripts used to produce so the
 * {@link de.chojo.lolorito.service.CraftDesynthLoader} classpath reader
 * still parses them 1:1.
 */
public final class CatalogFileWriter {

    private static final Logger log = getLogger(CatalogFileWriter.class);

    private final Path outputRoot;
    private final JsonMapper mapper;

    public CatalogFileWriter(Path outputRoot) {
        this.outputRoot = outputRoot;
        // INDENT_OUTPUT gives Jackson's default pretty print (2-space
        // indent + newline per array element) — matches what the Node
        // JSON.stringify(body, null, 2) calls used to emit.
        this.mapper =
                JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    }

    public void writeIcons(Map<Integer, ItemSheetEntry> entries) throws IOException {
        var iconTree = mapper.createObjectNode();
        var itemTree = mapper.createObjectNode();
        var stackTree = mapper.createObjectNode();
        var sorted = new TreeMap<>(entries);
        for (var e : sorted.values()) {
            String key = Integer.toString(e.id());
            iconTree.put(key, e.icon());
            stackTree.put(key, e.stackSize());
            var row = mapper.createObjectNode();
            row.put("icon", e.icon());
            row.put("ilvl", e.ilvl());
            row.put("stackSize", e.stackSize());
            if (e.category() != null && !e.category().isEmpty()) row.put("category", e.category());
            if (e.description() != null && !e.description().isEmpty()) row.put("description", e.description());
            // Omitted when false — absence means "no HQ variant".
            if (e.canBeHq()) row.put("canBeHq", true);
            itemTree.set(key, row);
        }
        writeJson(outputRoot.resolve("item/icons.json"), iconTree);
        writeJson(outputRoot.resolve("item/items.json"), itemTree);
        writeJson(outputRoot.resolve("item/stack-sizes.json"), stackTree);
        log.info("Wrote {} icon + item + stack-size mappings", sorted.size());
    }

    public void writeRecipes(List<RecipeRecord> recipes) throws IOException {
        var body = mapper.createObjectNode();
        var comment = body.putArray("$comment");
        comment.add("Auto-refreshed by de.chojo.lolorito.catalog.CatalogRefreshCli from beta.xivapi.com.");

        ArrayNode arr = body.putArray("recipes");
        for (var r : recipes) {
            ObjectNode row = arr.addObject();
            row.put("id", r.id());
            row.put("product_item_id", r.productItemId());
            row.put("craft_class", r.craftClass());
            row.put("level", r.level());
            row.put("yield", r.yield());
            var ings = row.putArray("ingredients");
            for (var i : r.ingredients()) {
                ObjectNode ingNode = ings.addObject();
                ingNode.put("item_id", i.itemId());
                ingNode.put("quantity", i.quantity());
            }
        }
        writeJson(outputRoot.resolve("recipe/recipes.json"), body);
        log.info("Wrote {} recipes", recipes.size());
    }

    public void writeDesynth(List<DesynthSource> sources, List<String> sourceCredits) throws IOException {
        var body = mapper.createObjectNode();
        var comment = body.putArray("$comment");
        comment.add("Auto-refreshed by de.chojo.lolorito.catalog.CatalogRefreshCli.");
        if (sourceCredits != null) for (var s : sourceCredits) comment.add("Source: " + s);
        comment.add("avg_qty priors: recipe-derived (crystals guaranteed at recipe qty; non-crystal "
                + "materials share a single per-attempt drop between the first 4). Teamcraft "
                + "fallback uses 1 / componentCount. class/level via XIVAPI Item.ClassJobRepair + "
                + "LevelItem. Real telemetry refines both via the fitter's Bayesian shrinkage.");

        ArrayNode arr = body.putArray("results");
        for (var r : sources) {
            ObjectNode row = arr.addObject();
            row.put("source_item_id", r.sourceItemId());
            if (r.desynthClass() == null) row.putNull("desynth_class");
            else row.put("desynth_class", r.desynthClass());
            if (r.desynthLevel() == null) row.putNull("desynth_level");
            else row.put("desynth_level", r.desynthLevel());
            var comps = row.putArray("components");
            for (var c : r.components()) {
                ObjectNode cNode = comps.addObject();
                cNode.put("component_item_id", c.componentItemId());
                cNode.put("avg_qty", c.avgQty());
            }
        }
        writeJson(outputRoot.resolve("desynth/desynth_results.json"), body);
        log.info("Wrote {} desynth rows", sources.size());
    }

    private void writeJson(Path target, JsonNode tree) throws IOException {
        Files.createDirectories(target.getParent());
        String body = mapper.writeValueAsString(tree) + "\n";
        Files.writeString(target, body, StandardCharsets.UTF_8);
    }
}
