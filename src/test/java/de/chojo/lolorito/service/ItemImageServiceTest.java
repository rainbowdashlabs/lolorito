/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ItemImageServiceTest {

    private static final Path TEMP_DATA = Path.of(System.getProperty("java.io.tmpdir"), "lolorito-icon-test");

    @BeforeAll
    static void configureDataDir() {
        System.setProperty("lolorito.dataDir", TEMP_DATA.toString());
    }

    @Test
    void unknownItemReturnsEmpty() {
        var catalog = new ItemCatalog();
        var svc = new ItemImageService(catalog, HttpClient.newHttpClient());
        assertThat(svc.iconBytes(999_999_999)).isEmpty();
    }

    @Test
    void fetchesFromHttpAndWritesToCacheOnFirstHit() throws Exception {
        // Fake catalog: one item id → icon id 12345.
        var catalog = new StubCatalog(Map.of(500_001, 12_345));
        byte[] payload = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        var svc = new ItemImageService(catalog, new StubClient(200, payload));

        var res1 = svc.iconBytes(500_001);
        assertThat(res1).contains(payload);

        // Second hit should hit the cache instead — swap in a client that would return junk.
        var res2 = new ItemImageService(catalog, new StubClient(500, new byte[0])).iconBytes(500_001);
        assertThat(res2).contains(payload);
    }

    @Test
    void non2xxResponseYieldsEmptyOptional() {
        var catalog = new StubCatalog(Map.of(500_002, 99_999));
        var svc = new ItemImageService(catalog, new StubClient(503, new byte[0]));
        assertThat(svc.iconBytes(500_002)).isEmpty();
    }

    @Test
    void ioExceptionDuringSendYieldsEmptyOptional() {
        var catalog = new StubCatalog(Map.of(500_003, 88_888));
        var throwing = new StubClient(0, new byte[0]);
        throwing.throwOnSend = new IOException("boom");
        var svc = new ItemImageService(catalog, throwing);
        assertThat(svc.iconBytes(500_003)).isEmpty();
    }

    /** Minimal ItemCatalog stub — pre-seeded with our test icon mappings. */
    private static final class StubCatalog extends ItemCatalog {
        private final Map<Integer, Integer> iconById;

        StubCatalog(Map<Integer, Integer> iconById) {
            super();
            this.iconById = new HashMap<>(iconById);
        }

        @Override
        public int iconIdFor(int itemId) {
            return iconById.getOrDefault(itemId, -1);
        }
    }

    /** Bare-minimum HttpClient stub — returns a fixed body/status for any request. */
    private static class StubClient extends HttpClient {
        private final int status;
        private final byte[] body;
        IOException throwOnSend;

        StubClient(int status, byte[] body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public java.util.Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public java.util.Optional<java.time.Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public java.util.Optional<java.net.CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public java.util.Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public java.util.Optional<java.net.ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            if (throwOnSend != null) throw throwOnSend;
            return (HttpResponse<T>) new StubResponse(request, status, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest req, HttpResponse.BodyHandler<T> h) {
            try {
                return CompletableFuture.completedFuture(send(req, h));
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest req, HttpResponse.BodyHandler<T> h, HttpResponse.PushPromiseHandler<T> p) {
            return sendAsync(req, h);
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            try {
                return javax.net.ssl.SSLContext.getDefault();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return new javax.net.ssl.SSLParameters();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }

    private static final class StubResponse implements HttpResponse<byte[]> {
        private final HttpRequest req;
        private final int status;
        private final byte[] body;

        StubResponse(HttpRequest req, int status, byte[] body) {
            this.req = req;
            this.status = status;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return req;
        }

        @Override
        public Optional<HttpResponse<byte[]>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(Map.<String, List<String>>of(), (a, b) -> true);
        }

        @Override
        public byte[] body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public java.net.URI uri() {
            return req.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
