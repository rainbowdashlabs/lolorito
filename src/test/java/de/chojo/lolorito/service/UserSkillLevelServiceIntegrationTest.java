/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.entity.SkillKind;
import de.chojo.lolorito.repository.RepositoryTestBase;
import de.chojo.lolorito.repository.UserSkillLevels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSkillLevelServiceIntegrationTest extends RepositoryTestBase {

    private UserSkillLevelService service;

    @BeforeEach
    void setUp() {
        service = new UserSkillLevelService(new UserSkillLevels(), new ItemCatalog());
        query("DELETE FROM user_skill_level").single(call()).delete();
    }

    @Test
    void normalisedRowsCoverAllCanonicalClassesForBothKinds() {
        var m = service.normalized(1L);
        assertThat(m).containsKeys(SkillKind.CRAFT, SkillKind.DESYNTH);
        assertThat(m.get(SkillKind.CRAFT).keySet())
                .containsExactlyInAnyOrderElementsOf(UserSkillLevelService.CANONICAL_CLASSES);
        assertThat(m.get(SkillKind.CRAFT).values()).allMatch(v -> v == 0);
    }

    @Test
    void putStoresLevelAndAllowedClassesReflectsIt() {
        service.put(1L, SkillKind.CRAFT, "blacksmith", 85);
        service.put(1L, SkillKind.CRAFT, "goldsmith", 60);
        service.put(1L, SkillKind.DESYNTH, "weaver", 42);
        var m = service.normalized(1L);
        assertThat(m.get(SkillKind.CRAFT)).containsEntry("blacksmith", 85).containsEntry("goldsmith", 60);
        assertThat(m.get(SkillKind.DESYNTH)).containsEntry("weaver", 42);
        assertThat(service.allowedCraftClasses(1L)).containsExactlyInAnyOrder("blacksmith", "goldsmith");
        assertThat(service.allowedDesynthClasses(1L)).containsExactly("weaver");
        assertThat(service.craftLevels(1L)).containsEntry("blacksmith", 85).containsEntry("goldsmith", 60);
        assertThat(service.craftLevels(1L)).doesNotContainKey("weaver");
    }

    @Test
    void putWithZeroLevelDeletesTheRow() {
        service.put(1L, SkillKind.CRAFT, "blacksmith", 85);
        service.put(1L, SkillKind.CRAFT, "blacksmith", 0);
        assertThat(service.allowedCraftClasses(1L)).isEmpty();
    }

    @Test
    void putRejectsUnknownClass() {
        assertThatThrownBy(() -> service.put(1L, SkillKind.CRAFT, "gunsmith", 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void levelOnlyFloorsAtZeroNoUpperCap() {
        // No upper clamp anywhere — a fixed cap breaks on the next
        // expansion. 720 desynth and a beyond-current-cap craft level
        // must both survive verbatim; negatives floor to 0.
        service.put(1L, SkillKind.DESYNTH, "blacksmith", 720);
        assertThat(service.normalized(1L).get(SkillKind.DESYNTH)).containsEntry("blacksmith", 720);
        service.put(1L, SkillKind.CRAFT, "carpenter", 120);
        assertThat(service.normalized(1L).get(SkillKind.CRAFT)).containsEntry("carpenter", 120);
        service.put(1L, SkillKind.CRAFT, "weaver", -5);
        assertThat(service.normalized(1L).get(SkillKind.CRAFT)).containsEntry("weaver", 0);
    }

    @Test
    void desynthHintTracksCatalogMaxItemLevel() {
        assertThat(service.desynthLevelHint()).isGreaterThan(100);
    }
}
