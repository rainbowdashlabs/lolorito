/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.universalis.entities.Language;
import de.chojo.universalis.entities.Name;
import de.chojo.universalis.provider.NameSupplier;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Fuzzy name lookup against the {@link NameSupplier}'s in-memory catalog.
 * Powers the item pickers on the SPA — each picker sends the user's
 * partial query and gets id + name (in the caller's locale) back.
 *
 * <p>The match strategy is intentionally simple: substring match (case-
 * insensitive), sorted so exact prefix matches come first, then
 * substring hits by name length ascending. Anything more elaborate can
 * wait for a real search index.
 */
@Singleton
public class ItemSearchService {

    /** Hard cap on the number of hits we return — keeps the JSON small. */
    public static final int MAX_HITS = 25;

    private final NameSupplier nameSupplier;

    @Inject
    public ItemSearchService(NameSupplier nameSupplier) {
        this.nameSupplier = nameSupplier;
    }

    public List<Hit> search(String query, Language language, int limit) {
        if (query == null || query.isBlank()) return List.of();
        String needle = query.trim().toLowerCase();
        // Token-mode match: splitting the query on whitespace and requiring
        // every token to appear as a substring lets "sav mat XI" find
        // "Savage Materia XI". The full phrase is kept around as the
        // primary needle so exact / prefix matches still win the ranking.
        String[] tokens = needle.split("\\s+");
        int cap = Math.clamp(limit, 1, MAX_HITS);
        var map = nameSupplier.languageMap(language);
        var hits = map.entrySet().stream()
                .filter(e -> matches(e.getKey(), needle, tokens))
                .sorted(Comparator.comparing((Map.Entry<String, Integer> e) -> {
                            String name = e.getKey();
                            if (name.equals(needle)) return 0;
                            if (name.startsWith(needle)) return 1;
                            if (name.contains(needle)) return 2;
                            return 3; // matched only via token-mode fallback
                        })
                        .thenComparing(e -> e.getKey().length())
                        .thenComparing(Map.Entry::getKey))
                .limit(cap)
                .map(e -> new Hit(e.getValue(), displayName(e.getValue(), language)))
                .toList();
        return hits;
    }

    /**
     * True when {@code name} either contains the full needle as a single
     * substring or matches every whitespace-separated token
     * individually. Case sensitivity is normalised by the caller.
     */
    private static boolean matches(String name, String needle, String[] tokens) {
        if (name == null) return false;
        if (name.contains(needle)) return true;
        for (String t : tokens) {
            if (t.isEmpty()) continue;
            if (!name.contains(t)) return false;
        }
        return tokens.length > 0;
    }

    private String displayName(int itemId, Language language) {
        Name name = nameSupplier.fromId(itemId);
        if (name == null) return String.valueOf(itemId);
        String localised = name.get(language);
        return localised == null || localised.isBlank() ? name.get(Language.ENGLISH) : localised;
    }

    /**
     * Resolve a batch of item ids to display names in the given locale.
     * Missing ids are omitted. Used by the alert-list view to back-fill
     * item names without hitting the full item detail endpoint.
     */
    public java.util.Map<Integer, String> namesById(java.util.Collection<Integer> ids, Language language) {
        java.util.Map<Integer, String> out = new java.util.LinkedHashMap<>();
        for (int id : ids) {
            Name name = nameSupplier.fromId(id);
            if (name == null) continue;
            String label = name.get(language);
            if (label == null || label.isBlank()) label = name.get(Language.ENGLISH);
            if (label != null && !label.isBlank()) out.put(id, label);
        }
        return out;
    }

    /**
     * One picker suggestion. Icons and item levels are looked up on the
     * SPA side from the bundled {@code icons.json} / xivapi cdn — the
     * backend stays lean.
     */
    public record Hit(int itemId, String name) {}
}
