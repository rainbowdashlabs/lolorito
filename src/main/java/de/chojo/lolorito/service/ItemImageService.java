/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Backend cache of item icon PNGs. The frontend used to embed
 * xivapi.com URLs directly, which meant every icon render depended on
 * a third-party service and leaked our user's traffic. This service
 * fetches the PNG once, writes it to a disk cache under the configured
 * data directory, and serves subsequent requests directly.
 *
 * <p>Layout: {@code {dataDir}/icon-cache/{high}/{icon}.png} where
 * {@code high} is {@code (iconId / 1000) * 1000} zero-padded to six
 * digits — matches XIVAPI's own convention so the cache is easy to
 * inspect and can be prewarmed from a game-client dump if we ever
 * need to run offline.
 */
@Singleton
public class ItemImageService {
    private static final Logger log = getLogger(ItemImageService.class);
    private static final String SOURCE_BASE = "https://beta.xivapi.com/api/1/asset";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final Path cacheRoot;
    private final ItemCatalog catalog;
    private final HttpClient http;
    private final ReentrantLock fetchLock = new ReentrantLock();

    @Inject
    public ItemImageService(ItemCatalog catalog) {
        this(catalog, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    /** Test seam — inject a stub HttpClient to bypass network in coverage tests. */
    ItemImageService(ItemCatalog catalog, HttpClient http) {
        this.catalog = catalog;
        this.http = http;
        this.cacheRoot = Path.of(System.getProperty("lolorito.dataDir", "data"), "icon-cache");
        try {
            Files.createDirectories(cacheRoot);
        } catch (IOException e) {
            log.warn("Failed to create icon cache dir {}", cacheRoot, e);
        }
    }

    /** Icon bytes for {@code itemId}, or empty if the item has no known icon and XIVAPI can't be reached. */
    public Optional<byte[]> iconBytes(int itemId) {
        int iconId = catalog.iconIdFor(itemId);
        if (iconId <= 0) return Optional.empty();
        Path onDisk = pathFor(iconId);
        fetchLock.lock();
        try {
            if (Files.exists(onDisk)) return Optional.of(Files.readAllBytes(onDisk));
            byte[] bytes = fetchFromXivapi(iconId);
            if (bytes == null) return Optional.empty();
            Files.createDirectories(onDisk.getParent());
            Files.write(onDisk, bytes);
            return Optional.of(bytes);
        } catch (IOException e) {
            log.warn("Icon cache I/O failed for icon {}: {}", iconId, e.getMessage());
            return Optional.empty();
        } finally {
            fetchLock.unlock();
        }
    }

    private byte[] fetchFromXivapi(int iconId) {
        String folder = String.format("%06d", (iconId / 1000) * 1000);
        String file = String.format("%06d", iconId);
        String path = "ui/icon/" + folder + "/" + file + ".tex";
        String url = SOURCE_BASE + "?path=" + java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8)
                + "&format=png";
        try {
            var req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "lolorito icon-proxy (+https://github.com/chojo1)")
                    .GET()
                    .build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) return null;
            return res.body();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("XIVAPI icon fetch failed for icon {}: {}", iconId, e.getMessage());
            return null;
        }
    }

    private Path pathFor(int iconId) {
        String folder = String.format("%06d", (iconId / 1000) * 1000);
        String file = String.format("%06d", iconId) + ".png";
        return cacheRoot.resolve(folder).resolve(file);
    }
}
