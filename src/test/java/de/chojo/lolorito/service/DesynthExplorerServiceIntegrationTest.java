/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import de.chojo.lolorito.config.file.File;
import de.chojo.lolorito.entity.SkillKind;
import de.chojo.lolorito.repository.DesynthResults;
import de.chojo.lolorito.repository.ItemDetail;
import de.chojo.lolorito.repository.RepositoryTestBase;
import de.chojo.lolorito.repository.UserSkillLevels;
import de.chojo.universalis.provider.NameSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.assertj.core.api.Assertions.assertThat;

class DesynthExplorerServiceIntegrationTest extends RepositoryTestBase {

    private static final long CALLER = 42L;

    private DesynthExplorerService service;
    private UserSkillLevelService skills;

    @BeforeEach
    void setUp() {
        var config = new File();
        skills = new UserSkillLevelService(new UserSkillLevels(), new ItemCatalog());
        service = new DesynthExplorerService(
                config,
                new DesynthResults(dataSource),
                new ItemDetail(),
                new de.chojo.lolorito.repository.MarketModels(dataSource),
                NameSupplier.EMPTY,
                skills,
                new UserPreferencesService(new de.chojo.lolorito.repository.UserPreferencesRepo()));
        query("DELETE FROM desynth_result").single(call()).delete();
        query("DELETE FROM listings").single(call()).delete();
        query("DELETE FROM market_model").single(call()).delete();
        query("DELETE FROM user_skill_level").single(call()).delete();
        insertWorld(66, "Odin", 7, "Light", "Europe");
    }

    @Test
    void unknownWorldReturnsEmpty() {
        assertThat(service.topCandidates(999_999, 10)).isEmpty();
    }

    @Test
    void sourceWithNoListingsIsSkipped() {
        seedDesynth(1001, 5001, 0.5, null, null);
        assertThat(service.topCandidates(66, 10)).isEmpty();
    }

    @Test
    void positiveEvSourceShowsUp() {
        seedDesynth(1001, 5001, 1.0, null, null);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);
        var out = service.topCandidates(66, 10);
        assertThat(out).isNotEmpty();
        assertThat(out.get(0).itemId()).isEqualTo(1001);
        assertThat(out.get(0).cheapestBuy()).isEqualTo(100);
    }

    @Test
    void classGateHidesSourceWhenCallerLevelBelowRequired() {
        seedDesynth(1001, 5001, 1.0, "carpenter", 60);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);
        skills.put(CALLER, SkillKind.DESYNTH, "carpenter", 40);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of(), 0);
        assertThat(out).isEmpty();
    }

    @Test
    void classGateShowsSourceWhenCallerAtOrAboveRequired() {
        seedDesynth(1001, 5001, 1.0, "carpenter", 40);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);
        skills.put(CALLER, SkillKind.DESYNTH, "carpenter", 40);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of(), 0);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).desynthClass()).isEqualTo("carpenter");
        assertThat(out.get(0).desynthLevel()).isEqualTo(40);
    }

    @Test
    void noStoredSkillsShowsClassTaggedSourcesUngated() {
        // Fallback behaviour: a user who never filled the skills form
        // must still see the opportunity surface — requirements are shown
        // per row, not used to blank the page.
        seedDesynth(1001, 5001, 1.0, "carpenter", 60);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of(), 0);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).desynthClass()).isEqualTo("carpenter");
    }

    @Test
    void nullGateAlwaysPassesEvenWithoutSkills() {
        // Teamcraft-fallback rows come with no class/level; explorer must
        // not hide what we can't verify, even for a user with zero skills.
        seedDesynth(1001, 5001, 1.0, null, null);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of(), 0);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).desynthClass()).isNull();
    }

    @Test
    void classFilterKeepsOnlyMatchingClasses() {
        seedDesynth(1001, 5001, 1.0, "carpenter", 20);
        seedDesynth(1002, 5002, 1.0, "armorer", 20);
        seedListing(66, 1001, 100);
        seedListing(66, 1002, 100);
        seedModel(66, 5001, 300, 300, true);
        seedModel(66, 5002, 300, 300, true);
        skills.put(CALLER, SkillKind.DESYNTH, "carpenter", 60);
        skills.put(CALLER, SkillKind.DESYNTH, "armorer", 60);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of("armorer"), 0);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).itemId()).isEqualTo(1002);
    }

    @Test
    void classFilterAcceptsUppercaseAliases() {
        seedDesynth(1001, 5001, 1.0, "carpenter", 20);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);
        skills.put(CALLER, SkillKind.DESYNTH, "carpenter", 60);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of("CARPENTER"), 0);
        assertThat(out).hasSize(1);
    }

    @Test
    void classFilterDropsNullClassRows() {
        // Teamcraft-fallback (null class) passes the qualifier gate but
        // must NOT survive an explicit class filter — the caller asked
        // for specific classes, so unknowns don't count.
        seedDesynth(1001, 5001, 1.0, null, null);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of("carpenter"), 0);
        assertThat(out).isEmpty();
    }

    @Test
    void minLevelHidesRowsBelowThreshold() {
        seedDesynth(1001, 5001, 1.0, "carpenter", 20);
        seedDesynth(1002, 5002, 1.0, "carpenter", 60);
        seedListing(66, 1001, 100);
        seedListing(66, 1002, 100);
        seedModel(66, 5001, 300, 300, true);
        seedModel(66, 5002, 300, 300, true);
        skills.put(CALLER, SkillKind.DESYNTH, "carpenter", 80);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of(), 40);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).itemId()).isEqualTo(1002);
    }

    @Test
    void minLevelKeepsUnknownLevelRows() {
        // Rows without a known level (Teamcraft) can't be compared to
        // the threshold; hiding them would surprise the user.
        seedDesynth(1001, 5001, 1.0, null, null);
        seedListing(66, 1001, 100);
        seedModel(66, 5001, 300, 300, true);

        var out = service.topCandidatesFor(
                66, 10, de.chojo.universalis.entities.Language.ENGLISH, CALLER, java.util.Set.of(), 50);
        assertThat(out).hasSize(1);
    }

    @Test
    void probePrefersCheaperHqBoard() {
        seedDesynth(1001, 5001, 1.0, null, null);
        seedListing(66, 1001, 100);
        seedListing(66, 1001, 60, true);
        seedModel(66, 5001, 300, 300, true);
        var out = service.topCandidates(66, 10);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).cheapestBuy()).isEqualTo(60);
        assertThat(out.get(0).hqSource()).isTrue();
    }

    private static void seedDesynth(int source, int component, double avgQty, String cls, Integer level) {
        query("""
                INSERT INTO desynth_result
                    (source_item_id, component_item_id, avg_qty, desynth_class, desynth_level)
                VALUES (:s, :c, :q, :cls, :lvl)
                """)
                .single(call().bind("s", source)
                        .bind("c", component)
                        .bind("q", avgQty)
                        .bind("cls", cls)
                        .bind("lvl", level))
                .insert();
    }

    private static void seedListing(int world, int item, int price) {
        seedListing(world, item, price, false);
    }

    private static void seedListing(int world, int item, int price, boolean hq) {
        query("""
                INSERT INTO listings(world, item, hq, review_time, unit_price, quantity, total)
                VALUES (:w, :i, :hq, now(), :p, 1, :p)
                """)
                .single(call().bind("w", world).bind("i", item).bind("hq", hq).bind("p", price))
                .insert();
    }

    private static void seedModel(int world, int item, int expected, int median, boolean sufficient) {
        // price_mu = ln(expected) is the mean of the log-normal fit.
        double mu = Math.log(expected);
        query("""
                INSERT INTO market_model
                    (world_id, item_id, hq, price_mu, price_sigma,
                     lambda_p95, lambda_p100, lambda_p105, sample_count, sufficient, fitted_at)
                VALUES (:w, :i, false, :mu, 0.2, 1.0, 0.5, 0.2, 50, :s, now())
                """)
                .single(call().bind("w", world).bind("i", item).bind("mu", mu).bind("s", sufficient))
                .insert();
    }
}
