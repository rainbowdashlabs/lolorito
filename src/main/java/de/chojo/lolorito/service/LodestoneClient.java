/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Singleton;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Scrapes the FFXIV Lodestone. No official JSON, so we hit the character
 * page with a plain {@link HttpClient} and let jsoup pick out the fields
 * we care about via the {@code lodestone-css-selectors} conventions.
 *
 * <p>Two independent safeguards protect the shared IP:
 * <ol>
 *   <li>A hard rate limiter — one request every {@link #MIN_INTERVAL} of
 *       wall clock. Everything queues behind it, we never open two
 *       requests in the same tick.</li>
 *   <li>Callers only invoke us on explicit user action + heavy caching in
 *       {@link CharacterProfileService}. No cron scraping.</li>
 * </ol>
 *
 * <p>Selectors are intentionally private constants — the schema of
 * Lodestone's HTML changes maybe once a year and it's easier to bump
 * these than to load an external JSON.
 */
@Singleton
public class LodestoneClient {

    private static final Logger log = getLogger(LodestoneClient.class);

    private static final String LODESTONE_BASE = "https://na.finalfantasyxiv.com/lodestone/character/";
    /** Minimum wall-clock gap between two Lodestone requests. */
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(3);
    /** Best-effort UA so we don't look like a rude scraper. */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; LoloritoBot/1.0; +https://github.com/RainbowDashLabs/lolorito)";
    /** Hard cap on the page size we're willing to parse. */
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    // ---- Selectors ---------------------------------------------------------
    // Verified against Lodestone HTML as of 2026-01; adjust if the layout
    // shifts. The class_job/ endpoint returns crafter/gatherer levels in
    // ".character__job li" blocks.
    private static final String SEL_NAME = ".frame__chara__name";
    private static final String SEL_WORLD = ".frame__chara__world";
    private static final String SEL_TITLE = ".frame__chara__title";
    private static final String SEL_PORTRAIT = ".js__image_popup img";
    private static final String SEL_ACTIVE_CLASS_ICON = ".character__class_icon img";
    private static final String SEL_ACTIVE_CLASS_LEVEL = ".character__class__data > p:eq(0)";
    private static final String SEL_JOB_LIST_ROW = ".character__job__role .character__job li";
    private static final String SEL_JOB_LIST_NAME = ".character__job__name";
    private static final String SEL_JOB_LIST_LEVEL = ".character__job__level";
    private static final String SEL_FC_NAME = ".character__freecompany__name a";

    private final HttpFetcher fetcher;
    /** Guards {@link #nextAvailable}. */
    private final ReentrantLock rateLock = new ReentrantLock(true);

    private long nextAvailable = System.nanoTime();

    public LodestoneClient() {
        this(defaultFetcher());
    }

    /** Public seam so tests in another package can inject a stub fetcher. */
    public LodestoneClient(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /**
     * Minimal {@code (url) → body} contract — production wires this to
     * a real {@link HttpClient}, tests point it at an in-memory map.
     * Returning null signals a 404; throwing signals any other error.
     */
    @FunctionalInterface
    public interface HttpFetcher {
        String fetch(String url) throws IOException, InterruptedException;
    }

    private static HttpFetcher defaultFetcher() {
        HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        return url -> {
            var req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) return null;
            if (res.statusCode() / 100 != 2) {
                throw new IOException("Lodestone returned HTTP " + res.statusCode() + " for " + url);
            }
            return res.body();
        };
    }

    /**
     * Fetch the given character's profile. Returns the parsed profile
     * as a schema-less {@link Profile} record. Throws on non-2xx or
     * jsoup parse failure — callers translate that to a 502.
     */
    public Profile fetchCharacter(long lodestoneId) throws IOException, InterruptedException {
        Document character = fetch(LODESTONE_BASE + lodestoneId + "/");
        Document jobs = fetch(LODESTONE_BASE + lodestoneId + "/class_job/");
        return parse(lodestoneId, character, jobs);
    }

    private Document fetch(String url) throws IOException, InterruptedException {
        acquire();
        String body = fetcher.fetch(url);
        if (body == null) throw new IOException("Lodestone character not found: " + url);
        if (body.length() > MAX_BODY_BYTES) {
            throw new IOException("Lodestone response was too large for " + url);
        }
        return Jsoup.parse(body, url);
    }

    /** Block until the rate limiter says we're clear to send. */
    private void acquire() {
        rateLock.lock();
        try {
            long now = System.nanoTime();
            long wait = nextAvailable - now;
            if (wait > 0) {
                LockSupport.parkNanos(wait);
            }
            nextAvailable = Math.max(nextAvailable, System.nanoTime()) + MIN_INTERVAL.toNanos();
        } finally {
            rateLock.unlock();
        }
    }

    private static Profile parse(long lodestoneId, Document character, Document jobs) {
        String name = textOr(character.selectFirst(SEL_NAME), "");
        String world = textOr(character.selectFirst(SEL_WORLD), "");
        String title = textOr(character.selectFirst(SEL_TITLE), null);
        String portrait = attrOr(character.selectFirst(SEL_PORTRAIT), "src", null);
        String freeCompany = textOr(character.selectFirst(SEL_FC_NAME), null);
        String activeClassIcon = attrOr(character.selectFirst(SEL_ACTIVE_CLASS_ICON), "src", null);
        String activeClassLevel = textOr(character.selectFirst(SEL_ACTIVE_CLASS_LEVEL), null);

        Map<String, Integer> jobLevels = new LinkedHashMap<>();
        for (Element row : jobs.select(SEL_JOB_LIST_ROW)) {
            String jobName = textOr(row.selectFirst(SEL_JOB_LIST_NAME), "");
            String level = textOr(row.selectFirst(SEL_JOB_LIST_LEVEL), "");
            if (jobName.isEmpty() || level.isEmpty() || "-".equals(level)) continue;
            try {
                jobLevels.put(jobName, Integer.parseInt(level.trim()));
            } catch (NumberFormatException ignored) {
                // Skip rows that aren't a plain number (e.g. "-", empty, "?").
            }
        }

        // Split "World [DC]" apart when present.
        String worldName = world;
        String dcName = null;
        int lb = world.indexOf('[');
        int rb = world.indexOf(']', lb + 1);
        if (lb > 0 && rb > lb) {
            worldName = world.substring(0, lb).trim();
            dcName = world.substring(lb + 1, rb).trim();
        }

        Set<String> crafters = new LinkedHashSet<>();
        Set<String> gatherers = new LinkedHashSet<>();
        classifyDoLDoH(jobLevels, crafters, gatherers);

        int maxCrafterLevel = crafters.stream().mapToInt(jobLevels::get).max().orElse(0);
        int maxGathererLevel = gatherers.stream().mapToInt(jobLevels::get).max().orElse(0);

        return new Profile(
                lodestoneId,
                name,
                worldName,
                dcName,
                title,
                portrait,
                freeCompany,
                activeClassIcon,
                activeClassLevel,
                jobLevels,
                maxCrafterLevel,
                maxGathererLevel);
    }

    private static void classifyDoLDoH(Map<String, Integer> jobs, Set<String> crafters, Set<String> gatherers) {
        for (String name : jobs.keySet()) {
            String lower = name.toLowerCase();
            if (lower.contains("carpenter")
                    || lower.contains("blacksmith")
                    || lower.contains("armorer")
                    || lower.contains("goldsmith")
                    || lower.contains("leatherworker")
                    || lower.contains("weaver")
                    || lower.contains("alchemist")
                    || lower.contains("culinarian")) {
                crafters.add(name);
            } else if (lower.contains("miner") || lower.contains("botanist") || lower.contains("fisher")) {
                gatherers.add(name);
            }
        }
    }

    private static String textOr(Element el, String fallback) {
        return el == null ? fallback : el.text().trim();
    }

    private static String attrOr(Element el, String attr, String fallback) {
        if (el == null) return fallback;
        String value = el.absUrl(attr);
        if (value == null || value.isBlank()) value = el.attr(attr);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Parsed Lodestone profile. The {@link #jobLevels} map is ordered as
     * Lodestone renders it, so DoW/DoM come first, DoH/DoL later — the SPA
     * can just iterate for display.
     */
    public record Profile(
            long lodestoneId,
            String name,
            String world,
            String dataCenter,
            String title,
            String portraitUrl,
            String freeCompany,
            String activeClassIconUrl,
            String activeClassLevel,
            Map<String, Integer> jobLevels,
            int maxCrafterLevel,
            int maxGathererLevel) {}
}
