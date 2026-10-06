/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.UserPreferences;
import de.chojo.lolorito.repository.UserPreferencesRepo;
import de.chojo.universalis.entities.Language;

import java.util.Set;

/**
 * Reads and validates {@link UserPreferences}. The set of supported
 * locales matches the SPA (en, de, fr, ja) and the Universalis
 * {@link Language} enum so item name lookups can pass the caller's
 * locale straight through.
 */
@Singleton
public class UserPreferencesService {

    private static final Set<String> LOCALES = Set.of("en", "de", "fr", "ja");
    private static final Set<String> THEMES = Set.of("dark", "light", "auto");

    private final UserPreferencesRepo repo;

    @Inject
    public UserPreferencesService(UserPreferencesRepo repo) {
        this.repo = repo;
    }

    public UserPreferences current(long userId) {
        return repo.find(userId).orElseGet(() -> UserPreferences.defaultFor(userId));
    }

    public UserPreferences setLocale(long userId, String locale) {
        String normal = normaliseLocale(locale);
        return repo.upsertLocale(userId, normal);
    }

    public UserPreferences setTheme(long userId, String theme) {
        String normal = normaliseTheme(theme);
        return repo.upsertTheme(userId, normal);
    }

    /** Returns the stored planner-params blob (raw JSON), or {@code null} if the user has none saved yet. */
    public String plannerParams(long userId) {
        return current(userId).plannerParamsJson();
    }

    public UserPreferences setPlannerParams(long userId, String plannerParamsJson) {
        return repo.upsertPlannerParams(userId, plannerParamsJson);
    }

    /**
     * Set (or clear) the caller's per-user alert webhook URL.
     * Passing null/blank clears the stored URL — the webhook dispatcher
     * then treats it as "no webhook configured" and falls back to the
     * instance-wide URL (if any).
     */
    public UserPreferences setAlertWebhookUrl(long userId, String url) {
        return repo.upsertAlertWebhookUrl(userId, url);
    }

    /** Convenience accessor for {@link WebhookAlertDispatcher}. */
    public String alertWebhookUrlFor(long userId) {
        return current(userId).alertWebhookUrl();
    }

    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

    /**
     * The attention fraction the user saved with their planner defaults,
     * or {@code fallback} when none is stored / the blob doesn't parse.
     * Lets the offers explorer, item detail, and desynth explorer rank
     * with the same attention model the planner uses instead of a
     * hardcoded constant.
     */
    public double attentionFractionFor(long userId, double fallback) {
        String json = current(userId).plannerParamsJson();
        if (json == null || json.isBlank()) return fallback;
        try {
            var node = JSON.readTree(json);
            var attention = node.get("attentionFraction");
            if (attention == null || !attention.isNumber()) return fallback;
            double v = attention.asDouble();
            return v >= 0.0 && v <= 1.0 ? v : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Map the stored locale to the Universalis {@link Language} used by name lookups. */
    public Language languageFor(long userId) {
        return toLanguage(current(userId).locale());
    }

    public static Language toLanguage(String locale) {
        if (locale == null) return Language.ENGLISH;
        return switch (locale.toLowerCase()) {
            case "de" -> Language.GERMAN;
            case "fr" -> Language.FRENCH;
            case "ja" -> Language.JAPANESE;
            default -> Language.ENGLISH;
        };
    }

    private static String normaliseLocale(String locale) {
        if (locale == null) return "en";
        String lower = locale.toLowerCase();
        if (lower.length() > 2) lower = lower.substring(0, 2);
        return LOCALES.contains(lower) ? lower : "en";
    }

    private static String normaliseTheme(String theme) {
        if (theme == null) return null;
        String lower = theme.toLowerCase();
        return THEMES.contains(lower) ? lower : null;
    }
}
