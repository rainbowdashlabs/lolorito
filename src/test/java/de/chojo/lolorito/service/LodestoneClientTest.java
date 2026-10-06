/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link LodestoneClient} against synthetic HTML — the fetch
 * layer is stubbed via the package-private {@link
 * LodestoneClient.HttpFetcher} seam so we can assert the parser and the
 * 404 → IOException translation without touching the real Lodestone.
 */
class LodestoneClientTest {

    @Test
    void parsesNameWorldFcAndJobLevels() throws Exception {
        var pages = new HashMap<String, String>();
        pages.put("https://na.finalfantasyxiv.com/lodestone/character/42/", """
                <html><body>
                  <p class="frame__chara__name">Aria Ashcastle</p>
                  <p class="frame__chara__world">Odin [Light]</p>
                  <p class="frame__chara__title">Warrior of Light</p>
                  <div class="js__image_popup"><img src="https://img/portrait.png"/></div>
                  <div class="character__freecompany__name"><a href="#">Golden Chocobo</a></div>
                </body></html>
                """);
        pages.put("https://na.finalfantasyxiv.com/lodestone/character/42/class_job/", """
                <html><body>
                  <div class="character__job__role"><ul class="character__job">
                    <li><div class="character__job__name">Blacksmith</div><div class="character__job__level">78</div></li>
                    <li><div class="character__job__name">Miner</div><div class="character__job__level">80</div></li>
                    <li><div class="character__job__name">Fisher</div><div class="character__job__level">-</div></li>
                  </ul></div>
                </body></html>
                """);
        var client = new LodestoneClient(url -> pages.get(url));

        var profile = client.fetchCharacter(42L);
        assertThat(profile.name()).isEqualTo("Aria Ashcastle");
        assertThat(profile.world()).isEqualTo("Odin");
        assertThat(profile.dataCenter()).isEqualTo("Light");
        assertThat(profile.title()).isEqualTo("Warrior of Light");
        assertThat(profile.freeCompany()).isEqualTo("Golden Chocobo");
        assertThat(profile.portraitUrl()).isEqualTo("https://img/portrait.png");
        assertThat(profile.jobLevels()).containsEntry("Blacksmith", 78).containsEntry("Miner", 80);
        assertThat(profile.jobLevels()).doesNotContainKey("Fisher"); // "-" ignored
        assertThat(profile.maxCrafterLevel()).isEqualTo(78);
        assertThat(profile.maxGathererLevel()).isEqualTo(80);
    }

    @Test
    void nullFetchResultIsTranslatedTo404IoException() {
        var client = new LodestoneClient((LodestoneClient.HttpFetcher) url -> null);
        assertThatThrownBy(() -> client.fetchCharacter(99L)).isInstanceOf(IOException.class);
    }

    @Test
    void oversizedResponseIsTranslatedToIoException() {
        String huge = "a".repeat(3 * 1024 * 1024);
        var client = new LodestoneClient((LodestoneClient.HttpFetcher) url -> huge);
        assertThatThrownBy(() -> client.fetchCharacter(1L)).isInstanceOf(IOException.class);
    }

    @Test
    void missingSelectorsFallBackGracefully() throws Exception {
        Map<String, String> pages = Map.of(
                "https://na.finalfantasyxiv.com/lodestone/character/7/",
                "<html><body></body></html>",
                "https://na.finalfantasyxiv.com/lodestone/character/7/class_job/",
                "<html><body></body></html>");
        var client = new LodestoneClient(url -> pages.get(url));
        var profile = client.fetchCharacter(7L);
        assertThat(profile.name()).isEmpty();
        assertThat(profile.world()).isEmpty();
        assertThat(profile.dataCenter()).isNull();
        assertThat(profile.jobLevels()).isEmpty();
        assertThat(profile.maxCrafterLevel()).isZero();
    }
}
