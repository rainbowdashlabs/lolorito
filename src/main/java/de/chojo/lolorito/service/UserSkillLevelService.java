/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.SkillKind;
import de.chojo.lolorito.entity.UserSkillLevel;
import de.chojo.lolorito.repository.UserSkillLevels;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the crafter + desynth skill levels for the current user.
 * Every read produces a fully-normalised map with all eight canonical
 * classes present (zero-filled for anything the user hasn't declared),
 * so the SPA can render a stable form without special-casing gaps.
 *
 * <p>The planner + item detail view consult
 * {@link #allowedCraftClasses(long)} / {@link #allowedDesynthClasses(long)}
 * to filter recipes and desynth results to what the user can actually
 * touch.
 */
@Singleton
public class UserSkillLevelService {

    /** Canonical FFXIV crafter names — locked to the recipes.json craft_class values. */
    public static final List<String> CANONICAL_CLASSES = List.of(
            "carpenter", "blacksmith", "armorer", "goldsmith", "leatherworker", "weaver", "alchemist", "culinarian");

    private final UserSkillLevels repo;
    private final ItemCatalog catalog;

    @Inject
    public UserSkillLevelService(UserSkillLevels repo, ItemCatalog catalog) {
        this.repo = repo;
        this.catalog = catalog;
    }

    /**
     * UI hint for the desynth "Max" button — the highest item level in
     * the catalog, which is what desynthesis skill tracks in-game. A
     * HINT, not a limit: nothing enforces it, so the next expansion's
     * higher ilvls flow through the catalog refresh without a deploy.
     */
    public int desynthLevelHint() {
        int fromCatalog = catalog == null ? 0 : catalog.maxItemLevel();
        return Math.max(100, fromCatalog);
    }

    /** All eight classes for both kinds, defaulting to level=0 when the user hasn't declared them. */
    public Map<SkillKind, Map<String, Integer>> normalized(long discordUserId) {
        var out = new EnumMap<SkillKind, Map<String, Integer>>(SkillKind.class);
        for (SkillKind k : SkillKind.values()) {
            var perClass = new java.util.LinkedHashMap<String, Integer>();
            for (String cls : CANONICAL_CLASSES) perClass.put(cls, 0);
            out.put(k, perClass);
        }
        for (UserSkillLevel row : repo.list(discordUserId)) {
            out.computeIfAbsent(row.kind(), k -> new HashMap<>()).put(row.className(), row.level());
        }
        return out;
    }

    /**
     * Set one skill level. Class name is normalised to lowercase. Levels
     * only floor at 0 — no upper clamp, deliberately: any fixed cap
     * (job level, item level) breaks the moment an expansion raises it.
     */
    public void put(long discordUserId, SkillKind kind, String className, int level) {
        String cls = className == null ? "" : className.trim().toLowerCase();
        if (cls.isEmpty()) throw new IllegalArgumentException("className required");
        if (!CANONICAL_CLASSES.contains(cls)) throw new IllegalArgumentException("unknown class: " + cls);
        repo.put(discordUserId, kind, cls, Math.max(0, level));
    }

    /** Class names the user can craft with at any level > 0. Used by the planner's "allow crafts" gate. */
    public Set<String> allowedCraftClasses(long discordUserId) {
        return classesWithLevel(discordUserId, SkillKind.CRAFT);
    }

    /** Class names the user can desynth with at any level > 0. Used by the desynth explorer. */
    public Set<String> allowedDesynthClasses(long discordUserId) {
        return classesWithLevel(discordUserId, SkillKind.DESYNTH);
    }

    /**
     * Per-class crafting levels for the caller. Only classes with
     * level > 0 are included. The craft scan and the chain planner gate
     * recipes on {@code level ≥ recipe.level} — an empty map means the
     * user hasn't recorded skills, and callers decide the fallback.
     */
    public Map<String, Integer> craftLevels(long discordUserId) {
        var out = new java.util.HashMap<String, Integer>();
        for (var row : repo.list(discordUserId)) {
            if (row.kind() == SkillKind.CRAFT && row.level() > 0) out.put(row.className(), row.level());
        }
        return out;
    }

    /**
     * Per-class desynth levels for the caller. Only classes with
     * level > 0 are included; the desynth explorer uses this map to
     * gate candidates whose required level exceeds the caller's.
     */
    public Map<String, Integer> desynthLevels(long discordUserId) {
        var out = new java.util.LinkedHashMap<String, Integer>();
        for (UserSkillLevel row : repo.list(discordUserId)) {
            if (row.kind() == SkillKind.DESYNTH && row.level() > 0) out.put(row.className(), row.level());
        }
        return out;
    }

    private Set<String> classesWithLevel(long discordUserId, SkillKind kind) {
        var out = new java.util.LinkedHashSet<String>();
        for (UserSkillLevel row : repo.list(discordUserId)) {
            if (row.kind() == kind && row.level() > 0) out.add(row.className());
        }
        return out;
    }
}
