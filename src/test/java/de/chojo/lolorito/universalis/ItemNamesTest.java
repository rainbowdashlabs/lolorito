/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.universalis;

import de.chojo.universalis.entities.Language;
import de.chojo.universalis.entities.Name;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemNamesTest {

    private static final NameSupplier NAMES = new NameSupplier() {
        @Override
        public Map<Integer, Name> ids() {
            return Map.of(5111, new Name("Iron Ore", "Eisenerz", "", ""), 5112, new Name("Gold Ore", "", "", ""));
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

    @Test
    void knownItemsUseTheRequestedLanguage() {
        assertEquals("Eisenerz", ItemNames.nameOf(NAMES, 5111, Language.GERMAN));
    }

    @Test
    void blankTranslationsFallBackToEnglish() {
        assertEquals("Gold Ore", ItemNames.nameOf(NAMES, 5112, Language.GERMAN));
    }

    @Test
    void unknownItemsFallBackToTheId() {
        assertEquals("9999", ItemNames.nameOf(NAMES, 9999, Language.GERMAN));
        assertEquals("9999", ItemNames.nameOf(null, 9999));
    }
}
