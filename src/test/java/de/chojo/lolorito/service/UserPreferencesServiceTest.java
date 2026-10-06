/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.UserPreferences;
import de.chojo.lolorito.repository.UserPreferencesRepo;
import de.chojo.universalis.entities.Language;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserPreferencesServiceTest {

    private final InMemory repo = new InMemory();
    private final UserPreferencesService svc = new UserPreferencesService(repo);

    @Test
    void unknownLocaleFallsBackToEnglish() {
        var out = svc.setLocale(1L, "klingon");
        assertEquals("en", out.locale());
    }

    @Test
    void supportedLocalesRoundTrip() {
        assertEquals("de", svc.setLocale(1L, "de").locale());
        assertEquals("fr", svc.setLocale(2L, "fr").locale());
        assertEquals("ja", svc.setLocale(3L, "JA").locale());
        assertEquals("en", svc.setLocale(4L, null).locale());
    }

    @Test
    void longerLocaleTagIsTruncated() {
        assertEquals("de", svc.setLocale(1L, "de-DE").locale());
    }

    @Test
    void themeAcceptsValidValuesRejectsRest() {
        assertEquals("dark", svc.setTheme(1L, "dark").theme());
        assertEquals("light", svc.setTheme(1L, "LIGHT").theme());
        assertEquals("auto", svc.setTheme(1L, "auto").theme());
        // Invalid theme value collapses to null so the SPA falls back to its own default.
        assertEquals(null, svc.setTheme(1L, "neon").theme());
        assertEquals(null, svc.setTheme(1L, null).theme());
    }

    @Test
    void currentReturnsDefaultsWhenAbsent() {
        var prefs = svc.current(999L);
        assertEquals("en", prefs.locale());
        assertEquals(null, prefs.theme());
    }

    @Test
    void languageForMapsToUniversalisLanguage() {
        svc.setLocale(1L, "de");
        assertEquals(Language.GERMAN, svc.languageFor(1L));
        svc.setLocale(1L, "fr");
        assertEquals(Language.FRENCH, svc.languageFor(1L));
        svc.setLocale(1L, "ja");
        assertEquals(Language.JAPANESE, svc.languageFor(1L));
        svc.setLocale(1L, "en");
        assertEquals(Language.ENGLISH, svc.languageFor(1L));
        // Unknown user → default English.
        assertEquals(Language.ENGLISH, svc.languageFor(9999L));
    }

    @Test
    void toLanguageStaticHandlesNull() {
        assertEquals(Language.ENGLISH, UserPreferencesService.toLanguage(null));
        assertEquals(Language.GERMAN, UserPreferencesService.toLanguage("DE"));
    }

    private static final class InMemory extends UserPreferencesRepo {
        final Map<Long, UserPreferences> map = new HashMap<>();

        @Override
        public Optional<UserPreferences> find(long discordUserId) {
            return Optional.ofNullable(map.get(discordUserId));
        }

        @Override
        public UserPreferences upsertLocale(long discordUserId, String locale) {
            var existing = map.get(discordUserId);
            var out =
                    new UserPreferences(discordUserId, locale, existing == null ? null : existing.theme(), null, null);
            map.put(discordUserId, out);
            return out;
        }

        @Override
        public UserPreferences upsertTheme(long discordUserId, String theme) {
            var existing = map.get(discordUserId);
            var out =
                    new UserPreferences(discordUserId, existing == null ? "en" : existing.locale(), theme, null, null);
            map.put(discordUserId, out);
            return out;
        }
    }
}
