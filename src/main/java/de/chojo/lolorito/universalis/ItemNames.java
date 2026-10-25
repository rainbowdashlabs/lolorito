/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.universalis;

import de.chojo.universalis.entities.Language;
import de.chojo.universalis.provider.NameSupplier;

/**
 * Display names for item ids. {@link NameSupplier#fromId} never returns null:
 * an unknown id comes back as an empty name, so the name falls back from the
 * requested language to English and finally to the id itself.
 */
public final class ItemNames {

    private ItemNames() {}

    public static String nameOf(NameSupplier names, int itemId, Language language) {
        String name = names == null ? null : names.fromId(itemId).get(language);
        if (isPresent(name)) return name;
        name = names == null ? null : names.fromId(itemId).get(Language.ENGLISH);
        return isPresent(name) ? name : String.valueOf(itemId);
    }

    public static String nameOf(NameSupplier names, int itemId) {
        return nameOf(names, itemId, Language.ENGLISH);
    }

    private static boolean isPresent(String name) {
        return name != null && !name.isBlank();
    }
}
