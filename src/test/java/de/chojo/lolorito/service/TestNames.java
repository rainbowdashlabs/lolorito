/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.universalis.entities.Name;
import de.chojo.universalis.provider.NameSupplier;

import java.util.Map;

/** Fixed item-name lookup for service tests; only id → name resolution is supported. */
final class TestNames {

    private TestNames() {}

    static NameSupplier of(Map<Integer, Name> names) {
        return new NameSupplier() {
            @Override
            public Map<Integer, Name> ids() {
                return names;
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
        };
    }

    static Name english(String english, String german) {
        return new Name(english, german, "", "");
    }
}
