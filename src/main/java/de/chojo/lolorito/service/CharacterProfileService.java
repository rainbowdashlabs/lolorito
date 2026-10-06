/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lolorito.entity.CharacterProfile;
import de.chojo.lolorito.entity.SkillKind;
import de.chojo.lolorito.repository.CharacterProfiles;
import org.slf4j.Logger;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Read-through + write-through cache in front of {@link LodestoneClient}.
 * Callers ask for a user's profile and get whatever is fresh:
 * <ul>
 *   <li>Cache hit &lt; {@link #REFRESH_TTL} → return the row.</li>
 *   <li>Missing / stale AND {@code forceRefresh} true → fetch, upsert, return.</li>
 *   <li>Missing / stale AND {@code forceRefresh} false → return the stale
 *       row if we have one; empty otherwise. The SPA decides when to
 *       call with {@code forceRefresh = true}.</li>
 * </ul>
 */
@Singleton
public class CharacterProfileService {

    private static final Logger log = getLogger(CharacterProfileService.class);
    /** How stale a row can get before we consider it worth refreshing. */
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final CharacterProfiles repo;
    private final LodestoneClient client;
    private final UserSkillLevelService skills;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Inject
    public CharacterProfileService(CharacterProfiles repo, LodestoneClient client, UserSkillLevelService skills) {
        this.repo = repo;
        this.client = client;
        this.skills = skills;
    }

    public Optional<CharacterProfile> current(long discordUserId) {
        return repo.find(discordUserId);
    }

    public boolean isStale(CharacterProfile profile) {
        return profile == null || profile.fetchedAt().plus(REFRESH_TTL).isBefore(Instant.now());
    }

    /**
     * Refresh a user's character from Lodestone, upserting the result.
     * Throws on network/parse failures — the route translates that to
     * an HTTP status the SPA can render.
     */
    public CharacterProfile refresh(long discordUserId, long lodestoneId) throws Exception {
        var profile = client.fetchCharacter(lodestoneId);
        String json = mapper.writeValueAsString(profile);
        log.info("Refreshed Lodestone profile for user={} lodestoneId={}", discordUserId, lodestoneId);
        var stored = repo.upsert(discordUserId, lodestoneId, json);
        importCraftLevels(discordUserId, profile.jobLevels());
        return stored;
    }

    /**
     * Push Lodestone-reported DoH job levels into the caller's crafting
     * skill map. Iterates the canonical crafter set and matches case-
     * insensitively so "Carpenter" / "carpenter" / "CARPENTER" all land in
     * the same row. Non-crafter entries in the profile are ignored.
     *
     * <p>Only writes crafting levels — desynth skill is not visible on
     * Lodestone, so it stays user-managed. Errors on a single class don't
     * abort the whole import.
     */
    private void importCraftLevels(long discordUserId, Map<String, Integer> jobLevels) {
        if (jobLevels == null || jobLevels.isEmpty()) return;
        int imported = 0;
        for (String canonical : UserSkillLevelService.CANONICAL_CLASSES) {
            Integer level = pickLevel(jobLevels, canonical);
            if (level == null || level <= 0) continue;
            try {
                skills.put(discordUserId, SkillKind.CRAFT, canonical, level);
                imported++;
            } catch (RuntimeException e) {
                log.warn("Skipping crafter {} import for user={}: {}", canonical, discordUserId, e.getMessage());
            }
        }
        if (imported > 0) {
            log.info("Imported {} crafter levels from Lodestone for user={}", imported, discordUserId);
        }
    }

    /** Case-insensitive lookup — Lodestone titles are Title-Case, our canonical form is lowercase. */
    private static Integer pickLevel(Map<String, Integer> jobLevels, String canonical) {
        for (var entry : jobLevels.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.equalsIgnoreCase(canonical)) return entry.getValue();
        }
        return null;
    }

    public boolean unlink(long discordUserId) {
        return repo.delete(discordUserId);
    }
}
