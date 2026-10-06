/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.catalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin wrapper around {@code beta.xivapi.com/api/1/sheet/*}. Handles paged
 * fetches ({@code ?limit=&after=}) and single-row lookups; parses responses
 * as a Jackson tree so the builder that owns the sheet can pick the shape
 * it needs without a DTO round-trip.
 *
 * <p>Best-effort: on any HTTP or parse error the caller receives {@code
 * null} rather than an exception — the refresh pipeline always prefers the
 * existing seed over a broken partial refresh.
 */
public final class XivapiClient {

    /** Default endpoint used by every builder unless overridden for tests. */
    public static final String DEFAULT_ENDPOINT = "https://beta.xivapi.com/api/1/sheet";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private final String endpoint;
    private final HttpClient http;
    private final JsonMapper mapper;

    public XivapiClient() {
        this(DEFAULT_ENDPOINT);
    }

    public XivapiClient(String endpoint) {
        this.endpoint = endpoint == null ? DEFAULT_ENDPOINT : endpoint;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.mapper = JsonMapper.builder().build();
    }

    /**
     * Fetch one page of {@code sheet} with the supplied XIVAPI field
     * selector and cursor. Returns the parsed body or {@code null} on
     * any transport / parse failure.
     */
    public JsonNode page(String sheet, String fields, int limit, Integer after) {
        var sb = new StringBuilder(endpoint)
                .append('/')
                .append(sheet)
                .append("?fields=")
                .append(fields);
        sb.append("&limit=").append(limit);
        if (after != null) sb.append("&after=").append(after);
        return fetch(sb.toString());
    }

    /**
     * Fetch a single row from {@code sheet}. Returns the parsed body or
     * {@code null} on failure — the desynth backfill treats {@code null}
     * distinctly from "row present but not desynthable".
     */
    public JsonNode row(String sheet, int rowId, String fields) {
        String url = endpoint + '/' + sheet + '/' + rowId + "?fields=" + fields;
        return fetch(url);
    }

    private JsonNode fetch(String url) {
        try {
            var req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) return null;
            return mapper.readTree(res.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }
}
