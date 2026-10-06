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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSearchServiceNamesTest {

    @Test
    void namesByIdSkipsUnknownIds() {
        var supplier = new StubNameSupplier(Map.of(1, "Alpha", 2, "Bravo"));
        var svc = new ItemSearchService(supplier);
        var out = svc.namesById(List.of(1, 3, 2), Language.ENGLISH);
        assertThat(out)
                .containsExactly(Map.entry(1, "Alpha"), Map.entry(2, "Bravo"))
                .doesNotContainKey(3);
    }

    @Test
    void namesByIdReturnsEmptyForEmptyInput() {
        var svc = new ItemSearchService(new StubNameSupplier(Map.of()));
        assertThat(svc.namesById(List.of(), Language.ENGLISH)).isEmpty();
    }

    /** Minimal in-memory NameSupplier — just enough surface to serve {@link ItemSearchService#namesById}. */
    private static final class StubNameSupplier implements NameSupplier {
        private final Map<Integer, Name> byId;

        StubNameSupplier(Map<Integer, String> byId) {
            var m = new java.util.HashMap<Integer, Name>();
            byId.forEach((k, v) -> m.put(k, new Name(v, v, v, v)));
            this.byId = m;
        }

        @Override
        public Map<Integer, Name> ids() {
            return byId;
        }

        @Override
        public Map<String, Integer> en() {
            return Map.of();
        }

        @Override
        public Map<String, Integer> de() {
            return Map.of();
        }

        @Override
        public Map<String, Integer> fr() {
            return Map.of();
        }

        @Override
        public Map<String, Integer> jp() {
            return Map.of();
        }
    }
}
