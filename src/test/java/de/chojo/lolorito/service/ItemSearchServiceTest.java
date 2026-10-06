/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.universalis.entities.Language;
import de.chojo.universalis.entities.Name;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSearchServiceTest {

    private static NameSupplier supplier() {
        return new NameSupplier() {
            private final Map<Integer, Name> ids = Map.of(
                    100, new Name("Iron Sword", "Eisenschwert", "Épée en fer", "アイアンソード"),
                    101, new Name("Iron Ore", "Eisenerz", "Minerai de fer", "アイアン鉱"),
                    200, new Name("Steel Sword", "Stahlschwert", "Épée en acier", "スチールソード"));

            @Override
            public Map<Integer, Name> ids() {
                return ids;
            }

            @Override
            public Map<String, Integer> en() {
                return build(Language.ENGLISH);
            }

            @Override
            public Map<String, Integer> de() {
                return build(Language.GERMAN);
            }

            @Override
            public Map<String, Integer> fr() {
                return build(Language.FRENCH);
            }

            @Override
            public Map<String, Integer> jp() {
                return build(Language.JAPANESE);
            }

            private Map<String, Integer> build(Language lang) {
                var out = new HashMap<String, Integer>();
                for (var e : ids.entrySet()) {
                    out.put(e.getValue().get(lang).toLowerCase(Locale.ROOT), e.getKey());
                }
                return out;
            }
        };
    }

    @Test
    void substringMatchesInLocale() {
        var svc = new ItemSearchService(supplier());
        var hits = svc.search("iron", Language.ENGLISH, 10);
        assertTrue(hits.stream().anyMatch(h -> h.itemId() == 100));
        assertTrue(hits.stream().anyMatch(h -> h.itemId() == 101));
    }

    @Test
    void resultsRespectLimit() {
        var svc = new ItemSearchService(supplier());
        var hits = svc.search("iron", Language.ENGLISH, 1);
        assertEquals(1, hits.size());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        var svc = new ItemSearchService(supplier());
        assertTrue(svc.search("", Language.ENGLISH, 10).isEmpty());
        assertTrue(svc.search(null, Language.ENGLISH, 10).isEmpty());
        assertTrue(svc.search("   ", Language.ENGLISH, 10).isEmpty());
    }

    @Test
    void germanNamesMatchInGermanLocale() {
        var svc = new ItemSearchService(supplier());
        var hits = svc.search("eisen", Language.GERMAN, 10);
        assertTrue(hits.stream().anyMatch(h -> h.itemId() == 100));
        assertEquals(
                "Eisenschwert",
                hits.stream()
                        .filter(h -> h.itemId() == 100)
                        .findFirst()
                        .orElseThrow()
                        .name());
    }

    @Test
    void japanesePicksJapaneseNames() {
        var svc = new ItemSearchService(supplier());
        var hits = svc.search("アイアン", Language.JAPANESE, 10);
        assertEquals(2, hits.size());
        assertTrue(hits.stream().allMatch(h -> h.name().startsWith("アイアン")));
    }

    @Test
    void exactMatchRanksAheadOfSubstring() {
        var svc = new ItemSearchService(supplier());
        var hits = svc.search("iron sword", Language.ENGLISH, 10);
        assertEquals(100, hits.getFirst().itemId(), "exact-match candidate should be first");
    }

    @Test
    void limitClampedToMaxHits() {
        var svc = new ItemSearchService(supplier());
        // limit > MAX_HITS should be clamped without throwing.
        var hits = svc.search("iron", Language.ENGLISH, 999);
        assertTrue(hits.size() <= ItemSearchService.MAX_HITS);
    }
}
